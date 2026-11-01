package com.nightbeam.omnivault.network.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.network.block.StorageMonitorBlockEntity;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shows the monitored item on the screen face with its network count below it. */
public class StorageMonitorRenderer implements BlockEntityRenderer<StorageMonitorBlockEntity, StorageMonitorRenderer.State> {
    private final Font font;
    private final ItemModelResolver items;

    public StorageMonitorRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
        this.items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StorageMonitorBlockEntity monitor, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(monitor, state, partialTick, camera, breakProgress);
        ItemStack stack = monitor.displayed();
        items.updateForTopItem(state.item, stack, ItemDisplayContext.GUI, monitor.getLevel(), null, 0);
        state.online = monitor.getBlockState().getValue(OrientedDeviceBlock.ACTIVE);
        state.facing = monitor.getBlockState().getValue(OrientedDeviceBlock.FACING);
        state.text = state.online ? Formatting.compact(monitor.count()) : "--";
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.rotateDegrees(Axis.YP, 180 - state.facing.toYRot());
        // Now -Z is the screen side; the screen surface sits 1px in front of the casing.
        pose.translate(0, 0.08, -0.5 - 0.002);
        pose.pushPose();
        pose.scale(0.42f, 0.42f, 0.02f);
        pose.rotateDegrees(Axis.YP, 180);
        state.item.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        pose.translate(0, -0.3, -0.005);
        // Face the text towards the viewer in front of the screen, like sign text.
        float scale = 0.012f;
        pose.rotateDegrees(Axis.YP, 180);
        pose.scale(scale, -scale, scale);
        collector.submitText(pose, -font.width(state.text) / 2f, 0, FormattedCharSequence.forward(state.text, Style.EMPTY), false,
                Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, state.online ? 0xFF8EF3FF : 0xFF5A6270, 0, 0);
        pose.popPose();
    }

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean online;
        Direction facing = Direction.NORTH;
        String text = "";
    }
}
