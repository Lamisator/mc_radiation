package dev.radiation.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Client-side spin of the alarm light's reflector. */
public class VaultAlarmLightBlockEntity extends BlockEntity {
	private float angle;
	private float prevAngle;

	public VaultAlarmLightBlockEntity(BlockPos pos, BlockState state) {
		super(VaultBlocks.ALARM_LIGHT_ENTITY, pos, state);
	}

	public float angle(float partialTicks) {
		return Mth.lerp(partialTicks, this.prevAngle, this.angle);
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, VaultAlarmLightBlockEntity light) {
		light.prevAngle = light.angle;
		if (state.getValue(VaultAlarmLightBlock.LIT)) {
			light.angle += 18.0F;
		}
	}
}
