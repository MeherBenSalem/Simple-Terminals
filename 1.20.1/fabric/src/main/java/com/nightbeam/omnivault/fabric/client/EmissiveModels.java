package com.nightbeam.omnivault.fabric.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightbeam.omnivault.OmniVault;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
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
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Glowing parts of our block models (LED strips, screens, energy rings), and the models' render types.
 * Forge reads the elements' {@code forge_data} and draws them full-bright; vanilla model loading drops
 * that data, so on Fabric the asset generator also marks exactly those elements unshaded, and this
 * wrapper draws every unshaded face of our models with an emissive material. Vanilla also ignores a
 * model's {@code render_type} (cutout vault trims, the Nexus Core's glass), so the wrapper reads it from
 * the model files and gives every face the matching blend mode.
 */
final class EmissiveModels {
    private static final Direction[] FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
    private static final FileToIdConverter MODELS = FileToIdConverter.json("models");

    private EmissiveModels() {
    }

    /** Each of our model files' render type as a blend mode, following parents within our models. */
    static CompletableFuture<Map<ResourceLocation, BlendMode>> loadRenderTypes(ResourceManager resources, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            Map<ResourceLocation, JsonObject> models = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : MODELS.listMatchingResources(resources).entrySet()) {
                ResourceLocation file = entry.getKey();
                if (!OmniVault.MOD_ID.equals(file.getNamespace())) {
                    continue;
                }
                try (Reader reader = entry.getValue().openAsReader()) {
                    models.put(MODELS.fileToId(file), JsonParser.parseReader(reader).getAsJsonObject());
                } catch (Exception e) {
                    OmniVault.LOG.warn("Could not read model {}", file, e);
                }
            }
            Map<ResourceLocation, BlendMode> blendModes = new HashMap<>();
            for (ResourceLocation id : models.keySet()) {
                BlendMode mode = blendMode(id, models, 0);
                if (mode != BlendMode.DEFAULT) {
                    blendModes.put(id, mode);
                }
            }
            return blendModes;
        }, executor);
    }

    private static BlendMode blendMode(ResourceLocation id, Map<ResourceLocation, JsonObject> models, int depth) {
        JsonObject model = models.get(id);
        if (model == null || depth > 16) {
            return BlendMode.DEFAULT;
        }
        if (model.has("render_type")) {
            return switch (model.get("render_type").getAsString()) {
                case "minecraft:solid", "solid" -> BlendMode.SOLID;
                case "minecraft:cutout_mipped", "cutout_mipped" -> BlendMode.CUTOUT_MIPPED;
                case "minecraft:cutout", "cutout" -> BlendMode.CUTOUT;
                case "minecraft:translucent", "translucent" -> BlendMode.TRANSLUCENT;
                default -> BlendMode.DEFAULT;
            };
        }
        ResourceLocation parent = model.has("parent") ? ResourceLocation.tryParse(model.get("parent").getAsString()) : null;
        return parent == null ? BlendMode.DEFAULT : blendMode(parent, models, depth + 1);
    }

    static void register(ModelLoadingPlugin.Context context, Map<ResourceLocation, BlendMode> blendModes) {
        context.modifyModelAfterBake().register((model, ctx) -> {
            ResourceLocation file = modelFile(ctx.id());
            if (model == null || model instanceof GlowingModel || file == null || !OmniVault.MOD_ID.equals(file.getNamespace())) {
                return model;
            }
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            BlendMode blendMode = blendModes.getOrDefault(file, BlendMode.DEFAULT);
            if (renderer == null || (blendMode == BlendMode.DEFAULT && !hasGlowingFaces(model))) {
                return model;
            }
            RenderMaterial standard = renderer.materialFinder().blendMode(blendMode).find();
            RenderMaterial glow = renderer.materialFinder().blendMode(blendMode).emissive(true).disableDiffuse(true).ambientOcclusion(TriState.FALSE).find();
            return new GlowingModel(model, standard, glow);
        });
    }

    /**
     * The model file a baked model comes from: block models are baked under their file's id (a block state
     * variant then refers to that), item models under their {@code #inventory} location.
     */
    @Nullable
    private static ResourceLocation modelFile(ResourceLocation id) {
        if (id instanceof ModelResourceLocation location) {
            return "inventory".equals(location.getVariant())
                    ? new ResourceLocation(location.getNamespace(), "item/" + location.getPath()) : null;
        }
        return id;
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
            this.wrapped = wrapped;
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
