package dev.radiation.network;

import dev.radiation.RadiationMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent to each player every update interval.
 *
 * @param rads       accumulated rads
 * @param exposure   rads per second currently being absorbed, or -1 if the player has no way of measuring it
 * @param netRate    overall change of rads per second (exposure minus treatment), used for smooth interpolation
 * @param protection fraction of incoming radiation currently blocked (0..1)
 */
public record RadiationStatusPayload(float rads, float exposure, float netRate, float protection) implements CustomPacketPayload {
	public static final Type<RadiationStatusPayload> TYPE = new Type<>(RadiationMod.id("status"));
	public static final StreamCodec<FriendlyByteBuf, RadiationStatusPayload> CODEC = CustomPacketPayload.codec(
			(payload, buf) -> {
				buf.writeFloat(payload.rads);
				buf.writeFloat(payload.exposure);
				buf.writeFloat(payload.netRate);
				buf.writeFloat(payload.protection);
			},
			buf -> new RadiationStatusPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

	@Override
	public Type<RadiationStatusPayload> type() {
		return TYPE;
	}
}
