package dev.radiation.client;

import dev.radiation.RadiationMod;
import dev.radiation.network.RadiationSettingsPayload;
import dev.radiation.network.RadiationStatusPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public class RadiationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfig.load();

		ClientPlayNetworking.registerGlobalReceiver(RadiationStatusPayload.TYPE, (payload, context) -> ClientRadiationState.onStatus(payload));
		ClientPlayNetworking.registerGlobalReceiver(RadiationSettingsPayload.TYPE, (payload, context) -> ClientRadiationState.onSettings(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientRadiationState.reset());

		ClientTickEvents.END_CLIENT_TICK.register(GeigerCounterSound::tick);
		HudElementRegistry.addLast(RadiationMod.id("rad_meter"), new RadMeterHud());
	}
}
