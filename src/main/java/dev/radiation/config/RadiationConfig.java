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

	/** Format of this file; older files (without it: 0) are upgraded on load (see {@link #migrate}). */
	public int configVersion = 0;
	static final int CURRENT_VERSION = 3;

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
	/**
	 * Fraction absorbed per block of concrete (tag radiation:shielding_concrete: all concrete, reinforced concrete). One
	 * block lets 10 % through, two 1 %, three 0.1 %: a few blocks of concrete reliably shield even a reactor core. (Real
	 * concrete is better still: a metre of it stops all but about 1/10,000 of the gamma rays from fission products.)
	 */
	public float concreteShielding = 0.90f;
	/** Fraction absorbed per block of heavy shielding (tag radiation:shielding_heavy: heavy concrete, iron blocks...). */
	public float heavyShielding = 0.97f;
	/** Fraction absorbed per block of water. */
	public float waterShielding = 0.30f;
	/** Rads per second at the centre of a Nuclear Waste Barrel. */
	public float barrelRads = 6f;
	/** Radius in blocks of a Nuclear Waste Barrel's radiation. */
	public float barrelRadius = 6f;

	// --- what radiation does to the land ---
	public Ecology ecology = new Ecology();

	// --- contaminated food ---
	public Food food = new Food();

	// --- radioactive clouds (burning reactors, nuclear detonations) ---
	public Clouds clouds = new Clouds();

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

	/**
	 * Radiation damages plants, soil and animals. All thresholds are dose rates in rad/s at the block (or the animal).
	 * Plants are far more robust than people, but not endlessly: grain stops growing at a few gray per day, pines die at a
	 * few hundred gray in total (the Red Forest at Chernobyl), grass and herbs take more. 0.01 rad/s is 8.6 Gy a day.
	 */
	public static class Ecology {
		public boolean enabled = true;
		/** Below this dose rate animals are not looked at (a lethal dose would take more than a day). */
		public static final float ANIMAL_FLOOR = 0.01f;
		/** Crops, saplings, stems, berries, cane and cactus grow at {@link #cropGrowthAtSlowdown} of their normal speed from here. */
		public float cropSlowdownRads = 0.01f;
		public float cropGrowthAtSlowdown = 0.5f;
		/** ...and at {@link #cropGrowthWhenStunted} from here (in between it is interpolated). */
		public float cropStuntRads = 0.1f;
		public float cropGrowthWhenStunted = 0.1f;
		/** Leaves die and fall: the forest turns into bare trunks. */
		public float leafDeathRads = 0.3f;
		/** Crops, flowers, grass, ferns and saplings die (dead bushes where they can stand). */
		public float plantDeathRads = 1.0f;
		/** Grass, podzol and farmland die back to bare dirt. */
		public float grassToDirtRads = 2.0f;
		/** Dirt of every kind turns to sand: nothing lives in the soil any more and it crumbles. */
		public float soilToSandRads = 25.0f;
		/**
		 * Animals and villagers (not undead) take up rads exactly like players: from any dose rate (where it is at least
		 * 0.01 rad/s), with the same sickness stages and effects, losing the same share of their health, and dying at
		 * {@code maxRads}. false = they are immune.
		 */
		public boolean animalsTakeRads = true;
		/** How often the land near radiation is looked at, in ticks, and how many surface blocks per chunk each time. */
		public int intervalTicks = 20;
		public int samplesPerChunk = 4;
		/** Chance that a sampled block above a threshold actually changes this time. */
		public float changeChance = 0.5f;

		/** Fraction of the normal growth that still happens at this dose rate (1 = unaffected). */
		public float cropGrowth(float rads) {
			if (rads < cropSlowdownRads) {
				return 1;
			}
			if (rads >= cropStuntRads) {
				return cropGrowthWhenStunted;
			}
			double t = Math.log(rads / cropSlowdownRads) / Math.log(cropStuntRads / cropSlowdownRads);
			return (float) (cropGrowthAtSlowdown + (cropGrowthWhenStunted - cropGrowthAtSlowdown) * t);
		}

		/** The lowest dose rate that does anything. */
		public float lowestThreshold() {
			float low = Float.MAX_VALUE;
			for (float t : new float[] {cropSlowdownRads, leafDeathRads, plantDeathRads, grassToDirtRads, soilToSandRads, animalsTakeRads ? ANIMAL_FLOOR : 0}) {
				if (t > 0) {
					low = Math.min(low, t);
				}
			}
			return low;
		}

		void sanitize() {
			cropGrowthAtSlowdown = Math.clamp(cropGrowthAtSlowdown, 0f, 1f);
			cropGrowthWhenStunted = Math.clamp(cropGrowthWhenStunted, 0f, 1f);
			if (cropSlowdownRads <= 0) cropSlowdownRads = 0.01f;
			if (cropStuntRads <= cropSlowdownRads) cropStuntRads = cropSlowdownRads * 10;
			intervalTicks = Math.max(1, intervalTicks);
			samplesPerChunk = Math.clamp(samplesPerChunk, 0, 256);
			changeChance = Math.clamp(changeChance, 0f, 1f);
		}
	}

	/**
	 * Contaminated food: every food carries the rads you take up when you eat it (see FoodContamination). The ground's
	 * radiation goes into crops, an animal's into its meat, a store's into the food kept in it.
	 */
	public static class Food {
		public boolean enabled = true;
		/** Rad per harvested item for each rad/s at the plant (wheat from a field at 0.5 rad/s: 10 rad). */
		public float cropUptake = 20f;
		/** Share of an animal's rads in each piece of meat (or egg...) it gives (a cow at 400 rads: 40 rad a steak). */
		public float animalShare = 0.1f;
		/** Rad per item for each rad of exposure in a container or on the ground (an hour at 1 rad/s: 18 rad). */
		public float storageUptake = 0.005f;
		/** How often stored food is looked at, in ticks. */
		public int storageIntervalTicks = 100;
		/**
		 * Point sources (by name prefix) whose radiation does not get into food, e.g. "radioactive_cloud", "fission_cloud",
		 * "fission_release" (clouds overhead and Fission's open core). Empty since 1.8.0: everything counts.
		 */
		public List<String> notContaminating = new ArrayList<>();
		/**
		 * Whether the gamma rays of radiating blocks (corium, debris, fuel, spent fuel) and waste barrels get into food
		 * too. True since 1.8.0; false is the 1.7.2 rule (only radioactivity lying there counts).
		 */
		public boolean gammaContaminates = true;
		/**
		 * The most a food can carry, per point of its nutrition (half a drumstick): bread (5) at most 20 rad. What has no
		 * nutrition of its own (wheat, sugar, eggs, milk...) counts with its share in what it is made into, see
		 * FoodContamination#nutrition. 0: no cap.
		 */
		public float radsPerNutrition = 4f;

		void sanitize() {
			cropUptake = Math.max(0, cropUptake);
			animalShare = Math.max(0, animalShare);
			storageUptake = Math.max(0, storageUptake);
			radsPerNutrition = Math.max(0, radsPerNutrition);
			storageIntervalTicks = Math.clamp(storageIntervalTicks, 20, 24000);
			if (notContaminating == null) notContaminating = new ArrayList<>();
		}
	}

	/** Radioactive clouds: how long they last and how much fallout they leave. */
	public static class Clouds {
		/** The longest a cloud drifts, in minutes (of play at 20 ticks per second), before it is gone. */
		public int maxAgeMinutes = 120;
		/** How much wider a cloud gets per block it drifts: the faster it spreads, the sooner it is too thin to matter. */
		public float spreadPerBlock = 0.012f;
		/** A cloud is gone when the dose rate under it falls below this (rad/s). */
		public float fadedRads = 0.001f;
		/** All fallout a cloud leaves behind is multiplied by this. 1 = Radiation 1.5.0/1.5.1. */
		public float falloutFactor = 5.0f;
		/** Where it rains under a cloud, it drops fallout this many times as heavily per block (and is used up sooner). */
		public float rainFactor = 5.0f;
		/** Rain also washes a cloud out: it loses this many times as much per block as in dry weather. 1 = rain does not wash it out faster. */
		public float rainWashout = 20.0f;

		void sanitize() {
			maxAgeMinutes = Math.clamp(maxAgeMinutes, 1, 24 * 60);
			spreadPerBlock = Math.clamp(spreadPerBlock, 0f, 1f);
			fadedRads = Math.max(0.0001f, fadedRads);
			falloutFactor = Math.clamp(falloutFactor, 0f, 1000f);
			rainFactor = Math.clamp(rainFactor, 0f, 100f);
			rainWashout = Math.clamp(rainWashout, 1f, 100f);
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
		if (ecology == null) ecology = new Ecology();
		ecology.sanitize();
		if (food == null) food = new Food();
		food.sanitize();
		if (clouds == null) clouds = new Clouds();
		clouds.sanitize();
		shieldingPerBlock = Math.clamp(shieldingPerBlock, 0f, 1f);
		concreteShielding = Math.clamp(concreteShielding, 0f, 1f);
		heavyShielding = Math.clamp(heavyShielding, 0f, 1f);
		waterShielding = Math.clamp(waterShielding, 0f, 1f);
		maxProtection = Math.clamp(maxProtection, 0f, 1f);
		radAwayDurationSeconds = Math.max(1, radAwayDurationSeconds);
		radXDurationSeconds = Math.max(1, radXDurationSeconds);
	}

	/**
	 * Files written by older versions keep their values, except defaults that changed: concrete used to let almost half
	 * of the radiation through every block (1.3), now it shields properly. Values someone changed by hand stay as they are.
	 */
	private void migrate() {
		if (configVersion < 2) {
			if (concreteShielding == 0.55f) concreteShielding = 0.90f;
			if (heavyShielding == 0.75f) heavyShielding = 0.97f;
			RadiationMod.LOGGER.info("Upgraded config/radiation.json to version 2 (concrete {}, heavy {})", concreteShielding, heavyShielding);
		}
		if (configVersion < 3) {
			// 1.8.0: no more exclusions - clouds, Fission's open core and gamma rays from blocks and barrels count for food
			if (food.notContaminating != null && new java.util.HashSet<>(food.notContaminating)
					.equals(java.util.Set.of("radioactive_cloud", "fission_cloud", "fission_release"))) {
				food.notContaminating = new ArrayList<>();
			}
			RadiationMod.LOGGER.info("Upgraded config/radiation.json to version 3 (food.notContaminating {}, gammaContaminates {})",
					food.notContaminating, food.gammaContaminates);
		}
		configVersion = CURRENT_VERSION;
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
		loaded.migrate();
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
