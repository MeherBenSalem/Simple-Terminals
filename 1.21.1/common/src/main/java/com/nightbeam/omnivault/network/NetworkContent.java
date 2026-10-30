package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.network.block.CableBlock;
import com.nightbeam.omnivault.network.block.ConstructorBlockEntity;
import com.nightbeam.omnivault.network.block.DestructorBlockEntity;
import com.nightbeam.omnivault.network.block.DirectionalDeviceBlock;
import com.nightbeam.omnivault.network.block.DriveBlockEntity;
import com.nightbeam.omnivault.network.block.ExportBusBlockEntity;
import com.nightbeam.omnivault.network.block.ImportBusBlockEntity;
import com.nightbeam.omnivault.network.block.NetworkPortBlockEntity;
import com.nightbeam.omnivault.network.block.NexusCoreBlock;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.network.block.SignalEmitterBlock;
import com.nightbeam.omnivault.network.block.SignalEmitterBlockEntity;
import com.nightbeam.omnivault.network.block.StorageBusBlockEntity;
import com.nightbeam.omnivault.network.block.StorageMonitorBlock;
import com.nightbeam.omnivault.network.block.StorageMonitorBlockEntity;
import com.nightbeam.omnivault.network.block.TerminalBlock;
import com.nightbeam.omnivault.network.block.TerminalBlockEntity;
import com.nightbeam.omnivault.network.block.WirelessTransmitterBlock;
import com.nightbeam.omnivault.network.block.WirelessTransmitterBlockEntity;
import com.nightbeam.omnivault.network.cell.CellTier;
import com.nightbeam.omnivault.network.cell.CellUsage;
import com.nightbeam.omnivault.network.cell.FluidCellItem;
import com.nightbeam.omnivault.network.cell.StorageCellItem;
import com.nightbeam.omnivault.network.item.WirelessTerminalItem;
import com.nightbeam.omnivault.network.menu.BusMenu;
import com.nightbeam.omnivault.network.menu.CoreMenu;
import com.nightbeam.omnivault.network.menu.CraftingTerminalMenu;
import com.nightbeam.omnivault.network.menu.DriveMenu;
import com.nightbeam.omnivault.network.menu.SignalEmitterMenu;
import com.nightbeam.omnivault.network.menu.TerminalMenu;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.registry.ModRegistry;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** The digital storage network: Nexus Core, cables, drives and cells, terminals, buses, wireless. */
public final class NetworkContent {
    // Shapes of pointing devices when facing north; z = 0 is the face touching the target.
    private static final double[][] BUS_SHAPE = {{1, 1, 0, 15, 15, 3}, {3, 3, 3, 13, 13, 8}, {5, 5, 8, 11, 11, 16}};
    private static final double[][] EMITTER_SHAPE = {{3, 3, 12, 13, 13, 16}, {5, 5, 6, 11, 11, 12}, {6, 6, 1, 10, 10, 6}};

