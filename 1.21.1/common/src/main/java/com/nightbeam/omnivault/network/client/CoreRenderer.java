package com.nightbeam.omnivault.network.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientPlatform;
import com.nightbeam.omnivault.network.block.NexusCoreBlock;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Nexus Core's floating omnium crystal and its counter-rotating ring. */
public class CoreRenderer implements BlockEntityRenderer<NexusCoreBlockEntity> {
    public static final ResourceLocation CRYSTAL = OmniVault.id("block/nexus_core_crystal");
    public static final ResourceLocation RING = OmniVault.id("block/nexus_core_ring");

    public CoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NexusCoreBlockEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (core.getLevel() == null) {
            return;
        }
        boolean active = core.getBlockState().getValue(NexusCoreBlock.ACTIVE);
        float time = core.getLevel().getGameTime() + partialTick;
        float speed = active ? 1.0f : 0.15f;
        ModelBlockRenderer renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(RenderType.translucent());
        int glow = active ? LightTexture.FULL_BRIGHT : light;
        float tint = active ? 1.0f : 0.45f;

        pose.pushPose();
        pose.translate(0.5, 0.5 + (active ? Mth.sin(time * 0.08f) * 0.03f : 0), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 2.0f * speed));
        pose.translate(-0.5, -0.5, -0.5);
        renderer.renderModel(pose.last(), consumer, null, ClientPlatform.INSTANCE.getStandaloneModel(CRYSTAL), tint, tint, tint, glow, overlay);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-time * 3.0f * speed));
        pose.mulPose(Axis.XP.rotationDegrees(18.0f));
        pose.translate(-0.5, -0.5, -0.5);
        renderer.renderModel(pose.last(), consumer, null, ClientPlatform.INSTANCE.getStandaloneModel(RING), tint, tint, tint, glow, overlay);
        pose.popPose();
    }
}
