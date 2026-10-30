package com.nightbeam.omnivault.fabric.client;

import com.nightbeam.omnivault.client.ClientRegistrar;
import com.nightbeam.omnivault.client.OmniVaultClient;
import com.nightbeam.omnivault.fabric.OmniVaultFabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
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
        List<ResourceLocation> models = List.copyOf(registrar.models);
        ModelLoadingPlugin.register(context -> context.addModels(models));
        ClientTickEvents.END_CLIENT_TICK.register(client -> OmniVaultClient.clientTick());
    }

    private static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        // Fabric runs play payload handlers on the client thread.
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload));
    }

    /** Fabric registers everything straight away, during client initialization. */
    private static final class Registrar implements ClientRegistrar {
        private final List<ResourceLocation> models = new ArrayList<>();

        @Override
        public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void screen(Supplier<MenuType<M>> type, ScreenFactory<M, U> factory) {
            MenuScreens.register(type.get(), factory::create);
        }

        @Override
        public <T extends BlockEntity> void blockEntityRenderer(Supplier<BlockEntityType<T>> type, BlockEntityRendererProvider<? super T> provider) {
            @SuppressWarnings("unchecked")
            BlockEntityRendererProvider<T> typed = (BlockEntityRendererProvider<T>) provider;
            BlockEntityRenderers.register(type.get(), typed);
        }

        @SafeVarargs
        @Override
        public final void blockColor(BlockColor color, Supplier<? extends Block>... blocks) {
            Block[] resolved = new Block[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                resolved[i] = blocks[i].get();
            }
            ColorProviderRegistry.BLOCK.register(color, resolved);
        }

        @SafeVarargs
        @Override
        public final void itemColor(ItemColor color, Supplier<? extends ItemLike>... items) {
            ItemLike[] resolved = new ItemLike[items.length];
            for (int i = 0; i < items.length; i++) {
                resolved[i] = items[i].get();
            }
            ColorProviderRegistry.ITEM.register(color, resolved);
        }

        @Override
        public void itemProperty(Supplier<? extends Item> item, ResourceLocation name, ClampedItemPropertyFunction function) {
            ItemProperties.register(item.get(), name, function);
        }

        @Override
        public void keyMapping(KeyMapping key) {
            KeyBindingHelper.registerKeyBinding(key);
        }

        @Override
        public void standaloneModel(ResourceLocation model) {
            models.add(model);
        }

        @Override
        public void playerLayer(Function<RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> factory) {
            LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
                if (renderer instanceof PlayerRenderer player) {
                    helper.register(factory.apply(player));
                }
            });
        }
    }
}
