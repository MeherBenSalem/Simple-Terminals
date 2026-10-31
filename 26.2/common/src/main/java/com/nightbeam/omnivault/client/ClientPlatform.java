package com.nightbeam.omnivault.client;

import com.nightbeam.omnivault.platform.Services;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.resources.Identifier;

/** Client-only loader services. */
public interface ClientPlatform {
    ClientPlatform INSTANCE = Services.load(ClientPlatform.class);

    /** A model registered with {@link ClientRegistrar#standaloneModel(Identifier)}, baked without rotation. */
    BlockStateModelPart getStandaloneModel(Identifier model);
}
