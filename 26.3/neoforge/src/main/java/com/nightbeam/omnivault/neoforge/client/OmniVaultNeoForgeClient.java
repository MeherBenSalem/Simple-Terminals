package com.nightbeam.omnivault.neoforge.client;

import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.ClientRegistrar;
import com.nightbeam.omnivault.client.OmniVaultClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Mod(value = OmniVault.MOD_ID, dist = Dist.CLIENT)
public class OmniVaultNeoForgeClient {
    public OmniVaultNeoForgeClient(IEventBus modBus) {
        OmniVaultClient.init();
        modBus.addListener((RegisterMenuScreensEvent e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((EntityRenderersEvent.AddLayers e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((RegisterColorHandlersEvent.BlockTintSources e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((RegisterColorHandlersEvent.ItemTintSources e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((RegisterKeyMappingsEvent e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
        modBus.addListener((ModelEvent.RegisterStandalone e) -> OmniVaultClient.register(new PhaseRegistrar(e)));
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
        public <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
                                                                                              BlockEntityRendererProvider<T, S> provider) {
            if (event instanceof EntityRenderersEvent.RegisterRenderers e) {
                e.registerBlockEntityRenderer(type.get(), provider);
            }
        }

        @SafeVarargs
        @Override
        public final void blockTints(List<BlockTintSource> layers, Supplier<? extends Block>... blocks) {
            if (event instanceof RegisterColorHandlersEvent.BlockTintSources e) {
                Block[] resolved = new Block[blocks.length];
                for (int i = 0; i < blocks.length; i++) {
                    resolved[i] = blocks[i].get();
                }
                e.register(layers, resolved);
            }
        }

        @Override
        public void itemTintSource(Identifier id, MapCodec<? extends ItemTintSource> codec) {
            if (event instanceof RegisterColorHandlersEvent.ItemTintSources e) {
                e.register(id, codec);
            }
        }

        @Override
        public void keyCategory(KeyMapping.Category category) {
            if (event instanceof RegisterKeyMappingsEvent e) {
                e.registerCategory(category);
            }
        }

        @Override
        public void keyMapping(KeyMapping key) {
            if (event instanceof RegisterKeyMappingsEvent e) {
                e.register(key);
            }
        }

        @Override
        public void standaloneModel(Identifier model) {
            if (event instanceof ModelEvent.RegisterStandalone e) {
                e.register(NeoForgeClientPlatform.key(model), SimpleUnbakedStandaloneModel.simpleModelWrapper(model));
            }
        }

        @Override
        public void playerLayer(Function<RenderLayerParent<AvatarRenderState, PlayerModel>, RenderLayer<AvatarRenderState, PlayerModel>> factory) {
            if (event instanceof EntityRenderersEvent.AddLayers e) {
                // Players and mannequins can both wear one.
                for (PlayerModelType skin : e.getSkins()) {
                    AvatarRenderer<?> player = e.getPlayerRenderer(skin);
                    if (player != null) {
                        player.addLayer(factory.apply(player));
                    }
                    AvatarRenderer<?> mannequin = e.getMannequinRenderer(skin);
                    if (mannequin != null) {
                        mannequin.addLayer(factory.apply(mannequin));
                    }
                }
            }
        }
    }
}
