package com.nightbeam.omnivault.drawer;

import com.mojang.serialization.Codec;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.registry.ModRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

/** Drawers, the compacting drawer, the drawer controller, trim, key and drawer upgrades. */
public final class DrawerContent {
    // Item data that survives breaking a drawer.
    public static final Supplier<DataComponentType<DrawerContents>> DRAWER_CONTENTS = ModRegistry.component("drawer_contents",
            b -> b.persistent(DrawerContents.CODEC).networkSynchronized(DrawerContents.STREAM_CODEC));
    public static final Supplier<DataComponentType<CompactingContents>> COMPACTING_CONTENTS = ModRegistry.component("compacting_contents",
            b -> b.persistent(CompactingContents.CODEC).networkSynchronized(CompactingContents.STREAM_CODEC));
    public static final Supplier<DataComponentType<Boolean>> DRAWER_LOCKED = ModRegistry.component("drawer_locked",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final Supplier<DataComponentType<ItemContainerContents>> DRAWER_UPGRADES = ModRegistry.component("drawer_upgrades",
            b -> b.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC).cacheEncoding());

    public static final Supplier<DrawerBlock> DRAWER_1X1 = drawer("drawer_1x1", DrawerLayout.SINGLE);
    public static final Supplier<DrawerBlock> DRAWER_1X2 = drawer("drawer_1x2", DrawerLayout.DOUBLE);
    public static final Supplier<DrawerBlock> DRAWER_2X2 = drawer("drawer_2x2", DrawerLayout.QUAD);
    public static final Supplier<DrawerBlock> COMPACTING_DRAWER = ModRegistry.blockWithItem("compacting_drawer",
            () -> new DrawerBlock(DrawerLayout.COMPACTING, metalProperties()),
            (DrawerBlock block) -> new DrawerBlockItem(block, new Item.Properties()));
    public static final Supplier<DrawerControllerBlock> DRAWER_CONTROLLER = ModRegistry.blockWithItem("drawer_controller",
            () -> new DrawerControllerBlock(metalProperties()));
    public static final Supplier<DrawerTrimBlock> DRAWER_TRIM = ModRegistry.blockWithItem("drawer_trim",
            () -> new DrawerTrimBlock(woodProperties().strength(2.0f, 3.0f)));

    public static final Supplier<DrawerKeyItem> DRAWER_KEY = ModRegistry.item("drawer_key", () -> new DrawerKeyItem(new Item.Properties()));
    public static final Supplier<DrawerUpgradeItem> IRON_UPGRADE = capacityUpgrade("iron_drawer_upgrade", 2, Rarity.COMMON);
    public static final Supplier<DrawerUpgradeItem> GOLD_UPGRADE = capacityUpgrade("gold_drawer_upgrade", 4, Rarity.COMMON);
    public static final Supplier<DrawerUpgradeItem> DIAMOND_UPGRADE = capacityUpgrade("diamond_drawer_upgrade", 8, Rarity.UNCOMMON);
    public static final Supplier<DrawerUpgradeItem> NETHERITE_UPGRADE = capacityUpgrade("netherite_drawer_upgrade", 16, Rarity.RARE);
    public static final Supplier<DrawerUpgradeItem> VOID_UPGRADE = ModRegistry.item("void_drawer_upgrade",
            () -> new DrawerUpgradeItem(DrawerUpgradeItem.Kind.VOID, 0, new Item.Properties()));

    public static final Supplier<BlockEntityType<DrawerBlockEntity>> DRAWER_BE = ModRegistry.blockEntity("drawer",
            DrawerBlockEntity::new, DRAWER_1X1, DRAWER_1X2, DRAWER_2X2);
    public static final Supplier<BlockEntityType<CompactingDrawerBlockEntity>> COMPACTING_BE = ModRegistry.blockEntity("compacting_drawer",
            CompactingDrawerBlockEntity::new, COMPACTING_DRAWER);
    public static final Supplier<BlockEntityType<DrawerControllerBlockEntity>> CONTROLLER_BE = ModRegistry.blockEntity("drawer_controller",
            DrawerControllerBlockEntity::new, DRAWER_CONTROLLER);

    public static final Supplier<MenuType<DrawerMenu>> DRAWER_MENU = ModRegistry.menu("drawer", DrawerMenu::new);

    private DrawerContent() {
    }

    private static BlockBehaviour.Properties woodProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASS)
                .strength(2.5f, 4.0f).sound(SoundType.WOOD);
    }

    private static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.NETHERITE_BLOCK);
    }

    private static Supplier<DrawerBlock> drawer(String name, DrawerLayout layout) {
        return ModRegistry.blockWithItem(name, () -> new DrawerBlock(layout, woodProperties()),
                (DrawerBlock block) -> new DrawerBlockItem(block, new Item.Properties()));
    }

    private static Supplier<DrawerUpgradeItem> capacityUpgrade(String name, int multiplier, Rarity rarity) {
        return ModRegistry.item(name, () -> new DrawerUpgradeItem(DrawerUpgradeItem.Kind.CAPACITY, multiplier, new Item.Properties().rarity(rarity)));
    }

    public static void init() {
        DrawerConfig.init();
        ModCapabilities.exposeItems(DRAWER_BE);
        ModCapabilities.exposeItems(COMPACTING_BE);
        ModCapabilities.exposeItems(CONTROLLER_BE);
    }
}
