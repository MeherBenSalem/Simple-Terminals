package com.nightbeam.omnivault.drawer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.List;

/** A submit node collector that hands everything to another one; implementations change what they need. */
interface DelegatingSubmitNodeCollector extends SubmitNodeCollector {
    SubmitNodeCollector delegate();

    @Override
    default OrderedSubmitNodeCollector order(int order) {
        return delegate().order(order);
    }

    @Override
    default void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        delegate().submitShadow(poseStack, radius, pieces);
    }

    @Override
    default void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough,
                               int lightCoords, CameraRenderState camera) {
        delegate().submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
    }

    @Override
    default void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
                            int lightCoords, int color, int backgroundColor, int outlineColor) {
        delegate().submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
    }

    @Override
    default void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color, Font.DisplayMode displayMode,
                                      int lightCoords) {
        delegate().submitTextBackground(poseStack, x0, y0, x1, y1, color, displayMode, lightCoords);
    }

    @Override
    default void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        delegate().submitFlame(poseStack, renderState, rotation);
    }

    @Override
    default void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        delegate().submitLeash(poseStack, leashState);
    }

    @Override
    default <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
                                 int tintedColor, @Nullable UvMapping uvMapping, int outlineColor) {
        delegate().submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, uvMapping, outlineColor);
    }

    @Override
    default <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords,
                                            int overlayCoords, int tintedColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        delegate().submitCrumblingOverlay(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, crumblingOverlay);
    }

    @Override
    default void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
        delegate().submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
    }

    @Override
    default void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
                                  int overlayCoords, int outlineColor) {
        delegate().submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    default void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent) {
        delegate().submitBreakingBlockModel(poseStack, parts, progress, isBlockTranslucent);
    }

    @Override
    default void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
        delegate().submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
    }

    @Override
    default void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
                            int[] tintLayers, ItemQuads quads, ItemStackRenderState.FoilType foilType) {
        delegate().submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
    }

    @Override
    default void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
        delegate().submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
    }

    @Override
    default void submitQuadParticleGroup(QuadParticleRenderState particles) {
        delegate().submitQuadParticleGroup(particles);
    }

    @Override
    default void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
        delegate().submitGizmoPrimitives(group, camera, onTop);
    }
}
