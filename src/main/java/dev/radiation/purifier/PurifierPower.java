package dev.radiation.purifier;

import dev.radiation.config.RadiationConfig;
import net.fabricmc.loader.api.FabricLoader;

/** Whether the purifier runs on Gridworks power. Only {@link GridworksCompat} touches Gridworks classes. */
public final class PurifierPower {
	private static final boolean GRIDWORKS = FabricLoader.getInstance().isModLoaded("gridworks");

	private PurifierPower() {
	}

	public static boolean gridworks() {
		return GRIDWORKS;
	}

	public static boolean needsGrid() {
		return GRIDWORKS && RadiationConfig.get().purifier.requireGridPower;
	}

	public static void init() {
		if (GRIDWORKS) {
			GridworksCompat.register();
		}
	}
}