    // -- data components ---------------------------------------------------------------------------
    public static final Supplier<DataComponentType<UUID>> CELL_ID = ModRegistry.component("cell_id",
            b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    public static final Supplier<DataComponentType<CellUsage>> CELL_USAGE = ModRegistry.component("cell_usage",
            b -> b.persistent(CellUsage.CODEC).networkSynchronized(CellUsage.STREAM_CODEC));
    public static final Supplier<DataComponentType<GlobalPos>> NETWORK_LINK = ModRegistry.component("network_link",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    // -- blocks --------------------------------------------------------------------------------------
    public static final Supplier<NexusCoreBlock> NEXUS_CORE = ModRegistry.blockWithItem("nexus_core",
            () -> new NexusCoreBlock(device().lightLevel(state -> state.getValue(NexusCoreBlock.ACTIVE) ? 12 : 3)),
            (UnaryOperator<Item.Properties>) p -> p.rarity(Rarity.UNCOMMON));
    public static final Supplier<CableBlock> NEXUS_CABLE = ModRegistry.blockWithItem("nexus_cable",
            () -> new CableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6f).sound(SoundType.COPPER)
                    .noOcclusion().lightLevel(state -> 4)));
    public static final Supplier<OrientedDeviceBlock> DRIVE = ModRegistry.blockWithItem("drive",
            () -> new OrientedDeviceBlock(DriveBlockEntity::new, device()));
    public static final Supplier<TerminalBlock> TERMINAL = ModRegistry.blockWithItem("terminal",
            () -> new TerminalBlock(TerminalBlock.Kind.STORAGE, TerminalBlockEntity::new, device().lightLevel(NetworkContent::screenLight)));
    public static final Supplier<TerminalBlock> CRAFTING_TERMINAL = ModRegistry.blockWithItem("crafting_terminal",
            () -> new TerminalBlock(TerminalBlock.Kind.CRAFTING, TerminalBlockEntity::new, device().lightLevel(NetworkContent::screenLight)));
    public static final Supplier<DirectionalDeviceBlock> IMPORT_BUS = ModRegistry.blockWithItem("import_bus",
            () -> new DirectionalDeviceBlock(ImportBusBlockEntity::new, BUS_SHAPE, device().noOcclusion()));
    public static final Supplier<DirectionalDeviceBlock> EXPORT_BUS = ModRegistry.blockWithItem("export_bus",
            () -> new DirectionalDeviceBlock(ExportBusBlockEntity::new, BUS_SHAPE, device().noOcclusion()));
    public static final Supplier<DirectionalDeviceBlock> STORAGE_BUS = ModRegistry.blockWithItem("storage_bus",
            () -> new DirectionalDeviceBlock(StorageBusBlockEntity::new, BUS_SHAPE, device().noOcclusion()));
    public static final Supplier<OrientedDeviceBlock> NETWORK_PORT = ModRegistry.blockWithItem("network_port",
            () -> new OrientedDeviceBlock(NetworkPortBlockEntity::new, device()) {
                @Override
                protected boolean connectsThroughFront() {
                    return true;
                }
            });
    public static final Supplier<DirectionalDeviceBlock> DESTRUCTOR = ModRegistry.blockWithItem("destructor",
            () -> new DirectionalDeviceBlock(DestructorBlockEntity::new, BUS_SHAPE, device().noOcclusion()));
    public static final Supplier<DirectionalDeviceBlock> CONSTRUCTOR = ModRegistry.blockWithItem("constructor",
            () -> new DirectionalDeviceBlock(ConstructorBlockEntity::new, BUS_SHAPE, device().noOcclusion()));
    public static final Supplier<StorageMonitorBlock> STORAGE_MONITOR = ModRegistry.blockWithItem("storage_monitor",
            () -> new StorageMonitorBlock(device().lightLevel(NetworkContent::screenLight)));
    public static final Supplier<SignalEmitterBlock> SIGNAL_EMITTER = ModRegistry.blockWithItem("signal_emitter",
            () -> new SignalEmitterBlock(SignalEmitterBlockEntity::new, EMITTER_SHAPE, device().noOcclusion()));
    public static final Supplier<WirelessTransmitterBlock> WIRELESS_TRANSMITTER = ModRegistry.blockWithItem("wireless_transmitter",
            () -> new WirelessTransmitterBlock(device().noOcclusion().lightLevel(state -> state.getValue(WirelessTransmitterBlock.ACTIVE) ? 9 : 0)));

    // -- items ---------------------------------------------------------------------------------------
    public static final Supplier<Item> CELL_HOUSING = ModRegistry.item("cell_housing", () -> new Item(new Item.Properties()));
    public static final Map<CellTier, Supplier<Item>> MATRICES = new EnumMap<>(CellTier.class);
    public static final Map<CellTier, Supplier<StorageCellItem>> CELLS = new EnumMap<>(CellTier.class);

    static {
        for (CellTier tier : CellTier.values()) {
            if (!tier.isCreative()) {
                MATRICES.put(tier, ModRegistry.item("storage_matrix_" + tier.id, () -> new Item(new Item.Properties().rarity(tier.rarity))));
            }
        }
        for (CellTier tier : CellTier.values()) {
            String name = tier.isCreative() ? "creative_storage_cell" : "storage_cell_" + tier.id;
            Supplier<Item> matrix = MATRICES.get(tier);
            CELLS.put(tier, ModRegistry.item(name, () -> new StorageCellItem(tier, matrix, new Item.Properties())));
        }
    }

    public static final Supplier<Item> FLUID_CELL_HOUSING = ModRegistry.item("fluid_cell_housing", () -> new Item(new Item.Properties()));
    public static final Map<CellTier, Supplier<FluidCellItem>> FLUID_CELLS = new EnumMap<>(CellTier.class);

    static {
        for (CellTier tier : CellTier.values()) {
            String name = tier.isCreative() ? "creative_fluid_cell" : "fluid_cell_" + tier.id;
            Supplier<Item> matrix = MATRICES.get(tier);
            FLUID_CELLS.put(tier, ModRegistry.item(name, () -> new FluidCellItem(tier, matrix, new Item.Properties())));
        }
    }

