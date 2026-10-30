package com.nightbeam.omnivault.neoforge.client;

import com.nightbeam.omnivault.client.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

public class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    public BakedModel getStandaloneModel(ResourceLocation model) {
        return Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(model));
    }
}
