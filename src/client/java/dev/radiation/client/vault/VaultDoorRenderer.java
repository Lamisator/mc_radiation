package dev.radiation.client.vault;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.radiation.RadiationMod;
import dev.radiation.vault.VaultDoorBlock;
import dev.radiation.vault.VaultDoorBlockEntity;
import net.minecraft.client.gui.Font;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the cog-shaped vault door with its painted number, pulled back and rolled aside by the opening animation.
 * The mesh is generated once: a toothed disc, extruded, with planar-mapped front and back art.
 */
public class VaultDoorRenderer implements BlockEntityRenderer<VaultDoorBlockEntity, VaultDoorRenderer.State> {
	private static final Identifier TEXTURE = RadiationMod.id("textures/entity/vault_door.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
	private static final int TEETH = 12;
	private static final int SAMPLES_PER_TOOTH = 8;
	private static final float R_BODY = 2.2F;
	private static final float R_TOOTH = 2.48F;
	/** Flush with both faces of the wall it closes (a hair behind them, so the teeth don't flicker against the corner blocks). */
	private static final float HALF_THICKNESS = 0.495F;
	private static final float[][] PROFILE = profile();
	/** The collar's steel comes from the frame blocks around the opening. */
	private static final RenderType COLLAR_TYPE = RenderTypes.entityCutout(RadiationMod.id("textures/block/vault_door_frame.png"));
	/** Gap between the door and the cog-shaped aperture in the collar. */
	private static final float COLLAR_CLEARANCE = 1.012F;
	/** Inner (aperture) and outer (opening outline) points of the collar, by angle. */
	private static final float[][][] COLLAR = collar();
	// the screw arm, mounted from the ceiling behind the door
	private static final float HEAD_IDLE = -2.4F;
	private static final float HEAD_PARKED = -2.25F;
	private static final float HOUSING_Z = -5.2F;
	private static final float HOUSING_HALF = 1.1F;
	private static final float STRUT_TOP = 4.0F;
	private static final float HEAD_RADIUS = 1.15F;
	private static final float HEAD_LENGTH = 0.7F;
	private static final float SCREW_TURNS = 3;
	private final Font font;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		float pull;
		float roll;
		/** z of the arm head's front face, local coordinates (door centre plane = 0, vault interior = negative). */
		float headFront;
		float screwAngle;
		boolean rollLeft;
		int number;
		int light;
	}

	public VaultDoorRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	private static float[][] profile() {
		int n = TEETH * SAMPLES_PER_TOOTH;
		float[][] p = new float[n][2];
		for (int i = 0; i < n; i++) {
			double phase = (double) i / SAMPLES_PER_TOOTH;
			double f = phase - Math.floor(phase);
			double r;
			if (f < 0.08) {
				r = Mth.lerp(f / 0.08, R_BODY, R_TOOTH);
			} else if (f < 0.42) {
				r = R_TOOTH;
			} else if (f < 0.5) {
				r = Mth.lerp((f - 0.42) / 0.08, R_TOOTH, R_BODY);
			} else {
				r = R_BODY;
			}
			double a = 2 * Math.PI * i / n;
			p[i][0] = (float) (Math.cos(a) * r);
			p[i][1] = (float) (Math.sin(a) * r);
		}
		return p;
	}

