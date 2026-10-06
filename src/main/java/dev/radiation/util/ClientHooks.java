package dev.radiation.util;

import dev.radiation.vault.VaultDoorBlockEntity;

import java.util.function.Consumer;

/** Bridges from common code into the client without loading client classes on a dedicated server. */
public final class ClientHooks {
	/** Opens the vault door settings screen (number and roll direction). */
	public static Consumer<VaultDoorBlockEntity> openVaultDoorSettings = door -> {
	};

	/** Starts the looping alarm of a moving vault door; it stops by itself when the door stops. */
	public static Consumer<VaultDoorBlockEntity> vaultAlarm = door -> {
	};

	private ClientHooks() {
	}
}
