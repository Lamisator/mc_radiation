package dev.radiation.mixin;

import dev.radiation.world.RadiationEcology;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Radiation slows the growth of crops and other plants: some of their random ticks are skipped. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateRandomTickMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void radiation$slowGrowth(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (RadiationEcology.suppressGrowth((BlockState) (Object) this, level, pos, random)) {
			ci.cancel();
		}
	}
}
