package dev.radiation.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Light fittings mounted on a wall, floor or ceiling: flat light panels and neon tubes. {@code facing} points away
 * from the surface the lamp is attached to.
 */
public class VaultLampBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	private final Map<Direction, VoxelShape> shapes;

	public VaultLampBlock(Properties properties, boolean tube) {
		super(properties);
		this.shapes = tube ? tubeShapes() : panelShapes();
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN));
	}

	public static VaultLampBlock tube(Properties properties) {
		return new VaultLampBlock(properties, true);
	}

	public static VaultLampBlock panel(Properties properties) {
		return new VaultLampBlock(properties, false);
	}


	private static Map<Direction, VoxelShape> tubeShapes() {
		Map<Direction, VoxelShape> m = new EnumMap<>(Direction.class);
		m.put(Direction.UP, Block.box(0, 0, 7, 16, 2, 9));
		m.put(Direction.DOWN, Block.box(0, 14, 7, 16, 16, 9));
		m.put(Direction.NORTH, Block.box(0, 7, 14, 16, 9, 16));
		m.put(Direction.SOUTH, Block.box(0, 7, 0, 16, 9, 2));
		m.put(Direction.EAST, Block.box(0, 7, 0, 2, 9, 16));
		m.put(Direction.WEST, Block.box(14, 7, 0, 16, 9, 16));
		return m;
	}

	private static Map<Direction, VoxelShape> panelShapes() {
		Map<Direction, VoxelShape> m = new EnumMap<>(Direction.class);
		m.put(Direction.UP, Block.box(2, 0, 2, 14, 1, 14));
		m.put(Direction.DOWN, Block.box(2, 15, 2, 14, 16, 14));
		m.put(Direction.NORTH, Block.box(2, 2, 15, 14, 14, 16));
		m.put(Direction.SOUTH, Block.box(2, 2, 0, 14, 14, 1));
		m.put(Direction.EAST, Block.box(0, 2, 2, 1, 14, 14));
		m.put(Direction.WEST, Block.box(15, 2, 2, 16, 14, 14));
		return m;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shapes.get(state.getValue(FACING));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos support = pos.relative(facing.getOpposite());
		return level.getBlockState(support).isFaceSturdy(level, support, facing);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
				? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}
}
