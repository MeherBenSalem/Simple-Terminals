package com.nightbeam.omnivault.physical.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Draws baked lid models from block entity renderers. Models are authored facing north with the hinge
 * along the back top edge of the body (y = 10px, z = 15px); faces get the same directional shade as
 * chunk-rendered blocks so the lid matches the body, and full-bright model elements stay full-bright.
 */
public final class LidRendering {
    public static final float HINGE_Y = 10f / 16f;
    public static final float HINGE_Z = 15f / 16f;
    public static final float MAX_ANGLE = 100f;
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
    private static final RandomSource RANDOM = RandomSource.create();

    private LidRendering() {
    }

    /** Ease-out cubic, like the vanilla chest lid. */
    public static float ease(float openness) {
        float t = 1.0f - openness;
        return 1.0f - t * t * t;
    }

    /** Rotates the pose from block space into the north-facing model space of a vault facing {@code facing}. */
    public static void faceModel(PoseStack pose, Direction facing) {
        pose.translate(0.5f, 0.0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
        pose.translate(-0.5f, 0.0f, -0.5f);
    }

    /** Swings the lid open around its hinge; {@code openness} is already eased (0 closed, 1 open). */
    public static void hinge(PoseStack pose, float openness) {
        if (openness <= 0.0f) {
            return;
        }
        pose.translate(0.0f, HINGE_Y, HINGE_Z);
        pose.mulPose(Axis.XP.rotationDegrees(openness * MAX_ANGLE));
        pose.translate(0.0f, -HINGE_Y, -HINGE_Z);
    }

    /** Renders every quad of {@code model}; tinted quads take {@code tint} (ARGB). */
    public static void render(@Nullable Level level, BlockState state, BakedModel model, PoseStack.Pose pose, VertexConsumer consumer,
                              int light, int overlay, int tint) {
        float r = (tint >> 16 & 0xFF) / 255f;
        float g = (tint >> 8 & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        int[] lights = {light, light, light, light};
        for (Direction side : SIDES) {
            RANDOM.setSeed(42L);
            for (BakedQuad quad : model.getQuads(state, side, RANDOM)) {
                float shade = level != null ? level.getShade(quad.getDirection(), quad.isShade()) : 1.0f;
                float[] brightness = {shade, shade, shade, shade};
                if (quad.isTinted()) {
                    consumer.putBulkData(pose, quad, brightness, r, g, b, 1.0f, lights, overlay, false);
                } else {
                    consumer.putBulkData(pose, quad, brightness, 1.0f, 1.0f, 1.0f, 1.0f, lights, overlay, false);
                }
            }
        }
    }
}
