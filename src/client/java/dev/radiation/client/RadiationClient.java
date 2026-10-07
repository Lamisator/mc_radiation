package dev.radiation.client;

import dev.radiation.RadiationMod;
import dev.radiation.network.RadiationSettingsPayload;
import dev.radiation.network.RadiationStatusPayload;
import dev.radiation.client.vault.AlarmLightRenderer;
import dev.radiation.client.vault.SlidingDoorRenderer;
import dev.radiation.client.vault.VaultAlarmSound;
import dev.radiation.client.vault.VaultDoorRenderer;
import dev.radiation.client.vault.VaultDoorScreen;
import dev.radiation.util.ClientHooks;
import dev.radiation.vault.VaultBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
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
		ClientPlayNetworking.registerGlobalReceiver(dev.radiation.network.CloudPayload.TYPE,
				(payload, context) -> context.client().execute(() -> CloudRenderer.receive(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(CloudRenderer::clear));
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(CloudRenderer::render);

		ClientTickEvents.END_CLIENT_TICK.register(GeigerCounterSound::tick);
		HudElementRegistry.addLast(RadiationMod.id("rad_meter"), new RadMeterHud());

		BlockEntityRendererRegistry.register(VaultBlocks.VAULT_DOOR_ENTITY, VaultDoorRenderer::new);
		BlockEntityRendererRegistry.register(VaultBlocks.SLIDING_DOOR_ENTITY, SlidingDoorRenderer::new);
		ClientHooks.openVaultDoorSettings = door -> Minecraft.getInstance().gui.setScreen(new VaultDoorScreen(door));
		ClientHooks.vaultAlarm = door -> Minecraft.getInstance().getSoundManager().play(new VaultAlarmSound(door));
		BlockEntityRendererRegistry.register(VaultBlocks.ALARM_LIGHT_ENTITY, AlarmLightRenderer::new);
	}
}
