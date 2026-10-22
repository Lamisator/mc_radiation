package dev.radiation.thirst;

import dev.radiation.RadiationMod;
import dev.radiation.config.RadiationConfig;
import dev.radiation.food.FoodContamination;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ConsumableListener;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import java.util.List;

/**
 * Where drinking water comes from: water blocks (a sip with an empty hand, or into a bottle or canteen), and what
 * vanilla drinks give. Water bottles filled where there is radiation carry it (component radiation:contamination, the
 * same as contaminated food), and so do canteens.
 */
public final class WaterSources {
	/** Servings of water in a drink: a water bottle 1, milk 1, honey and soups ½. */
	public record Hydration(float servings) implements ConsumableListener {
		public static final Codec<Hydration> CODEC = Codec.FLOAT.xmap(Hydration::new, Hydration::servings);
		public static final StreamCodec<io.netty.buffer.ByteBuf, Hydration> STREAM_CODEC = ByteBufCodecs.FLOAT.map(Hydration::new, Hydration::servings);

		@Override
		public void onConsume(Level level, LivingEntity user, ItemStack stack, Consumable consumable) {
			if (!level.isClientSide() && user instanceof ServerPlayer player && RadiationConfig.get().thirst.enabled) {
				// the rads of a contaminated bottle come with its radiation:contamination component
				Thirst.drink(player, this.servings * RadiationConfig.get().thirst.servingPoints, 0);
			}
		}
	}

	public static final DataComponentType<Hydration> HYDRATION = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, RadiationMod.id("hydration"),
			DataComponentType.<Hydration>builder().persistent(Hydration.CODEC).networkSynchronized(Hydration.STREAM_CODEC).build());

	private WaterSources() {
	}

	public static void init() {
		DefaultItemComponentEvents.MODIFY.register(context -> {
			context.modify(Items.POTION, b -> b.set(HYDRATION, new Hydration(1)));
			context.modify(Items.MILK_BUCKET, b -> b.set(HYDRATION, new Hydration(1)));
			context.modify(Items.HONEY_BOTTLE, b -> b.set(HYDRATION, new Hydration(0.5f)));
			context.modify(List.of(Items.MUSHROOM_STEW, Items.BEETROOT_SOUP, Items.RABBIT_STEW, Items.SUSPICIOUS_STEW),
					(b, item) -> b.set(HYDRATION, new Hydration(0.5f)));
		});

		// a glass bottle filled where the water is contaminated: the bottle carries it
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.GLASS_BOTTLE) || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			BlockPos water = lookingAtWater(sp, ClipContext.Fluid.SOURCE_ONLY);
			if (water == null || !level.mayInteract(player, water)) {
				return InteractionResult.PASS;
			}
			float rads = Thirst.waterRads(server, water);
			if (rads <= 0) {
				return InteractionResult.PASS; // clean water: vanilla does the rest
			}
			ItemStack filled = PotionContents.createItemStack(Items.POTION, Potions.WATER);
			FoodContamination.set(filled, rads);
			level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
			level.gameEvent(player, GameEvent.FLUID_PICKUP, water);
			player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
			// the callback cuts vanilla's use short, including putting the result in the hand: do it here
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filled));
			return InteractionResult.SUCCESS;
		});
	}

	/** A water bottle (any potion that is plain water), dirty or purified water or a canteen: their contamination is per serving of water. */
	public static boolean isWater(ItemStack stack) {
		if (stack.is(Items.POTION)) {
			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
			return contents != null && contents.is(Potions.WATER);
		}
		return stack.is(dev.radiation.purifier.PurifierBlocks.DIRTY_WATER) || stack.is(dev.radiation.purifier.PurifierBlocks.PURIFIED_WATER)
				|| stack.getItem() instanceof CanteenItem;
	}

	/** Water that is there: like food, it takes up radiation where it is stored (an empty canteen has nothing to take it up). */
	public static boolean holdsWater(ItemStack stack) {
		return isWater(stack) && !(stack.getItem() instanceof CanteenItem && CanteenItem.servings(stack) <= 0);
	}

	/** The water block the player looks at, within reach, or null. */
	public static @Nullable BlockPos lookingAtWater(Player player, ClipContext.Fluid fluid) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluid, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = hit.getBlockPos();
		return player.level().getFluidState(pos).is(FluidTags.WATER) ? pos : null;
	}
}
