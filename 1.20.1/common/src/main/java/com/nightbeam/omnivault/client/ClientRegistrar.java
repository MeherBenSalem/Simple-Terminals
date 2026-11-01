package com.nightbeam.omnivault.client;

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
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Function;
import java.util.function.Supplier;

/** Loader-neutral client registration, called once during client setup by each loader. */
public interface ClientRegistrar {
    <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void screen(Supplier<MenuType<M>> type, ScreenFactory<M, U> factory);

    <T extends BlockEntity> void blockEntityRenderer(Supplier<BlockEntityType<T>> type, BlockEntityRendererProvider<? super T> provider);

    @SuppressWarnings("unchecked")
    void blockColor(BlockColor color, Supplier<? extends Block>... blocks);

    @SuppressWarnings("unchecked")
    void itemColor(ItemColor color, Supplier<? extends ItemLike>... items);

    void itemProperty(Supplier<? extends Item> item, ResourceLocation name, ClampedItemPropertyFunction function);

    void keyMapping(KeyMapping key);

    /** A model that is baked but not referenced by any block state, for block entity renderers. */
    void standaloneModel(ResourceLocation model);

    void playerLayer(Function<RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> factory);

    @FunctionalInterface
    interface ScreenFactory<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> {
        U create(M menu, Inventory inventory, Component title);
    }
}
