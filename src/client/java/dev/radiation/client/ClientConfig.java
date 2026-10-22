package dev.radiation.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.radiation.RadiationMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Per-player presentation settings, stored in config/radiation-client.json. */
public class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("radiation-client.json");
	private static ClientConfig instance = new ClientConfig();

	public static ClientConfig get() {
		return instance;
	}

	public enum HudMode {
		/** Shown while being irradiated or treated, and for a few seconds afterwards (like Fallout). */
		EXPOSURE,
		/** Shown whenever you carry any rads. */
		NONZERO,
		/** Always shown. */
		ALWAYS,
		/** Never shown (Geiger clicks still play). */
		HIDDEN
	}

	/** Distance of the meter from the left edge of the screen, in GUI pixels. */
	public int hudX = 6;
	/** Distance of the meter from the top edge of the screen, in GUI pixels. */
	public int hudY = 6;
	public float hudScale = 1.0f;
	public HudMode hudMode = HudMode.EXPOSURE;
	/** How long the meter stays visible after the last exposure (EXPOSURE mode). */
	public float hudLingerSeconds = 4f;
	/** Holding a Geiger counter in either hand always shows the meter. */
	public boolean showWhileHoldingGeiger = true;
	/** The ambient Geiger clicks while radiation reaches you: on or off ({@code /geiger on|off}). */
	public boolean geigerSound = true;
	/** Their loudness in percent of the original click (1.10.1 and before: always 100 %); {@code /geiger volume <percent>}. */
	public float geigerVolumePercent = 30;
	/** Up to 1.10.1: absolute volume (0.6 by default); converted to geigerVolumePercent on load. */
	public Float geigerVolume;
	/** Geiger clicks per second for each rad per second of exposure. */
	public float geigerClicksPerRad = 2.5f;
	public float geigerMaxClicksPerSecond = 60f;

	public static void load() {
		ClientConfig loaded = null;
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				loaded = GSON.fromJson(reader, ClientConfig.class);
			} catch (Exception e) {
				RadiationMod.LOGGER.error("Could not read {}, using defaults", PATH, e);
			}
		}
		instance = loaded == null ? new ClientConfig() : loaded;
		if (instance.hudMode == null) instance.hudMode = HudMode.EXPOSURE;
		if (instance.hudScale <= 0) instance.hudScale = 1f;
		if (instance.geigerVolume != null) {
			// 1.10.1 and before: 0.6 was the untouched default, which becomes the new default of 30 %
			float old = instance.geigerVolume;
			if (old <= 0) {
				instance.geigerSound = false;
			} else if (Math.abs(old - ORIGINAL_VOLUME) > 1e-4) {
				instance.geigerVolumePercent = old / ORIGINAL_VOLUME * 100;
			}
			instance.geigerVolume = null;
		}
		instance.geigerVolumePercent = Math.clamp(instance.geigerVolumePercent, 0, 200);
		save();
	}

	/** The click volume of 1.10.1 and before, 100 %. */
	public static final float ORIGINAL_VOLUME = 0.6f;

	/** Volume of one ambient click, 0 when they are switched off. */
	public float clickVolume() {
		return geigerSound ? ORIGINAL_VOLUME * geigerVolumePercent / 100f : 0;
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (Exception e) {
			RadiationMod.LOGGER.error("Could not write {}", PATH, e);
		}
	}
}
