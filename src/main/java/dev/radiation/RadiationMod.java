package dev.radiation;

import dev.radiation.command.RadiationCommands;
import dev.radiation.config.RadiationConfig;
import dev.radiation.network.RadiationSettingsPayload;
import dev.radiation.network.RadiationStatusPayload;
import dev.radiation.network.VaultDoorSettingsPayload;
import dev.radiation.vault.VaultBlocks;
import dev.radiation.vault.VaultDoorBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import dev.radiation.registry.ModRegistry;
import dev.radiation.world.RadiationTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadiationMod implements ModInitializer {
	public static final String MOD_ID = "radiation";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		RadiationConfig.load();
		ModRegistry.init();
		VaultBlocks.init();

		PayloadTypeRegistry.clientboundPlay().register(RadiationStatusPayload.TYPE, RadiationStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RadiationSettingsPayload.TYPE, RadiationSettingsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(dev.radiation.network.CloudPayload.TYPE, dev.radiation.network.CloudPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VaultDoorSettingsPayload.TYPE, VaultDoorSettingsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VaultDoorSettingsPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!player.isCreative() && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
				return;
			}
			if (player.blockPosition().distSqr(payload.pos()) < 64 * 64
					&& player.level().getBlockEntity(payload.pos()) instanceof VaultDoorBlockEntity door) {
				door.configure(payload.number(), payload.rollLeft());
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RadiationTracker.onServerStarted(server);
			dev.radiation.world.Clouds.load(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			dev.radiation.world.Clouds.unload();
			RadiationTracker.onServerStopping(server);
		});
		ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> {
			RadiationTracker.saveSources();
			dev.radiation.world.Clouds.save();
		});
		ServerTickEvents.END_SERVER_TICK.register(RadiationTracker::tick);
		ServerTickEvents.END_SERVER_TICK.register(dev.radiation.world.Clouds::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RadiationTracker.onPlayerJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RadiationTracker.onPlayerLeave(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> RadiationTracker.onPlayerRespawn(newPlayer));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> RadiationCommands.register(dispatcher));

		LOGGER.info("Radiation loaded. Watch your Geiger counter.");
	}
}
