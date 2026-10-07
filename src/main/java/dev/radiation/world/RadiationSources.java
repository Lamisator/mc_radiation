package dev.radiation.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.radiation.RadiationMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * All radiation zones, point sources and waste barrels of a world.
 * Persisted as human-editable JSON in &lt;world&gt;/radiation_sources.json.
 */
public class RadiationSources {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public List<Zone> zones = new ArrayList<>();
	public List<PointSource> sources = new ArrayList<>();
	public List<Barrel> barrels = new ArrayList<>();
	/** Radiating blocks of other mods (see RadiationApi#setEmitter), kept in sync by them. */
	public List<Emitter> emitters = new ArrayList<>();
	private transient long version;
	private transient java.util.Map<String, Emitter> emitterIndex;

	private transient Path path;
	private transient boolean dirty;

	/** An axis-aligned box with a uniform radiation level. */
	public static class Zone {
		public String name;
		public String dimension;
		public int minX, minY, minZ, maxX, maxY, maxZ;
		/** Rads per second inside the zone. */
		public float rads;
		/** Blocks over which the radiation fades in from the zone's border (0 = hard edge). */
		public float fade;

		public boolean contains(double x, double y, double z) {
			return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
		}

		/** Radiation at a point inside the zone, taking the fade-in border into account. */
		public float radsAt(double x, double y, double z) {
			if (fade <= 0) {
				return rads;
			}
			double edge = Math.min(Math.min(Math.min(x - minX, maxX + 1 - x), Math.min(y - minY, maxY + 1 - y)), Math.min(z - minZ, maxZ + 1 - z));
			return (float) (rads * Math.min(1.0, edge / fade));
		}

		public String describe() {
			return String.format(Locale.ROOT, "%s [%s] (%d %d %d) -> (%d %d %d), %.1f rad/s%s",
					name, dimension, minX, minY, minZ, maxX, maxY, maxZ, rads, fade > 0 ? String.format(Locale.ROOT, ", fade %.1f", fade) : "");
		}
	}

	/** A point that radiates in all directions, getting weaker with distance. */
	public static class PointSource {
		public String name;
		public String dimension;
		public double x, y, z;
		/** Rads per second at the centre. */
		public float rads;
		public float radius;
		public Falloff falloff = Falloff.LINEAR;
		/** Whether solid blocks between the source and the player absorb radiation. */
		public boolean shielded = true;
		/** Half-life in game ticks; 0 = the source never weakens. */
		public long halfLifeTicks;
		/** Game time at which the source had {@link #rads}. */
		public long startTime;
		/** Part of {@link #rads} that decays slowly (long-lived nuclides, like caesium in fallout next to iodine). */
		public float longLivedFraction;
		/** Half-life of the long-lived part in game ticks; 0 = it never decays. */
		public long longHalfLifeTicks;
		/** Game ticks after {@link #startTime} when the source is gone for good, fading out over the last fifth; 0 = no end. */
		public long lifetimeTicks;

		/** Rads per second at the centre at game time {@code now}, after radioactive decay. */
		public float radsAt(long now) {
			if (halfLifeTicks <= 0) {
				return rads;
			}
			long age = Math.max(0, now - startTime);
			double decayed = Math.pow(0.5, age / (double) halfLifeTicks);
			double lasting = longHalfLifeTicks > 0 ? Math.pow(0.5, age / (double) longHalfLifeTicks) : 1;
			double end = 1;
			if (lifetimeTicks > 0) {
				end = Math.clamp((lifetimeTicks - age) / (0.2 * lifetimeTicks), 0, 1);
			}
			return (float) (rads * (longLivedFraction * lasting + (1 - longLivedFraction) * decayed) * end);
		}

		public String describe() {
			String decay = halfLifeTicks > 0 ? String.format(Locale.ROOT, ", half-life %.1f days", halfLifeTicks / 24000.0) : "";
			if (lifetimeTicks > 0) {
				decay += String.format(Locale.ROOT, ", gone after %.1f days", lifetimeTicks / 24000.0);
			}
			return String.format(Locale.ROOT, "%s [%s] (%.1f %.1f %.1f), %.2f rad/s, radius %.1f, %s%s%s",
					name, dimension, x, y, z, rads, radius, falloff.name().toLowerCase(Locale.ROOT), shielded ? "" : ", unshielded", decay);
		}
	}

