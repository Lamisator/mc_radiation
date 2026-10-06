package dev.radiation.network;

import dev.radiation.RadiationMod;
import dev.radiation.config.RadiationConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** The parts of the server config the HUD needs: the meter's scale and the sickness stages. */
public record RadiationSettingsPayload(float maxRads, List<StageInfo> stages) implements CustomPacketPayload {
	public static final Type<RadiationSettingsPayload> TYPE = new Type<>(RadiationMod.id("settings"));
	public static final StreamCodec<FriendlyByteBuf, RadiationSettingsPayload> CODEC = CustomPacketPayload.codec(
			(payload, buf) -> {
				buf.writeFloat(payload.maxRads);
				buf.writeVarInt(payload.stages.size());
				for (StageInfo stage : payload.stages) {
					buf.writeFloat(stage.threshold);
					buf.writeUtf(stage.name, 256);
					buf.writeInt(stage.color);
				}
			},
			buf -> {
				float max = buf.readFloat();
				int count = buf.readVarInt();
				List<StageInfo> stages = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					stages.add(new StageInfo(buf.readFloat(), buf.readUtf(256), buf.readInt()));
				}
				return new RadiationSettingsPayload(max, stages);
			});

	public record StageInfo(float threshold, String name, int color) {
	}

	public static RadiationSettingsPayload fromConfig(RadiationConfig config) {
		List<StageInfo> stages = new ArrayList<>();
		for (RadiationConfig.Stage stage : config.stages) {
			stages.add(new StageInfo(stage.threshold, stage.name, stage.argb()));
		}
		return new RadiationSettingsPayload(config.maxRads, stages);
	}

	@Override
	public Type<RadiationSettingsPayload> type() {
		return TYPE;
	}
}
