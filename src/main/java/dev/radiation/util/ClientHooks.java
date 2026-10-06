package dev.radiation.util;

import dev.radiation.vault.VaultDoorBlockEntity;

import java.util.function.Consumer;

/** Bridges from common code into the client without loading client classes on a dedicated server. */
public final class ClientHooks {
	/** Opens the vault door settings screen (number and roll direction). */
	public static Consumer<VaultDoorBlockEntity> openVaultDoorSettings = door -> {
	};

	private ClientHooks() {
	}
}
