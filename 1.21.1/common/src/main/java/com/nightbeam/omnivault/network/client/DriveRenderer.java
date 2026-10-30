package com.nightbeam.omnivault.network.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientPlatform;
import com.nightbeam.omnivault.network.block.DriveBlockEntity;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.network.cell.CellItem;
import com.nightbeam.omnivault.network.cell.StorageCellItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the inserted cells in the drive's ten bays, each with a status LED: green while there is
 * room, amber when nearly full, red when full, dark while the network is offline.
 */
public class DriveRenderer implements BlockEntityRenderer<DriveBlockEntity> {
    public static final ResourceLocation CELL = OmniVault.id("block/drive_cell");
    public static final ResourceLocation LED = OmniVault.id("block/drive_cell_led");

    /** Bay origins in pixels on the north-facing front; slot 0 is the viewer's top left. */
    public static float bayX(int slot) {
        return slot % 2 == 0 ? 8.5f : 2.0f;
    }

    public static float bayY(int slot) {
        return 11.0f - 2.5f * (slot / 2);
    }

    public DriveRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DriveBlockEntity drive, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel cell = ClientPlatform.INSTANCE.getStandaloneModel(CELL);
        BakedModel led = ClientPlatform.INSTANCE.getStandaloneModel(LED);
        ModelBlockRenderer renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
        Direction facing = drive.getBlockState().getValue(OrientedDeviceBlock.FACING);
        // The light passed in is sampled inside the solid drive (always dark); the bays face the front.
        if (drive.getLevel() != null) {
            light = LevelRenderer.getLightColor(drive.getLevel(), drive.getBlockPos().relative(facing));
        }
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        for (int slot = 0; slot < DriveBlockEntity.SLOTS; slot++) {
            ItemStack stack = drive.cells().getItem(slot);
            if (!(stack.getItem() instanceof CellItem item)) {
                continue;
            }
            pose.pushPose();
            pose.translate(bayX(slot) / 16f, bayY(slot) / 16f, 0);
            int tier = item.tier().color;
            renderer.renderModel(pose.last(), consumer, null, cell, r(tier), g(tier), b(tier), light, overlay);
            int status = !drive.clientOnline() ? 0x30343A : switch (StorageCellItem.fillState(stack)) {
                case 2 -> 0xFF4A4A;
                case 1 -> 0xF2C14E;
                default -> 0x5BE08A;
            };
            renderer.renderModel(pose.last(), consumer, null, led, r(status), g(status), b(status),
                    drive.clientOnline() ? LightTexture.FULL_BRIGHT : light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    private static float r(int rgb) {
        return (rgb >> 16 & 0xFF) / 255f;
    }

    private static float g(int rgb) {
        return (rgb >> 8 & 0xFF) / 255f;
    }

    private static float b(int rgb) {
        return (rgb & 0xFF) / 255f;
    }
}
