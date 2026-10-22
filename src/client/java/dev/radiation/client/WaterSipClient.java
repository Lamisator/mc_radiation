package dev.radiation.client;

import dev.radiation.network.DrinkWaterPayload;
import dev.radiation.thirst.Thirst;
import dev.radiation.thirst.WaterSources;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Right-click water with an empty hand: a sip (twice a second while held). */
public final class WaterSipClient {
	private static int cooldown;

	private WaterSipClient() {
	}

	public static void tick(Minecraft mc) {
		if (cooldown > 0) {
			cooldown--;
		}
		var player = mc.player;
		if (player == null || mc.gui.screen() != null || !Thirst.clientEnabled || !mc.options.keyUse.isDown() || player.isSpectator()
				|| !player.getMainHandItem().isEmpty() || cooldown > 0 || Thirst.get(player) >= Thirst.MAX) {
			return;
		}
		// an entity in the way: that is what the click is for
		if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
			return;
		}
		if (WaterSources.lookingAtWater(player, ClipContext.Fluid.ANY) == null) {
			return;
		}
		cooldown = 10;
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		ClientPlayNetworking.send(DrinkWaterPayload.INSTANCE);
	}
}
