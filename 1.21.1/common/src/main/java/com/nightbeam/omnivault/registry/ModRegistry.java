package com.nightbeam.omnivault.registry;

import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Registration helpers shared by every feature. Each feature keeps its own content class
 * (for example {@code NetworkContent}) that calls these from static initialisers.
 */
public final class ModRegistry {
    private static final List<Supplier<? extends Item>> TAB_ITEMS = new ArrayList<>();

    private ModRegistry() {
    }

    public static <T extends Block> Supplier<T> block(String name, Supplier<T> factory) {
        return Services.REGISTRY.register(Registries.BLOCK, name, factory);
    }

    /** Registers a block and its block item, and lists the item in the creative tab. */
    public static <T extends Block> Supplier<T> blockWithItem(String name, Supplier<T> factory) {
        return blockWithItem(name, factory, UnaryOperator.identity());
    }

    public static <T extends Block> Supplier<T> blockWithItem(String name, Supplier<T> factory, UnaryOperator<Item.Properties> properties) {
        Supplier<T> block = block(name, factory);
        item(name, () -> new BlockItem(block.get(), properties.apply(new Item.Properties())));
        return block;
    }

    public static <T extends Block, I extends BlockItem> Supplier<T> blockWithItem(String name, Supplier<T> factory, Function<T, I> itemFactory) {
        Supplier<T> block = block(name, factory);
        item(name, () -> itemFactory.apply(block.get()));
        return block;
    }

    public static <T extends Item> Supplier<T> item(String name, Supplier<T> factory) {
        Supplier<T> item = Services.REGISTRY.register(Registries.ITEM, name, factory);
        TAB_ITEMS.add(item);
        return item;
    }

    /** Registers an item that is not listed in the creative tab (for example a technical item). */
    public static <T extends Item> Supplier<T> hiddenItem(String name, Supplier<T> factory) {
        return Services.REGISTRY.register(Registries.ITEM, name, factory);
    }

    @SafeVarargs
    public static <T extends BlockEntity> Supplier<BlockEntityType<T>> blockEntity(String name, IPlatformHelper.BlockEntityFactory<T> factory, Supplier<? extends Block>... blocks) {
        return Services.REGISTRY.register(Registries.BLOCK_ENTITY_TYPE, name, () -> {
            Block[] resolved = new Block[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                resolved[i] = blocks[i].get();
            }
            return Services.PLATFORM.createBlockEntityType(factory, resolved);
        });
    }

    public static <T extends AbstractContainerMenu> Supplier<MenuType<T>> menu(String name, IPlatformHelper.MenuFactory<T> factory) {
        return Services.REGISTRY.register(Registries.MENU, name, () -> Services.PLATFORM.createMenuType(factory));
    }

    public static <T> Supplier<DataComponentType<T>> component(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return Services.REGISTRY.register(Registries.DATA_COMPONENT_TYPE, name, () -> builder.apply(DataComponentType.builder()).build());
    }

    public static List<Supplier<? extends Item>> tabItems() {
        return Collections.unmodifiableList(TAB_ITEMS);
    }
}
