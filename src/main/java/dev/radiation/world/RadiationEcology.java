package dev.radiation.world;

import dev.radiation.config.RadiationConfig;
import dev.radiation.registry.ModRegistry;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * What radiation does to the land around it: crops grow slower, then leaves, plants and grass die, and at last the soil
 * itself turns to sand. Animals take up rads like players do and die of them. Everything is driven by the dose rate at the
 * spot, so concrete, water and earth protect plants exactly as they protect people.
 *
 * <p>Only chunks within reach of some radiation are looked at ("hot" chunks, rebuilt whenever sources change and every
 * ten seconds as decaying sources weaken), and dose rates are cached per 4x4 cell and height for half a minute.
 */
public final class RadiationEcology {
	private static final int CELL_TTL_TICKS = 600;
	private static final int REINDEX_TICKS = 200;
	private static final double MAX_REACH = 256;

	private static final Map<String, LongOpenHashSet> HOT = new HashMap<>();
	private static final Map<String, Long2FloatOpenHashMap> RATES = new HashMap<>();
	private static long indexedVersion = Long.MIN_VALUE;
	private static long indexedAt = Long.MIN_VALUE;
	private static long ratesSince;
	private static int runs;
	private static long foodAt;

	private RadiationEcology() {
	}

	public static void reset() {
		HOT.clear();
		RATES.clear();
		indexedVersion = Long.MIN_VALUE;
		indexedAt = Long.MIN_VALUE;
	}

	// ------------------------------------------------------------------ crops (called for every random tick)

	/**
	 * Whether a random tick of a growing plant should be skipped because radiation slows its growth. Called from the
	 * random tick of every block state, so the common case (not a plant, or no radiation near) returns at once.
	 */
	public static boolean suppressGrowth(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		RadiationConfig.Ecology eco = RadiationConfig.get().ecology;
		if (!eco.enabled || !grows(state) || !hot(level, pos)) {
			return false;
		}
		float growth = eco.cropGrowth(rateAt(level, pos));
		return growth < 1 && random.nextFloat() >= growth;
	}

