package dev.radiation.thirst;

import com.mojang.serialization.Codec;
import dev.radiation.RadiationMod;
import dev.radiation.config.RadiationConfig;
import dev.radiation.food.FoodContamination;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A canteen: five servings of water. Right-click water to fill it, hold right-click to drink one serving. Water from a
 * contaminated pond carries its radiation (component radiation:contamination, per serving); topping a canteen up mixes
 * the old water with the new.
 */
public class CanteenItem extends Item {
	public static final DataComponentType<Integer> WATER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, RadiationMod.id("canteen_water"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public CanteenItem(Properties properties) {
		super(properties);
	}

	public static int servings(ItemStack stack) {
		return stack.getOrDefault(WATER, 0);
	}

	public static int capacity() {
		return RadiationConfig.get().thirst.canteenServings;
	}

	/** Pours {@code servings} of water carrying {@code rads} per serving into the canteen; mixes with what is in it. Returns how much went in. */
	public static int fill(ItemStack stack, int servings, float rads) {
		int had = servings(stack);
		int add = Math.min(servings, capacity() - had);
		if (add <= 0) {
			return 0;
		}
		float mixed = (FoodContamination.of(stack) * had + rads * add) / (had + add);
		stack.set(WATER, had + add);
		FoodContamination.set(stack, FoodContamination.fine(mixed));
		return add;
	}

	/** Takes one serving out; returns its rads, or -1 when empty. */
	public static float take(ItemStack stack) {
		int had = servings(stack);
		if (had <= 0) {
			return -1;
		}
		float rads = FoodContamination.of(stack);
		stack.set(WATER, had - 1);
		if (had == 1) {
			FoodContamination.set(stack, 0);
		}
		return rads;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockPos water = WaterSources.lookingAtWater(player, ClipContext.Fluid.SOURCE_ONLY);
		if (water != null && level.mayInteract(player, water) && servings(stack) < capacity()) {
			if (level instanceof ServerLevel server) {
				fill(stack, capacity(), Thirst.waterRads(server, water));
				level.gameEvent(player, GameEvent.FLUID_PICKUP, water);
			}
			level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 0.8F);
			return InteractionResult.SUCCESS;
		}
		if (servings(stack) <= 0) {
			return InteractionResult.PASS;
		}
		if (Thirst.enabled(player) && Thirst.get(player) >= Thirst.MAX && !player.isCreative()) {
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			float rads = take(stack);
			if (rads >= 0) {
				Thirst.drink(player, RadiationConfig.get().thirst.servingPoints, rads);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.6f, 1f);
			}
		}
		entity.gameEvent(GameEvent.DRINK);
		return stack;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		if (!level.isClientSide() && ticksRemaining % 8 == 0 && ticksRemaining > 0) {
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.35f,
					0.9f + level.getRandom().nextFloat() * 0.2f);
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 32;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.DRINK;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13f * Math.clamp(servings(stack) / (float) capacity(), 0f, 1f));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return FoodContamination.of(stack) > 0 ? 0x7FBF2F : 0x3F76E4;
	}
}
