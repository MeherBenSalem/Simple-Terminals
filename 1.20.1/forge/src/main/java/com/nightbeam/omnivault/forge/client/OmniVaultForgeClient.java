package com.nightbeam.omnivault.forge.client;

import com.nightbeam.omnivault.client.ClientRegistrar;
import com.nightbeam.omnivault.client.OmniVaultClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.function.Function;
import java.util.function.Supplier;

/** Client setup, called from the mod constructor on the physical client only. */
public final class OmniVaultForgeClient {
    private OmniVaultForgeClient() {
    }

    public static void init(IEventBus modBus) {
        OmniVaultClient.init();
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((EntityRenderersEvent.AddLayers e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((RegisterColorHandlersEvent.Block e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((RegisterColorHandlersEvent.Item e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((RegisterKeyMappingsEvent e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((ModelEvent.RegisterAdditional e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        // Forge 1.20.1 has no menu screen event: screens and item properties are registered during client setup.
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> OmniVaultClient.register(new PhaseRegistrar(e))));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                OmniVaultClient.clientTick();
            }
        });
    }

    /** Forwards each registration call to the one Forge event that is currently firing. */
    private record PhaseRegistrar(Event event) implements ClientRegistrar {
        @Override
        public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void screen(Supplier<MenuType<M>> type, ScreenFactory<M, U> factory) {
            if (event instanceof FMLClientSetupEvent) {
                MenuScreens.register(type.get(), factory::create);
            }
        }

        @Override
        public <T extends BlockEntity> void blockEntityRenderer(Supplier<BlockEntityType<T>> type, BlockEntityRendererProvider<? super T> provider) {
            if (event instanceof EntityRenderersEvent.RegisterRenderers e) {
                @SuppressWarnings("unchecked")
                BlockEntityRendererProvider<T> typed = (BlockEntityRendererProvider<T>) provider;
                e.registerBlockEntityRenderer(type.get(), typed);
            }
        }

        @SafeVarargs
        @Override
        public final void blockColor(BlockColor color, Supplier<? extends Block>... blocks) {
            if (event instanceof RegisterColorHandlersEvent.Block e) {
                Block[] resolved = new Block[blocks.length];
                for (int i = 0; i < blocks.length; i++) {
                    resolved[i] = blocks[i].get();
                }
                e.register(color, resolved);
            }
        }

        @SafeVarargs
        @Override
        public final void itemColor(ItemColor color, Supplier<? extends ItemLike>... items) {
            if (event instanceof RegisterColorHandlersEvent.Item e) {
                ItemLike[] resolved = new ItemLike[items.length];
                for (int i = 0; i < items.length; i++) {
                    resolved[i] = items[i].get();
                }
                e.register(color, resolved);
            }
        }

        @Override
        public void itemProperty(Supplier<? extends Item> item, ResourceLocation name, ClampedItemPropertyFunction function) {
            if (event instanceof FMLClientSetupEvent) {
                ItemProperties.register(item.get(), name, function);
            }
        }

        @Override
        public void keyMapping(KeyMapping key) {
            if (event instanceof RegisterKeyMappingsEvent e) {
                e.register(key);
            }
        }

        @Override
        public void standaloneModel(ResourceLocation model) {
            if (event instanceof ModelEvent.RegisterAdditional e) {
                e.register(model);
            }
        }

        @Override
        public void playerLayer(Function<RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> factory) {
            if (event instanceof EntityRenderersEvent.AddLayers e) {
                for (String skin : e.getSkins()) {
                    if (e.getSkin(skin) instanceof PlayerRenderer renderer) {
                        renderer.addLayer(factory.apply(renderer));
                    }
                }
            }
        }
    }
}
