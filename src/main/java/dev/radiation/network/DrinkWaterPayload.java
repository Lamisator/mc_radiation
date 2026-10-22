package dev.radiation.network;

import dev.radiation.RadiationMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player right-clicks water with an empty hand (a sip). The server checks the water itself. */
public record DrinkWaterPayload() implements CustomPacketPayload {
	public static final DrinkWaterPayload INSTANCE = new DrinkWaterPayload();
	public static final Type<DrinkWaterPayload> TYPE = new Type<>(RadiationMod.id("drink_water"));
	public static final StreamCodec<ByteBuf, DrinkWaterPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<DrinkWaterPayload> type() {
		return TYPE;
	}
}
