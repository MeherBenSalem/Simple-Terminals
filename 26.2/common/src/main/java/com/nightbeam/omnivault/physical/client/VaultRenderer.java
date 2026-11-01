package com.nightbeam.omnivault.physical.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientPlatform;
import com.nightbeam.omnivault.physical.vault.VaultBlock;
import com.nightbeam.omnivault.physical.vault.VaultBlockEntity;
import com.nightbeam.omnivault.physical.vault.VaultTier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** The body of a vault is a normal block model; this renders its lid, swinging on the hinge. */
public class VaultRenderer implements BlockEntityRenderer<VaultBlockEntity, VaultRenderer.State> {
    private static final Map<VaultTier, Identifier> LIDS = new EnumMap<>(VaultTier.class);

    static {
        for (VaultTier tier : VaultTier.values()) {
            LIDS.put(tier, OmniVault.id("block/" + tier.vaultName() + "_lid"));
        }
    }

    public VaultRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static Identifier lidModel(VaultTier tier) {
        return LIDS.get(tier);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VaultBlockEntity vault, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(vault, state, partialTick, camera, breakProgress);
        BlockState block = vault.getBlockState();
        state.facing = block.getBlock() instanceof VaultBlock ? block.getValue(VaultBlock.FACING) : null;
        state.tier = vault.tier();
        state.openness = LidRendering.ease(vault.getOpenNess(partialTick));
        state.lighting = vault.getLevel() instanceof ClientLevel level ? level.cardinalLighting() : CardinalLighting.DEFAULT;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.facing == null) {
            return;
        }
        pose.pushPose();
        LidRendering.faceModel(pose, state.facing);
        LidRendering.hinge(pose, state.openness);
        LidRendering.render(collector, pose, ClientPlatform.INSTANCE.getStandaloneModel(lidModel(state.tier)), state.lighting, state.facing,
                state.lightCoords, 0xFFFFFFFF);
        pose.popPose();
    }

    public static class State extends BlockEntityRenderState {
        @Nullable
        Direction facing;
        VaultTier tier = VaultTier.COPPER;
        float openness;
        CardinalLighting lighting = CardinalLighting.DEFAULT;
    }
}
