package dev.radiation.vault;

import dev.radiation.util.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The cog-shaped vault door. This block sits in the centre of a round 5×5 opening; the other 20 cells are
 * {@link VaultDoorPartBlock}s while the door is closed. The door itself is drawn by the block entity renderer:
 * when it opens it is pulled back into the vault and rolls aside.
 */
public class VaultDoorBlock extends BaseEntityBlock {
	/** The side the door's printed front faces: the outside of the vault. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public VaultDoorBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(POWERED, false));
	}


	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN, POWERED);
	}

	/** The 20 cells around the centre that the closed door fills: a 5×5 square without its corners. */
	public static List<BlockPos> footprint(BlockPos center, Direction facing) {
		Direction right = facing.getClockWise();
		List<BlockPos> cells = new ArrayList<>(20);
		for (int a = -2; a <= 2; a++) {
			for (int b = -2; b <= 2; b++) {
				if ((a == 0 && b == 0) || (Math.abs(a) == 2 && Math.abs(b) == 2)) {
					continue;
				}
				cells.add(center.relative(right, a).above(b));
			}
		}
		return cells;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		Level level = context.getLevel();
		for (BlockPos cell : footprint(context.getClickedPos(), facing)) {
			if (!level.getBlockState(cell).canBeReplaced(context) || cell.getY() < level.getMinY() || cell.getY() > level.getMaxY()) {
				if (context.getPlayer() != null && !level.isClientSide()) {
					context.getPlayer().sendOverlayMessage(Component.translatable("block.radiation.vault_door.no_room").withStyle(ChatFormatting.RED));
				}
				return null;
			}
		}
		return this.defaultBlockState().setValue(FACING, facing);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		// also covers /setblock and structure placement, not just players
		if (!level.isClientSide() && !oldState.is(this) && !state.getValue(OPEN)) {
			placeParts(level, pos, state.getValue(FACING));
		}
	}

	static void placeParts(Level level, BlockPos pos, Direction facing) {
		BlockState part = VaultBlocks.VAULT_DOOR_PART.defaultBlockState();
		for (BlockPos cell : footprint(pos, facing)) {
			BlockState old = level.getBlockState(cell);
			if (old.isAir() || old.canBeReplaced() || old.is(VaultBlocks.VAULT_DOOR_PART)) {
				level.setBlock(cell, part, Block.UPDATE_ALL);
			}
		}
	}

	static void removeParts(Level level, BlockPos pos, Direction facing) {
		for (BlockPos cell : footprint(pos, facing)) {
			if (level.getBlockState(cell).is(VaultBlocks.VAULT_DOOR_PART)) {
				level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		removeParts(level, pos, state.getValue(FACING));
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return use(level, pos, player);
	}

	/** Shared by the door and its parts: sneak-use opens the settings, a plain use explains how to open it. */
	static InteractionResult use(Level level, BlockPos controller, Player player) {
		if (player.isShiftKeyDown()) {
			if (level.isClientSide() && level.getBlockEntity(controller) instanceof VaultDoorBlockEntity door) {
				if (player.isCreative() || player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
					ClientHooks.openVaultDoorSettings.accept(door);
				} else {
					player.sendOverlayMessage(Component.translatable("block.radiation.vault_door.no_permission").withStyle(ChatFormatting.RED));
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable("block.radiation.vault_door.use_console").withStyle(ChatFormatting.YELLOW));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (level.isClientSide()) {
			return;
		}
		boolean powered = level.hasNeighborSignal(pos);
		if (powered != state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
			if (powered && level.getBlockEntity(pos) instanceof VaultDoorBlockEntity door) {
				door.toggle();
			}
		}
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VaultDoorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, VaultBlocks.VAULT_DOOR_ENTITY, VaultDoorBlockEntity::tick);
	}
}
