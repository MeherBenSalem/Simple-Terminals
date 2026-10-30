package com.nightbeam.omnivault.network.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.network.block.StorageMonitorBlockEntity;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the monitored item and its network count on the monitor's screen. */
public class StorageMonitorRenderer implements BlockEntityRenderer<StorageMonitorBlockEntity> {
    private final Font font;

    public StorageMonitorRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(StorageMonitorBlockEntity monitor, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = monitor.displayed();
        if (stack.isEmpty()) {
            return;
        }
        boolean online = monitor.getBlockState().getValue(OrientedDeviceBlock.ACTIVE);
        Direction facing = monitor.getBlockState().getValue(OrientedDeviceBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        // Now -Z is the screen side; the screen surface sits 1px in front of the casing.
        pose.translate(0, 0.08, -0.5 - 0.002);
        pose.pushPose();
        pose.scale(0.42f, 0.42f, 0.02f);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffers, monitor.getLevel(), 0);
        pose.popPose();
        String text = online ? Formatting.compact(monitor.count()) : "--";
        pose.translate(0, -0.3, -0.005);
        // Face the text towards the viewer in front of the screen, like sign text.
        float scale = 0.012f;
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, 0, online ? 0xFF8EF3FF : 0xFF5A6270, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
