package dev.radiation.world;

import dev.radiation.RadiationMod;
import dev.radiation.config.RadiationConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Radioactivity lying in the ground and in water: fallout from clouds, fission products spilled on the floor or kept in
 * a chest, runoff from decontamination. Kept per 4x4 column and per height (a spill on a hall floor and fallout on its roof
 * are different patches), so it can be washed off a small area again.
 *
 * <p>Each patch holds two parts. <b>Fallout</b> fades like iodine-131 except for a long-lived share like caesium-137, and is
 * gone after {@code clouds.falloutLifetimeDays} (the same rules as 1.9's fallout sources). <b>Lasting</b> contamination
 * (spilled fission products) has the half-life {@code contamination.lastingHalfLifeDays} and stays until it is washed off.
 *
 * <p>Ground patches irradiate whatever is near (dose rate measured one metre above an even patch; neighbours within a few
 * blocks count, walls and roofs shield). Water patches irradiate whoever is in that water; a lake mixes slowly.
 */
public final class Contamination {
	public static final int SHIFT = 2;
	/** Height not known yet: deposited on unloaded land, settled on the real surface once the chunk is loaded. */
	static final int UNKNOWN = Integer.MIN_VALUE;
	/** Iodine-131: 8 days; the long-lived share fades with clouds.falloutLongHalfLifeDays. */
	static final long IODINE_HALF_LIFE = 8 * 24000L;
	private static final float GONE = 0.0005f;
	private static final int FILE_VERSION = 1;

	/** One contaminated patch: a 4x4 column at one height. */
	public static final class Patch {
		final int cx, cz;
		/** The air block just above the contaminated surface (for water: just above the water). */
		int y;
		/** Fallout dose rate (rad/s) at {@link #falloutAt} and its long-lived share. */
		float fallout, longShare;
		long falloutAt;
		/** Lasting contamination (rad/s) at {@link #lastingAt}. */
		float lasting;
		long lastingAt;

		Patch(int cx, int cz, int y) {
			this.cx = cx;
			this.cz = cz;
			this.y = y;
		}

		public float radsAt(long now) {
			return falloutAt(now) + lastingAt(now);
		}

		float falloutAt(long now) {
			if (fallout <= 0) return 0;
			RadiationConfig.Clouds cfg = RadiationConfig.get().clouds;
			long age = Math.max(0, now - falloutAt);
			double longHalf = cfg.falloutLongHalfLifeDays * 24000.0;
			double v = fallout * ((1 - longShare) * Math.pow(0.5, age / (double) IODINE_HALF_LIFE)
					+ longShare * (longHalf > 0 ? Math.pow(0.5, age / longHalf) : 1));
			double lifetime = cfg.falloutLifetimeDays * 24000.0;
			if (lifetime > 0) {
				v *= Math.clamp((lifetime - age) / (0.2 * lifetime), 0, 1);
			}
			return (float) v;
		}

		float lastingAt(long now) {
			if (lasting <= 0) return 0;
			double half = RadiationConfig.get().contamination.lastingHalfLifeDays * 24000.0;
			return half > 0 ? (float) (lasting * Math.pow(0.5, Math.max(0, now - lastingAt) / half)) : lasting;
		}

		/** Adds fallout (rad/s now) with the given long-lived share; the decay starts again from now. */
		void addFallout(long now, float rads, float share) {
			RadiationConfig.Clouds cfg = RadiationConfig.get().clouds;
			long age = Math.max(0, now - falloutAt);
			double longHalf = cfg.falloutLongHalfLifeDays * 24000.0;
			double fade = 1;
			double lifetime = cfg.falloutLifetimeDays * 24000.0;
			if (lifetime > 0 && fallout > 0) fade = Math.clamp((lifetime - age) / (0.2 * lifetime), 0, 1);
			double shortPart = fallout <= 0 ? 0 : fallout * (1 - longShare) * Math.pow(0.5, age / (double) IODINE_HALF_LIFE) * fade;
			double longPart = fallout <= 0 ? 0 : fallout * longShare * (longHalf > 0 ? Math.pow(0.5, age / longHalf) : 1) * fade;
			shortPart += rads * (1 - share);
			longPart += rads * share;
			fallout = (float) (shortPart + longPart);
			longShare = fallout > 0 ? (float) (longPart / fallout) : 0;
			falloutAt = now;
		}

		void addLasting(long now, float rads) {
			lasting = lastingAt(now) + rads;
			lastingAt = now;
		}

		/** Washes off a share of both parts (their decay clocks keep running). */
		void scale(float keep) {
			fallout *= keep;
			lasting *= keep;
		}
	}

	/** Per dimension: column key -> patches of that column (usually one). */
	private static final Map<String, Long2ObjectOpenHashMap<List<Patch>>> GROUND = new HashMap<>();
	private static final Map<String, Long2ObjectOpenHashMap<List<Patch>>> WATER = new HashMap<>();
	private static Path path;
	private static boolean dirty;
	private static long version;

	private Contamination() {
	}

	public static long column(int cx, int cz) {
		return ChunkPos.pack(cx, cz);
	}

	/** Changes whenever patches come or go (for the land sweep's index of hot chunks). */
	public static long version() {
		return version;
	}

	// ------------------------------------------------------------------ lifecycle

	public static void load(MinecraftServer server) {
		GROUND.clear();
		WATER.clear();
		path = server.getWorldPath(LevelResource.ROOT).resolve("radiation_contamination.dat");
		dirty = false;
		version++;
		if (!Files.exists(path)) {
			return;
		}
		try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new java.util.zip.GZIPInputStream(Files.newInputStream(path))))) {
			int fileVersion = in.readInt();
			if (fileVersion != FILE_VERSION) {
				RadiationMod.LOGGER.warn("{}: unknown version {}, ignored", path, fileVersion);
				return;
			}
			for (Map<String, Long2ObjectOpenHashMap<List<Patch>>> map : List.of(GROUND, WATER)) {
				int dims = in.readInt();
				for (int d = 0; d < dims; d++) {
					String dim = in.readUTF();
					int n = in.readInt();
					Long2ObjectOpenHashMap<List<Patch>> cols = map.computeIfAbsent(dim, k -> new Long2ObjectOpenHashMap<>());
					for (int i = 0; i < n; i++) {
						Patch p = new Patch(in.readInt(), in.readInt(), in.readInt());
						p.fallout = in.readFloat();
						p.longShare = in.readFloat();
						p.falloutAt = in.readLong();
						p.lasting = in.readFloat();
						p.lastingAt = in.readLong();
						cols.computeIfAbsent(column(p.cx, p.cz), k -> new ArrayList<>(1)).add(p);
					}
				}
			}
		} catch (Exception e) {
			RadiationMod.LOGGER.error("Could not read {}", path, e);
		}
		RadiationMod.LOGGER.info("Contamination: {} ground and {} water patches", count(GROUND), count(WATER));
	}

	public static void saveIfDirty() {
		if (dirty) save();
	}

	public static void save() {
		if (path == null) return;
		dirty = false;
		try {
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new java.util.zip.GZIPOutputStream(Files.newOutputStream(tmp))))) {
				out.writeInt(FILE_VERSION);
				for (Map<String, Long2ObjectOpenHashMap<List<Patch>>> map : List.of(GROUND, WATER)) {
					out.writeInt(map.size());
					for (var e : map.entrySet()) {
						out.writeUTF(e.getKey());
						List<Patch> all = new ArrayList<>();
						for (List<Patch> l : e.getValue().values()) all.addAll(l);
						out.writeInt(all.size());
						for (Patch p : all) {
							out.writeInt(p.cx);
							out.writeInt(p.cz);
							out.writeInt(p.y);
							out.writeFloat(p.fallout);
							out.writeFloat(p.longShare);
							out.writeLong(p.falloutAt);
							out.writeFloat(p.lasting);
							out.writeLong(p.lastingAt);
						}
					}
				}
			}
			Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			RadiationMod.LOGGER.error("Could not write {}", path, e);
		}
	}

	public static void unload() {
		save();
		path = null;
		GROUND.clear();
		WATER.clear();
	}

	private static int count(Map<String, Long2ObjectOpenHashMap<List<Patch>>> map) {
		int n = 0;
		for (var cols : map.values()) for (List<Patch> l : cols.values()) n += l.size();
		return n;
	}

	public static int groundPatches() {
		return count(GROUND);
	}

	public static int waterPatches() {
		return count(WATER);
	}

	// ------------------------------------------------------------------ surfaces

	/** The air block above the surface of a column, or UNKNOWN if its chunk is not loaded. */
	private static int surface(ServerLevel level, int x, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) return UNKNOWN;
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private static boolean water(ServerLevel level, int x, int y, int z) {
		return level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
	}

	private static List<Patch> patches(Map<String, Long2ObjectOpenHashMap<List<Patch>>> map, String dim, int cx, int cz, boolean create) {
		Long2ObjectOpenHashMap<List<Patch>> cols = map.get(dim);
		if (cols == null) {
			if (!create) return null;
			cols = new Long2ObjectOpenHashMap<>();
			map.put(dim, cols);
		}
		List<Patch> list = cols.get(column(cx, cz));
		if (list == null && create) {
			list = new ArrayList<>(1);
			cols.put(column(cx, cz), list);
		}
		return list;
	}

	/** The patch at about that height (within a block), made if missing. */
	private static Patch patch(Map<String, Long2ObjectOpenHashMap<List<Patch>>> map, String dim, int cx, int cz, int y) {
		List<Patch> list = patches(map, dim, cx, cz, true);
		for (Patch p : list) {
			if (p.y == y || p.y != UNKNOWN && y != UNKNOWN && Math.abs(p.y - y) <= 1) return p;
		}
		Patch p = new Patch(cx, cz, y);
		list.add(p);
		version++;
		return p;
	}

	/**
	 * Where contamination landing on {@code (x, y, z)} ends up: in the water if it is water there, otherwise on the ground
	 * at that height. {@code y} UNKNOWN = on the surface of the column, wherever that is.
	 */
	private static void land(ServerLevel level, int x, int y, int z, Consumer<Patch> add) {
		String dim = RadiationTracker.dimensionId(level);
		int surface = y == UNKNOWN ? surface(level, x, z) : y;
		if (surface != UNKNOWN && water(level, x, surface - 1, z)) {
			// on a lake or river: up to its surface
			int top = surface;
			while (water(level, x, top, z) && top < surface + 64) top++;
			add.accept(patch(WATER, dim, x >> SHIFT, z >> SHIFT, top));
		} else {
			add.accept(patch(GROUND, dim, x >> SHIFT, z >> SHIFT, surface));
		}
		dirty = true;
	}

	// ------------------------------------------------------------------ adding

	/**
	 * Fallout from a cloud: {@code rads} at the centre, fading linearly to nothing at {@code radius} (the same dose field as
	 * the fallout sources up to 1.9), on the surface of every 4x4 column in reach.
	 */
	public static void depositFallout(ServerLevel level, double x, double z, double rads, double radius, float longShare) {
		long now = level.getGameTime();
		int c0x = (int) Math.floor((x - radius) / 4), c1x = (int) Math.floor((x + radius) / 4);
		int c0z = (int) Math.floor((z - radius) / 4), c1z = (int) Math.floor((z + radius) / 4);
		for (int cx = c0x; cx <= c1x; cx++) {
			for (int cz = c0z; cz <= c1z; cz++) {
				double dx = cx * 4 + 2 - x, dz = cz * 4 + 2 - z;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d >= radius) continue;
				float value = (float) (rads * (1 - d / radius));
				if (value < GONE) continue;
				land(level, cx * 4 + 2, UNKNOWN, cz * 4 + 2, p -> p.addFallout(now, value, longShare));
			}
		}
	}

	/** Lasting contamination (spilled fission products...) on the surface at {@code pos} (the air block above it). */
	public static void contaminate(ServerLevel level, BlockPos pos, float rads) {
		if (!(rads > 0)) return;
		long now = level.getGameTime();
		land(level, pos.getX(), pos.getY(), pos.getZ(), p -> p.addLasting(now, rads));
	}

	/**
	 * The contamination (rad/s) of the patch at {@code pos}: the ground patch of that column within a block of that
	 * height, or, if {@code pos} is in water, that water.
	 */
	public static float at(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		String dim = RadiationTracker.dimensionId(level);
		boolean wet = level.getFluidState(pos).is(FluidTags.WATER);
		List<Patch> list = patches(wet ? WATER : GROUND, dim, pos.getX() >> SHIFT, pos.getZ() >> SHIFT, false);
		if (list == null) return 0;
		float most = 0;
		for (Patch p : list) {
			boolean match = wet ? p.y >= pos.getY() && p.y - pos.getY() <= 64 : p.y != UNKNOWN && Math.abs(p.y - pos.getY()) <= 1;
			if (match) most = Math.max(most, p.radsAt(now));
		}
		return most;
	}

	/** What a Geiger counter reads off one patch: fallout and lasting parts now (rad/s), water or ground, and the strongest patch next to it. */
	public record Reading(float fallout, float lasting, boolean water, int y, float strongestNear) {
		public float total() {
			return fallout + lasting;
		}
	}

	/**
	 * The patch at {@code pos} (the air block above the ground, or a water block), split into fallout and lasting
	 * contamination, plus the strongest patch in the eight columns around it at about that height.
	 */
	public static Reading read(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		String dim = RadiationTracker.dimensionId(level);
		boolean wet = level.getFluidState(pos).is(FluidTags.WATER);
		Map<String, Long2ObjectOpenHashMap<List<Patch>>> map = wet ? WATER : GROUND;
		int cx = pos.getX() >> SHIFT, cz = pos.getZ() >> SHIFT;
		float fallout = 0, lasting = 0, near = 0;
		int y = pos.getY();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				List<Patch> list = patches(map, dim, cx + dx, cz + dz, false);
				if (list == null) continue;
				for (Patch p : list) {
					boolean match = wet ? p.y >= pos.getY() && p.y - pos.getY() <= 64 : p.y != UNKNOWN && Math.abs(p.y - pos.getY()) <= 1;
					if (!match) continue;
					if (dx == 0 && dz == 0) {
						if (p.radsAt(now) > fallout + lasting) {
							fallout = p.falloutAt(now);
							lasting = p.lastingAt(now);
							y = p.y;
						}
					} else {
						near = Math.max(near, p.radsAt(now));
					}
				}
			}
		}
		return new Reading(fallout, lasting, wet, y, near);
	}

	/** Fallout-like contamination in the water patch at {@code pos} (runoff). */
	private static void intoWater(ServerLevel level, Patch water, float fallout, float share, float lasting) {
		long now = level.getGameTime();
		if (fallout > 0) water.addFallout(now, fallout, share);
		if (lasting > 0) water.addLasting(now, lasting);
		dirty = true;
	}

	// ------------------------------------------------------------------ dose rates

	/**
	 * Dose rate at a point from contaminated ground near it: every patch within a few blocks counts by how near it is
	 * (an even field gives its own value one metre above it), weaker higher up, shielded by what lies in between.
	 */
	public static double groundDose(ServerLevel level, Vec3 pos, RadiationConfig config) {
		Long2ObjectOpenHashMap<List<Patch>> cols = GROUND.get(RadiationTracker.dimensionId(level));
		if (cols == null || cols.isEmpty()) return 0;
		long now = level.getGameTime();
		int pcx = (int) Math.floor(pos.x) >> SHIFT, pcz = (int) Math.floor(pos.z) >> SHIFT;
		double weights = 0, sum = 0;
		for (int cx = pcx - 1; cx <= pcx + 1; cx++) {
			for (int cz = pcz - 1; cz <= pcz + 1; cz++) {
				double dx = cx * 4 + 2 - pos.x, dz = cz * 4 + 2 - pos.z;
				double w = 1 / (1 + (dx * dx + dz * dz) / 16);
				weights += w;
				List<Patch> list = cols.get(column(cx, cz));
				if (list == null) continue;
				for (int i = 0; i < list.size(); i++) {
					Patch p = list.get(i);
					if (p.y == UNKNOWN) {
						settle(level, p, cols);
						if (i >= list.size() || list.get(i) != p) {
							// it was water: moved over to the water patches
							i--;
							continue;
						}
						if (p.y == UNKNOWN) continue;
					}
					float rads = p.radsAt(now);
					if (rads < GONE) continue;
					double h = pos.y - p.y;
					if (h < -24 || h > 64) continue;
					double height = h <= 1.5 ? 1 : 1 / (1 + (h - 1.5) / 8);
					double value = rads * w * height;
					if (value < EmitterIndex.NEGLIGIBLE) continue;
					Vec3 from = new Vec3(cx * 4 + 2, p.y + 0.5, cz * 4 + 2);
					sum += value * RadiationTracker.transmission(level, from, pos, null, config);
				}
			}
		}
		return weights > 0 ? sum / weights : 0;
	}

	/** Dose rate of the water around a point, if the point is in water. */
	public static double waterDose(ServerLevel level, Vec3 pos) {
		Long2ObjectOpenHashMap<List<Patch>> cols = WATER.get(RadiationTracker.dimensionId(level));
		if (cols == null || cols.isEmpty()) return 0;
		BlockPos b = BlockPos.containing(pos);
		if (!level.getFluidState(b).is(FluidTags.WATER)) return 0;
		List<Patch> list = cols.get(column(b.getX() >> SHIFT, b.getZ() >> SHIFT));
		if (list == null) return 0;
		long now = level.getGameTime();
		double most = 0;
		for (Patch p : list) {
			// the patch's surface above the point, and not a different pond far below or above
			if (p.y >= b.getY() && p.y - b.getY() <= 64) most = Math.max(most, p.radsAt(now));
		}
		return most;
	}

	/** A patch put down on unloaded land gets its real surface (and moves into the water if that is water). */
	private static void settle(ServerLevel level, Patch p, Long2ObjectOpenHashMap<List<Patch>> cols) {
		int x = p.cx * 4 + 2, z = p.cz * 4 + 2;
		int s = surface(level, x, z);
		if (s == UNKNOWN) return;
		if (water(level, x, s - 1, z)) {
			List<Patch> list = cols.get(column(p.cx, p.cz));
			if (list != null) list.remove(p);
			int top = s;
			while (water(level, x, top, z) && top < s + 64) top++;
			Patch w = patch(WATER, RadiationTracker.dimensionId(level), p.cx, p.cz, top);
			long now = level.getGameTime();
			w.addFallout(now, p.falloutAt(now), p.longShare);
			if (p.lasting > 0) w.addLasting(now, p.lastingAt(now));
			p.y = top;
			version++;
		} else {
			p.y = s;
		}
		dirty = true;
	}

	// ------------------------------------------------------------------ washing off

	/**
	 * Washes {@code share} of the contamination off the ground within {@code radius} blocks of {@code centre} (patches
	 * within two blocks of its height). {@code runoff} of what comes off flows into the nearest water within 16 blocks, the
	 * rest goes down the drain. Returns the dose rate (rad/s) washed off.
	 */
	public static float wash(ServerLevel level, BlockPos centre, double radius, float share, float runoff) {
		Long2ObjectOpenHashMap<List<Patch>> cols = GROUND.get(RadiationTracker.dimensionId(level));
		if (cols == null || cols.isEmpty()) return 0;
		long now = level.getGameTime();
		float fallout = 0, lasting = 0, longPart = 0;
		int r = (int) Math.ceil(radius / 4) + 1;
		int ccx = centre.getX() >> SHIFT, ccz = centre.getZ() >> SHIFT;
		for (int cx = ccx - r; cx <= ccx + r; cx++) {
			for (int cz = ccz - r; cz <= ccz + r; cz++) {
				double dx = cx * 4 + 2 - (centre.getX() + 0.5), dz = cz * 4 + 2 - (centre.getZ() + 0.5);
				if (dx * dx + dz * dz > (radius + 2) * (radius + 2)) continue;
				List<Patch> list = cols.get(column(cx, cz));
				if (list == null) continue;
				for (Patch p : List.copyOf(list)) {
					if (p.y == UNKNOWN) settle(level, p, cols);
					if (p.y == UNKNOWN || !list.contains(p) || Math.abs(p.y - centre.getY()) > 2) continue;
					float f = p.falloutAt(now) * share, l = p.lastingAt(now) * share;
					fallout += f;
					longPart += f * p.longShare;
					lasting += l;
					p.scale(1 - share);
				}
			}
		}
		float removed = fallout + lasting;
		if (removed <= 0) return 0;
		dirty = true;
		if (runoff > 0) {
			Patch water = nearestWater(level, centre, 16);
			if (water != null) {
				intoWater(level, water, fallout * runoff, fallout > 0 ? longPart / fallout : 0, lasting * runoff);
			}
		}
		return removed;
	}

	/** The water patch of the nearest water surface within {@code reach} blocks (looking at column centres). */
	private static Patch nearestWater(ServerLevel level, BlockPos from, int reach) {
		Patch best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -reach; dx <= reach; dx += 2) {
			for (int dz = -reach; dz <= reach; dz += 2) {
				double d = dx * dx + dz * dz;
				if (d > reach * reach || d >= bestD) continue;
				int x = from.getX() + dx, z = from.getZ() + dz;
				int s = surface(level, x, z);
				if (s == UNKNOWN || Math.abs(s - from.getY()) > 8 || !water(level, x, s - 1, z)) continue;
				bestD = d;
				best = patch(WATER, RadiationTracker.dimensionId(level), x >> SHIFT, z >> SHIFT, s);
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ upkeep (part of the land sweep)

	/** Forgets patches that have faded away; lets contaminated water mix with the water next to it. */
	static void upkeep(MinecraftServer server, long now, boolean mix) {
		for (Map<String, Long2ObjectOpenHashMap<List<Patch>>> map : List.of(GROUND, WATER)) {
			for (Long2ObjectOpenHashMap<List<Patch>> cols : map.values()) {
				for (Iterator<List<Patch>> it = cols.values().iterator(); it.hasNext(); ) {
					List<Patch> list = it.next();
					if (list.removeIf(p -> p.radsAt(now) < GONE)) {
						version++;
						dirty = true;
					}
					if (list.isEmpty()) it.remove();
				}
			}
		}
		if (mix) {
			float share = RadiationConfig.get().contamination.waterMixing;
			if (share > 0) {
				for (ServerLevel level : server.getAllLevels()) mixWater(level, now, share);
			}
		}
	}

	/** Each water patch shares {@code share} of its excess with every neighbouring water column at the same level. */
	private static void mixWater(ServerLevel level, long now, float share) {
		Long2ObjectOpenHashMap<List<Patch>> cols = WATER.get(RadiationTracker.dimensionId(level));
		if (cols == null || cols.isEmpty()) return;
		List<Patch> all = new ArrayList<>();
		for (List<Patch> l : cols.values()) all.addAll(l);
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (Patch p : all) {
			float rads = p.radsAt(now);
			if (rads < GONE * 20) continue;
			for (int[] s : sides) {
				int nx = p.cx + s[0], nz = p.cz + s[1];
				int x = nx * 4 + 2, z = nz * 4 + 2;
				if (!level.hasChunk(x >> 4, z >> 4) || !water(level, x, p.y - 1, z)) continue;
				Patch n = patch(WATER, RadiationTracker.dimensionId(level), nx, nz, p.y);
				float diff = rads - n.radsAt(now);
				if (diff <= GONE) continue;
				float moved = diff * share;
				float keep = 1 - moved / Math.max(rads, 1e-9f);
				float f = p.falloutAt(now), l = p.lastingAt(now);
				float fm = f / rads * moved, lm = l / rads * moved;
				p.scale(keep);
				if (fm > 0) n.addFallout(now, fm, p.longShare);
				if (lm > 0) n.addLasting(now, lm);
				rads = p.radsAt(now);
				dirty = true;
			}
		}
	}

	/** Chunks with contamination of at least {@code low} rad/s, for the land sweep. */
	static void markHot(long now, float low, java.util.function.BiConsumer<String, long[]> mark) {
		for (Map<String, Long2ObjectOpenHashMap<List<Patch>>> map : List.of(GROUND, WATER)) {
			for (var e : map.entrySet()) {
				for (List<Patch> list : e.getValue().values()) {
					for (Patch p : list) {
						if (p.radsAt(now) >= low) {
							mark.accept(e.getKey(), new long[]{p.cx * 4L - 6, p.cz * 4L - 6, p.cx * 4L + 9, p.cz * 4L + 9});
						}
					}
				}
			}
		}
	}

	/** What a map shows of a contaminated patch: its 4x4 column (west/north corner), height, dose rate, water or not. */
	public interface PatchVisitor {
		void visit(int x, int y, int z, float rads, boolean water);
	}

	/** Every patch of the level with at least {@code least} rad/s (height UNKNOWN = not settled yet). */
	public static void forEach(ServerLevel level, float least, PatchVisitor visitor) {
		long now = level.getGameTime();
		String dim = RadiationTracker.dimensionId(level);
		for (var entry : List.of(Map.entry(false, GROUND), Map.entry(true, WATER))) {
			Long2ObjectOpenHashMap<List<Patch>> cols = entry.getValue().get(dim);
			if (cols == null) continue;
			for (List<Patch> list : cols.values()) {
				for (Patch p : list) {
					float r = p.radsAt(now);
					if (r >= least) visitor.visit(p.cx * 4, p.y, p.cz * 4, r, entry.getKey());
				}
			}
		}
	}

	/** Lines for {@code /radiation contamination}. */
	public static List<String> describe(ServerLevel level, BlockPos at, int radius) {
		List<String> lines = new ArrayList<>();
		long now = level.getGameTime();
		String dim = RadiationTracker.dimensionId(level);
		for (var entry : List.of(Map.entry("ground", GROUND), Map.entry("water", WATER))) {
			Long2ObjectOpenHashMap<List<Patch>> cols = entry.getValue().get(dim);
			if (cols == null) continue;
			int n = 0;
			double most = 0, total = 0;
			Patch strongest = null;
			for (List<Patch> list : cols.values()) {
				for (Patch p : list) {
					double dx = p.cx * 4 + 2 - at.getX(), dz = p.cz * 4 + 2 - at.getZ();
					if (dx * dx + dz * dz > (double) radius * radius) continue;
					float r = p.radsAt(now);
					n++;
					total += r;
					if (r > most) {
						most = r;
						strongest = p;
					}
				}
			}
			String where = strongest == null ? "" : String.format(Locale.ROOT, " at %d %s %d", strongest.cx * 4 + 2,
					strongest.y == UNKNOWN ? "?" : Integer.toString(strongest.y), strongest.cz * 4 + 2);
			lines.add(String.format(Locale.ROOT, "%s: %d patches within %d blocks, strongest %.3f rad/s%s, sum %.2f", entry.getKey(), n, radius, most, where, total));
		}
		return lines;
	}

	/** Removes every patch within {@code radius} blocks (command). */
	public static int clear(ServerLevel level, BlockPos at, int radius) {
		int n = 0;
		String dim = RadiationTracker.dimensionId(level);
		for (Map<String, Long2ObjectOpenHashMap<List<Patch>>> map : List.of(GROUND, WATER)) {
			Long2ObjectOpenHashMap<List<Patch>> cols = map.get(dim);
			if (cols == null) continue;
			for (Iterator<List<Patch>> it = cols.values().iterator(); it.hasNext(); ) {
				List<Patch> list = it.next();
				int before = list.size();
				list.removeIf(p -> {
					double dx = p.cx * 4 + 2 - at.getX(), dz = p.cz * 4 + 2 - at.getZ();
					return dx * dx + dz * dz <= (double) radius * radius;
				});
				n += before - list.size();
				if (list.isEmpty()) it.remove();
			}
		}
		if (n > 0) {
			version++;
			dirty = true;
		}
		return n;
	}
}
