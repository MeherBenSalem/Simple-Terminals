package com.nightbeam.omnivault.client;

import com.nightbeam.omnivault.platform.Services;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/** Client-only loader services. */
public interface ClientPlatform {
    ClientPlatform INSTANCE = Services.load(ClientPlatform.class);

    /** A model registered with {@link ClientRegistrar#standaloneModel(ResourceLocation)}. */
    BakedModel getStandaloneModel(ResourceLocation model);
}
