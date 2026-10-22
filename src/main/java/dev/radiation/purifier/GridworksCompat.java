package dev.radiation.purifier;

import net.antwire.gridworks.api.ElectricDevice;
import net.antwire.gridworks.api.GridworksApi;
import net.antwire.gridworks.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The only class that touches Gridworks; loaded only when it is installed. The purifier is a 230 V device. */
final class GridworksCompat {
	private GridworksCompat() {
	}

	static void register() {
		GridworksApi.DEVICE.registerForBlocks((level, pos, state, be, side) -> new Device(level, pos.immutable()), PurifierBlocks.WATER_PURIFIER);
	}

	private record Device(Level level, BlockPos pos) implements ElectricDevice {
		@Override
		public VoltageClass voltage() {
			return VoltageClass.AC_230V;
		}

		@Override
		public double demand() {
			return this.level.getBlockEntity(this.pos) instanceof WaterPurifierBlockEntity be ? be.demand() : 0;
		}

		@Override
		public void supplied(double fraction, long gameTime) {
			if (this.level.getBlockEntity(this.pos) instanceof WaterPurifierBlockEntity be) {
				be.supplied(fraction, gameTime);
			}
		}
	}
}