	/** A placed Nuclear Waste Barrel block; strength and radius come from the config. */
	public static class Barrel {
		public String dimension;
		public int x, y, z;

		public Barrel() {
		}

		public Barrel(String dimension, int x, int y, int z) {
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public boolean is(String dimension, int x, int y, int z) {
			return this.x == x && this.y == y && this.z == z && this.dimension.equals(dimension);
		}
	}

	/** A block that radiates: strength in rads per second at one metre, falling off with the square of the distance. */
	public static class Emitter {
		public String dimension;
		public int x, y, z;
		public String block;
		public float rads;
		public float radius;

		public String key() {
			return key(this.dimension, this.x, this.y, this.z);
		}

		public static String key(String dimension, int x, int y, int z) {
			return dimension + "|" + x + "|" + y + "|" + z;
		}
	}

	public java.util.Map<String, Emitter> emitterIndex() {
		if (this.emitterIndex == null) {
			this.emitterIndex = new java.util.HashMap<>();
			if (this.emitters == null) {
				this.emitters = new ArrayList<>();
			}
			for (Emitter e : this.emitters) {
				this.emitterIndex.put(e.key(), e);
			}
		}
		return this.emitterIndex;
	}

	public void putEmitter(Emitter e) {
		Emitter old = this.emitterIndex().put(e.key(), e);
		if (old != null) {
			this.emitters.remove(old);
		}
		this.emitters.add(e);
		this.markDirty();
	}

	public boolean removeEmitter(String key) {
		Emitter old = this.emitterIndex().remove(key);
		if (old != null) {
			this.emitters.remove(old);
			this.markDirty();
			return true;
		}
		return false;
	}

	public enum Falloff {
		/** Full strength everywhere inside the radius. */
		CONSTANT,
		/** Fades evenly from full strength at the centre to zero at the radius. */
		LINEAR,
		/** Drops quickly near the centre, long weak tail. */
		QUADRATIC,
		/** Physically inspired 1/(1+d²) falloff, cut off at the radius. */
		INVERSE_SQUARE;

		public double apply(double distance, double radius) {
			if (distance >= radius) {
				return 0;
			}
			double t = distance / radius;
			return switch (this) {
				case CONSTANT -> 1;
				case LINEAR -> 1 - t;
				case QUADRATIC -> (1 - t) * (1 - t);
				case INVERSE_SQUARE -> 1 / (1 + distance * distance) * (1 - t * t);
			};
		}
	}

	public static RadiationSources load(MinecraftServer server) {
		Path path = server.getWorldPath(LevelResource.ROOT).resolve("radiation_sources.json");
		RadiationSources data = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				data = GSON.fromJson(reader, RadiationSources.class);
			} catch (Exception e) {
				RadiationMod.LOGGER.error("Could not read {}; starting with no radiation sources", path, e);
			}
		}
		if (data == null) {
			data = new RadiationSources();
		}
		if (data.zones == null) data.zones = new ArrayList<>();
		if (data.sources == null) data.sources = new ArrayList<>();
		if (data.barrels == null) data.barrels = new ArrayList<>();
		if (data.emitters == null) data.emitters = new ArrayList<>();
		data.emitters.removeIf(e -> e == null || e.dimension == null);
		data.zones.removeIf(z -> z == null || z.name == null || z.dimension == null);
		data.sources.removeIf(s -> s == null || s.name == null || s.dimension == null);
		data.barrels.removeIf(b -> b == null || b.dimension == null);
		for (PointSource source : data.sources) {
			if (source.falloff == null) source.falloff = Falloff.LINEAR;
		}
		data.path = path;
		return data;
	}

	public void markDirty() {
		dirty = true;
		version++;
	}

	/** Changes with every change to zones, sources, barrels or emitters (for caches). */
	public long version() {
		return version;
	}

	public void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	public void save() {
		if (path == null) {
			return;
		}
		dirty = false;
		version++;
		try {
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(this, writer);
			}
			Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			RadiationMod.LOGGER.error("Could not save {}", path, e);
		}
	}

	public Zone zone(String name) {
		return zones.stream().filter(z -> z.name.equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	public PointSource source(String name) {
		return sources.stream().filter(s -> s.name.equalsIgnoreCase(name)).findFirst().orElse(null);
	}
}
