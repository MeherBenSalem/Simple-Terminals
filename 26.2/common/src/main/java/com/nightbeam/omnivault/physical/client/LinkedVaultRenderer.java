package com.nightbeam.omnivault.physical.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientPlatform;
import com.nightbeam.omnivault.physical.linked.LinkedFrequency;
import com.nightbeam.omnivault.physical.linked.LinkedVaultBlock;
import com.nightbeam.omnivault.physical.linked.LinkedVaultBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
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

/** The Linked Vault's lid (with a padlock while private) and its three colour latches. */
public class LinkedVaultRenderer implements BlockEntityRenderer<LinkedVaultBlockEntity, LinkedVaultRenderer.State> {
    public static final Identifier LID = OmniVault.id("block/linked_vault_lid");
    public static final Identifier LID_PRIVATE = OmniVault.id("block/linked_vault_lid_private");
    /** One latch, modelled at the middle position; the others are drawn shifted by ±3px. */
    public static final Identifier LATCH = OmniVault.id("block/linked_vault_latch");

    public LinkedVaultRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LinkedVaultBlockEntity vault, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(vault, state, partialTick, camera, breakProgress);
        BlockState block = vault.getBlockState();
        state.facing = block.getBlock() instanceof LinkedVaultBlock ? block.getValue(LinkedVaultBlock.FACING) : null;
        LinkedFrequency frequency = vault.frequency();
        state.isPrivate = frequency.isPrivate();
        for (int i = 0; i < 3; i++) {
            state.latches[i] = LinkedFrequency.tint(frequency.color(i));
        }
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
        BlockStateModelPart lid = ClientPlatform.INSTANCE.getStandaloneModel(state.isPrivate ? LID_PRIVATE : LID);
        LidRendering.render(collector, pose, lid, state.lighting, state.facing, state.lightCoords, 0xFFFFFFFF);
        BlockStateModelPart latch = ClientPlatform.INSTANCE.getStandaloneModel(LATCH);
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate((LinkedVaultBlock.latchCenter(i) - 8.0) / 16.0, 0.0, 0.0);
            LidRendering.render(collector, pose, latch, state.lighting, state.facing, state.lightCoords, state.latches[i]);
            pose.popPose();
        }
        pose.popPose();
    }

    public static class State extends BlockEntityRenderState {
        @Nullable
        Direction facing;
        boolean isPrivate;
        final int[] latches = new int[3];
        float openness;
        CardinalLighting lighting = CardinalLighting.DEFAULT;
    }
}
