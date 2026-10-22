package dev.radiation.item;

import com.mojang.serialization.Codec;
import dev.radiation.RadiationMod;
import dev.radiation.config.RadiationConfig;
import dev.radiation.world.Contamination;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * A pressure sprayer for washing radioactivity off the ground. Right-click water to fill it; hold right-click on the
 * ground to spray: every burst washes a share of the contamination off the patches within a few blocks of the spot
 * (see {@code contamination} in the config). Some of the dirty water runs into the nearest pond or river, which gets
 * contaminated in turn; the rest goes down the drain.
 */
public class DecontaminationSprayerItem extends Item {
	public static final DataComponentType<Integer> WATER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, RadiationMod.id("sprayer_water"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
	private static final int BURST_TICKS = 5;

	public DecontaminationSprayerItem(Properties properties) {
		super(properties);
	}

	public static int water(ItemStack stack) {
		return stack.getOrDefault(WATER, 0);
	}

	private static int tank() {
		return RadiationConfig.get().contamination.sprayerTank;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult fluid = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (fluid.getType() == HitResult.Type.BLOCK && level.getFluidState(fluid.getBlockPos()).is(FluidTags.WATER)) {
			if (water(stack) < tank()) {
				stack.set(WATER, tank());
				level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1f, 1f);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (water(stack) <= 0) {
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.radiation.sprayer.empty").withStyle(ChatFormatting.YELLOW));
			}
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		if (!(entity instanceof Player player) || (getUseDuration(stack, entity) - ticksRemaining) % BURST_TICKS != 0) {
			return;
		}
		if (water(stack) <= 0) {
			entity.releaseUsingItem();
			return;
		}
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos ground = hit.getBlockPos();
		BlockPos surface = ground.above();
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		RadiationConfig.Contamination cfg = RadiationConfig.get().contamination;
		float washed = Contamination.wash(server, surface, cfg.sprayRadius, cfg.sprayWashes, cfg.runoffToWater);
		if (!player.getAbilities().instabuild) {
			stack.set(WATER, water(stack) - 1);
		}
		Vec3 nozzle = player.getEyePosition().add(player.getLookAngle().scale(0.8)).add(0, -0.3, 0);
		Vec3 spot = hit.getLocation();
		for (int i = 0; i < 6; i++) {
			double t = (i + 1) / 7.0;
			server.sendParticles(ParticleTypes.SPLASH, Mth.lerp(t, nozzle.x, spot.x), Mth.lerp(t, nozzle.y, spot.y), Mth.lerp(t, nozzle.z, spot.z),
					2, 0.05, 0.05, 0.05, 0.02);
		}
		server.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y + 0.1, spot.z, 25, cfg.sprayRadius * 0.4, 0.05, cfg.sprayRadius * 0.4, 0.1);
		server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WATER.defaultBlockState()), spot.x, spot.y + 0.1, spot.z, 8, 0.6, 0.05, 0.6, 0.1);
		server.playSound(null, spot.x, spot.y, spot.z, SoundEvents.WEATHER_RAIN, SoundSource.PLAYERS, 0.5f, 1.6f);
		if (player instanceof ServerPlayer sp) {
			sp.sendOverlayMessage(Component.translatable("message.radiation.sprayer.washed",
					String.format(Locale.ROOT, "%.3f", washed), water(stack), tank()).withStyle(washed > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13f * Math.clamp(water(stack) / (float) tank(), 0f, 1f));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0x3F76E4;
	}
}