	/**
	 * The opening is a 5×5 square without its corners; the door is round. The collar fills the space between: from the
	 * outline of the opening in to a cog-shaped aperture just larger than the door, so the door sits in the wall with
	 * no gaps, and rolls out of a cog-shaped hole when it opens.
	 */
	private static float[][][] collar() {
		float[][] outline = {{-1.5F, 2.5F}, {-1.5F, 1.5F}, {-2.5F, 1.5F}, {-2.5F, -1.5F}, {-1.5F, -1.5F}, {-1.5F, -2.5F}, {1.5F, -2.5F}, {1.5F, -1.5F},
				{2.5F, -1.5F}, {2.5F, 1.5F}, {1.5F, 1.5F}, {1.5F, 2.5F}};
		int n = PROFILE.length;
		float[][] aperture = new float[n][2];
		for (int i = 0; i < n; i++) {
			aperture[i][0] = PROFILE[i][0] * COLLAR_CLEARANCE;
			aperture[i][1] = PROFILE[i][1] * COLLAR_CLEARANCE;
		}
		// every corner of either outline gets its own ray, so both edges stay exact
		java.util.TreeSet<Double> angles = new java.util.TreeSet<>();
		for (float[] p : aperture) {
			angles.add(norm(Math.atan2(p[1], p[0])));
		}
		for (float[] p : outline) {
			angles.add(norm(Math.atan2(p[1], p[0])));
		}
		float[][] inner = new float[angles.size()][];
		float[][] outer = new float[angles.size()][];
		int k = 0;
		for (double a : angles) {
			double dx = Math.cos(a);
			double dy = Math.sin(a);
			double ti = ray(aperture, dx, dy);
			double to = ray(outline, dx, dy);
			inner[k] = new float[]{(float) (dx * ti), (float) (dy * ti)};
			outer[k] = new float[]{(float) (dx * to), (float) (dy * to)};
			k++;
		}
		return new float[][][]{inner, outer};
	}

	private static double norm(double a) {
		return a < 0 ? a + 2 * Math.PI : a;
	}

	/** Distance along a ray from the centre to a closed polygon around it. */
	private static double ray(float[][] poly, double dx, double dy) {
		double best = Double.MAX_VALUE;
		for (int i = 0; i < poly.length; i++) {
			float[] p = poly[i];
			float[] q = poly[(i + 1) % poly.length];
			double ex = q[0] - p[0];
			double ey = q[1] - p[1];
			double det = dx * -ey - dy * -ex;
			if (Math.abs(det) < 1.0E-12) {
				continue;
			}
			double t = (p[0] * -ey - p[1] * -ex) / det;
			double u = (dx * p[1] - dy * p[0]) / det;
			if (t > 0 && u >= -1.0E-9 && u <= 1 + 1.0E-9) {
				best = Math.min(best, t);
			}
		}
		return best;
	}

