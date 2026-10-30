package com.nightbeam.omnivault.neoforge.client;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientRegistrar;
import com.nightbeam.omnivault.client.OmniVaultClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
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
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Function;
import java.util.function.Supplier;

@Mod(value = OmniVault.MOD_ID, dist = Dist.CLIENT)
public class OmniVaultNeoForgeClient {
    public OmniVaultNeoForgeClient(IEventBus modBus) {
        OmniVaultClient.init();
        modBus.addListener((RegisterMenuScreensEvent e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((EntityRenderersEvent.AddLayers e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((RegisterColorHandlersEvent.Block e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((RegisterColorHandlersEvent.Item e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((RegisterKeyMappingsEvent e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((ModelEvent.RegisterAdditional e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> OmniVaultClient.register(new PhaseRegistrar(e))));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> OmniVaultClient.clientTick());
    }

    /** Forwards each registration call to the one NeoForge event that is currently firing. */
    private record PhaseRegistrar(Event event) implements ClientRegistrar {
        @Override
        public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void screen(Supplier<MenuType<M>> type, ScreenFactory<M, U> factory) {
            if (event instanceof RegisterMenuScreensEvent e) {
                e.register(type.get(), factory::create);
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
                e.register(ModelResourceLocation.standalone(model));
            }
        }

        @Override
        public void playerLayer(Function<RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> factory) {
            if (event instanceof EntityRenderersEvent.AddLayers e) {
                for (PlayerSkin.Model skin : e.getSkins()) {
                    if (e.getSkin(skin) instanceof PlayerRenderer renderer) {
                        renderer.addLayer(factory.apply(renderer));
                    }
                }
            }
        }
    }
}
