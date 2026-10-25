package dev.radiation.world;

import dev.radiation.config.RadiationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Radiating things that are not blocks and come and go: an item of spent fuel lying on the ground, a chest with fission
 * products in it, a CASTOR on its wagon. Other mods renew them every few seconds ({@code RadiationApi.pulse}); one that
 * is not renewed goes out after its time. Not saved: they come back as soon as their owner renews them.
 */
final class Pulses {
	record Pulse(String dimension, double x, double y, double z, float rads, float radius, long until) {
	}

	private static final Map<String, Pulse> PULSES = new HashMap<>();

	private Pulses() {
	}

	static void put(String key, Pulse pulse) {
		PULSES.put(key, pulse);
	}

	static void remove(String key) {
		PULSES.remove(key);
	}

	static void clear() {
		PULSES.clear();
	}

	static int count() {
		return PULSES.size();
	}

	static double exposure(ServerLevel level, Vec3 pos, String dimension, RadiationConfig config, List<net.minecraft.network.chat.Component> breakdown) {
		if (PULSES.isEmpty()) return 0;
		long now = level.getGameTime();
		double total = 0;
		for (Iterator<Map.Entry<String, Pulse>> it = PULSES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Pulse> e = it.next();
			Pulse p = e.getValue();
			if (p.until < now) {
				it.remove();
				continue;
			}
			if (!p.dimension.equals(dimension)) continue;
			double dx = p.x - pos.x, dy = p.y - pos.y, dz = p.z - pos.z;
			double d2 = dx * dx + dy * dy + dz * dz;
			double r2 = (double) p.radius * p.radius;
			if (d2 >= r2) continue;
			double value = p.rads / Math.max(0.25, d2) * (1 - d2 / r2);
			if (value <= EmitterIndex.NEGLIGIBLE) continue;
			Vec3 from = new Vec3(p.x, p.y, p.z);
			value *= RadiationTracker.transmission(level, from, pos, BlockPos.containing(from), config);
			if (value > EmitterIndex.NEGLIGIBLE) {
				total += value;
				if (breakdown != null) {
					breakdown.add(net.minecraft.network.chat.Component.translatable("commands.radiation.check.pulse", e.getKey(), String.format(Locale.ROOT, "%.2f", value)));
				}
			}
		}
		return total;
	}
}
