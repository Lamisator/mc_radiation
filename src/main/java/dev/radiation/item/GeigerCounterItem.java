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

/** Right-click for a detailed reading. Carrying it shows the rad meter and, if the server requires it, enables the RAD/s readout. */
public class GeigerCounterItem extends Item {
	public GeigerCounterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
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
}
