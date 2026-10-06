package dev.radiation.api;

import dev.radiation.config.RadiationConfig;
import dev.radiation.world.RadiationSources;
import dev.radiation.world.RadiationTracker;
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
		sources.sources.add(source);
		sources.save();
		return unique;
	}

	/** @return whether a source of that name existed */
	public static boolean removeSource(String name) {
		RadiationSources sources = RadiationTracker.sources();
		RadiationSources.PointSource source = sources.source(name);
		if (source == null) {
			return false;
		}
		sources.sources.remove(source);
		sources.save();
		return true;
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
