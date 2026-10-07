package dev.radiation.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.radiation.RadiationMod;
import dev.radiation.network.CloudPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Radioactive clouds: from a burning reactor core (Fission), a nuclear detonation (RedButton) or anything else that
 * calls {@code RadiationApi.releaseCloud}. A cloud rises to its height above the ground and drifts with the {@link Wind},
 * spreading as it goes. It irradiates the land under it (roofs shield people indoors) and leaves fallout behind:
 * sources that fade like iodine-131 except for a long-lived part like caesium-137. Deposits that land in the same
 * 48-block cell add up into one source.
 *
 * <p>Rain washes a cloud out: where it rains under it, it drops its fallout four times as often and five times as
 * heavily per block travelled - the hot spots of Chernobyl's fallout were where it rained - and is gone after a few
 * hundred blocks. A dry cloud fades away only when it has spread too thin, after a few kilometres.
 */
public final class Clouds {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
	private static final int UPDATE_TICKS = 10;
	/**
	 * Dry: a deposit every 32 blocks, taking 0.8 % of the cloud; in rain every 8 blocks, taking {@code rainWashout} times
	 * as much per block (4 % by default) and leaving {@code rainFactor} times as much fallout. All fallout is multiplied by
	 * {@code falloutFactor} (config: {@code clouds}).
	 */
	private static final double DRY_EVERY = 32, DRY_TAKES = 0.008, DRY_RATIO = 0.4;
	private static final double WET_EVERY = 8;
	/** Iodine-131: 8 days; about 15 % of the early fallout dose rate is long-lived caesium. Game time: 1 day = 24000 ticks. */
	private static final long IODINE_HALF_LIFE = 8 * 24000L;
	private static final float LONG_LIVED = 0.15F;
	private static final int CELL = 48;
	private static final int PARTICLE_RANGE = 400;
	private static final double VISIBLE = 1000;

	static final class Cloud {
		String dimension;
		double x, y, z;
		/** Dose rate (rad/s) on the ground under the cloud while it still has its first size. */
		double activity;
		double r0, altitude;
		double travelled, sinceDeposit, ground;
		long age;
		String source;
		boolean raining;
		transient int id;
		transient double radius, density;
	}

	static final class State {
		List<Cloud> clouds = new ArrayList<>();
		double windTowards = Double.NaN;
		double windSpeed;
	}

	private static State state = new State();
	private static int nextId;
	private static Path path;
	/** Fallout cell ("dimension x z") to the source that collects it. */
	private static final Map<String, String> CELLS = new HashMap<>();
	private static final Set<UUID> SEEING = new HashSet<>();
	/** Smoke columns (a burning reactor...): key to column; they vanish unless renewed. */
	private static final Map<String, Column> COLUMNS = new HashMap<>();

	private record Column(int id, String dimension, double x, double y, double z, float width, float height, float density, long until) {
	}

	private Clouds() {
	}

	// ------------------------------------------------------------------ lifecycle

