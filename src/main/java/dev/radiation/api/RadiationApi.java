package dev.radiation.api;

import dev.radiation.config.RadiationConfig;
import dev.radiation.world.RadiationSources;
import dev.radiation.world.RadiationTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Stable entry point for other mods. Everything here is server-side and must be called on the server thread
 * while a server is running.
 */
public final class RadiationApi {
	private RadiationApi() {
	}

	/** How a point source weakens towards its radius; mirrors the falloffs of {@code /radiation source add}. */
	public enum Falloff {
		CONSTANT, LINEAR, QUADRATIC, INVERSE_SQUARE
	}

	/**
	 * Adds a persistent point source, exactly as if an admin had used {@code /radiation source add}.
	 *
	 * @param name     preferred name; a numeric suffix is appended if it is already taken
	 * @param rads     rads per second at the centre
	 * @param shielded whether solid blocks between source and player absorb radiation
	 * @return the name the source was stored under (usable with {@link #removeSource} and {@code /radiation source remove})
	 */
	public static String addSource(ServerLevel level, String name, Vec3 pos, float rads, float radius, Falloff falloff, boolean shielded) {
		return addSource(level, name, pos, rads, radius, falloff, shielded, 0);
	}

	/**
	 * Like {@link #addSource(ServerLevel, String, Vec3, float, float, Falloff, boolean)}, for a source that decays:
	 * {@code rads} now, half of it after {@code halfLifeTicks} game ticks (24000 = one Minecraft day), and so on. It is
	 * removed by itself once it has decayed to practically nothing. 0 = no decay.
	 */
	public static String addSource(ServerLevel level, String name, Vec3 pos, float rads, float radius, Falloff falloff, boolean shielded,
			long halfLifeTicks) {
		return addSource(level, name, pos, rads, radius, falloff, shielded, halfLifeTicks, 0);
	}