	private static void buildCollar(PoseStack.Pose pose, VertexConsumer b, int light) {
		float[][] in = COLLAR[0];
		float[][] out = COLLAR[1];
		int n = in.length;
		float z = HALF_THICKNESS;
		// one flat patch of the frame's steel between the bolt heads
		float u = 0.5F;
		float v = 0.5F;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float[] a = in[i], a2 = in[j], o = out[i], o2 = out[j];
			// front, back
			vertex(b, pose, a[0], a[1], z, u, v, 0, 0, 1, light);
			vertex(b, pose, o[0], o[1], z, u, v, 0, 0, 1, light);
			vertex(b, pose, o2[0], o2[1], z, u, v, 0, 0, 1, light);
			vertex(b, pose, a2[0], a2[1], z, u, v, 0, 0, 1, light);
			vertex(b, pose, a2[0], a2[1], -z, u, v, 0, 0, -1, light);
			vertex(b, pose, o2[0], o2[1], -z, u, v, 0, 0, -1, light);
			vertex(b, pose, o[0], o[1], -z, u, v, 0, 0, -1, light);
			vertex(b, pose, a[0], a[1], -z, u, v, 0, 0, -1, light);
			// the aperture's edge, facing the middle
			float nx = -(a2[1] - a[1]);
			float ny = a2[0] - a[0];
			float len = Mth.sqrt(nx * nx + ny * ny);
			if (len < 1.0E-6F) {
				continue;
			}
			nx /= len;
			ny /= len;
			vertex(b, pose, a[0], a[1], -z, 0.25F, v, nx, ny, 0, light);
			vertex(b, pose, a[0], a[1], z, 0.25F, v, nx, ny, 0, light);
			vertex(b, pose, a2[0], a2[1], z, 0.25F, v, nx, ny, 0, light);
			vertex(b, pose, a2[0], a2[1], -z, 0.25F, v, nx, ny, 0, light);
		}
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(VaultDoorBlockEntity door, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(door, state, partialTicks, cameraPosition, breakProgress);
		state.facing = door.getBlockState().getValue(VaultDoorBlock.FACING);
		float t = door.progress(partialTicks);
		float pullT = phase(t, VaultDoorBlockEntity.T_SCREW, VaultDoorBlockEntity.T_PULL);
		float rollT = phase(t, VaultDoorBlockEntity.T_RELEASE, VaultDoorBlockEntity.TOTAL_TICKS);
		state.pull = smooth(pullT) * VaultDoorBlockEntity.PULL_DEPTH;
		state.roll = smooth(rollT) * VaultDoorBlockEntity.ROLL_DISTANCE;
		float back = -HALF_THICKNESS;
		if (t < VaultDoorBlockEntity.T_EXTEND) {
			state.headFront = Mth.lerp(smooth(phase(t, 0, VaultDoorBlockEntity.T_EXTEND)), HEAD_IDLE, back);
		} else if (t < VaultDoorBlockEntity.T_PULL) {
			state.headFront = back - state.pull;
		} else {
			state.headFront = Mth.lerp(smooth(phase(t, VaultDoorBlockEntity.T_PULL, VaultDoorBlockEntity.T_RELEASE)),
					back - VaultDoorBlockEntity.PULL_DEPTH, HEAD_PARKED);
		}
		float screwIn = phase(t, VaultDoorBlockEntity.T_EXTEND, VaultDoorBlockEntity.T_SCREW);
		float screwOut = phase(t, VaultDoorBlockEntity.T_PULL, VaultDoorBlockEntity.T_RELEASE);
		state.screwAngle = (float) (SCREW_TURNS * 2 * Math.PI * (screwIn - screwOut));
		state.rollLeft = door.rollLeft();
		state.number = door.number();
		Level level = door.getLevel();
		if (level != null) {
			BlockPos pos = door.getBlockPos();
			Direction inside = state.facing.getOpposite();
			int a = LightCoordsUtil.getLightCoords(level, pos.relative(state.facing));
			int b = LightCoordsUtil.getLightCoords(level, pos.relative(inside));
			int c = LightCoordsUtil.getLightCoords(level, pos.relative(inside, 2));
			state.light = LightCoordsUtil.max(LightCoordsUtil.max(a, b), c);
		}
	}

