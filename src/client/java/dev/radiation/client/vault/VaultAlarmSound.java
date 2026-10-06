package dev.radiation.client.vault;

import dev.radiation.vault.VaultBlocks;
import dev.radiation.vault.VaultDoorBlockEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The klaxon that loops while a vault door is opening or closing. */
public class VaultAlarmSound extends AbstractTickableSoundInstance {
	private final VaultDoorBlockEntity door;

	public VaultAlarmSound(VaultDoorBlockEntity door) {
		super(VaultBlocks.VAULT_ALARM, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.door = door;
		this.looping = true;
		this.delay = 0;
		this.volume = 2.5F;
		this.x = door.getBlockPos().getX() + 0.5;
		this.y = door.getBlockPos().getY() + 0.5;
		this.z = door.getBlockPos().getZ() + 0.5;
	}

	@Override
	public void tick() {
		if (this.door.isRemoved() || !this.door.isMoving()) {
			this.door.alarmPlaying = false;
			this.stop();
		}
	}
}
