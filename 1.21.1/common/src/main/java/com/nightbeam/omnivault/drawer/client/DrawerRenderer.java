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
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws each compartment's item flat on the drawer front (like an inventory icon), with a compact
 * count on the label plate, a lock icon when locked and a void icon with a void upgrade. Locked empty
 * compartments keep a translucent "ghost" of their item.
 */
public class DrawerRenderer implements BlockEntityRenderer<AbstractDrawerBlockEntity> {
    public static final ResourceLocation LOCK_ICON = OmniVault.id("block/drawer/lock_icon");
    public static final ResourceLocation VOID_ICON = OmniVault.id("block/drawer/void_icon");
    private static final float PX = 1 / 16f;
    private static final double LABEL_DISTANCE_SQ = 32 * 32;
    private static final int TEXT = 0xFFE6F6FF;
    private static final int TEXT_FULL = 0xFFF2B33D;
    private static final int TEXT_EMPTY = 0xFF8B949E;

    private final ItemRenderer items;
    private final Font font;

    public DrawerRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
        this.font = context.getFont();
    }

    @Override
    public void render(AbstractDrawerBlockEntity drawer, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = drawer.getLevel();
        BlockState state = drawer.getBlockState();
        if (level == null || !(state.getBlock() instanceof DrawerBlock block)) {
            return;
        }
        Direction facing = state.getValue(DrawerBlock.FACING);
        BlockPos pos = drawer.getBlockPos();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 front = Vec3.atCenterOf(pos).add(facing.getStepX() * 0.5, 0, facing.getStepZ() * 0.5);
        Vec3 toCamera = camera.subtract(front);
        if (toCamera.x * facing.getStepX() + toCamera.z * facing.getStepZ() <= 0) {
            return; // looking at the back or sides only: the front is hidden
        }
        boolean labels = toCamera.lengthSqr() < LABEL_DISTANCE_SQ;
        int light = LevelRenderer.getLightColor(level, pos.relative(facing));
        DrawerLayout layout = block.layout();

        pose.pushPose();
        // Local frame: +x to the viewer's right, +y up, +z towards the viewer, front plane at z = 0.5.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        for (int slot = 0; slot < drawer.compartments() && slot < layout.compartments(); slot++) {
            ItemKey key = drawer.keyAt(slot);
            if (key == null) {
                continue;
            }
            long count = drawer.countAt(slot);
            DrawerLayout.Compartment compartment = layout.compartment(slot);
            renderItem(key, count <= 0, compartment, pose, buffers, light, level);
            if (labels) {
                renderLabel(drawer, count, count >= drawer.capacityAt(slot), compartment, pose, buffers);
            }
        }
        pose.popPose();
    }

    private void renderItem(ItemKey key, boolean ghost, DrawerLayout.Compartment c, PoseStack pose, MultiBufferSource buffers, int light, Level level) {
        ItemStack stack = key.toStack();
        boolean custom = items.getModel(stack, level, null, 0).isCustomRenderer();
        if (ghost) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        }
        pose.pushPose();
        pose.translate((c.itemU() - 8) * PX, (8 - c.itemV()) * PX, 0.5f - (DrawerLayout.PANEL_DEPTH - 0.12f) * PX);
        float size = c.itemSize() * PX;
        pose.scale(size, size, 0.001f);
        MultiBufferSource target = new FlatBuffers(buffers, ghost && !custom);
        int itemLight = ghost && custom ? LightTexture.pack(2, 2) : light;
        items.renderStatic(stack, ItemDisplayContext.GUI, itemLight, OverlayTexture.NO_OVERLAY, pose, target, level, 0);
        pose.popPose();
    }

    private void renderLabel(AbstractDrawerBlockEntity drawer, long count, boolean full, DrawerLayout.Compartment c, PoseStack pose, MultiBufferSource buffers) {
        String text = Formatting.compact(count);
        boolean locked = drawer.isLocked();
        boolean voiding = drawer.isVoiding();
        float height = c.textHeight();
        float scale = height / 7f; // digits are 7 font units tall
        float icon = height * 1.15f;
        float gap = height * 0.35f;
        float width = font.width(text) * scale + (locked ? icon + gap : 0) + (voiding ? icon + gap : 0);
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
        if (locked) {
            icon(pose, buffers, LOCK_ICON, x, c.labelV() - icon / 2, icon, z);
            x += icon + gap;
        }
        int color = count <= 0 ? TEXT_EMPTY : full ? TEXT_FULL : TEXT;
        pose.pushPose();
        pose.translate((x - 8) * PX, (8 - top) * PX, z);
        pose.scale(scale * PX, -scale * PX, scale * PX);
        font.drawInBatch(text, 0, 0, color, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
        x += font.width(text) * scale + gap;
        if (voiding) {
            icon(pose, buffers, VOID_ICON, x, c.labelV() - icon / 2, icon, z);
        }
    }

    /** A sprite quad at face pixels (u, v) (top-left corner), facing the viewer. */
    private static void icon(PoseStack pose, MultiBufferSource buffers, ResourceLocation sprite, float u, float v, float size, float z) {
        TextureAtlasSprite tex = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(sprite);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f matrix = pose.last().pose();
        float x0 = (u - 8) * PX;
        float x1 = (u + size - 8) * PX;
        float y0 = (8 - v - size) * PX;
        float y1 = (8 - v) * PX;
        vertex(consumer, matrix, x0, y0, z, tex.getU0(), tex.getV1());
        vertex(consumer, matrix, x1, y0, z, tex.getU1(), tex.getV1());
        vertex(consumer, matrix, x1, y1, z, tex.getU1(), tex.getV0());
        vertex(consumer, matrix, x0, y1, z, tex.getU0(), tex.getV0());
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v) {
        consumer.addVertex(matrix, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    /**
     * Wraps the buffers an item renders into: normals point up so flattened icons are lit evenly on
     * every facing, and ghosts are drawn translucent and dimmed.
     */
    private record FlatBuffers(MultiBufferSource delegate, boolean ghost) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return new FlatConsumer(delegate.getBuffer(ghost ? Sheets.translucentItemSheet() : type), ghost);
        }
    }

    private record FlatConsumer(VertexConsumer delegate, boolean ghost) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (ghost) {
                delegate.setColor(red * 3 / 4, green * 3 / 4, blue * 3 / 4, alpha * 2 / 5);
            } else {
                delegate.setColor(red, green, blue, alpha);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(0, 1, 0);
            return this;
        }
    }
}
