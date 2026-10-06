package dev.radiation.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Invisible, solid filler for the closed vault door's opening. Breaking one breaks the whole door. */
public class VaultDoorPartBlock extends Block {
	public VaultDoorPartBlock(Properties properties) {
		super(properties);
	}


	/** The vault door controller this cell belongs to, if any. */
	public static @Nullable BlockPos findController(BlockGetter level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof VaultDoorBlock) {
				Direction facing = state.getValue(VaultDoorBlock.FACING);
				if (VaultDoorBlock.footprint(p, facing).contains(pos)) {
					return p.immutable();
				}
			}
		}
		return null;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		BlockPos controller = findController(level, pos);
		if (controller != null && !level.isClientSide()) {
			level.destroyBlock(controller, !player.isCreative(), player);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		BlockPos controller = findController(level, pos);
		return controller == null ? InteractionResult.PASS : VaultDoorBlock.use(level, controller, player);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(VaultBlocks.VAULT_DOOR_ITEM);
	}
}
