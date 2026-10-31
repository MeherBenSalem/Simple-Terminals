package com.nightbeam.omnivault.client;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Loader-neutral client registration, called once during client setup by each loader. */
public interface ClientRegistrar {
    <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void screen(Supplier<MenuType<M>> type, ScreenFactory<M, U> factory);

    <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
                                                                                       BlockEntityRendererProvider<T, S> provider);

    /** Tints for the blocks' models, by tint index. */
    @SuppressWarnings("unchecked")
    void blockTints(List<BlockTintSource> layers, Supplier<? extends Block>... blocks);

    /** A tint source type that item model definitions name in their {@code tints}. */
    void itemTintSource(Identifier id, MapCodec<? extends ItemTintSource> codec);

    void keyCategory(KeyMapping.Category category);

    void keyMapping(KeyMapping key);

    /** A model that is baked but not referenced by any block state, for block entity renderers. */
    void standaloneModel(Identifier model);

    void playerLayer(Function<RenderLayerParent<AvatarRenderState, PlayerModel>, RenderLayer<AvatarRenderState, PlayerModel>> factory);

    @FunctionalInterface
    interface ScreenFactory<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> {
        U create(M menu, Inventory inventory, Component title);
    }
}
