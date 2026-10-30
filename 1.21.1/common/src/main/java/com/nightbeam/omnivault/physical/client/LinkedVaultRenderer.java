package com.nightbeam.omnivault.physical.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientPlatform;
import com.nightbeam.omnivault.physical.linked.LinkedFrequency;
import com.nightbeam.omnivault.physical.linked.LinkedVaultBlock;
import com.nightbeam.omnivault.physical.linked.LinkedVaultBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Renders the Linked Vault lid (public or private variant) and its three dyed latches. */
public class LinkedVaultRenderer implements BlockEntityRenderer<LinkedVaultBlockEntity> {
    public static final ResourceLocation LID = OmniVault.id("block/linked_vault_lid");
    public static final ResourceLocation LID_PRIVATE = OmniVault.id("block/linked_vault_lid_private");
    /** One latch, modelled at the middle position; the others are drawn shifted by ±3px. */
    public static final ResourceLocation LATCH = OmniVault.id("block/linked_vault_latch");

    public LinkedVaultRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LinkedVaultBlockEntity vault, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = vault.getBlockState();
        if (!(state.getBlock() instanceof LinkedVaultBlock)) {
            return;
        }
        LinkedFrequency frequency = vault.frequency();
        VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
        pose.pushPose();
        LidRendering.faceModel(pose, state.getValue(LinkedVaultBlock.FACING));
        LidRendering.hinge(pose, LidRendering.ease(vault.getOpenNess(partialTick)));
        BakedModel lid = ClientPlatform.INSTANCE.getStandaloneModel(frequency.isPrivate() ? LID_PRIVATE : LID);
        LidRendering.render(vault.getLevel(), state, lid, pose.last(), consumer, light, overlay, 0xFFFFFFFF);
        BakedModel latch = ClientPlatform.INSTANCE.getStandaloneModel(LATCH);
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate((LinkedVaultBlock.latchCenter(i) - 8.0) / 16.0, 0.0, 0.0);
            LidRendering.render(vault.getLevel(), state, latch, pose.last(), consumer, light, overlay, LinkedFrequency.tint(frequency.color(i)));
            pose.popPose();
        }
        pose.popPose();
    }
}
