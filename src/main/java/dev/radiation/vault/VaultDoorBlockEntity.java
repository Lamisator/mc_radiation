package dev.radiation.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * State and animation of a vault door. Opening: the screw arm behind the door extends and screws into the cog,
 * pulls it back into the vault, lets go, and the cog rolls aside. Closing plays it backwards. Both sides tick the
 * animation; the server only sends a block update when the door is toggled or reconfigured.
 */
public class VaultDoorBlockEntity extends BlockEntity {
	// animation timeline in ticks: the arm extends, screws its head into the door, pulls the door back,
	// unscrews and backs off, then the door rolls aside
	public static final int T_EXTEND = 25;
	public static final int T_SCREW = 45;
	public static final int T_PULL = 81;
	public static final int T_RELEASE = 95;
	public static final int TOTAL_TICKS = 175;
	/** How far the door is pulled back into the vault, in blocks. */
	public static final float PULL_DEPTH = 1.1F;
	/** How far it rolls aside, in blocks. */
	public static final float ROLL_DISTANCE = 5.3F;
	/** Radius used for the rolling rotation. */
	public static final float RADIUS = 2.45F;

	private int number = 1;
	private boolean rollLeft;
	private boolean open;
	private int progress;
	private int prevProgress;
	private boolean ticked;

	public VaultDoorBlockEntity(BlockPos pos, BlockState state) {
		super(VaultBlocks.VAULT_DOOR_ENTITY, pos, state);
	}

	public int number() {
		return this.number;
	}

	public boolean rollLeft() {
		return this.rollLeft;
	}

	public boolean isOpen() {
		return this.open;
	}

	public Direction facing() {
		return this.getBlockState().getValue(VaultDoorBlock.FACING);
	}

	/** Animation position in ticks, interpolated for rendering. */
	public float progress(float partialTicks) {
		return Mth.lerp(partialTicks, this.prevProgress, this.progress);
	}

	public void configure(int number, boolean rollLeft) {
		this.number = Mth.clamp(number, 0, 999);
		this.rollLeft = rollLeft;
		this.sync();
	}

	public void toggle() {
		this.setOpen(!this.open);
	}

	public void setOpen(boolean open) {
		if (this.level == null || this.level.isClientSide() || open == this.open) {
			return;
		}
		this.open = open;
		BlockState state = this.getBlockState();
		if (open) {
			VaultDoorBlock.removeParts(this.level, this.worldPosition, state.getValue(VaultDoorBlock.FACING));
			this.level.setBlock(this.worldPosition, state.setValue(VaultDoorBlock.OPEN, true), Block.UPDATE_ALL);
		}
		this.level.playSound(null, this.worldPosition, open ? VaultBlocks.VAULT_DOOR_OPEN : VaultBlocks.VAULT_DOOR_CLOSE, SoundSource.BLOCKS, 3.0F, 1.0F);
		this.sync();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, VaultDoorBlockEntity door) {
		door.ticked = true;
		door.prevProgress = door.progress;
		if (door.open && door.progress < TOTAL_TICKS) {
			door.progress++;
		} else if (!door.open && door.progress > 0) {
			door.progress--;
			if (door.progress == 0 && !level.isClientSide()) {
				VaultDoorBlock.placeParts(level, pos, state.getValue(VaultDoorBlock.FACING));
				level.setBlock(pos, state.setValue(VaultDoorBlock.OPEN, false), Block.UPDATE_ALL);
				door.setChanged();
			}
		}
		if (!level.isClientSide() && door.progress != door.prevProgress && (door.progress == TOTAL_TICKS || door.progress == 0)) {
			door.setChanged();
		}
	}

	private void sync() {
		this.setChanged();
		if (this.level != null) {
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.number = Mth.clamp(input.getIntOr("number", 1), 0, 999);
		this.rollLeft = input.getBooleanOr("roll_left", false);
		this.open = input.getBooleanOr("open", false);
		int synced = Mth.clamp(input.getIntOr("progress", this.open ? TOTAL_TICKS : 0), 0, TOTAL_TICKS);
		// a running client animation is only corrected when it has drifted noticeably
		if (!this.ticked || Math.abs(synced - this.progress) > 10) {
			this.progress = synced;
			this.prevProgress = synced;
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("number", this.number);
		output.putBoolean("roll_left", this.rollLeft);
		output.putBoolean("open", this.open);
		output.putInt("progress", this.progress);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
