package com.nightbeam.omnivault.fabric.client;

import com.nightbeam.omnivault.OmniVault;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Glowing parts of our block models (LED strips, screens, energy rings). NeoForge reads the elements'
 * {@code neoforge_data} and draws them full-bright; vanilla model loading drops that data, so on
 * Fabric the asset generator also marks exactly those elements unshaded, and this wrapper draws every
 * unshaded face of our models with an emissive material.
 */
final class EmissiveModels {
    private static final Direction[] FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

    private EmissiveModels() {
    }

    static void register(ModelLoadingPlugin.Context context) {
        context.modifyModelAfterBake().register((model, ctx) -> {
            if (model == null || !(ours(ctx.resourceId()) || ours(ctx.topLevelId()))) {
                return model;
            }
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null || !hasGlowingFaces(model)) {
                return model;
            }
            RenderMaterial glow = renderer.materialFinder().emissive(true).disableDiffuse(true).ambientOcclusion(TriState.FALSE).find();
            return new GlowingModel(model, renderer.materialFinder().find(), glow);
        });
    }

    private static boolean ours(@Nullable ResourceLocation id) {
        return id != null && OmniVault.MOD_ID.equals(id.getNamespace());
    }

    private static boolean ours(@Nullable ModelResourceLocation id) {
        return id != null && ours(id.id());
    }

    private static boolean hasGlowingFaces(BakedModel model) {
        RandomSource random = RandomSource.create(42);
        for (Direction face : FACES) {
            for (BakedQuad quad : model.getQuads(null, face, random)) {
                if (!quad.isShade()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class GlowingModel extends ForwardingBakedModel {
        private final RenderMaterial standard;
        private final RenderMaterial glow;

        GlowingModel(BakedModel wrapped, RenderMaterial standard, RenderMaterial glow) {
            super(wrapped);
            this.standard = standard;
            this.glow = glow;
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
            emit(state, random, context, true);
        }

        @Override
        public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
            emit(null, random, context, false);
        }

        private void emit(@Nullable BlockState state, Supplier<RandomSource> random, RenderContext context, boolean cull) {
            QuadEmitter emitter = context.getEmitter();
            for (Direction face : FACES) {
                if (cull && face != null && context.isFaceCulled(face)) {
                    continue;
                }
                for (BakedQuad quad : wrapped.getQuads(state, face, random.get())) {
                    emitter.fromVanilla(quad, quad.isShade() ? standard : glow, face);
                    emitter.emit();
                }
            }
        }
    }
}
