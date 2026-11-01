package com.nightbeam.omnivault.fabric.client;

import com.nightbeam.omnivault.client.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

public class FabricClientPlatform implements ClientPlatform {
    @Override
    public BakedModel getStandaloneModel(ResourceLocation model) {
        // Added through ModelLoadingPlugin; Fabric looks extra models up by their plain id.
        return Minecraft.getInstance().getModelManager().getModel(model);
    }
}
