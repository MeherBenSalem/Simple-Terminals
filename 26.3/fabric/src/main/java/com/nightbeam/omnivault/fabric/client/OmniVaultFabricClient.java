package com.nightbeam.omnivault.fabric.client;

import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.client.ClientRegistrar;
import com.nightbeam.omnivault.client.OmniVaultClient;
import com.nightbeam.omnivault.fabric.OmniVaultFabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class OmniVaultFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OmniVaultFabric.forEachClientHandler(OmniVaultFabricClient::registerReceiver);
        Registrar registrar = new Registrar();
        OmniVaultClient.register(registrar);
        List<Identifier> models = List.copyOf(registrar.models);
        ModelLoadingPlugin.register(context -> models.forEach(model -> context.addModel(FabricClientPlatform.key(model),
                FabricClientPlatform.unbaked(model))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> OmniVaultClient.clientTick());
    }

    private static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        // Fabric runs play payload handlers on the client thread.
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload));
    }

    /** Fabric registers everything straight away, during client initialization. */
    private static final class Registrar implements ClientRegistrar {
        private final List<Identifier> models = new ArrayList<>();

        @Override
        public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void screen(Supplier<MenuType<M>> type, ScreenFactory<M, U> factory) {
            MenuScreens.register(type.get(), factory::create);
        }

        @Override
        public <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
                                                                                              BlockEntityRendererProvider<T, S> provider) {
            BlockEntityRenderers.register(type.get(), provider);
        }

        @SafeVarargs
        @Override
        public final void blockTints(List<BlockTintSource> layers, Supplier<? extends Block>... blocks) {
            Block[] resolved = new Block[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                resolved[i] = blocks[i].get();
            }
            BlockColorRegistry.register(layers, resolved);
        }

        @Override
        public void itemTintSource(Identifier id, MapCodec<? extends ItemTintSource> codec) {
            ItemTintSources.ID_MAPPER.put(id, codec);
        }

        @Override
        public void keyCategory(KeyMapping.Category category) {
            KeyMapping.Category.register(category.id());
        }

        @Override
        public void keyMapping(KeyMapping key) {
            KeyMappingHelper.registerKeyMapping(key);
        }

        @Override
        public void standaloneModel(Identifier model) {
            models.add(model);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void playerLayer(Function<RenderLayerParent<AvatarRenderState, PlayerModel>, RenderLayer<AvatarRenderState, PlayerModel>> factory) {
            // Players and mannequins can both wear one.
            LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
                if (renderer instanceof AvatarRenderer<?> avatar) {
                    helper.register(factory.apply((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar));
                }
            });
        }
    }
}
