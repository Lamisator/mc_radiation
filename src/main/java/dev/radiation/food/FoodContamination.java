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
 * <li>the field: a crop harvested where the ground is irradiated takes up {@code food.cropUptake} rad per rad/s there;
 * <li>the animal: meat, eggs and the like from an irradiated animal carry {@code food.animalShare} of its rads;
 * <li>storage: food lying in a chest, barrel, furnace... or on the ground where it is irradiated takes up
 * {@code food.storageUptake} rad per rad of exposure. Food carried in an inventory takes up nothing more;
 * <li>processing: what is made of contaminated food is contaminated too - the contamination of all ingredients is
 * shared out over the result (three wheat at 10 rad make a loaf of bread at 30 rad; a raw steak at 20 rad makes a
 * cooked one at 20).
 * </ul>
 * Harvests and products are rounded to a few steps (0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10...) so that they stack.
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
				// a harvest: what the plant took up from the irradiated ground
				each = RadiationEcology.rateAt(level, BlockPos.containing(origin)) * cfg.cropUptake;
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
		return c == null ? 0 : c.rads();
	}

	public static void set(ItemStack stack, float rads) {
		if (rads <= 0) stack.remove(CONTAMINATION);
		else stack.set(CONTAMINATION, new Contamination(rads));
	}

	/** What processing gives: the contamination of everything that went in, shared out over what comes out. */
	public static void processed(ItemStack result, List<ItemStack> inputs) {
		if (result.isEmpty() || !contaminable(result)) return;
		float total = 0;
		for (ItemStack in : inputs) {
			if (!in.isEmpty()) total += of(in);
		}
		if (total <= 0) return;
		set(result, coarse(total / Math.max(1, result.getCount())));
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

	// ------------------------------------------------------------------ storage

	/**
	 * Food stored where it is irradiated takes up radiation: every container in a chunk near radiation, and food lying on
	 * the ground. Called every {@code food.storageIntervalTicks} for the chunks near radiation.
	 */
	public static void storage(ServerLevel level, LevelChunk chunk, float seconds) {
		RadiationConfig.Food cfg = RadiationConfig.get().food;
		for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
			if (!(be instanceof Container container) || be.isRemoved()) continue;
			float rate = RadiationEcology.rateAt(level, be.getBlockPos());
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
