package dev.radiation.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Rotating amber warning light that turns on while a vault door within 16 blocks opens or closes. */
public class VaultAlarmLightBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final int RANGE = 16;
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		SHAPES.put(Direction.UP, Block.box(4, 0, 4, 12, 8, 12));
		SHAPES.put(Direction.DOWN, Block.box(4, 8, 4, 12, 16, 12));
		SHAPES.put(Direction.NORTH, Block.box(4, 4, 8, 12, 12, 16));
		SHAPES.put(Direction.SOUTH, Block.box(4, 4, 0, 12, 12, 8));
		SHAPES.put(Direction.EAST, Block.box(0, 4, 4, 8, 12, 12));
		SHAPES.put(Direction.WEST, Block.box(8, 4, 4, 16, 12, 12));
	}

	public VaultAlarmLightBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	/** Switches every alarm light within {@link #RANGE} blocks of a vault door on or off. */
	static void setNearby(Level level, BlockPos door, boolean lit) {
		for (BlockPos p : BlockPos.betweenClosed(door.offset(-RANGE, -RANGE, -RANGE), door.offset(RANGE, RANGE, RANGE))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof VaultAlarmLightBlock && s.getValue(LIT) != lit) {
				level.setBlock(p, s.setValue(LIT, lit), Block.UPDATE_ALL);
			}
		}
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VaultAlarmLightBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? createTickerHelper(type, VaultBlocks.ALARM_LIGHT_ENTITY, VaultAlarmLightBlockEntity::clientTick) : null;
	}
}
