package dev.radiation.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * {@code /geiger}: the ambient Geiger clicks on this client. {@code /geiger volume <percent>} (100 = as loud as up to
 * 1.10.1, 30 by default), {@code /geiger off}, {@code /geiger on}; saved in config/radiation-client.json.
 */
public final class GeigerCommand {
	private GeigerCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("geiger")
				.executes(ctx -> status(ctx.getSource()))
				.then(ClientCommands.literal("on").executes(ctx -> {
					ClientConfig.get().geigerSound = true;
					ClientConfig.save();
					return status(ctx.getSource());
				}))
				.then(ClientCommands.literal("off").executes(ctx -> {
					ClientConfig.get().geigerSound = false;
					ClientConfig.save();
					return status(ctx.getSource());
				}))
				.then(ClientCommands.literal("volume")
						.then(ClientCommands.argument("percent", FloatArgumentType.floatArg(0, 200)).executes(ctx -> {
							ClientConfig config = ClientConfig.get();
							config.geigerVolumePercent = FloatArgumentType.getFloat(ctx, "percent");
							config.geigerSound = config.geigerVolumePercent > 0;
							ClientConfig.save();
							return status(ctx.getSource());
						})))));
	}

	private static int status(FabricClientCommandSource source) {
		ClientConfig config = ClientConfig.get();
		String volume = String.format(Locale.ROOT, "%.0f", config.geigerVolumePercent);
		source.sendFeedback(config.geigerSound
				? Component.translatable("message.radiation.geiger_sound.on", volume).withStyle(ChatFormatting.YELLOW)
				: Component.translatable("message.radiation.geiger_sound.off", volume).withStyle(ChatFormatting.GRAY));
		return 1;
	}
}
