package com.nightbeam.omnivault.registry;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Registration helpers shared by every feature. Each feature keeps its own content class
 * (for example {@code NetworkContent}) that calls these from static initialisers.
 *
 * <p>Blocks and items get their properties from here, already carrying their registry id (Minecraft
 * reads the id while constructing them), as with vanilla's own {@code Blocks.register}.
 */
public final class ModRegistry {
    private static final List<Supplier<? extends Item>> TAB_ITEMS = new ArrayList<>();

    private ModRegistry() {
    }

    public static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String name) {
        return ResourceKey.create(registry, OmniVault.id(name));
    }

    public static <T extends Block> Supplier<T> block(String name, Function<BlockBehaviour.Properties, T> factory,
                                                      Supplier<BlockBehaviour.Properties> properties) {
        return Services.REGISTRY.register(Registries.BLOCK, name, () -> factory.apply(properties.get().setId(key(Registries.BLOCK, name))));
    }

    /** Registers a block and its block item, and lists the item in the creative tab. */
    public static <T extends Block> Supplier<T> blockWithItem(String name, Function<BlockBehaviour.Properties, T> factory,
                                                              Supplier<BlockBehaviour.Properties> properties) {
        return blockWithItem(name, factory, properties, UnaryOperator.identity());
    }

    public static <T extends Block> Supplier<T> blockWithItem(String name, Function<BlockBehaviour.Properties, T> factory,
                                                              Supplier<BlockBehaviour.Properties> properties, UnaryOperator<Item.Properties> itemProperties) {
        Supplier<T> block = block(name, factory, properties);
        item(name, p -> new BlockItem(block.get(), p), p -> itemProperties.apply(p.useBlockDescriptionPrefix()));
        return block;
    }

    public static <T extends Block, I extends BlockItem> Supplier<T> blockWithItem(String name, Function<BlockBehaviour.Properties, T> factory,
                                                                                   Supplier<BlockBehaviour.Properties> properties,
                                                                                   BiFunction<T, Item.Properties, I> itemFactory) {
        Supplier<T> block = block(name, factory, properties);
        item(name, p -> itemFactory.apply(block.get(), p.useBlockDescriptionPrefix()));
        return block;
    }

    public static <T extends Item> Supplier<T> item(String name, Function<Item.Properties, T> factory) {
        return item(name, factory, UnaryOperator.identity());
    }

    public static <T extends Item> Supplier<T> item(String name, Function<Item.Properties, T> factory, UnaryOperator<Item.Properties> properties) {
        Supplier<T> item = hiddenItem(name, factory, properties);
        TAB_ITEMS.add(item);
        return item;
    }

    /** Registers an item that is not listed in the creative tab (for example a technical item). */
    public static <T extends Item> Supplier<T> hiddenItem(String name, Function<Item.Properties, T> factory, UnaryOperator<Item.Properties> properties) {
        return Services.REGISTRY.register(Registries.ITEM, name,
                () -> factory.apply(properties.apply(new Item.Properties().setId(key(Registries.ITEM, name)))));
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
