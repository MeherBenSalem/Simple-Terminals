package com.nightbeam.omnivault.autocraft;

import com.nightbeam.omnivault.autocraft.block.AssemblerBlockEntity;
import com.nightbeam.omnivault.autocraft.block.AutocraftDeviceBlock;
import com.nightbeam.omnivault.autocraft.block.CraftingProcessorBlockEntity;
import com.nightbeam.omnivault.autocraft.block.PatternProviderBlock;
import com.nightbeam.omnivault.autocraft.block.PatternProviderBlockEntity;
import com.nightbeam.omnivault.autocraft.block.PatternTerminalBlockEntity;
import com.nightbeam.omnivault.autocraft.menu.AssemblerMenu;
import com.nightbeam.omnivault.autocraft.menu.CraftRequestMenu;
import com.nightbeam.omnivault.autocraft.menu.CraftingStatusMenu;
import com.nightbeam.omnivault.autocraft.menu.PatternProviderMenu;
import com.nightbeam.omnivault.autocraft.menu.PatternTerminalMenu;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.autocraft.pattern.PatternItem;
import com.nightbeam.omnivault.network.api.CraftingHooks;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.registry.ModRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Autocrafting: patterns, the encoding terminal, pattern providers, assemblers and crafting processors. */
public final class AutocraftContent {
    // -- data components ---------------------------------------------------------------------------
    public static final Supplier<DataComponentType<PatternData>> PATTERN = ModRegistry.component("pattern",
            b -> b.persistent(PatternData.CODEC).networkSynchronized(PatternData.STREAM_CODEC).cacheEncoding());

    // -- items ---------------------------------------------------------------------------------------
    public static final Supplier<PatternItem> BLANK_PATTERN = ModRegistry.item("blank_pattern",
            () -> new PatternItem(PatternItem.Kind.BLANK, new Item.Properties()));
    public static final Supplier<PatternItem> CRAFTING_PATTERN = ModRegistry.hiddenItem("crafting_pattern",
            () -> new PatternItem(PatternItem.Kind.CRAFTING, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Supplier<PatternItem> PROCESSING_PATTERN = ModRegistry.hiddenItem("processing_pattern",
            () -> new PatternItem(PatternItem.Kind.PROCESSING, new Item.Properties().rarity(Rarity.UNCOMMON)));

    // -- blocks --------------------------------------------------------------------------------------
    public static final Supplier<OrientedDeviceBlock> PATTERN_TERMINAL = ModRegistry.blockWithItem("pattern_terminal",
            () -> new OrientedDeviceBlock(PatternTerminalBlockEntity::new, device().lightLevel(state -> lit(state, 7))));
    public static final Supplier<PatternProviderBlock> PATTERN_PROVIDER = ModRegistry.blockWithItem("pattern_provider",
            () -> new PatternProviderBlock(PatternProviderBlockEntity::new, device().lightLevel(state -> lit(state, 5))));
    public static final Supplier<AutocraftDeviceBlock> ASSEMBLER = ModRegistry.blockWithItem("assembler",
            () -> new AutocraftDeviceBlock(AssemblerBlockEntity::new, device().noOcclusion()
                    .lightLevel(state -> state.getValue(AutocraftDeviceBlock.WORKING) ? 13 : lit(state, 4))),
            (UnaryOperator<Item.Properties>) p -> p.rarity(Rarity.UNCOMMON));
    /** Late game: the same machine, sixteen times the crafts per cycle. */
    public static final Supplier<AutocraftDeviceBlock> QUANTUM_ASSEMBLER = ModRegistry.blockWithItem("quantum_assembler",
            () -> new AutocraftDeviceBlock(AssemblerBlockEntity::new, device().noOcclusion()
                    .lightLevel(state -> state.getValue(AutocraftDeviceBlock.WORKING) ? 15 : lit(state, 6))),
            (UnaryOperator<Item.Properties>) p -> p.rarity(Rarity.EPIC));
    public static final Supplier<AutocraftDeviceBlock> CRAFTING_PROCESSOR = ModRegistry.blockWithItem("crafting_processor",
            () -> new AutocraftDeviceBlock(CraftingProcessorBlockEntity::new, device()
                    .lightLevel(state -> state.getValue(AutocraftDeviceBlock.WORKING) ? 10 : lit(state, 4))));

    // -- block entities ------------------------------------------------------------------------------
    public static final Supplier<BlockEntityType<PatternTerminalBlockEntity>> PATTERN_TERMINAL_BE =
            ModRegistry.blockEntity("pattern_terminal", PatternTerminalBlockEntity::new, PATTERN_TERMINAL);
    public static final Supplier<BlockEntityType<PatternProviderBlockEntity>> PATTERN_PROVIDER_BE =
            ModRegistry.blockEntity("pattern_provider", PatternProviderBlockEntity::new, PATTERN_PROVIDER);
    public static final Supplier<BlockEntityType<AssemblerBlockEntity>> ASSEMBLER_BE =
            ModRegistry.blockEntity("assembler", AssemblerBlockEntity::new, ASSEMBLER, QUANTUM_ASSEMBLER);
    public static final Supplier<BlockEntityType<CraftingProcessorBlockEntity>> CRAFTING_PROCESSOR_BE =
            ModRegistry.blockEntity("crafting_processor", CraftingProcessorBlockEntity::new, CRAFTING_PROCESSOR);

    // -- menus ---------------------------------------------------------------------------------------
    public static final Supplier<MenuType<PatternTerminalMenu>> PATTERN_TERMINAL_MENU = ModRegistry.menu("pattern_terminal", PatternTerminalMenu::client);
    public static final Supplier<MenuType<PatternProviderMenu>> PATTERN_PROVIDER_MENU = ModRegistry.menu("pattern_provider", PatternProviderMenu::client);
    public static final Supplier<MenuType<AssemblerMenu>> ASSEMBLER_MENU = ModRegistry.menu("assembler", AssemblerMenu::client);
    public static final Supplier<MenuType<CraftRequestMenu>> CRAFT_REQUEST_MENU = ModRegistry.menu("craft_request", CraftRequestMenu::client);
    public static final Supplier<MenuType<CraftingStatusMenu>> CRAFTING_STATUS_MENU = ModRegistry.menu("crafting_status", CraftingStatusMenu::client);

    private AutocraftContent() {
    }

    private static BlockBehaviour.Properties device() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6.0f).sound(SoundType.NETHERITE_BLOCK);
    }

    private static int lit(BlockState state, int light) {
        return state.getValue(OrientedDeviceBlock.ACTIVE) ? light : 0;
    }

    public static void init() {
        AutocraftConfig.init();
        AutocraftPackets.init();
        ModCapabilities.exposeItems(PATTERN_PROVIDER_BE);
        CraftingHooks.install(new CraftingService());
    }
}
