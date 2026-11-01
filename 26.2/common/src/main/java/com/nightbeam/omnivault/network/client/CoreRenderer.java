package com.nightbeam.omnivault.network.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientPlatform;
import com.nightbeam.omnivault.network.block.NexusCoreBlock;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The Nexus Core's floating omnium crystal and its counter-rotating ring. */
public class CoreRenderer implements BlockEntityRenderer<NexusCoreBlockEntity, CoreRenderer.State> {
    public static final Identifier CRYSTAL = OmniVault.id("block/nexus_core_crystal");
    public static final Identifier RING = OmniVault.id("block/nexus_core_ring");

    public CoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(NexusCoreBlockEntity core, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(core, state, partialTick, camera, breakProgress);
        state.active = core.getBlockState().getValue(NexusCoreBlock.ACTIVE);
        state.time = core.getLevel() == null ? 0 : core.getLevel().getGameTime() + partialTick;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float speed = state.active ? 1.0f : 0.15f;
        int glow = state.active ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        int tint = state.active ? 0xFFFFFFFF : 0xFF737373;

        pose.pushPose();
        pose.translate(0.5, 0.5 + (state.active ? Mth.sin(state.time * 0.08f) * 0.03f : 0), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(state.time * 2.0f * speed));
        pose.translate(-0.5, -0.5, -0.5);
        collector.submitBlockModel(pose, RenderTypes.translucentMovingBlock(), List.of(ClientPlatform.INSTANCE.getStandaloneModel(CRYSTAL)),
                new int[]{tint}, glow, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.time * 3.0f * speed));
        pose.mulPose(Axis.XP.rotationDegrees(18.0f));
        pose.translate(-0.5, -0.5, -0.5);
        collector.submitBlockModel(pose, RenderTypes.translucentMovingBlock(), List.of(ClientPlatform.INSTANCE.getStandaloneModel(RING)),
                new int[]{tint}, glow, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    public static class State extends BlockEntityRenderState {
        boolean active;
        float time;
    }
}
