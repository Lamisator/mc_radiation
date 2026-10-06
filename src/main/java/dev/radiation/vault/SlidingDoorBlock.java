package dev.radiation.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A two-block-tall steel door that slides straight up into the wall above it. Doors standing side by side in the
 * same wall open and close together, so they can be combined into wider doorways.
 */
public class SlidingDoorBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	private static final VoxelShape PANEL_Z = Block.box(0, 0, 6, 16, 16, 10);
	private static final VoxelShape PANEL_X = Block.box(6, 0, 0, 10, 16, 16);
	private static final int MAX_GROUP = 4;

	public SlidingDoorBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
				.setValue(OPEN, false).setValue(POWERED, false));
	}


	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, OPEN, POWERED);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? PANEL_Z : PANEL_X;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : this.getShape(state, level, pos, context);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type != PathComputationType.WATER && state.getValue(OPEN);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
			return null;
		}
		boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(POWERED, powered).setValue(OPEN, powered);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		DoubleBlockHalf half = state.getValue(HALF);
		if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
			return neighbourState.is(this) && neighbourState.getValue(HALF) != half
					? neighbourState.setValue(HALF, half)
					: Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// breaking the upper half in creative must not drop the item from the lower half
		if (!level.isClientSide() && (player.preventsBlockDrops() || !player.hasCorrectToolForDrops(state)) && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos below = pos.below();
			BlockState lower = level.getBlockState(below);
			if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
				level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				level.levelEvent(player, 2001, below, Block.getId(lower));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide()) {
			setGroupOpen(level, lower(pos, state), !state.getValue(OPEN));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (level.isClientSide()) {
			return;
		}
		BlockPos lower = lower(pos, state);
		boolean powered = level.hasNeighborSignal(lower) || level.hasNeighborSignal(lower.above());
		if (powered != state.getValue(POWERED)) {
			BlockState lowerState = level.getBlockState(lower);
			if (lowerState.is(this)) {
				level.setBlock(lower, lowerState.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
				level.setBlock(lower.above(), level.getBlockState(lower.above()).setValue(POWERED, powered), Block.UPDATE_CLIENTS);
			}
			if (powered != state.getValue(OPEN)) {
				setGroupOpen(level, lower, powered);
			}
		}
	}

	private static BlockPos lower(BlockPos pos, BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
	}

	/** Opens or closes this door and every sliding door standing next to it in the same wall. */
	private void setGroupOpen(Level level, BlockPos lower, boolean open) {
		BlockState origin = level.getBlockState(lower);
		if (!origin.is(this)) {
			return;
		}
		Direction facing = origin.getValue(FACING);
		List<BlockPos> group = new ArrayList<>();
		group.add(lower);
		for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
			for (int i = 1; i <= MAX_GROUP; i++) {
				BlockPos p = lower.relative(side, i);
				BlockState s = level.getBlockState(p);
				if (!s.is(this) || s.getValue(HALF) != DoubleBlockHalf.LOWER || s.getValue(FACING).getAxis() != facing.getAxis()) {
					break;
				}
				group.add(p);
			}
		}
		for (BlockPos p : group) {
			BlockState s = level.getBlockState(p);
			if (s.getValue(OPEN) != open) {
				level.setBlock(p, s.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
				BlockState up = level.getBlockState(p.above());
				if (up.is(this)) {
					level.setBlock(p.above(), up.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
				}
			}
		}
		level.playSound(null, lower, open ? VaultBlocks.SLIDING_DOOR_OPEN : VaultBlocks.SLIDING_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F,
				0.95F + level.getRandom().nextFloat() * 0.1F);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new SlidingDoorBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? createTickerHelper(type, VaultBlocks.SLIDING_DOOR_ENTITY, SlidingDoorBlockEntity::clientTick) : null;
	}
}
