package dev.radiation.network;

import dev.radiation.RadiationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: new printed number and roll direction for a vault door. */
public record VaultDoorSettingsPayload(BlockPos pos, int number, boolean rollLeft) implements CustomPacketPayload {
	public static final Type<VaultDoorSettingsPayload> TYPE = new Type<>(RadiationMod.id("vault_door_settings"));
	public static final StreamCodec<FriendlyByteBuf, VaultDoorSettingsPayload> CODEC = CustomPacketPayload.codec(
			(payload, buf) -> {
				buf.writeBlockPos(payload.pos);
				buf.writeVarInt(payload.number);
				buf.writeBoolean(payload.rollLeft);
			},
			buf -> new VaultDoorSettingsPayload(buf.readBlockPos(), buf.readVarInt(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
