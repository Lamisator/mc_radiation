package dev.radiation.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Client-side slide animation of a sliding door; the open/closed state itself lives in the block state. */
public class SlidingDoorBlockEntity extends BlockEntity {
	public static final int SLIDE_TICKS = 12;
	private int progress = -1;
	private int prevProgress;

	public SlidingDoorBlockEntity(BlockPos pos, BlockState state) {
		super(VaultBlocks.SLIDING_DOOR_ENTITY, pos, state);
	}

	/** 0 = closed, 1 = fully raised. */
	public float openness(float partialTicks) {
		if (this.progress < 0) {
			return this.getBlockState().getValue(SlidingDoorBlock.OPEN) ? 1.0F : 0.0F;
		}
		float t = Mth.lerp(partialTicks, this.prevProgress, this.progress) / SLIDE_TICKS;
		// ease in and out like a hydraulic piston
		return t * t * (3 - 2 * t);
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, SlidingDoorBlockEntity door) {
		boolean open = state.getValue(SlidingDoorBlock.OPEN);
		if (door.progress < 0) {
			door.progress = open ? SLIDE_TICKS : 0;
		}
		door.prevProgress = door.progress;
		door.progress = Mth.clamp(door.progress + (open ? 1 : -1), 0, SLIDE_TICKS);
	}
}
