package com.nightbeam.omnivault.autocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.block.AssemblerBlockEntity;
import com.nightbeam.omnivault.autocraft.block.AutocraftDeviceBlock;
import com.nightbeam.omnivault.client.ClientPlatform;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Inside the Assembler's glass chamber: a pair of counter-rotating omnium rings (violet in the Quantum
 * Assembler) that spin up while it works, and the item it is making, floating and turning in the
 * middle, lit full-bright.
 */
public class AssemblerRenderer implements BlockEntityRenderer<AssemblerBlockEntity, AssemblerRenderer.State> {
    public static final Identifier RING = OmniVault.id("block/assembler_ring");
    public static final Identifier QUANTUM_RING = OmniVault.id("block/quantum_assembler_ring");

    private final ItemModelResolver items;

    public AssemblerRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AssemblerBlockEntity assembler, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(assembler, state, partialTick, camera, breakProgress);
        BlockState block = assembler.getBlockState();
        state.active = block.getValue(AutocraftDeviceBlock.ACTIVE);
        state.working = block.getValue(AutocraftDeviceBlock.WORKING);
        state.quantum = block.is(AutocraftContent.QUANTUM_ASSEMBLER.get());
        state.time = assembler.getLevel() == null ? 0 : assembler.getLevel().getGameTime() + partialTick;
        ItemStack display = assembler.display();
        if (state.working && !display.isEmpty()) {
            items.updateForTopItem(state.item, display, ItemDisplayContext.FIXED, assembler.getLevel(), null, (int) assembler.getBlockPos().asLong());
        } else {
            state.item.clear();
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float speed = state.working ? 6.0f : state.active ? 0.6f : 0.0f;
        int glow = state.active ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        int tint = state.working ? 0xFFFFFFFF : state.active ? 0xFFB3B3B3 : 0xFF595959;
        List<BlockStateModelPart> ring = List.of(ClientPlatform.INSTANCE.getStandaloneModel(state.quantum ? QUANTUM_RING : RING));
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees((i == 0 ? 1 : -1.6f) * state.time * speed));
            pose.mulPose(Axis.XP.rotationDegrees(i == 0 ? 20.0f : -35.0f));
            float scale = i == 0 ? 1.0f : 0.72f;
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, -0.5, -0.5);
            collector.submitBlockModel(pose, RenderTypes.translucentMovingBlock(), ring, new int[]{tint}, glow, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }

        if (!state.item.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 0.5 + Mth.sin(state.time * 0.15f) * 0.04, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(state.time * 4.0f));
            pose.scale(0.42f, 0.42f, 0.42f);
            state.item.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    public static class State extends BlockEntityRenderState {
        boolean active;
        boolean working;
        boolean quantum;
        float time;
        final ItemStackRenderState item = new ItemStackRenderState();
    }
}
