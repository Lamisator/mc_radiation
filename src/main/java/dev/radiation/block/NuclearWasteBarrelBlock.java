package dev.radiation.block;

import dev.radiation.world.RadiationTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A leaking barrel that radiates its surroundings. Strength and radius are set in the config. */
public class NuclearWasteBarrelBlock extends Block {
	private static final DustParticleOptions OOZE = new DustParticleOptions(0x7CFF3A, 0.9f);

	public NuclearWasteBarrelBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			RadiationTracker.addBarrel(serverLevel, pos);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
			double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
			level.addParticle(OOZE, x, pos.getY() + 1.05, z, 0, 0.02, 0);
		}
	}
}
