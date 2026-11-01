package com.nightbeam.omnivault.fabric.client;

import com.nightbeam.omnivault.client.ClientPlatform;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedExtraModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FabricClientPlatform implements ClientPlatform {
    private static final Map<Identifier, ExtraModelKey<BlockStateModelPart>> KEYS = new ConcurrentHashMap<>();

    /** The key a standalone model is added and looked up under. */
    static ExtraModelKey<BlockStateModelPart> key(Identifier model) {
        return KEYS.computeIfAbsent(model, id -> ExtraModelKey.create(id::toString));
    }

    /** Bakes a block model without rotation, as NeoForge's simple standalone models do. */
    static UnbakedExtraModel<BlockStateModelPart> unbaked(Identifier model) {
        return new UnbakedExtraModel<>() {
            @Override
            public BlockStateModelPart bake(ModelBaker baker) {
                return SimpleModelWrapper.bake(baker, model, BlockModelRotation.IDENTITY);
            }

            @Override
            public void resolveDependencies(ResolvableModel.Resolver resolver) {
                resolver.markDependency(model);
            }
        };
    }

    @Override
    public BlockStateModelPart getStandaloneModel(Identifier model) {
        return Minecraft.getInstance().getModelManager().getModel(key(model));
    }
}
