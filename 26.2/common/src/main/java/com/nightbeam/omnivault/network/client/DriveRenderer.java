package com.nightbeam.omnivault.network.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientPlatform;
import com.nightbeam.omnivault.network.block.DriveBlockEntity;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.network.cell.CellItem;
import com.nightbeam.omnivault.network.cell.StorageCellItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Draws the inserted cells in the drive's ten bays, each with a status LED: green while there is
 * room, amber when nearly full, red when full, dark while the network is offline.
 */
public class DriveRenderer implements BlockEntityRenderer<DriveBlockEntity, DriveRenderer.State> {
    public static final Identifier CELL = OmniVault.id("block/drive_cell");
    public static final Identifier LED = OmniVault.id("block/drive_cell_led");

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
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DriveBlockEntity drive, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(drive, state, partialTick, camera, breakProgress);
        state.facing = drive.getBlockState().getValue(OrientedDeviceBlock.FACING);
        // The light sampled for the block entity is inside the solid drive (always dark); the bays face the front.
        if (drive.getLevel() != null) {
            state.lightCoords = LightCoordsUtil.getLightCoords(drive.getLevel(), drive.getBlockPos().relative(state.facing));
        }
        state.online = drive.clientOnline();
        for (int slot = 0; slot < DriveBlockEntity.SLOTS; slot++) {
            ItemStack stack = drive.cells().getItem(slot);
            if (!(stack.getItem() instanceof CellItem item)) {
                state.tiers[slot] = 0;
                continue;
            }
            state.tiers[slot] = 0xFF000000 | item.tier().color;
            state.status[slot] = 0xFF000000 | (!state.online ? 0x30343A : switch (StorageCellItem.fillState(stack)) {
                case 2 -> 0xFF4A4A;
                case 1 -> 0xF2C14E;
                default -> 0x5BE08A;
            });
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        List<BlockStateModelPart> cell = List.of(ClientPlatform.INSTANCE.getStandaloneModel(CELL));
        List<BlockStateModelPart> led = List.of(ClientPlatform.INSTANCE.getStandaloneModel(LED));
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - state.facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        for (int slot = 0; slot < DriveBlockEntity.SLOTS; slot++) {
            if (state.tiers[slot] == 0) {
                continue;
            }
            pose.pushPose();
            pose.translate(bayX(slot) / 16f, bayY(slot) / 16f, 0);
            collector.submitBlockModel(pose, RenderTypes.cutoutMovingBlock(), cell, new int[]{state.tiers[slot]}, state.lightCoords,
                    OverlayTexture.NO_OVERLAY, 0);
            collector.submitBlockModel(pose, RenderTypes.cutoutMovingBlock(), led, new int[]{state.status[slot]},
                    state.online ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        boolean online;
        /** Tier colour of the cell in each bay, 0 for an empty bay. */
        final int[] tiers = new int[DriveBlockEntity.SLOTS];
        final int[] status = new int[DriveBlockEntity.SLOTS];
    }
}
