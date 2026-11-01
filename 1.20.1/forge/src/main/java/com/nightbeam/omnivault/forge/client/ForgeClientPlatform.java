package com.nightbeam.omnivault.forge.client;

import com.nightbeam.omnivault.client.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

public class ForgeClientPlatform implements ClientPlatform {
    @Override
    public BakedModel getStandaloneModel(ResourceLocation model) {
        // Added through ModelEvent.RegisterAdditional; Forge looks extra models up by their plain id.
        return Minecraft.getInstance().getModelManager().getModel(model);
    }
}
