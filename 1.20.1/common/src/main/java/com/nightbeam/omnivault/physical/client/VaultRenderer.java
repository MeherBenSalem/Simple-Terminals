package com.nightbeam.omnivault.physical.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientPlatform;
import com.nightbeam.omnivault.physical.vault.VaultBlock;
import com.nightbeam.omnivault.physical.vault.VaultBlockEntity;
import com.nightbeam.omnivault.physical.vault.VaultTier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;

/** The body of a vault is a normal block model; this renders its lid, swinging on the hinge. */
public class VaultRenderer implements BlockEntityRenderer<VaultBlockEntity> {
    private static final Map<VaultTier, ResourceLocation> LIDS = new EnumMap<>(VaultTier.class);

    static {
        for (VaultTier tier : VaultTier.values()) {
            LIDS.put(tier, OmniVault.id("block/" + tier.vaultName() + "_lid"));
        }
    }

    public VaultRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static ResourceLocation lidModel(VaultTier tier) {
        return LIDS.get(tier);
    }

    @Override
    public void render(VaultBlockEntity vault, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = vault.getBlockState();
        if (!(state.getBlock() instanceof VaultBlock)) {
            return;
        }
        pose.pushPose();
        LidRendering.faceModel(pose, state.getValue(VaultBlock.FACING));
        LidRendering.hinge(pose, LidRendering.ease(vault.getOpenNess(partialTick)));
        LidRendering.render(vault.getLevel(), state, ClientPlatform.INSTANCE.getStandaloneModel(lidModel(vault.tier())), pose.last(),
                buffers.getBuffer(RenderType.cutout()), light, overlay, 0xFFFFFFFF);
        pose.popPose();
    }
}
