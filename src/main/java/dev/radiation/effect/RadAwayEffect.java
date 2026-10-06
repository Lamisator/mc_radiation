package dev.radiation.effect;

import dev.radiation.config.RadiationConfig;
import dev.radiation.world.RadiationTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Flushes rads out of the body over the effect's duration. */
public class RadAwayEffect extends MobEffect {
	public RadAwayEffect(MobEffectCategory category, int color) {
		super(category, color);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		return true;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
		if (mob instanceof ServerPlayer player) {
			RadiationConfig config = RadiationConfig.get();
			float perTick = config.radAwayTotalRads / (config.radAwayDurationSeconds * 20f);
			RadiationTracker.addRads(player, -perTick * (amplification + 1));
		}
		return true;
	}
}
