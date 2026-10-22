package dev.radiation.item;

import dev.radiation.config.RadiationConfig;
import dev.radiation.registry.ModRegistry;
import dev.radiation.world.RadiationTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Right-click for a detailed reading. Carrying it shows the rad meter and, if the server requires it, enables the RAD/s
 * readout. Sneak + right-click on the ground or on water reads the contamination of that patch alone (Radiation 1.10's
 * contaminated ground and water), whatever else radiates around.
 */
public class GeigerCounterItem extends Item {
	public GeigerCounterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isShiftKeyDown()) {
			return this.probe(level, player, hand);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			RadiationConfig config = RadiationConfig.get();
			float raw = RadiationTracker.exposureAt(serverPlayer.level(), RadiationTracker.bodyCenter(serverPlayer), null);
			float protection = RadiationTracker.protection(serverPlayer, config);
			float rads = RadiationTracker.getRads(serverPlayer);
			int stage = config.stageIndexFor(rads);
			String condition = stage >= 0 ? config.stages.get(stage).name : Component.translatable("message.radiation.healthy").getString();

			serverPlayer.sendSystemMessage(Component.translatable("message.radiation.reading.header").withStyle(ChatFormatting.GOLD));
			serverPlayer.sendSystemMessage(Component.translatable("message.radiation.reading.ambient",
					String.format(Locale.ROOT, "%.2f", raw), String.format(Locale.ROOT, "%.2f", raw * (1 - protection))).withStyle(ChatFormatting.YELLOW));
			serverPlayer.sendSystemMessage(Component.translatable("message.radiation.reading.absorbed",
					String.format(Locale.ROOT, "%.0f", rads), String.format(Locale.ROOT, "%.0f", config.maxRads)).withStyle(ChatFormatting.RED));
			serverPlayer.sendSystemMessage(Component.translatable("message.radiation.reading.protection",
					String.format(Locale.ROOT, "%.0f", protection * 100)).withStyle(ChatFormatting.AQUA));
			serverPlayer.sendSystemMessage(Component.translatable("message.radiation.reading.condition", condition).withStyle(ChatFormatting.GRAY));
			serverPlayer.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), ModRegistry.GEIGER_CLICK, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
		}
		return InteractionResult.SUCCESS;
	}

	/** Holds the counter's probe to the patch of ground (or water) the player looks at. */
	private InteractionResult probe(Level level, Player player, InteractionHand hand) {
		net.minecraft.world.phys.BlockHitResult hit = getPlayerPOVHitResult(level, player, net.minecraft.world.level.ClipContext.Fluid.ANY);
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.radiation.probe.aim").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer sp) {
			net.minecraft.core.BlockPos block = hit.getBlockPos();
			boolean water = level.getFluidState(block).is(net.minecraft.tags.FluidTags.WATER);
			net.minecraft.core.BlockPos at = water ? block : block.above();
			dev.radiation.world.Contamination.Reading r = dev.radiation.world.Contamination.read(sp.level(), at);
			String where = String.format(Locale.ROOT, "%d %d %d", block.getX(), block.getY(), block.getZ());
			sp.sendSystemMessage(Component.translatable(water ? "message.radiation.probe.header_water" : "message.radiation.probe.header", where)
					.withStyle(ChatFormatting.GOLD));
			if (r.total() < 0.0005f) {
				sp.sendSystemMessage(Component.translatable("message.radiation.probe.clean").withStyle(ChatFormatting.GREEN));
			} else {
				sp.sendSystemMessage(Component.translatable("message.radiation.probe.total", fmt(r.total())).withStyle(ChatFormatting.YELLOW));
				if (r.fallout() >= 0.0005f) {
					sp.sendSystemMessage(Component.translatable("message.radiation.probe.fallout", fmt(r.fallout())).withStyle(ChatFormatting.GRAY));
				}
				if (r.lasting() >= 0.0005f) {
					sp.sendSystemMessage(Component.translatable("message.radiation.probe.lasting", fmt(r.lasting())).withStyle(ChatFormatting.GRAY));
				}
			}
			if (r.strongestNear() >= 0.0005f) {
				sp.sendSystemMessage(Component.translatable("message.radiation.probe.near", fmt(r.strongestNear())).withStyle(ChatFormatting.GRAY));
			}
			sp.getCooldowns().addCooldown(player.getItemInHand(hand), 10);
			float pitch = r.total() < 0.0005f ? 0.8f : (float) Math.min(2.0, 1.0 + Math.log10(1 + r.total()));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), ModRegistry.GEIGER_CLICK, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, pitch);
		}
		return InteractionResult.SUCCESS;
	}

	private static String fmt(float rads) {
		return String.format(Locale.ROOT, rads >= 10 ? "%.1f" : rads >= 0.1 ? "%.2f" : "%.4f", rads);
	}
}