	public static boolean grows(BlockState state) {
		Block b = state.getBlock();
		return state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS) || b instanceof StemBlock || b instanceof SweetBerryBushBlock
				|| b instanceof CocoaBlock || b instanceof SugarCaneBlock || b instanceof CactusBlock || b instanceof BambooStalkBlock
				|| b instanceof NetherWartBlock;
	}

	// ------------------------------------------------------------------ the land (every intervalTicks)

	public static void tick(MinecraftServer server, RadiationConfig config) {
		RadiationSources sources = RadiationTracker.sources();
		long now = server.overworld().getGameTime();
		if (sources.version() != indexedVersion || now - indexedAt >= REINDEX_TICKS || now < indexedAt) {
			index(server, sources, config, now);
		}
		if (now - ratesSince >= CELL_TTL_TICKS || now < ratesSince) {
			RATES.clear();
			ratesSince = now;
		}
		RadiationConfig.Ecology eco = config.ecology;
		boolean animals = eco.animalsTakeRads && (++runs % 2 == 0);
		boolean storage = config.food.enabled && now - foodAt >= config.food.storageIntervalTicks;
		if (storage || now < foodAt) foodAt = now;
		for (ServerLevel level : server.getAllLevels()) {
			LongOpenHashSet hot = HOT.get(RadiationTracker.dimensionId(level));
			if (hot == null || hot.isEmpty()) {
				continue;
			}
			RandomSource random = level.getRandom();
			for (long chunk : hot.toLongArray()) {
				int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				for (int i = 0; i < eco.samplesPerChunk; i++) {
					sample(level, (cx << 4) + random.nextInt(16), (cz << 4) + random.nextInt(16), eco, random);
				}
			}
			if (animals) {
				animals(level, hot, eco, config, eco.intervalTicks * 2 / 20f);
			}
			if (storage) {
				food(level, hot, config.food.storageIntervalTicks / 20f);
			}
		}
	}

	private static void sample(ServerLevel level, int x, int z, RadiationConfig.Ecology eco, RandomSource random) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		if (top < level.getMinY()) {
			return;
		}
		BlockPos pos = new BlockPos(x, top, z);
		// measured just above the surface, where plants live (not inside the ground, which would shield it)
		float rate = rateAt(level, pos.above());
		if (rate < eco.lowestThreshold() || random.nextFloat() >= eco.changeChance) {
			return;
		}
		// the top block (leaves, a plant, grass...) and what it stands on
		if (!change(level, pos, level.getBlockState(pos), rate, eco)) {
			BlockPos below = pos.below();
			change(level, below, level.getBlockState(below), rate, eco);
		}
	}

	private static boolean change(ServerLevel level, BlockPos pos, BlockState s, float rate, RadiationConfig.Ecology eco) {
		if (s.is(BlockTags.LEAVES)) {
			if (rate >= eco.leafDeathRads) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0);
				return true;
			}
			return false;
		}
		if (plant(s)) {
			if (rate >= eco.plantDeathRads) {
				kill(level, pos, s);
				return true;
			}
			return false;
		}
		if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.FARMLAND) || s.is(Blocks.MOSS_BLOCK)) {
			if (rate >= eco.soilToSandRads) {
				level.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
				return true;
			}
			if (rate >= eco.grassToDirtRads) {
				level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
				return true;
			}
			return false;
		}
		if ((s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.DIRT_PATH)) && rate >= eco.soilToSandRads) {
			level.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
			return true;
		}
		return false;
	}

	private static boolean plant(BlockState s) {
		return s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.FLOWERS) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)
				|| s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN) || s.is(Blocks.BUSH) || s.is(Blocks.SWEET_BERRY_BUSH)
				|| s.getBlock() instanceof StemBlock || s.is(Blocks.ATTACHED_MELON_STEM) || s.is(Blocks.ATTACHED_PUMPKIN_STEM);
	}

	/** The plant dies: a dead bush where one can stand, otherwise nothing. */
	private static void kill(ServerLevel level, BlockPos pos, BlockState s) {
		BlockPos base = pos;
		if (s.getBlock() instanceof DoublePlantBlock && s.hasProperty(DoublePlantBlock.HALF) && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
			base = pos.below();
		}
		level.destroyBlock(base, false);
		level.removeBlock(base.above(), false);
		BlockState dead = Blocks.DEAD_BUSH.defaultBlockState();
		if (level.getBlockState(base).isAir() && dead.canSurvive(level, base)) {
			level.setBlock(base, dead, Block.UPDATE_ALL);
		}
	}

	// ------------------------------------------------------------------ animals

	/** Animals and villagers: rads, sickness stages and death exactly as for players (see RadiationTracker). */
	private static void animals(ServerLevel level, LongOpenHashSet hot, RadiationConfig.Ecology eco, RadiationConfig config, float seconds) {
		for (Entity e : level.getAllEntities()) {
			if (!(e instanceof Mob mob) || !mob.isAlive() || mob.is(EntityTypeTags.UNDEAD)) {
				continue;
			}
			Float had = mob.getAttached(ModRegistry.RADS);
			float rads = had == null ? 0 : had;
			boolean near = hot.contains(ChunkPos.pack(mob.blockPosition()));
			if (!near && rads <= 0) {
				continue;
			}
			float rate = near ? RadiationTracker.exposureAt(level, mob.position().add(0, mob.getBbHeight() * 0.5, 0), null) : 0;
			rads = Math.clamp(rads + rate * seconds - config.naturalDecayPerSecond * seconds, 0, config.maxRads);
			if (rads <= 0 && had == null) {
				continue;
			}
			mob.setAttached(ModRegistry.RADS, rads);
			RadiationTracker.applySickness(mob, config.stageIndexFor(rads), config, (int) (seconds * 20) + 60);
			if (rads >= config.maxRads) {
				mob.hurtServer(level, level.damageSources().source(ModRegistry.RADIATION_DAMAGE), Float.MAX_VALUE);
			}
		}
	}

	// ------------------------------------------------------------------ food in storage

	private static void food(ServerLevel level, LongOpenHashSet hot, float seconds) {
		for (long chunk : hot.toLongArray()) {
			var c = level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
			if (c != null) {
				dev.radiation.food.FoodContamination.storage(level, c, seconds);
				dev.radiation.food.FoodContamination.crops(level, c);
			}
		}
		for (Entity e : level.getAllEntities()) {
			if (e instanceof net.minecraft.world.entity.item.ItemEntity item && item.isAlive() && hot.contains(ChunkPos.pack(item.blockPosition()))) {
				dev.radiation.food.FoodContamination.onGround(item, RadiationTracker.contaminationAt(level, item.position()), seconds);
			}
		}
	}

	// ------------------------------------------------------------------ dose rates

	/** Dose rate at a block, cached per 4x4 column cell and block height (measured at the cell's centre line). */
	public static float rateAt(ServerLevel level, BlockPos pos) {
		Long2FloatOpenHashMap cache = RATES.computeIfAbsent(RadiationTracker.dimensionId(level), k -> {
			Long2FloatOpenHashMap m = new Long2FloatOpenHashMap();
			m.defaultReturnValue(Float.NaN);
			return m;
		});
		long cell = BlockPos.asLong(pos.getX() >> 2, pos.getY(), pos.getZ() >> 2);
		float rate = cache.get(cell);
		if (Float.isNaN(rate)) {
			Vec3 center = new Vec3((pos.getX() & ~3) + 2.0, pos.getY() + 0.5, (pos.getZ() & ~3) + 2.0);
			rate = RadiationTracker.exposureAt(level, center, null);
			if (cache.size() > 200_000) {
				cache.clear();
			}
			cache.put(cell, rate);
		}
		return rate;
	}

	private static boolean hot(ServerLevel level, BlockPos pos) {
		LongOpenHashSet hot = HOT.get(RadiationTracker.dimensionId(level));
		return hot != null && hot.contains(ChunkPos.pack(pos));
	}

	/** Which chunks could get at least the lowest threshold from some zone, source, emitter or barrel. */
	private static void index(MinecraftServer server, RadiationSources sources, RadiationConfig config, long now) {
		HOT.clear();
		float low = config.ecology.lowestThreshold();
		for (RadiationSources.Zone z : sources.zones) {
			if (z.rads >= low) {
				mark(z.dimension, z.minX, z.minZ, z.maxX, z.maxZ);
			}
		}
		for (RadiationSources.PointSource s : sources.sources) {
			float rads = s.radsAt(now);
			if (rads < low) {
				continue;
			}
			// the distance at which the falloff brings it down to the threshold
			double lo = 0, hi = Math.min(s.radius, MAX_REACH);
			for (int i = 0; i < 20; i++) {
				double mid = (lo + hi) / 2;
				if (rads * s.falloff.apply(mid, s.radius) >= low) lo = mid; else hi = mid;
			}
			markRound(s.dimension, s.x, s.z, hi);
		}
		for (RadiationSources.Emitter e : sources.emitters) {
			if (e.rads * 4 < low) {
				continue;
			}
			markRound(e.dimension, e.x + 0.5, e.z + 0.5, Math.min(Math.min(e.radius, Math.sqrt(e.rads / low)), MAX_REACH));
		}
		if (config.barrelRads >= low && config.barrelRadius > 0) {
			for (RadiationSources.Barrel b : sources.barrels) {
				markRound(b.dimension, b.x + 0.5, b.z + 0.5, Math.min(config.barrelRadius, MAX_REACH));
			}
		}
		indexedVersion = sources.version();
		indexedAt = now;
	}

	private static void markRound(String dimension, double x, double z, double reach) {
		mark(dimension, (int) Math.floor(x - reach), (int) Math.floor(z - reach), (int) Math.floor(x + reach), (int) Math.floor(z + reach));
	}

	private static void mark(String dimension, int minX, int minZ, int maxX, int maxZ) {
		LongOpenHashSet set = HOT.computeIfAbsent(dimension, d -> new LongOpenHashSet());
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				set.add(ChunkPos.pack(cx, cz));
			}
		}
	}
}
