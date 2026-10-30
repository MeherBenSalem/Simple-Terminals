package com.nightbeam.omnivault.content;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.item.OmniToolItem;
import com.nightbeam.omnivault.item.UpgradeItem;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.registry.ModRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

/** Materials, components, tools and upgrade cards used across every feature. */
public final class CoreContent {
    public static final Supplier<Item> OMNIUM_DUST = ModRegistry.item("omnium_dust", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> OMNIUM_BLEND = ModRegistry.item("omnium_blend", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> OMNIUM_INGOT = ModRegistry.item("omnium_ingot", () -> new Item(new Item.Properties()));
    public static final Supplier<Block> OMNIUM_BLOCK = ModRegistry.blockWithItem("omnium_block", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK).lightLevel(state -> 6)));
    public static final Supplier<Item> LOGIC_PROCESSOR = ModRegistry.item("logic_processor", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> ADVANCED_PROCESSOR = ModRegistry.item("advanced_processor", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> QUANTUM_PROCESSOR = ModRegistry.item("quantum_processor", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final Supplier<Item> UPGRADE_BASE = ModRegistry.item("upgrade_base", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> QUANTUM_UPGRADE_TEMPLATE = ModRegistry.item("quantum_upgrade_template",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC)));

    public static final Supplier<OmniToolItem> OMNI_TOOL = ModRegistry.item("omni_tool", () -> new OmniToolItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<UpgradeItem> SPEED_UPGRADE = upgrade("speed_upgrade", UpgradeItem.Kind.SPEED);
    public static final Supplier<UpgradeItem> STACK_UPGRADE = upgrade("stack_upgrade", UpgradeItem.Kind.STACK);
    public static final Supplier<UpgradeItem> FUZZY_UPGRADE = upgrade("fuzzy_upgrade", UpgradeItem.Kind.FUZZY);
    public static final Supplier<UpgradeItem> RANGE_UPGRADE = upgrade("range_upgrade", UpgradeItem.Kind.RANGE);
    public static final Supplier<UpgradeItem> REDSTONE_UPGRADE = upgrade("redstone_upgrade", UpgradeItem.Kind.REDSTONE);

    public static final Supplier<CreativeModeTab> TAB = Services.REGISTRY.register(Registries.CREATIVE_MODE_TAB, "main",
            () -> Services.PLATFORM.creativeTabBuilder()
                    .title(Component.translatable("itemGroup." + OmniVault.MOD_ID))
                    .icon(() -> new ItemStack(OMNIUM_INGOT.get()))
                    .displayItems((parameters, output) -> ModRegistry.tabItems().forEach(item -> output.accept(item.get())))
                    .build());

    private CoreContent() {
    }

    private static Supplier<UpgradeItem> upgrade(String name, UpgradeItem.Kind kind) {
        return ModRegistry.item(name, () -> new UpgradeItem(kind, new Item.Properties()));
    }

    public static void init() {
    }
}
