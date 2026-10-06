package dev.radiation.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.radiation.RadiationMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side gameplay configuration, stored in config/radiation.json.
 * Reload in-game with /radiation reload.
 */
public class RadiationConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("radiation.json");

	private static RadiationConfig instance = new RadiationConfig();

	public static RadiationConfig get() {
		return instance;
	}

	// --- core ---
	/** Accumulated rads at which the player dies. */
	public float maxRads = 1000f;
	/** Sickness stages, ordered by threshold. */
	public List<Stage> stages = defaultStages();
	/** Rads removed per second without any treatment (0 = rads never wear off, like in Fallout). */
	public float naturalDecayPerSecond = 0f;
	/** How often (in ticks) radiation is evaluated for each player. 20 ticks = 1 second. */
	public int updateIntervalTicks = 10;
	/** Whether players in creative or spectator mode accumulate rads. */
	public boolean affectCreativeAndSpectator = false;

	// --- environment ---
	/** Each solid block between a point source and the player removes this fraction of the remaining radiation. */
	public float shieldingPerBlock = 0.35f;
	/** Rads per second at the centre of a Nuclear Waste Barrel. */
	public float barrelRads = 6f;
	/** Radius in blocks of a Nuclear Waste Barrel's radiation. */
	public float barrelRadius = 6f;

	// --- protection ---
	/** Fraction of incoming radiation blocked by each worn item. Combined additively, capped by maxProtection. */
	public Map<String, Float> protectiveItems = defaultProtectiveItems();
	/** Fraction of incoming radiation blocked by Rad-X (per effect level). */
	public float radXResistance = 0.5f;
	/** Upper limit for the total protection from all sources (1.0 = full immunity possible). */
	public float maxProtection = 0.95f;

	// --- medicine (durations require a restart, amounts apply immediately) ---
	/** Total rads removed by one RadAway. */
	public float radAwayTotalRads = 150f;
	public int radAwayDurationSeconds = 10;
	public int radXDurationSeconds = 240;

	// --- client/HUD behaviour enforced by the server ---
	/** If true, the RAD/s readout and Geiger clicks are only available while carrying a Geiger counter. */
	public boolean requireGeigerCounter = false;

	public static class Stage {
		public float threshold;
		public String name;
		/** ARGB colour of the stage label on the HUD. */
		public String color = "#FFE0A030";
		/** Change of max health in half-hearts (negative = fewer hearts). */
		public double maxHealthModifier = 0;
		public List<EffectEntry> effects = new ArrayList<>();

		public Stage() {
		}

		Stage(float threshold, String name, String color, double maxHealthModifier, EffectEntry... effects) {
			this.threshold = threshold;
			this.name = name;
			this.color = color;
			this.maxHealthModifier = maxHealthModifier;
			this.effects = new ArrayList<>(List.of(effects));
		}

		public int argb() {
			try {
				String hex = color.startsWith("#") ? color.substring(1) : color;
				long value = Long.parseLong(hex, 16);
				return hex.length() <= 6 ? (int) (0xFF000000L | value) : (int) value;
			} catch (NumberFormatException e) {
				return 0xFFE0A030;
			}
		}
	}

	public static class EffectEntry {
		/** Mob effect id, e.g. "minecraft:hunger". */
		public String effect;
		/** 0 = level I, 1 = level II, ... */
		public int amplifier;

		public EffectEntry() {
		}

		EffectEntry(String effect, int amplifier) {
			this.effect = effect;
			this.amplifier = amplifier;
		}
	}

	private static List<Stage> defaultStages() {
		List<Stage> stages = new ArrayList<>();
		stages.add(new Stage(250, "Mild Radiation Sickness", "#FFE8C020", -2,
				new EffectEntry("minecraft:hunger", 0)));
		stages.add(new Stage(500, "Radiation Sickness", "#FFF08020", -4,
				new EffectEntry("minecraft:hunger", 0),
				new EffectEntry("minecraft:weakness", 0)));
		stages.add(new Stage(750, "Severe Radiation Sickness", "#FFFF3030", -8,
				new EffectEntry("minecraft:hunger", 1),
				new EffectEntry("minecraft:weakness", 1),
				new EffectEntry("minecraft:slowness", 0),
				new EffectEntry("minecraft:mining_fatigue", 0)));
		return stages;
	}

	private static Map<String, Float> defaultProtectiveItems() {
		Map<String, Float> items = new LinkedHashMap<>();
		items.put("radiation:hazmat_helmet", 0.225f);
		items.put("radiation:hazmat_chestplate", 0.225f);
		items.put("radiation:hazmat_leggings", 0.225f);
		items.put("radiation:hazmat_boots", 0.225f);
		return items;
	}

	/** The highest stage whose threshold has been reached, or -1 if none. */
	public int stageIndexFor(float rads) {
		int index = -1;
		for (int i = 0; i < stages.size(); i++) {
			if (rads >= stages.get(i).threshold) {
				index = i;
			}
		}
		return index;
	}

	private void sanitize() {
		if (maxRads <= 0) maxRads = 1000f;
		if (updateIntervalTicks < 1) updateIntervalTicks = 1;
		if (stages == null) stages = new ArrayList<>();
		stages.removeIf(s -> s == null || s.name == null);
		stages.sort(Comparator.comparingDouble(s -> s.threshold));
		for (Stage stage : stages) {
			if (stage.effects == null) stage.effects = new ArrayList<>();
			stage.effects.removeIf(e -> e == null || e.effect == null);
		}
		if (protectiveItems == null) protectiveItems = new LinkedHashMap<>();
		shieldingPerBlock = Math.clamp(shieldingPerBlock, 0f, 1f);
		maxProtection = Math.clamp(maxProtection, 0f, 1f);
		radAwayDurationSeconds = Math.max(1, radAwayDurationSeconds);
		radXDurationSeconds = Math.max(1, radXDurationSeconds);
	}

	/** Loads the config from disk, writing defaults if the file is missing. Returns false on a parse error. */
	public static boolean load() {
		RadiationConfig loaded = null;
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				loaded = GSON.fromJson(reader, RadiationConfig.class);
			} catch (Exception e) {
				RadiationMod.LOGGER.error("Could not read {}, keeping previous settings", PATH, e);
				return false;
			}
		}
		if (loaded == null) {
			loaded = new RadiationConfig();
		}
		loaded.sanitize();
		instance = loaded;
		save();
		return true;
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			RadiationMod.LOGGER.error("Could not write {}", PATH, e);
		}
	}
}
