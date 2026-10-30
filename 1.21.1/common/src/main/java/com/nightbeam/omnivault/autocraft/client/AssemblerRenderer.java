package com.nightbeam.omnivault.autocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.autocraft.block.AssemblerBlockEntity;
import com.nightbeam.omnivault.autocraft.block.AutocraftDeviceBlock;
import com.nightbeam.omnivault.client.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inside the Assembler's glass chamber: a pair of counter-rotating omnium rings that spin up while it
 * works, and the item it is making, floating and turning in the middle, lit full-bright.
 */
public class AssemblerRenderer implements BlockEntityRenderer<AssemblerBlockEntity> {
    public static final ResourceLocation RING = OmniVault.id("block/assembler_ring");

    public AssemblerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AssemblerBlockEntity assembler, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (assembler.getLevel() == null) {
            return;
        }
        BlockState state = assembler.getBlockState();
        boolean active = state.getValue(AutocraftDeviceBlock.ACTIVE);
        boolean working = state.getValue(AutocraftDeviceBlock.WORKING);
        float time = assembler.getLevel().getGameTime() + partialTick;
        float speed = working ? 6.0f : active ? 0.6f : 0.0f;
        int glow = active ? LightTexture.FULL_BRIGHT : light;
        float tint = working ? 1.0f : active ? 0.7f : 0.35f;

        ModelBlockRenderer renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(RenderType.translucent());
        for (int ring = 0; ring < 2; ring++) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees((ring == 0 ? 1 : -1.6f) * time * speed));
            pose.mulPose(Axis.XP.rotationDegrees(ring == 0 ? 20.0f : -35.0f));
            float scale = ring == 0 ? 1.0f : 0.72f;
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, -0.5, -0.5);
            renderer.renderModel(pose.last(), consumer, null, ClientPlatform.INSTANCE.getStandaloneModel(RING), tint, tint, tint, glow, overlay);
            pose.popPose();
        }

        ItemStack display = assembler.display();
        if (working && !display.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 0.5 + Mth.sin(time * 0.15f) * 0.04, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(time * 4.0f));
            pose.scale(0.42f, 0.42f, 0.42f);
            Minecraft.getInstance().getItemRenderer().renderStatic(display, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, assembler.getLevel(), (int) assembler.getBlockPos().asLong());
            pose.popPose();
        }
    }
}
