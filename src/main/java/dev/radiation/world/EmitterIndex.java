package dev.radiation.world;

import dev.radiation.config.RadiationConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The radiating blocks of every dimension, grouped into 32-block cells and those into 8-block cells. A dose rate needs
 * the shielding between each block and the point; for a group far enough away the lines from its blocks all run
 * through practically the same ground, so the group gets one line from its (rads-weighted) centre instead of one per
 * block. Near the point every block keeps its own line. A melted-down core leaves thousands of radiating blocks behind;
 * without this, one dose rate cost thousands of block-by-block lines.
 *
 * <p>The strengths are read live from the emitters; the grouping is rebuilt when emitters come or go, and every second
 * while their strengths change (the centres are weighted by strength, the reach by radius).
 */
final class EmitterIndex {
	private static final int FINE_SHIFT = 3;
	private static final int COARSE_SHIFT = 5;
	/** A whole 32-block group this far (blocks between the point and the group's box) takes one line. */
	private static final double COARSE_FAR = 48;
	/** An 8-block group this far takes one line. */
	private static final double FINE_FAR = 12;
	/** Contributions below this (rad/s, before shielding) are not worth a line through the blocks. */
	static final double NEGLIGIBLE = 0.0001;
	private static final int REFRESH_TICKS = 20;

	private static Map<String, List<Group>> byDimension = Map.of();
	private static RadiationSources indexed;
	private static long layout = Long.MIN_VALUE;
	private static long valueVersion = Long.MIN_VALUE;
	private static long builtAt = Long.MIN_VALUE;

	private EmitterIndex() {
	}

	/** A box of emitters: coarse groups hold fine groups, fine groups hold the emitters. */
	static final class Group {
		final List<RadiationSources.Emitter> members = new ArrayList<>();
		final List<Group> children = new ArrayList<>();
		double cx, cy, cz;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		float reach;

		void add(RadiationSources.Emitter e) {
			members.add(e);
			minX = Math.min(minX, e.x);
			minY = Math.min(minY, e.y);
			minZ = Math.min(minZ, e.z);
			maxX = Math.max(maxX, e.x);
			maxY = Math.max(maxY, e.y);
			maxZ = Math.max(maxZ, e.z);
			reach = Math.max(reach, e.radius);
		}

		void finish() {
			double w = 0, x = 0, y = 0, z = 0;
			for (RadiationSources.Emitter e : members) {
				double r = Math.max(e.rads, 1e-6);
				w += r;
				x += r * (e.x + 0.5);
				y += r * (e.y + 0.5);
				z += r * (e.z + 0.5);
			}
			cx = x / w;
			cy = y / w;
			cz = z / w;
		}

		/** Squared distance from the point to the box around the members' block centres. */
		double distanceSqr(Vec3 p) {
			double dx = Math.max(0, Math.max(minX + 0.5 - p.x, p.x - maxX - 0.5));
			double dy = Math.max(0, Math.max(minY + 0.5 - p.y, p.y - maxY - 0.5));
			double dz = Math.max(0, Math.max(minZ + 0.5 - p.z, p.z - maxZ - 0.5));
			return dx * dx + dy * dy + dz * dz;
		}
	}

	static void reset() {
		byDimension = Map.of();
		indexed = null;
		layout = Long.MIN_VALUE;
		valueVersion = Long.MIN_VALUE;
		builtAt = Long.MIN_VALUE;
	}

	private static List<Group> groups(RadiationSources sources, String dimension, long now) {
		boolean stale = sources != indexed || sources.emitterLayout() != layout
				|| sources.version() != valueVersion && (now - builtAt >= REFRESH_TICKS || now < builtAt);
		if (stale) {
			build(sources, now);
		}
		return byDimension.getOrDefault(dimension, List.of());
	}

