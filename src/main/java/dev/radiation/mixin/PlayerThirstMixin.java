package dev.radiation.mixin;

import dev.radiation.thirst.Thirst;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Thirst follows hunger: what exhausts you makes you thirsty; too thirsty and you cannot sprint. */
@Mixin(Player.class)
public abstract class PlayerThirstMixin {
	@Inject(method = "causeFoodExhaustion", at = @At("HEAD"))
	private void radiation$thirstExhaustion(float amount, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer player && !player.getAbilities().invulnerable) {
			Thirst.exhaust(player, amount);
		}
	}

	@Inject(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("HEAD"), cancellable = true)
	private void radiation$thirstSprint(CallbackInfoReturnable<Boolean> cir) {
		if (Thirst.tooThirstyToSprint((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
