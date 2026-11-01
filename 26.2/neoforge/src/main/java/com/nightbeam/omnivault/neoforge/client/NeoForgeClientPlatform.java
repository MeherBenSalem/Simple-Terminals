package com.nightbeam.omnivault.neoforge.client;

import com.nightbeam.omnivault.client.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NeoForgeClientPlatform implements ClientPlatform {
    private static final Map<Identifier, StandaloneModelKey<BlockStateModelPart>> KEYS = new ConcurrentHashMap<>();

    /** The key a standalone model is registered and looked up under. */
    static StandaloneModelKey<BlockStateModelPart> key(Identifier model) {
        return KEYS.computeIfAbsent(model, id -> new StandaloneModelKey<>(id::toString));
    }

    @Override
    public BlockStateModelPart getStandaloneModel(Identifier model) {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(key(model));
    }
}
