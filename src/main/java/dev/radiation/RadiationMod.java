package dev.radiation;

import dev.radiation.command.RadiationCommands;
import dev.radiation.config.RadiationConfig;
import dev.radiation.network.RadiationSettingsPayload;
import dev.radiation.network.RadiationStatusPayload;
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

		PayloadTypeRegistry.clientboundPlay().register(RadiationStatusPayload.TYPE, RadiationStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RadiationSettingsPayload.TYPE, RadiationSettingsPayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(RadiationTracker::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(RadiationTracker::onServerStopping);
		ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> RadiationTracker.saveSources());
		ServerTickEvents.END_SERVER_TICK.register(RadiationTracker::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RadiationTracker.onPlayerJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RadiationTracker.onPlayerLeave(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> RadiationTracker.onPlayerRespawn(newPlayer));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> RadiationCommands.register(dispatcher));

		LOGGER.info("Radiation loaded. Watch your Geiger counter.");
	}
}
