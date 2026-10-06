package dev.radiation.client;

import dev.radiation.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Plays randomly timed Geiger clicks, their frequency proportional to the current exposure. */
public final class GeigerCounterSound {
	private static final RandomSource RANDOM = RandomSource.create();

	private GeigerCounterSound() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.isPaused() || !client.player.isAlive() || !ClientRadiationState.received) {
			return;
		}
		float exposure = ClientRadiationState.exposure;
		if (exposure <= 0) {
			return;
		}
		ClientConfig config = ClientConfig.get();
		if (config.geigerVolume <= 0) {
			return;
		}
		double clicksPerSecond = Math.min(config.geigerMaxClicksPerSecond, exposure * config.geigerClicksPerRad);
		int clicks = poisson(clicksPerSecond / 20.0);
		for (int i = 0; i < Math.min(clicks, 4); i++) {
			float pitch = 0.85f + RANDOM.nextFloat() * 0.3f;
			float volume = config.geigerVolume * (0.7f + RANDOM.nextFloat() * 0.3f);
			client.getSoundManager().play(new SimpleSoundInstance(ModRegistry.GEIGER_CLICK.location(), SoundSource.PLAYERS, volume, pitch,
					SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
		}
	}

	private static int poisson(double lambda) {
		double limit = Math.exp(-lambda);
		double product = RANDOM.nextDouble();
		int count = 0;
		while (product > limit && count < 16) {
			count++;
			product *= RANDOM.nextDouble();
		}
		return count;
	}
}
