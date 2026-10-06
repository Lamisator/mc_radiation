package dev.radiation.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** An effect whose behaviour lives in {@link dev.radiation.world.RadiationTracker}; the effect itself is a marker. */
public class SimpleEffect extends MobEffect {
	public SimpleEffect(MobEffectCategory category, int color) {
		super(category, color);
	}
}
