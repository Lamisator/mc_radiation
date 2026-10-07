package dev.radiation.food;

import com.mojang.serialization.Codec;
import dev.radiation.RadiationMod;
import dev.radiation.config.RadiationConfig;
import dev.radiation.registry.ModRegistry;
import dev.radiation.world.RadiationEcology;
import dev.radiation.world.RadiationTracker;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ConsumableListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Contaminated food. Every food (and what food is made of: wheat, sugar, eggs, milk... the tag
 * {@code radiation:contaminable}) carries a contamination: the rads you take up when you eat one. It comes from
 * <ul>
 * <li>the field: a crop harvested where the ground is contaminated takes up {@code food.cropUptake} rad per rad/s of
 * contamination there ({@link RadiationTracker#contaminationAt}: since 1.8.0 all radiation there, see the config);
 * <li>the animal: meat, eggs and the like from an irradiated animal carry {@code food.animalShare} of its rads;
 * <li>storage: food lying in a chest, barrel, furnace... or on the ground where there is contamination takes up
 * {@code food.storageUptake} rad per rad of exposure. Food carried in an inventory takes up nothing more;
 * <li>processing: what is made of contaminated food is contaminated too - the contamination of all ingredients is
 * shared out over the result (three wheat at 10 rad make a loaf of bread at 30 rad; a raw steak at 20 rad makes a
 * cooked one at 20).
 * </ul>
 * Harvests and products are rounded to a few steps (0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10...) so that they stack. No item
 * carries more than {@code food.radsPerNutrition} rad per point of nutrition ({@link #cap}): bread at most 20 rad.
 */
public final class FoodContamination {
	public static final TagKey<Item> CONTAMINABLE = TagKey.create(Registries.ITEM, RadiationMod.id("contaminable"));

	/** The contamination of one item: rads taken up when it is eaten. */
	public record Contamination(float rads) implements ConsumableListener {
		public static final Codec<Contamination> CODEC = Codec.FLOAT.xmap(Contamination::new, Contamination::rads);
		public static final StreamCodec<io.netty.buffer.ByteBuf, Contamination> STREAM_CODEC = ByteBufCodecs.FLOAT.map(Contamination::new, Contamination::rads);

		@Override
		public void onConsume(Level level, LivingEntity user, ItemStack stack, Consumable consumable) {
			if (level.isClientSide() || rads <= 0 || !RadiationConfig.get().food.enabled) return;
			float rads = Math.min(this.rads, cap(stack));
			if (user instanceof ServerPlayer player) {
				RadiationTracker.addRads(player, rads);
				player.sendOverlayMessage(Component.translatable("message.radiation.ate_contaminated", format(rads)).withStyle(ChatFormatting.GOLD));
			} else {
				Float had = user.getAttached(ModRegistry.RADS);
				user.setAttached(ModRegistry.RADS, Math.min(RadiationConfig.get().maxRads, (had == null ? 0 : had) + rads));
			}
		}
	}

	public static final DataComponentType<Contamination> CONTAMINATION = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			RadiationMod.id("contamination"),
			DataComponentType.<Contamination>builder().persistent(Contamination.CODEC).networkSynchronized(Contamination.STREAM_CODEC).build());

	/** Exposure collected by a container or an item on the ground that has not been passed on to its food yet. */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Float> PENDING = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<Float>builder()
			.persistent(Codec.FLOAT)
			.buildAndRegister(RadiationMod.id("food_exposure"));

	/**
	 * What a growing plant has taken up: the highest contamination seen at its block while it grew (rads per harvested
	 * item), and, for crops, the age it had then (a younger crop in the same place is a new one, planted later).
	 */
	public record Exposure(float rads, int age) {
		public static final Codec<Exposure> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
				Codec.FLOAT.fieldOf("rads").forGetter(Exposure::rads),
				Codec.INT.optionalFieldOf("age", -1).forGetter(Exposure::age)).apply(i, Exposure::new));
	}

	/** Per chunk: block position (as a long, written as text) to what the plant there has taken up. */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<java.util.Map<String, Exposure>> CROP_EXPOSURE =
			net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<java.util.Map<String, Exposure>>builder()
					.persistent(Codec.unboundedMap(Codec.STRING, Exposure.CODEC))
					.buildAndRegister(RadiationMod.id("crop_exposure"));

	private FoodContamination() {
	}

	public static void init() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			RadiationConfig.Food cfg = RadiationConfig.get().food;
			if (!cfg.enabled || drops.isEmpty()) return;
			ServerLevel level = context.getLevel();
			BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
			Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
			Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
			float each = 0;
			if (state != null && origin != null && plant(state)) {
				// a harvest: what the plant took up from the irradiated ground - now, or at any time while it grew
				BlockPos at = BlockPos.containing(origin);
				each = Math.max(RadiationTracker.contaminationAt(level, Vec3.atCenterOf(at)) * cfg.cropUptake, takeExposure(level, at, state));
			} else if (state == null && entity instanceof LivingEntity animal && !(entity instanceof net.minecraft.world.entity.player.Player)) {
				Float rads = animal.getAttached(ModRegistry.RADS);
				each = rads == null ? 0 : rads * cfg.animalShare;
			}
			if (each <= 0) return;
			float q = coarse(each);
			if (q <= 0) return;
			for (ItemStack s : drops) {
				if (contaminable(s)) set(s, Math.max(of(s), q));
			}
		});
	}

	// ------------------------------------------------------------------ stacks

	/** Food, or what food is made of. */
	public static boolean contaminable(ItemStack stack) {
		return !stack.isEmpty() && (stack.has(DataComponents.FOOD) || stack.is(CONTAMINABLE));
	}

	public static float of(ItemStack stack) {
		Contamination c = stack.get(CONTAMINATION);
		return c == null ? 0 : Math.min(c.rads(), cap(stack));
	}

	public static void set(ItemStack stack, float rads) {
		rads = Math.min(rads, cap(stack));
		if (rads <= 0) stack.remove(CONTAMINATION);
		else stack.set(CONTAMINATION, new Contamination(rads));
	}

	/**
	 * The most one item can carry: {@code food.radsPerNutrition} per point of nutrition. Bread (5) at most 20 rad, a steak
	 * (8) 32, an apple (4) 16, a melon slice (2) 8.
	 */
	public static float cap(ItemStack stack) {
		float per = RadiationConfig.get().food.radsPerNutrition;
		if (per <= 0) return Float.MAX_VALUE;
		return fine(per * nutrition(stack));
	}

	/**
	 * Nutrition of a food, or for what food is made of its share in what it becomes: wheat a third of a loaf, a pumpkin
	 * half a pie, a melon nine slices, mushrooms half a stew, hay nine wheat...
	 */
	public static float nutrition(ItemStack stack) {
		var food = stack.get(DataComponents.FOOD);
		if (food != null) return food.nutrition();
		Item i = stack.getItem();
		if (i == Items.WHEAT) return 5f / 3;
		if (i == Items.HAY_BLOCK) return 15;
		if (i == Items.SUGAR_CANE || i == Items.SUGAR) return 1;
		if (i == Items.PUMPKIN || i == Items.CARVED_PUMPKIN) return 4;
		if (i == Items.MELON) return 18;
		if (i == Items.COCOA_BEANS) return 4;
		if (i == Items.EGG || i == Items.BROWN_EGG || i == Items.BLUE_EGG) return 2;
		if (i == Items.MILK_BUCKET) return 4;
		if (i == Items.BROWN_MUSHROOM || i == Items.RED_MUSHROOM) return 3;
		if (i == Items.HONEYCOMB) return 2;
		if (i == Items.CAKE) return 14;
		return 4;
	}

	/** What processing gives: the contamination of everything that went in, shared out over what comes out. */
	public static void processed(ItemStack result, List<ItemStack> inputs) {
		if (result.isEmpty() || !contaminable(result)) return;
		float total = 0;
		for (ItemStack in : inputs) {
			if (!in.isEmpty()) total += of(in);
		}
		if (total <= 0) return;
		// two significant digits (not the coarse steps of a harvest): cooking keeps what went in, a capped value stays capped
		set(result, fine(total / Math.max(1, result.getCount())));
	}

	/** Steps that stack: 0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10, 20... (nearest step on a log scale; below 0.07 nothing). */
	public static float coarse(float rads) {
		if (!(rads >= 0.07f)) return 0;
		double decade = Math.pow(10, Math.floor(Math.log10(rads)));
		double best = decade, diff = Double.MAX_VALUE;
		for (double step : new double[]{1, 2, 3, 5, 10}) {
			double d = Math.abs(Math.log(rads / (step * decade)));
			if (d < diff) {
				diff = d;
				best = step * decade;
			}
		}
		return (float) best;
	}

	/** Two significant digits, for food that slowly takes up radiation in storage. */
	static float fine(double rads) {
		if (rads < 0.01) return 0;
		double decade = Math.pow(10, Math.floor(Math.log10(rads)) - 1);
		return (float) (Math.round(rads / decade) * decade);
	}

	/** 30, 5.8, 0.25: no needless decimals. */
	public static String format(float rads) {
		String s = rads >= 10 ? String.format(Locale.ROOT, "%.0f", rads) : rads >= 1 ? String.format(Locale.ROOT, "%.1f", rads)
				: String.format(Locale.ROOT, "%.2f", rads);
		return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
	}

	/** Plants whose harvest takes up what is in the ground. */
	static boolean plant(BlockState state) {
		var b = state.getBlock();
		return RadiationEcology.grows(state) || state.is(BlockTags.LEAVES) || b == Blocks.PUMPKIN || b == Blocks.MELON || b == Blocks.BROWN_MUSHROOM
				|| b == Blocks.RED_MUSHROOM || b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT || b == Blocks.SWEET_BERRY_BUSH;
	}

	// ------------------------------------------------------------------ crops remember

	private static int age(BlockState state) {
		return state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop ? crop.getAge(state) : -1;
	}

	/**
	 * Every plant in a contaminated chunk remembers the worst contamination it has seen (measured per 4x4 cell, at the
	 * plants' height). Harvested later - even long after the fallout is gone - it still carries it. Called with the
	 * food storage sweep, every {@code food.storageIntervalTicks}.
	 */
	public static void crops(ServerLevel level, LevelChunk chunk) {
		RadiationConfig.Food cfg = RadiationConfig.get().food;
		if (cfg.cropUptake <= 0) return;
		java.util.Map<String, Exposure> had = chunk.getAttached(CROP_EXPOSURE);
		java.util.Map<String, Exposure> map = had == null ? new java.util.HashMap<>() : new java.util.HashMap<>(had);
		boolean changed = false;
		float[] cells = new float[16];
		java.util.Arrays.fill(cells, Float.NaN);
		int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int top = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, dx, dz);
				for (int y = top; y >= top - 2; y--) {
					pos.set(x0 + dx, y, z0 + dz);
					BlockState s = chunk.getBlockState(pos);
					if (!plant(s)) continue;
					int cell = (dx >> 2) + (dz >> 2) * 4;
					if (Float.isNaN(cells[cell])) {
						cells[cell] = RadiationTracker.contaminationAt(level, new Vec3(x0 + (dx & ~3) + 2.0, y + 0.5, z0 + (dz & ~3) + 2.0));
					}
					float rads = cells[cell] * cfg.cropUptake;
					if (rads < 0.07f) continue;
					String key = Long.toString(pos.asLong());
					Exposure old = map.get(key);
					if (old == null || rads > old.rads() || age(s) != old.age() && age(s) >= 0) {
						float keep = old == null || age(s) >= 0 && age(s) < old.age() ? 0 : old.rads();
						map.put(key, new Exposure(Math.max(rads, keep), age(s)));
						changed = true;
					}
				}
			}
		}
		// plants that are gone (eaten, trampled, dug up) leave nothing behind
		changed |= map.keySet().removeIf(k -> !plant(chunk.getBlockState(BlockPos.of(Long.parseLong(k)))));
		if (changed) {
			if (map.isEmpty()) chunk.removeAttached(CROP_EXPOSURE);
			else chunk.setAttached(CROP_EXPOSURE, map);
		}
	}

	/** What the plant harvested at pos has taken up while it grew (also from the blocks above and below: cane, bamboo, cactus); forgets it. */
	static float takeExposure(ServerLevel level, BlockPos pos, BlockState state) {
		LevelChunk chunk = level.getChunkAt(pos);
		java.util.Map<String, Exposure> had = chunk.getAttached(CROP_EXPOSURE);
		if (had == null || had.isEmpty()) return 0;
		java.util.Map<String, Exposure> map = new java.util.HashMap<>(had);
		float most = 0;
		for (BlockPos p : new BlockPos[]{pos, pos.above(), pos.below()}) {
			Exposure e = map.get(Long.toString(p.asLong()));
			if (e == null) continue;
			if (p.equals(pos)) {
				map.remove(Long.toString(p.asLong()));
				// a crop younger than when it was exposed was planted afterwards
				if (age(state) >= 0 && e.age() >= 0 && age(state) < e.age()) continue;
			}
			most = Math.max(most, e.rads());
		}
		if (map.isEmpty()) chunk.removeAttached(CROP_EXPOSURE);
		else chunk.setAttached(CROP_EXPOSURE, map);
		return most;
	}

	// ------------------------------------------------------------------ storage

	/**
	 * Food stored where it is irradiated takes up radiation: every container in a chunk near radiation, and food lying on
	 * the ground. Called every {@code food.storageIntervalTicks} for the chunks near radiation.
	 */
	public static void storage(ServerLevel level, LevelChunk chunk, float seconds) {
		RadiationConfig.Food cfg = RadiationConfig.get().food;
		for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
			if (!(be instanceof Container container) || be.isRemoved()) continue;
			float rate = RadiationTracker.contaminationAt(level, Vec3.atCenterOf(be.getBlockPos()));
			if (rate < 0.0005f) continue;
			float pending = be.getAttachedOrElse(PENDING, 0f) + rate * seconds * cfg.storageUptake;
			float most = 0;
			boolean any = false;
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack s = container.getItem(i);
				if (contaminable(s)) {
					any = true;
					most = Math.max(most, of(s));
				}
			}
			if (!any) {
				// nothing to take it up: an empty chest does not save it up for later
				if (be.hasAttached(PENDING)) be.removeAttached(PENDING);
				continue;
			}
			if (pending >= Math.max(0.05f, most * 0.1f)) {
				for (int i = 0; i < container.getContainerSize(); i++) {
					ItemStack s = container.getItem(i);
					if (contaminable(s)) set(s, fine(of(s) + pending));
				}
				pending = 0;
			}
			be.setAttached(PENDING, pending);
			be.setChanged();
		}
	}

	/** Food lying on the ground. */
	public static void onGround(ItemEntity item, float rate, float seconds) {
		ItemStack s = item.getItem();
		if (!contaminable(s) || rate < 0.0005f) return;
		float pending = item.getAttachedOrElse(PENDING, 0f) + rate * seconds * RadiationConfig.get().food.storageUptake;
		if (pending >= Math.max(0.05f, of(s) * 0.1f)) {
			ItemStack copy = s.copy();
			set(copy, fine(of(s) + pending));
			item.setItem(copy);
			pending = 0;
		}
		item.setAttached(PENDING, pending);
	}
}
