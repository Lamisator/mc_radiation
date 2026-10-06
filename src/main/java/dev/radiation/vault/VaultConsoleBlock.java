package dev.radiation.vault;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Control pedestal: pressing its button opens or closes the nearest vault door within 16 blocks. */
public class VaultConsoleBlock extends HorizontalDirectionalBlock {
	public static final int RANGE = 16;
	private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 11, 13), Block.box(1, 11, 1, 15, 15, 15));

	public VaultConsoleBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}


	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	public static @Nullable VaultDoorBlockEntity nearestDoor(Level level, BlockPos pos) {
		VaultDoorBlockEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-RANGE, -RANGE, -RANGE), pos.offset(RANGE, RANGE, RANGE))) {
			if (level.getBlockState(p).getBlock() instanceof VaultDoorBlock && level.getBlockEntity(p) instanceof VaultDoorBlockEntity door) {
				double d = p.distSqr(pos);
				if (d < bestDist) {
					bestDist = d;
					best = door;
				}
			}
		}
		return best;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		level.playSound(null, pos, VaultBlocks.VAULT_CONSOLE_BEEP, SoundSource.BLOCKS, 1.0F, 1.0F);
		VaultDoorBlockEntity door = nearestDoor(level, pos);
		if (door == null) {
			player.sendOverlayMessage(Component.translatable("block.radiation.vault_console.no_door").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		boolean opening = !door.isOpen();
		door.setOpen(opening);
		player.sendOverlayMessage(Component.translatable(opening ? "block.radiation.vault_console.opening" : "block.radiation.vault_console.closing",
				door.number()).withStyle(ChatFormatting.YELLOW));
		return InteractionResult.SUCCESS;
	}
}