	private static void build(RadiationSources sources, long now) {
		Map<String, Long2ObjectOpenHashMap<Group>> coarse = new HashMap<>();
		Map<String, Long2ObjectOpenHashMap<Group>> fine = new HashMap<>();
		for (RadiationSources.Emitter e : sources.emitters) {
			if (e.rads <= 0) {
				continue;
			}
			long ck = BlockPos.asLong(e.x >> COARSE_SHIFT, e.y >> COARSE_SHIFT, e.z >> COARSE_SHIFT);
			long fk = BlockPos.asLong(e.x >> FINE_SHIFT, e.y >> FINE_SHIFT, e.z >> FINE_SHIFT);
			Group c = coarse.computeIfAbsent(e.dimension, d -> new Long2ObjectOpenHashMap<>()).computeIfAbsent(ck, k -> new Group());
			Group f = fine.computeIfAbsent(e.dimension, d -> new Long2ObjectOpenHashMap<>()).computeIfAbsent(fk, k -> {
				Group g = new Group();
				c.children.add(g);
				return g;
			});
			c.add(e);
			f.add(e);
		}
		Map<String, List<Group>> result = new HashMap<>();
		coarse.forEach((dimension, groups) -> {
			List<Group> list = new ArrayList<>(groups.values());
			for (Group c : list) {
				c.finish();
				for (Group f : c.children) {
					f.finish();
				}
			}
			result.put(dimension, list);
		});
		byDimension = result;
		indexed = sources;
		layout = sources.emitterLayout();
		valueVersion = sources.version();
		builtAt = now;
	}

	/** Dose rate at {@code pos} from every radiating block of the level, shielding included. */
	static double exposure(ServerLevel level, Vec3 pos, RadiationSources sources, String dimension, RadiationConfig config) {
		double total = 0;
		for (Group c : groups(sources, dimension, level.getGameTime())) {
			double d2 = c.distanceSqr(pos);
			if (d2 >= (double) c.reach * c.reach) {
				continue;
			}
			if (d2 >= COARSE_FAR * COARSE_FAR) {
				total += far(level, pos, c, config);
				continue;
			}
			for (Group f : c.children) {
				double fd2 = f.distanceSqr(pos);
				if (fd2 >= (double) f.reach * f.reach) {
					continue;
				}
				if (fd2 >= FINE_FAR * FINE_FAR) {
					total += far(level, pos, f, config);
					continue;
				}
				for (RadiationSources.Emitter e : f.members) {
					double value = unshielded(e, pos);
					if (value > NEGLIGIBLE) {
						BlockPos blockPos = new BlockPos(e.x, e.y, e.z);
						value *= RadiationTracker.transmission(level, Vec3.atCenterOf(blockPos), pos, blockPos, config);
						if (value > NEGLIGIBLE) {
							total += value;
						}
					}
				}
			}
		}
		return total;
	}

	/** A distant group: every member's own strength and distance, one line from the group's centre for the shielding. */
	private static double far(ServerLevel level, Vec3 pos, Group g, RadiationConfig config) {
		double sum = 0;
		for (RadiationSources.Emitter e : g.members) {
			sum += unshielded(e, pos);
		}
		if (sum <= NEGLIGIBLE) {
			return 0;
		}
		Vec3 centre = new Vec3(g.cx, g.cy, g.cz);
		// the centre may sit inside one of the group's blocks: that block does not shield its own radiation
		BlockPos own = BlockPos.containing(centre);
		double value = sum * RadiationTracker.transmission(level, centre, pos, own, config);
		return value > NEGLIGIBLE ? value : 0;
	}

	/** An emitter's dose rate at a point before shielding: inverse square, faded to zero at its radius. */
	static double unshielded(RadiationSources.Emitter e, Vec3 pos) {
		if (e.rads <= 0) {
			return 0;
		}
		double dx = e.x + 0.5 - pos.x, dy = e.y + 0.5 - pos.y, dz = e.z + 0.5 - pos.z;
		double d2 = dx * dx + dy * dy + dz * dz;
		double r2 = (double) e.radius * e.radius;
		if (d2 >= r2) {
			return 0;
		}
		return e.rads / Math.max(0.25, d2) * (1 - d2 / r2);
	}
}
