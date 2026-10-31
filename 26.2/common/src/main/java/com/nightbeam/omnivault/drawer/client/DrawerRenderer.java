package com.nightbeam.omnivault.drawer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.drawer.AbstractDrawerBlockEntity;
import com.nightbeam.omnivault.drawer.DrawerBlock;
import com.nightbeam.omnivault.drawer.DrawerLayout;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws each compartment's item flat on the drawer front (like an inventory icon), with a compact
 * count on the label plate, a lock icon when locked and a void icon with a void upgrade. Locked empty
 * compartments keep a translucent "ghost" of their item.
 */
public class DrawerRenderer implements BlockEntityRenderer<AbstractDrawerBlockEntity, DrawerRenderer.State> {
    public static final Identifier LOCK_ICON = OmniVault.id("block/drawer/lock_icon");
    public static final Identifier VOID_ICON = OmniVault.id("block/drawer/void_icon");
    private static final float PX = 1 / 16f;
    private static final double LABEL_DISTANCE_SQ = 32 * 32;
    private static final int TEXT = 0xFFE6F6FF;
    private static final int TEXT_FULL = 0xFFF2B33D;
    private static final int TEXT_EMPTY = 0xFF8B949E;
    /** Ghosts with their own renderer (chests, banners...) are drawn dark instead of translucent. */
    private static final int GHOST_LIGHT = LightCoordsUtil.pack(2, 2);

    private final ItemModelResolver items;
    private final Font font;