	/**
	 * A decaying source of which a part never decays, e.g. fallout: {@code longLivedFraction} of {@code rads} stays (caesium),
	 * the rest halves every {@code halfLifeTicks} (iodine).
	 */
	public static String addSource(ServerLevel level, String name, Vec3 pos, float rads, float radius, Falloff falloff, boolean shielded,
			long halfLifeTicks, float longLivedFraction) {
		RadiationSources sources = RadiationTracker.sources();
		String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.+-]", "_");
		String unique = base;
		for (int i = 2; sources.source(unique) != null; i++) {
			unique = base + "_" + i;
		}
		RadiationSources.PointSource source = new RadiationSources.PointSource();
		source.name = unique;
		source.dimension = RadiationTracker.dimensionId(level);
		source.x = pos.x;
		source.y = pos.y;
		source.z = pos.z;
		source.rads = rads;
		source.radius = radius;
		source.falloff = RadiationSources.Falloff.valueOf(falloff.name());
		source.shielded = shielded;
		source.halfLifeTicks = Math.max(0, halfLifeTicks);
		source.startTime = level.getGameTime();
		source.longLivedFraction = Math.clamp(longLivedFraction, 0f, 1f);
		sources.sources.add(source);
		sources.markDirty();
		return unique;
	}

	/**
	 * Moves and/or changes an existing source, e.g. a drifting cloud; its decay starts again from {@code rads} now.
	 *
	 * @return false if there is no source of that name (any more)
	 */
	public static boolean updateSource(ServerLevel level, String name, Vec3 pos, float rads, float radius) {
		RadiationSources sources = RadiationTracker.sources();
		RadiationSources.PointSource source = sources.source(name);
		if (source == null) {
			return false;
		}
		source.dimension = RadiationTracker.dimensionId(level);
		source.x = pos.x;
		source.y = pos.y;
		source.z = pos.z;
		source.rads = rads;
		source.radius = radius;
		source.startTime = level.getGameTime();
		sources.markDirty();
		return true;
	}

	/**
	 * Releases a radioactive cloud at {@code pos}: it rises to {@code altitude} blocks above the ground, drifts with the
	 * wind, irradiates what is under it and leaves fallout (more where it rains, which also washes it out quickly).
	 *
	 * @param strength dose rate in rad/s on the ground right under it while it is {@code radius} blocks wide; it weakens as
	 *                 it spreads and drops its fallout
	 */
	public static void releaseCloud(ServerLevel level, Vec3 pos, double strength, double radius, double altitude) {
		dev.radiation.world.Clouds.release(level, pos, strength, radius, altitude);
	}

	/**
	 * Shows a column of dark smoke rising {@code height} blocks from {@code foot} (a burning reactor, a fire...), drawn by
	 * clients up to a kilometre away; {@code width} at the foot, wider further up. It lasts {@code ticks} ticks unless
	 * renewed with the same {@code key}. Purely visual.
	 */
	public static void smokeColumn(ServerLevel level, String key, Vec3 foot, double width, double height, int ticks) {
		dev.radiation.world.Clouds.smoke(level, key, foot, width, height, 1, ticks);
	}

	/** The wind that carries clouds, in blocks per tick (horizontal). */
	public static Vec3 wind(ServerLevel level) {
		return dev.radiation.world.Wind.velocity(level);
	}

	/** Whether a source of that name exists. */
	public static boolean hasSource(String name) {
		return RadiationTracker.sources().source(name) != null;
	}

	/** @return whether a source of that name existed */
	public static boolean removeSource(String name) {
		RadiationSources sources = RadiationTracker.sources();
		RadiationSources.PointSource source = sources.source(name);
		if (source == null) {
			return false;
		}
		sources.sources.remove(source);
		sources.markDirty();
		return true;
	}

	/**
	 * Makes the block at {@code pos} radiate: {@code radsAtOneMetre} rads per second at one metre, falling off with the
	 * square of the distance, absorbed by blocks in between (concrete and water better than other blocks), up to
	 * {@code radius}. Calling it again updates the strength; 0 or less removes it. The emitter is saved with the world and
	 * dropped automatically when the block at that position changes.
	 */
	public static void setEmitter(ServerLevel level, BlockPos pos, float radsAtOneMetre, float radius) {
		RadiationSources sources = RadiationTracker.sources();
		String dim = RadiationTracker.dimensionId(level);
		if (radsAtOneMetre <= 0) {
			sources.removeEmitter(RadiationSources.Emitter.key(dim, pos.getX(), pos.getY(), pos.getZ()));
			return;
		}
		RadiationSources.Emitter existing = sources.emitterIndex().get(RadiationSources.Emitter.key(dim, pos.getX(), pos.getY(), pos.getZ()));
		String block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
		if (existing != null && existing.block.equals(block)) {
			if (Math.abs(existing.rads - radsAtOneMetre) > existing.rads * 0.002 || existing.radius != radius) {
				existing.rads = radsAtOneMetre;
				existing.radius = radius;
				sources.markDirty();
			}
			return;
		}
		RadiationSources.Emitter e = new RadiationSources.Emitter();
		e.dimension = dim;
		e.x = pos.getX();
		e.y = pos.getY();
		e.z = pos.getZ();
		e.block = block;
		e.rads = radsAtOneMetre;
		e.radius = radius;
		sources.putEmitter(e);
	}

	public static void removeEmitter(ServerLevel level, BlockPos pos) {
		setEmitter(level, pos, 0, 0);
	}

	/** A sensible cut-off radius for an emitter: where it falls below 0.01 rad/s unshielded (at most 96 blocks). */
	public static float radiusFor(float radsAtOneMetre) {
		return (float) Math.clamp(Math.sqrt(Math.max(0, radsAtOneMetre) / 0.01), 4, 96);
	}

	/** Fraction of radiation that gets through the blocks on the line between two points. */
	public static double transmission(ServerLevel level, Vec3 from, Vec3 to) {
		return RadiationTracker.transmission(level, from, to, null, RadiationConfig.get());
	}

	/** Raw radiation (before the player's protection) at a position, in rads per second. */
	public static float exposureAt(ServerLevel level, Vec3 pos) {
		return RadiationTracker.exposureAt(level, pos, null);
	}

	public static float getRads(ServerPlayer player) {
		return RadiationTracker.getRads(player);
	}

	/** Adds (or with a negative amount removes) absorbed rads, ignoring the player's protection. */
	public static void addRads(ServerPlayer player, float amount) {
		RadiationTracker.addRads(player, amount);
	}

	/**
	 * Adds a dose of incoming radiation, reduced by the player's hazmat gear and Rad-X like ambient radiation is.
	 * Creative and spectator players are skipped unless the config says otherwise.
	 */
	public static void irradiate(ServerPlayer player, float amount) {
		RadiationConfig config = RadiationConfig.get();
		if (!config.affectCreativeAndSpectator && (player.isCreative() || player.isSpectator())) {
			return;
		}
		RadiationTracker.addRads(player, amount * (1 - RadiationTracker.protection(player, config)));
	}
}
