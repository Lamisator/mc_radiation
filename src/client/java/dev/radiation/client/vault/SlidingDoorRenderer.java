package dev.radiation.client.vault;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.radiation.RadiationMod;
import dev.radiation.vault.SlidingDoorBlock;
import dev.radiation.vault.SlidingDoorBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The two-block steel panel of a sliding door, raised into the wall above while open. */
public class SlidingDoorRenderer implements BlockEntityRenderer<SlidingDoorBlockEntity, SlidingDoorRenderer.State> {
	private static final Identifier TEXTURE = RadiationMod.id("textures/entity/vault_sliding_door.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
	/** How far the panel rises; a little short of two blocks so it never pokes out of a 2-block lintel. */
	private static final float LIFT = 1.97F;
	private static final float T = 2.0F / 16.0F;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		float openness;
		int light;
	}

	public SlidingDoorRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SlidingDoorBlockEntity door, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(door, state, partialTicks, cameraPosition, breakProgress);
		state.facing = door.getBlockState().getValue(SlidingDoorBlock.FACING);
		state.openness = door.openness(partialTicks);
		Level level = door.getLevel();
		if (level != null) {
			BlockPos pos = door.getBlockPos();
			int light = 0;
			for (Direction d : new Direction[]{state.facing, state.facing.getOpposite()}) {
				light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, pos.relative(d)));
				light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, pos.above().relative(d)));
			}
			state.light = light;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5, state.openness * LIFT, 0.5);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
		int light = state.light;
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> box(pose, buffer, light));
		poseStack.popPose();
	}

	/** A 1 × 2 × 4/16 panel centred on the local origin's column; texture 64 × 64: front 0-16, back 16-32, edge 32-36. */
	private static void box(PoseStack.Pose pose, VertexConsumer b, int light) {
		float x0 = -0.5F, x1 = 0.5F, y0 = 0, y1 = 2, z0 = -T, z1 = T;
		float fu0 = 0, fu1 = 0.25F, bu0 = 0.25F, bu1 = 0.5F, eu0 = 0.5F, eu1 = 0.5625F, v0 = 0, v1 = 0.5F;
		// front (+z)
		quad(b, pose, light, 0, 0, 1, x0, y0, z1, fu0, v1, x1, y0, z1, fu1, v1, x1, y1, z1, fu1, v0, x0, y1, z1, fu0, v0);
		// back (-z)
		quad(b, pose, light, 0, 0, -1, x1, y0, z0, bu0, v1, x0, y0, z0, bu1, v1, x0, y1, z0, bu1, v0, x1, y1, z0, bu0, v0);
		// sides
		quad(b, pose, light, 1, 0, 0, x1, y0, z1, eu0, v1, x1, y0, z0, eu1, v1, x1, y1, z0, eu1, v0, x1, y1, z1, eu0, v0);
		quad(b, pose, light, -1, 0, 0, x0, y0, z0, eu0, v1, x0, y0, z1, eu1, v1, x0, y1, z1, eu1, v0, x0, y1, z0, eu0, v0);
		// bottom edge (visible while raised) and top
		quad(b, pose, light, 0, -1, 0, x0, y0, z0, eu0, 0.5F, x1, y0, z0, eu0, 1, x1, y0, z1, eu1, 1, x0, y0, z1, eu1, 0.5F);
		quad(b, pose, light, 0, 1, 0, x0, y1, z1, eu0, 0.5F, x1, y1, z1, eu0, 1, x1, y1, z0, eu1, 1, x0, y1, z0, eu1, 0.5F);
	}

	private static void quad(VertexConsumer b, PoseStack.Pose pose, int light, float nx, float ny, float nz,
			float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
			float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
		v(b, pose, ax, ay, az, au, av, nx, ny, nz, light);
		v(b, pose, bx, by, bz, bu, bv, nx, ny, nz, light);
		v(b, pose, cx, cy, cz, cu, cv, nx, ny, nz, light);
		v(b, pose, dx, dy, dz, du, dv, nx, ny, nz, light);
	}

	private static void v(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
		b.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	@Override
	public int getViewDistance() {
		return 64;
	}
}
