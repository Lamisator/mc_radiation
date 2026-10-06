package dev.radiation.client;

import dev.radiation.network.RadiationSettingsPayload;
import dev.radiation.network.RadiationStatusPayload;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.List;

/** Latest radiation data received from the server. */
public final class ClientRadiationState {
	private ClientRadiationState() {
	}

	public static float maxRads = 1000f;
	public static List<RadiationSettingsPayload.StageInfo> stages = List.of();

	public static float rads;
	/** Rads/s being absorbed; -1 if the player cannot measure it. */
	public static float exposure;
	public static float netRate;
	public static float protection;
	public static long lastUpdateMillis;
	/** Last time anything radiation-related happened, used to fade the HUD out. */
	public static long lastActivityMillis = Long.MIN_VALUE / 2;
	public static boolean received;

	public static void onSettings(RadiationSettingsPayload payload) {
		maxRads = payload.maxRads();
		stages = List.copyOf(payload.stages());
	}

	public static void onStatus(RadiationStatusPayload payload) {
		long now = Util.getMillis();
		boolean changed = Math.abs(payload.rads() - rads) > 0.01f;
		rads = payload.rads();
		exposure = payload.exposure();
		netRate = payload.netRate();
		protection = payload.protection();
		lastUpdateMillis = now;
		received = true;
		if (changed || payload.exposure() > 0.001f) {
			lastActivityMillis = now;
		}
	}

	public static void reset() {
		maxRads = 1000f;
		stages = List.of();
		rads = exposure = netRate = protection = 0;
		received = false;
		lastActivityMillis = Long.MIN_VALUE / 2;
	}

	/** Rads extrapolated between server updates for a smoothly moving bar. */
	public static float predictedRads() {
		float elapsed = Math.min(1.0f, (Util.getMillis() - lastUpdateMillis) / 1000f);
		return Mth.clamp(rads + netRate * elapsed, 0, maxRads);
	}

	/** Index of the current sickness stage, or -1. */
	public static int stageIndex(float value) {
		int index = -1;
		for (int i = 0; i < stages.size(); i++) {
			if (value >= stages.get(i).threshold()) {
				index = i;
			}
		}
		return index;
	}
}