    public static final Supplier<WirelessTerminalItem> WIRELESS_TERMINAL = ModRegistry.item("wireless_terminal",
            () -> new WirelessTerminalItem(false, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Supplier<WirelessTerminalItem> QUANTUM_WIRELESS_TERMINAL = ModRegistry.item("quantum_wireless_terminal",
            () -> new WirelessTerminalItem(true, new Item.Properties().rarity(Rarity.EPIC)));

    // -- block entities ------------------------------------------------------------------------------
    public static final Supplier<BlockEntityType<NexusCoreBlockEntity>> NEXUS_CORE_BE = ModRegistry.blockEntity("nexus_core", NexusCoreBlockEntity::new, NEXUS_CORE);
    public static final Supplier<BlockEntityType<DriveBlockEntity>> DRIVE_BE = ModRegistry.blockEntity("drive", DriveBlockEntity::new, DRIVE);
    public static final Supplier<BlockEntityType<TerminalBlockEntity>> TERMINAL_BE = ModRegistry.blockEntity("terminal", TerminalBlockEntity::new, TERMINAL, CRAFTING_TERMINAL);
    public static final Supplier<BlockEntityType<ImportBusBlockEntity>> IMPORT_BUS_BE = ModRegistry.blockEntity("import_bus", ImportBusBlockEntity::new, IMPORT_BUS);
    public static final Supplier<BlockEntityType<ExportBusBlockEntity>> EXPORT_BUS_BE = ModRegistry.blockEntity("export_bus", ExportBusBlockEntity::new, EXPORT_BUS);
    public static final Supplier<BlockEntityType<StorageBusBlockEntity>> STORAGE_BUS_BE = ModRegistry.blockEntity("storage_bus", StorageBusBlockEntity::new, STORAGE_BUS);
    public static final Supplier<BlockEntityType<NetworkPortBlockEntity>> NETWORK_PORT_BE = ModRegistry.blockEntity("network_port", NetworkPortBlockEntity::new, NETWORK_PORT);
    public static final Supplier<BlockEntityType<DestructorBlockEntity>> DESTRUCTOR_BE = ModRegistry.blockEntity("destructor", DestructorBlockEntity::new, DESTRUCTOR);
    public static final Supplier<BlockEntityType<ConstructorBlockEntity>> CONSTRUCTOR_BE = ModRegistry.blockEntity("constructor", ConstructorBlockEntity::new, CONSTRUCTOR);
    public static final Supplier<BlockEntityType<StorageMonitorBlockEntity>> STORAGE_MONITOR_BE = ModRegistry.blockEntity("storage_monitor", StorageMonitorBlockEntity::new, STORAGE_MONITOR);
    public static final Supplier<BlockEntityType<SignalEmitterBlockEntity>> SIGNAL_EMITTER_BE = ModRegistry.blockEntity("signal_emitter", SignalEmitterBlockEntity::new, SIGNAL_EMITTER);
    public static final Supplier<BlockEntityType<WirelessTransmitterBlockEntity>> WIRELESS_TRANSMITTER_BE = ModRegistry.blockEntity("wireless_transmitter", WirelessTransmitterBlockEntity::new, WIRELESS_TRANSMITTER);

    // -- menus ---------------------------------------------------------------------------------------
    public static final Supplier<MenuType<TerminalMenu>> TERMINAL_MENU = ModRegistry.menu("terminal", TerminalMenu::client);
    public static final Supplier<MenuType<CraftingTerminalMenu>> CRAFTING_TERMINAL_MENU = ModRegistry.menu("crafting_terminal", CraftingTerminalMenu::client);
    public static final Supplier<MenuType<DriveMenu>> DRIVE_MENU = ModRegistry.menu("drive", DriveMenu::client);
    public static final Supplier<MenuType<BusMenu>> BUS_MENU = ModRegistry.menu("bus", BusMenu::client);
    public static final Supplier<MenuType<SignalEmitterMenu>> SIGNAL_EMITTER_MENU = ModRegistry.menu("signal_emitter", SignalEmitterMenu::client);
    public static final Supplier<MenuType<CoreMenu>> CORE_MENU = ModRegistry.menu("core", CoreMenu::client);

    private NetworkContent() {
    }

    private static BlockBehaviour.Properties device() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6.0f).sound(SoundType.NETHERITE_BLOCK);
    }

    private static int screenLight(net.minecraft.world.level.block.state.BlockState state) {
        return state.getValue(OrientedDeviceBlock.ACTIVE) ? 7 : 0;
    }

    public static void init() {
        NetworkConfig.init();
        NetworkManager.init();
        NetworkPackets.init();
        CraftingTransfer.init();
        ModCapabilities.exposeEnergy(NEXUS_CORE_BE);
        ModCapabilities.exposeItems(NETWORK_PORT_BE);
        ModCapabilities.exposeFluids(NETWORK_PORT_BE);
    }
}