    public DrawerRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AbstractDrawerBlockEntity drawer, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(drawer, state, partialTick, camera, breakProgress);
        state.compartments.clear();
        Level level = drawer.getLevel();
        BlockState blockState = drawer.getBlockState();
        if (level == null || !(blockState.getBlock() instanceof DrawerBlock block)) {
            return;
        }
        Direction facing = blockState.getValue(DrawerBlock.FACING);
        BlockPos pos = drawer.getBlockPos();
        Vec3 front = Vec3.atCenterOf(pos).add(facing.getStepX() * 0.5, 0, facing.getStepZ() * 0.5);
        Vec3 toCamera = camera.subtract(front);
        if (toCamera.x * facing.getStepX() + toCamera.z * facing.getStepZ() <= 0) {
            return; // looking at the back or sides only: the front is hidden
        }
        state.facing = facing;
        state.labels = toCamera.lengthSqr() < LABEL_DISTANCE_SQ;
        state.lightCoords = LightCoordsUtil.getLightCoords(level, pos.relative(facing));
        state.locked = drawer.isLocked();
        state.voiding = drawer.isVoiding();
        DrawerLayout layout = block.layout();
        for (int slot = 0; slot < drawer.compartments() && slot < layout.compartments(); slot++) {
            ItemKey key = drawer.keyAt(slot);
            if (key == null) {
                continue;
            }
            long count = drawer.countAt(slot);
            Compartment compartment = new Compartment(layout.compartment(slot), count, count >= drawer.capacityAt(slot), count <= 0);
            ItemStack stack = key.toStack();
            if (compartment.ghost) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            }
            items.updateForTopItem(compartment.item, stack, ItemDisplayContext.GUI, level, null, 0);
            state.compartments.add(compartment);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.compartments.isEmpty()) {
            return;
        }
        pose.pushPose();
        // Local frame: +x to the viewer's right, +y up, +z towards the viewer, front plane at z = 0.5.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        for (Compartment compartment : state.compartments) {
            submitItem(compartment, pose, collector, state.lightCoords);
            if (state.labels) {
                submitLabel(state, compartment, pose, collector);
            }
        }
        pose.popPose();
    }

    private static void submitItem(Compartment compartment, PoseStack pose, SubmitNodeCollector collector, int light) {
        DrawerLayout.Compartment c = compartment.layout;
        pose.pushPose();
        pose.translate((c.itemU() - 8) * PX, (8 - c.itemV()) * PX, 0.5f - (DrawerLayout.PANEL_DEPTH - 0.12f) * PX);
        float size = c.itemSize() * PX;
        pose.scale(size, size, 0.001f);
        compartment.item.submit(pose, new FlatItems(collector, compartment.ghost, light), compartment.ghost ? GHOST_LIGHT : light,
                OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    private void submitLabel(State state, Compartment compartment, PoseStack pose, SubmitNodeCollector collector) {
        DrawerLayout.Compartment c = compartment.layout;
        String text = Formatting.compact(compartment.count);
        float height = c.textHeight();
        float scale = height / 7f; // digits are 7 font units tall
        float icon = height * 1.15f;
        float gap = height * 0.35f;
        float width = font.width(text) * scale + (state.locked ? icon + gap : 0) + (state.voiding ? icon + gap : 0);
        if (width > c.labelWidth()) {
            float shrink = c.labelWidth() / width;
            scale *= shrink;
            icon *= shrink;
            gap *= shrink;
            width = c.labelWidth();
            height *= shrink;
        }
        float z = 0.5f - (DrawerLayout.LABEL_DEPTH - 0.06f) * PX;
        float x = c.labelU() - width / 2;
        float top = c.labelV() - height / 2;
        if (state.locked) {
            icon(pose, collector, LOCK_ICON, x, c.labelV() - icon / 2, icon, z);
            x += icon + gap;
        }
        int color = compartment.count <= 0 ? TEXT_EMPTY : compartment.full ? TEXT_FULL : TEXT;
        pose.pushPose();
        pose.translate((x - 8) * PX, (8 - top) * PX, z);
        pose.scale(scale * PX, -scale * PX, scale * PX);
        collector.submitText(pose, 0, 0, FormattedCharSequence.forward(text, Style.EMPTY), false, Font.DisplayMode.POLYGON_OFFSET,
                LightCoordsUtil.FULL_BRIGHT, color, 0, 0);
        pose.popPose();
        x += font.width(text) * scale + gap;
        if (state.voiding) {
            icon(pose, collector, VOID_ICON, x, c.labelV() - icon / 2, icon, z);
        }
    }

    /** A sprite quad at face pixels (u, v) (top-left corner), facing the viewer. */
    private static void icon(PoseStack pose, SubmitNodeCollector collector, Identifier sprite, float u, float v, float size, float z) {
        TextureAtlasSprite tex = Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, sprite));
        float x0 = (u - 8) * PX;
        float x1 = (u + size - 8) * PX;
        float y0 = (8 - v - size) * PX;
        float y1 = (8 - v) * PX;
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (last, consumer) -> {
            Matrix4f matrix = last.pose();
            vertex(consumer, matrix, x0, y0, z, tex.getU0(), tex.getV1());
            vertex(consumer, matrix, x1, y0, z, tex.getU1(), tex.getV1());
            vertex(consumer, matrix, x1, y1, z, tex.getU1(), tex.getV0());
            vertex(consumer, matrix, x0, y1, z, tex.getU0(), tex.getV0());
        });
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v) {
        consumer.addVertex(matrix, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        boolean labels;
        boolean locked;
        boolean voiding;
        final List<Compartment> compartments = new ArrayList<>();
    }

    private static final class Compartment {
        final DrawerLayout.Compartment layout;
        final long count;
        final boolean full;
        final boolean ghost;
        final ItemStackRenderState item = new ItemStackRenderState();

        Compartment(DrawerLayout.Compartment layout, long count, boolean full, boolean ghost) {
            this.layout = layout;
            this.count = count;
            this.full = full;
            this.ghost = ghost;
        }
    }

    /**
     * Passes an item's submissions on, but draws its plain quads itself: normals point up so flattened
     * icons are lit evenly on every facing, and ghosts are drawn translucent and dimmed. Items with their
     * own renderer go through unchanged.
     */
    private record FlatItems(SubmitNodeCollector delegate, boolean ghost, int light) implements DelegatingSubmitNodeCollector {
        @Override
        public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
                               int[] tintLayers, List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
            Map<RenderType, List<BakedQuad>> byType = new LinkedHashMap<>();
            for (BakedQuad quad : quads) {
                byType.computeIfAbsent(renderType(quad), type -> new ArrayList<>()).add(quad);
            }
            int itemLight = ghost ? light : lightCoords;
            byType.forEach((type, typeQuads) -> delegate.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                for (BakedQuad quad : typeQuads) {
                    int color = quad.materialInfo().isTinted() && quad.materialInfo().tintIndex() < tintLayers.length
                            ? tintLayers[quad.materialInfo().tintIndex()] : -1;
                    putFlat(pose.pose(), buffer, quad, ghost ? ghostColor(color) : color, itemLight, overlayCoords);
                }
            }));
            if (!ghost && foilType != ItemStackRenderState.FoilType.NONE) {
                delegate.submitCustomGeometry(poseStack, RenderTypes.glint(), (pose, buffer) -> {
                    for (BakedQuad quad : quads) {
                        putFlat(pose.pose(), buffer, quad, -1, itemLight, overlayCoords);
                    }
                });
            }
        }

        private RenderType renderType(BakedQuad quad) {
            if (!ghost) {
                return quad.materialInfo().itemRenderType();
            }
            return quad.materialInfo().sprite().atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)
                    ? Sheets.translucentBlockItemSheet() : Sheets.translucentItemSheet();
        }

        private static int ghostColor(int argb) {
            int a = (argb >>> 24) * 2 / 5;
            int r = (argb >> 16 & 0xFF) * 3 / 4;
            int g = (argb >> 8 & 0xFF) * 3 / 4;
            int b = (argb & 0xFF) * 3 / 4;
            return a << 24 | r << 16 | g << 8 | b;
        }

        private static void putFlat(Matrix4f matrix, VertexConsumer buffer, BakedQuad quad, int color, int light, int overlay) {
            int emission = quad.materialInfo().lightEmission();
            int vertexLight = LightCoordsUtil.lightCoordsWithEmission(light, emission);
            for (int vertex = 0; vertex < 4; vertex++) {
                Vector3f position = matrix.transformPosition(quad.position(vertex), new Vector3f());
                long uv = quad.packedUV(vertex);
                buffer.addVertex(position.x(), position.y(), position.z(), color, UVPair.unpackU(uv), UVPair.unpackV(uv), overlay, vertexLight,
                        0, 1, 0);
            }
        }
    }
}
