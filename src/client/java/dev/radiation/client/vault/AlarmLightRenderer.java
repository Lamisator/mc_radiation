package dev.radiation.client.vault;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.radiation.RadiationMod;
import dev.radiation.vault.VaultAlarmLightBlock;
import dev.radiation.vault.VaultAlarmLightBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Sweeping light beams of a lit alarm light: two glowing fans that rotate around the lamp's axis. */
public class AlarmLightRenderer implements BlockEntityRenderer<VaultAlarmLightBlockEntity, AlarmLightRenderer.State> {
	private static final Identifier TEXTURE = RadiationMod.id("textures/entity/vault_alarm_beam.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);
	private static final int FULL_BRIGHT = 0xF000F0;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.UP;
		boolean lit;
		float angle;
	}

	public AlarmLightRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(VaultAlarmLightBlockEntity light, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(light, state, partialTicks, cameraPosition, breakProgress);
		state.facing = light.getBlockState().getValue(VaultAlarmLightBlock.FACING);
		state.lit = light.getBlockState().getValue(VaultAlarmLightBlock.LIT);
		state.angle = light.angle(partialTicks);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.lit) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		// turn the lamp's local "up" to its facing, then sit at the dome's centre
		poseStack.rotate(state.facing.getRotation());
		poseStack.translate(0, -0.5 + 4 / 16.0, 0);
		poseStack.rotate(Axis.YP.rotationDegrees(state.angle));
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, AlarmLightRenderer::beams);
		poseStack.popPose();
	}

	/** Two opposite beams, each a widening fan 2.5 blocks long, seen from both sides. */
	private static void beams(PoseStack.Pose pose, VertexConsumer b) {
		for (int side = -1; side <= 1; side += 2) {
			float len = 2.5F * side;
			float w0 = 0.12F, w1 = 0.9F;
			fan(b, pose, 0, -w0, len, -w1, w0, w1);
			fan(b, pose, 0, w0, len, w1, -w0, -w1);
		}
	}

	private static void fan(VertexConsumer b, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float y0b, float y1b) {
		v(b, pose, x0, y0, 0, 0, 0);
		v(b, pose, x1, y1, 0, 1, 0);
		v(b, pose, x1, y1b, 0, 1, 1);
		v(b, pose, x0, y0b, 0, 0, 1);
		v(b, pose, x0, 0, y0, 0, 0);
		v(b, pose, x1, 0, y1, 1, 0);
		v(b, pose, x1, 0, y1b, 1, 1);
		v(b, pose, x0, 0, y0b, 0, 1);
	}

	private static void v(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float vv) {
		b.addVertex(pose, x, y, z).setColor(-1).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}
}