	public static void load(MinecraftServer server) {
		path = server.getWorldPath(LevelResource.ROOT).resolve("radiation_clouds.json");
		state = new State();
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path)) {
				State s = GSON.fromJson(r, State.class);
				if (s != null) state = s;
			} catch (Exception e) {
				RadiationMod.LOGGER.error("Could not read {}", path, e);
			}
		}
		if (state.clouds == null) state.clouds = new ArrayList<>();
		state.clouds.removeIf(c -> c == null || c.dimension == null);
		for (Cloud c : state.clouds) c.id = ++nextId;
		if (Double.isNaN(state.windTowards)) Wind.release(); else Wind.fix(state.windTowards, state.windSpeed);
		CELLS.clear();
		for (RadiationSources.PointSource s : RadiationTracker.sources().sources) {
			if (s.name.startsWith("fallout")) {
				CELLS.put(cell(s.dimension, s.x, s.z), s.name);
			}
		}
		SEEING.clear();
	}

	public static void save() {
		if (path == null) return;
		state.windTowards = Wind.fixed() ? Wind.fixedTowards : Double.NaN;
		state.windSpeed = Wind.fixedSpeed;
		try {
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp)) {
				GSON.toJson(state, w);
			}
			Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			RadiationMod.LOGGER.error("Could not write {}", path, e);
		}
	}

	public static void unload() {
		save();
		path = null;
		state = new State();
		CELLS.clear();
		SEEING.clear();
		COLUMNS.clear();
	}

	// ------------------------------------------------------------------ releasing

	/**
	 * A new cloud. {@code strength}: its dose rate (rad/s) on the ground right under it while it is still {@code radius}
	 * blocks wide; it floats {@code altitude} blocks above the ground once it has risen.
	 */
	public static void release(ServerLevel level, Vec3 at, double strength, double radius, double altitude) {
		if (strength <= dev.radiation.config.RadiationConfig.get().clouds.fadedRads) return;
		Cloud c = new Cloud();
		c.dimension = RadiationTracker.dimensionId(level);
		c.x = at.x;
		c.y = at.y;
		c.z = at.z;
		c.ground = at.y - 2;
		c.activity = strength;
		c.r0 = Math.max(4, radius);
		c.altitude = Math.clamp(altitude, 10, 600);
		c.id = ++nextId;
		c.source = dev.radiation.api.RadiationApi.addSource(level, "radioactive_cloud", at, (float) strength, (float) (c.r0 + 8),
				dev.radiation.api.RadiationApi.Falloff.LINEAR, true);
		state.clouds.add(c);
		save();
	}

	/**
	 * Shows a column of smoke rising {@code height} blocks from {@code foot}, {@code width} blocks wide there, for the next
	 * {@code ticks} ticks; the same {@code key} renews it.
	 */
	public static void smoke(ServerLevel level, String key, Vec3 foot, double width, double height, double density, int ticks) {
		Column old = COLUMNS.get(key);
		COLUMNS.put(key, new Column(old != null ? old.id : ++nextId, RadiationTracker.dimensionId(level), foot.x, foot.y, foot.z, (float) width,
				(float) height, (float) density, level.getGameTime() + ticks));
	}

	public static int count() {
		return state.clouds.size();
	}

	// ------------------------------------------------------------------ every tick

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % UPDATE_TICKS != 0 || state.clouds.isEmpty() && COLUMNS.isEmpty() && SEEING.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		COLUMNS.values().removeIf(c -> c.until < now);
		boolean changed = false;
		for (Iterator<Cloud> it = state.clouds.iterator(); it.hasNext(); ) {
			Cloud c = it.next();
			ServerLevel level = level(server, c.dimension);
			if (level == null) continue;
			if (!drift(level, c)) {
				dev.radiation.api.RadiationApi.removeSource(c.source);
				it.remove();
				changed = true;
			}
		}
		send(server);
		if (changed) save();
	}

	/** Moves a cloud one step; false once it has faded away or rained out. */
	private static boolean drift(ServerLevel level, Cloud c) {
		Vec3 v = Wind.velocity(level).scale(UPDATE_TICKS);
		c.x += v.x;
		c.z += v.z;
		double step = v.horizontalDistance();
		c.travelled += step;
		c.sinceDeposit += step;
		c.age += UPDATE_TICKS;
		int bx = (int) Math.floor(c.x), bz = (int) Math.floor(c.z);
		boolean loaded = level.hasChunk(bx >> 4, bz >> 4);
		if (loaded) {
			c.ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		} else {
			// nobody near: the ground as the world generator would make it, so that fallout lands on it and not in the air
			var source = level.getChunkSource();
			c.ground = source.getGenerator().getBaseHeight(bx, bz, Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState());
		}
		c.raining = level.isRaining() && raining(level, new BlockPos(bx, (int) c.ground, bz));
		double target = c.ground + c.altitude + Math.min(80, c.travelled * 0.05);
		c.y += Math.clamp((target - c.y) * 0.15, -2.0, 8.0);
		dev.radiation.config.RadiationConfig.Clouds cfg = dev.radiation.config.RadiationConfig.get().clouds;
		double radius = c.r0 + cfg.spreadPerBlock * c.travelled;
		double onGround = c.activity * (c.r0 / radius) * (c.r0 / radius);
		if (onGround < cfg.fadedRads || c.age > cfg.maxAgeMinutes * 60L * 20) {
			return false;
		}
		double height = Math.max(0, c.y - c.ground);
		double reach = radius + height + 8;
		double centre = onGround / Math.max(0.05, 1 - height / reach);
		if (!dev.radiation.api.RadiationApi.updateSource(level, c.source, new Vec3(c.x, c.y, c.z), (float) centre, (float) reach)) {
			c.source = dev.radiation.api.RadiationApi.addSource(level, "radioactive_cloud", new Vec3(c.x, c.y, c.z), (float) centre, (float) reach,
					dev.radiation.api.RadiationApi.Falloff.LINEAR, true);
		}
		double every = c.raining ? WET_EVERY : DRY_EVERY;
		double ratio = DRY_RATIO * cfg.falloutFactor * (c.raining ? cfg.rainFactor : 1);
		double takes = c.raining ? DRY_TAKES * WET_EVERY / DRY_EVERY * cfg.rainWashout : DRY_TAKES;
		while (c.sinceDeposit >= every) {
			c.sinceDeposit -= every;
			deposit(level, c.x, c.ground + 1, c.z, ratio * onGround * every / radius, radius * 0.9 + 4);
			c.activity *= 1 - Math.min(0.9, takes);
		}
		c.radius = radius;
		c.density = Math.clamp(Math.sqrt(onGround / 2.0), 0.15, 1.0);
		particles(level, c, radius);
		return true;
	}

	/** Rain or snow falls here (a dry biome, or under a roof, nothing comes down). */
	private static boolean raining(ServerLevel level, BlockPos ground) {
		Biome biome = level.getBiome(ground).value();
		return biome.getPrecipitationAt(ground, level.getSeaLevel()) != Biome.Precipitation.NONE;
	}

	/** Fallout: added to the source of its 48-block cell, or a new one there. */
	private static void deposit(ServerLevel level, double x, double y, double z, double rads, double radius) {
		if (rads < 0.0005) return;
		String dim = RadiationTracker.dimensionId(level);
		String key = cell(dim, x, z);
		RadiationSources sources = RadiationTracker.sources();
		String name = CELLS.get(key);
		RadiationSources.PointSource s = name == null ? null : sources.source(name);
		long now = level.getGameTime();
		if (s != null) {
			s.rads = s.radsAt(now) + (float) rads;
			s.startTime = now;
			s.radius = Math.max(s.radius, (float) radius);
			sources.markDirty();
			return;
		}
		double cx = (Math.floor(x / CELL) + 0.5) * CELL, cz = (Math.floor(z / CELL) + 0.5) * CELL;
		name = dev.radiation.api.RadiationApi.addSource(level, "fallout", new Vec3(cx, y, cz), (float) rads, (float) Math.max(radius, CELL),
				dev.radiation.api.RadiationApi.Falloff.LINEAR, true, IODINE_HALF_LIFE, LONG_LIVED);
		CELLS.put(key, name);
	}

	private static String cell(String dim, double x, double z) {
		return dim + " " + (int) Math.floor(x / CELL) + " " + (int) Math.floor(z / CELL);
	}

	private static void particles(ServerLevel level, Cloud c, double radius) {
		RandomSource random = level.getRandom();
		double r = Math.min(radius, 120);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(c.x, c.y, c.z) > (PARTICLE_RANGE + r) * (PARTICLE_RANGE + r)) continue;
			for (int i = 0; i < 16; i++) {
				double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
				level.sendParticles(player, i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true,
						c.x + Math.cos(a) * d, c.y + (random.nextDouble() - 0.5) * r * 0.5, c.z + Math.sin(a) * d, 1, 0.5, 0.3, 0.5, 0.01);
			}
			// fallout coming down: much more of it in the rain
			for (int i = 0; i < (c.raining ? 18 : 6); i++) {
				double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
				level.sendParticles(player, ParticleTypes.WHITE_ASH, true, false, c.x + Math.cos(a) * d, c.ground + 2 + random.nextDouble() * 10,
						c.z + Math.sin(a) * d, 4, 2, 2, 2, 0);
			}
		}
	}

	/** Every player gets the clouds within a kilometre (far beyond entity tracking). */
	private static void send(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String dim = RadiationTracker.dimensionId(player.level());
			List<CloudPayload.Cloud> list = new ArrayList<>();
			for (Cloud c : state.clouds) {
				if (c.dimension.equals(dim) && c.radius > 0 && player.distanceToSqr(c.x, c.y, c.z) < (VISIBLE + c.radius) * (VISIBLE + c.radius)) {
					list.add(new CloudPayload.Cloud(c.id, c.x, c.y, c.z, (float) c.radius, (float) c.density));
				}
			}
			for (Column c : COLUMNS.values()) {
				if (c.dimension.equals(dim) && player.distanceToSqr(c.x, c.y, c.z) < VISIBLE * VISIBLE) {
					list.add(new CloudPayload.Cloud(c.id, c.x, c.y, c.z, c.width, c.density, c.height));
				}
			}
			if (!list.isEmpty() || SEEING.remove(player.getUUID())) {
				ServerPlayNetworking.send(player, new CloudPayload(list));
				if (!list.isEmpty()) SEEING.add(player.getUUID());
			}
		}
	}

	private static ServerLevel level(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	public static List<String> describe() {
		List<String> lines = new ArrayList<>();
		for (Cloud c : state.clouds) {
			double radius = c.r0 + dev.radiation.config.RadiationConfig.get().clouds.spreadPerBlock * c.travelled;
			lines.add(String.format(Locale.ROOT, "cloud at %.0f %.0f %.0f, %.0f blocks out, %.0f wide, %.3f rad/s below%s", c.x, c.y, c.z, c.travelled,
					2 * radius, c.activity * (c.r0 / radius) * (c.r0 / radius), c.raining ? ", raining out" : ""));
		}
		return lines;
	}
}
