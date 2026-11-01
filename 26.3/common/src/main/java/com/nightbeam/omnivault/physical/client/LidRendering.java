package com.nightbeam.omnivault.physical.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.CardinalLighting;

/**
 * Draws baked lid models from block entity renderers. Models are authored facing north with the hinge
 * along the back top edge of the body (y = 10px, z = 15px); faces get the same directional shade as
 * chunk-rendered blocks so the lid matches the body, and glowing model elements keep their light.
 */
public final class LidRendering {
    public static final float HINGE_Y = 10f / 16f;
    public static final float HINGE_Z = 15f / 16f;
    public static final float MAX_ANGLE = 100f;
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

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

    /**
     * Submits every quad of {@code model}; tinted quads take {@code tint} (ARGB). {@code facing} is the
     * vault's facing, so each face is shaded like the side of the body it turns to.
     */
    public static void render(SubmitNodeCollector collector, PoseStack pose, BlockStateModelPart model, CardinalLighting lighting,
                              Direction facing, int light, int tint) {
        collector.submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock(), (last, buffer) -> {
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(light);
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            for (Direction side : SIDES) {
                for (BakedQuad quad : model.getQuads(side)) {
                    float shade = quad.materialInfo().shade() ? lighting.byFace(turn(quad.direction(), facing)) : lighting.up();
                    int color = quad.materialInfo().isTinted() ? tint : 0xFFFFFFFF;
                    instance.setColor(ARGB.scaleRGB(color, shade));
                    buffer.putBakedQuad(last, quad, instance);
                }
            }
        });
    }

    /** The world direction a model-space (north-facing) direction turns to for a vault facing {@code facing}. */
    private static Direction turn(Direction direction, Direction facing) {
        if (direction.getAxis().isVertical()) {
            return direction;
        }
        return switch (direction) {
            case EAST -> facing.getClockWise();
            case SOUTH -> facing.getOpposite();
            case WEST -> facing.getCounterClockWise();
            default -> facing;
        };
    }
}