	private static float phase(float t, float from, float to) {
		return Mth.clamp((t - from) / (to - from), 0, 1);
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		// local +z = the printed front, pointing out of the vault; local x = in the wall plane
		poseStack.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
		int armLight = state.light;
		float headFront = state.headFront;
		float screwAngle = state.screwAngle;
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> buildArm(pose, buffer, armLight, headFront, screwAngle));
		collector.submitCustomGeometry(poseStack, COLLAR_TYPE, (pose, buffer) -> buildCollar(pose, buffer, armLight));
		float side = state.rollLeft ? 1 : -1;
		poseStack.translate(side * state.roll, 0, -state.pull);
		poseStack.rotate(Axis.ZP.rotation(-side * state.roll / VaultDoorBlockEntity.RADIUS));
		int light = state.light;
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> buildMesh(pose, buffer, light));

		FormattedCharSequence text = Component.literal(String.valueOf(state.number)).withStyle(net.minecraft.ChatFormatting.BOLD).getVisualOrderText();
		float width = this.font.width(text);
		float scale = Math.min(1.25F / 7.0F, 2.5F / Math.max(width, 1));
		// the number is painted on the outside only; the inside carries the screw arm's coupling
		for (int face = 0; face < 1; face++) {
			poseStack.pushPose();
			if (face == 1) {
				poseStack.rotate(Axis.YP.rotationDegrees(180));
			}
			poseStack.translate(0, 0, HALF_THICKNESS + 0.004);
			poseStack.scale(scale, -scale, scale);
			collector.submitText(poseStack, -width / 2, -3.5F, text, false, Font.DisplayMode.POLYGON_OFFSET, light, 0xFFF2C21E, 0, 0);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static void buildMesh(PoseStack.Pose pose, VertexConsumer buffer, int light) {
		int n = PROFILE.length;
		float z0 = HALF_THICKNESS;
		for (int i = 0; i < n; i++) {
			float[] p = PROFILE[i];
			float[] q = PROFILE[(i + 1) % n];
			// front (+z): fan from the centre, counter-clockwise seen from the front
			vertex(buffer, pose, 0, 0, z0, frontU(0), frontV(0), 0, 0, 1, light);
			vertex(buffer, pose, p[0], p[1], z0, frontU(p[0]), frontV(p[1]), 0, 0, 1, light);
			vertex(buffer, pose, q[0], q[1], z0, frontU(q[0]), frontV(q[1]), 0, 0, 1, light);
			vertex(buffer, pose, 0, 0, z0, frontU(0), frontV(0), 0, 0, 1, light);
			// back (-z): mirrored winding, art from the right half of the texture
			vertex(buffer, pose, 0, 0, -z0, backU(0), frontV(0), 0, 0, -1, light);
			vertex(buffer, pose, q[0], q[1], -z0, backU(-q[0]), frontV(q[1]), 0, 0, -1, light);
			vertex(buffer, pose, p[0], p[1], -z0, backU(-p[0]), frontV(p[1]), 0, 0, -1, light);
			vertex(buffer, pose, 0, 0, -z0, backU(0), frontV(0), 0, 0, -1, light);
			// rim
			float nx = q[1] - p[1];
			float ny = -(q[0] - p[0]);
			float len = Mth.sqrt(nx * nx + ny * ny);
			nx /= len;
			ny /= len;
			float u0 = (float) i / n;
			float u1 = (float) (i + 1) / n;
			vertex(buffer, pose, p[0], p[1], -z0, u0, 0.5F, nx, ny, 0, light);
			vertex(buffer, pose, q[0], q[1], -z0, u1, 0.5F, nx, ny, 0, light);
			vertex(buffer, pose, q[0], q[1], z0, u1, 0.5625F, nx, ny, 0, light);
			vertex(buffer, pose, p[0], p[1], z0, u0, 0.5625F, nx, ny, 0, light);
		}
	}

	/** Ceiling strut, housing, fixed sleeve, telescoping piston and the rotating screw head with its threaded spike. */
	private static void buildArm(PoseStack.Pose pose, VertexConsumer b, int light, float headFront, float angle) {
		float h = HOUSING_HALF;
		// strut up to the ceiling and the housing it carries
		box(pose, b, light, -0.5F, h, HOUSING_Z - 0.5F, 0.5F, STRUT_TOP, HOUSING_Z + 0.5F, 0, 144 / 256F, 1, 160 / 256F);
		box(pose, b, light, -h, -h, HOUSING_Z - h, h, h, HOUSING_Z + h, 0.5F, 0.75F, 0.75F, 1.0F);
		box(pose, b, light, -0.8F, STRUT_TOP - 0.2F, HOUSING_Z - 1.2F, 0.8F, STRUT_TOP, HOUSING_Z + 1.2F, 0, 176 / 256F, 1, 192 / 256F);
		float sleeveEnd = HOUSING_Z + h + 1.0F;
		cylinder(pose, b, light, 0, 0.7F, HOUSING_Z + h, sleeveEnd, 0, 16, 0, 176 / 256F, 1, 192 / 256F, false);
		float headBack = headFront - HEAD_LENGTH;
		if (headBack > sleeveEnd) {
			cylinder(pose, b, light, 0, 0.45F, sleeveEnd - 0.2F, headBack, 0, 12, 0, 160 / 256F, 1, 176 / 256F, false);
		}
		cylinder(pose, b, light, angle, HEAD_RADIUS, headBack, headFront, 0, 16, 0, 144 / 256F, 1, 160 / 256F, true);
		cylinder(pose, b, light, angle * 1.0F, 0.25F, headFront, headFront + 0.35F, 0, 8, 0, 160 / 256F, 1, 176 / 256F, true);
	}

	/** An n-sided prism along z; the front cap (+z) carries the head art at u 0-0.25, v 0.75-1. */
	private static void cylinder(PoseStack.Pose pose, VertexConsumer b, int light, float angle, float r, float z0, float z1, float y,
			int n, float u0, float v0, float u1, float v1, boolean caps) {
		for (int i = 0; i < n; i++) {
			double a0 = angle + 2 * Math.PI * i / n;
			double a1 = angle + 2 * Math.PI * (i + 1) / n;
			float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r + y;
			float x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r + y;
			float nx = (float) Math.cos((a0 + a1) / 2), ny = (float) Math.sin((a0 + a1) / 2);
			float ua = Mth.lerp((float) i / n, u0, u1), ub = Mth.lerp((float) (i + 1) / n, u0, u1);
			vertex(b, pose, x0, y0, z0, ua, v1, nx, ny, 0, light);
			vertex(b, pose, x1, y1, z0, ub, v1, nx, ny, 0, light);
			vertex(b, pose, x1, y1, z1, ub, v0, nx, ny, 0, light);
			vertex(b, pose, x0, y0, z1, ua, v0, nx, ny, 0, light);
			if (caps) {
				float cu = 0.125F, cv = 0.875F, s = 0.125F / r;
				vertex(b, pose, 0, y, z1, cu, cv, 0, 0, 1, light);
				vertex(b, pose, x0, y0, z1, cu + (float) Math.cos(a0 - angle) * r * s, cv - (float) Math.sin(a0 - angle) * r * s, 0, 0, 1, light);
				vertex(b, pose, x1, y1, z1, cu + (float) Math.cos(a1 - angle) * r * s, cv - (float) Math.sin(a1 - angle) * r * s, 0, 0, 1, light);
				vertex(b, pose, 0, y, z1, cu, cv, 0, 0, 1, light);
				vertex(b, pose, 0, y, z0, cu, cv, 0, 0, -1, light);
				vertex(b, pose, x1, y1, z0, cu, cv, 0, 0, -1, light);
				vertex(b, pose, x0, y0, z0, cu, cv, 0, 0, -1, light);
				vertex(b, pose, 0, y, z0, cu, cv, 0, 0, -1, light);
			}
		}
	}

	/** An axis-aligned box with the same texture region on every face. */
	private static void box(PoseStack.Pose pose, VertexConsumer b, int light, float x0, float y0, float z0, float x1, float y1, float z1,
			float u0, float v0, float u1, float v1) {
		// +z, -z, +x, -x, +y, -y
		quad(b, pose, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, v0, u1, v1);
		quad(b, pose, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u0, v0, u1, v1);
		quad(b, pose, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u0, v0, u1, v1);
		quad(b, pose, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0, v0, u1, v1);
		quad(b, pose, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v0, u1, v1);
		quad(b, pose, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v0, u1, v1);
	}

	private static void quad(VertexConsumer b, PoseStack.Pose pose, int light, float nx, float ny, float nz, float ax, float ay, float az,
			float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float u0, float v0, float u1, float v1) {
		vertex(b, pose, ax, ay, az, u0, v1, nx, ny, nz, light);
		vertex(b, pose, bx, by, bz, u1, v1, nx, ny, nz, light);
		vertex(b, pose, cx, cy, cz, u1, v0, nx, ny, nz, light);
		vertex(b, pose, dx, dy, dz, u0, v0, nx, ny, nz, light);
	}

	private static float frontU(float x) {
		return 0.25F + x / R_TOOTH * 0.25F;
	}

	private static float backU(float x) {
		return 0.75F + x / R_TOOTH * 0.25F;
	}

	private static float frontV(float y) {
		return 0.25F - y / R_TOOTH * 0.25F;
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz,
			int light) {
		buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
