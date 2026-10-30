package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.content.CoreContent;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.CableBlock;
import com.nightbeam.omnivault.network.block.DirectionalDeviceBlock;
import com.nightbeam.omnivault.network.block.DriveBlockEntity;
import com.nightbeam.omnivault.network.block.ExportBusBlockEntity;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.network.block.SignalEmitterBlock;
import com.nightbeam.omnivault.network.block.SignalEmitterBlockEntity;
import com.nightbeam.omnivault.network.cell.CellTier;
import com.nightbeam.omnivault.network.cell.StorageCellItem;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(OmniVault.MOD_ID)
@PrefixGameTestTemplate(false)
public class NetworkGameTests {
    private static final BlockPos CORE = new BlockPos(1, 1, 4);

    /** Core at (1,1,4) with a cable run east along z = 4 to x = 7. */
    private static void spine(GameTestHelper helper) {
        helper.setBlock(CORE, NetworkContent.NEXUS_CORE.get());
        for (int x = 2; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), NetworkContent.NEXUS_CABLE.get());
        }
    }

    private static StorageNetwork network(GameTestHelper helper) {
        if (!(helper.getBlockEntity(CORE) instanceof NexusCoreBlockEntity core)) {
            throw new IllegalStateException("No core");
        }
        return core.network();
    }

    private static DriveBlockEntity driveWithCell(GameTestHelper helper, BlockPos pos, CellTier tier) {
        // Fronts don't carry the network, so face the drive away from the cable spine.
        helper.setBlock(pos, NetworkContent.DRIVE.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        DriveBlockEntity drive = (DriveBlockEntity) helper.getBlockEntity(pos);
        drive.cells().setItem(0, new ItemStack(NetworkContent.CELLS.get(tier).get()));
        return drive;
    }

    private static BlockPos facing(GameTestHelper helper, BlockPos relative, net.minecraft.world.level.block.Block block, Direction direction) {
        helper.setBlock(relative, block.defaultBlockState().setValue(DirectionalDeviceBlock.FACING, direction));
        return relative;
    }

    @GameTest(template = "floor_9x9")
    public static void cellsStoreAndReturnItems(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        helper.runAfterDelay(5, () -> {
            StorageNetwork network = network(helper);
            helper.assertTrue(network.isActive(), "network should be online");
            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            helper.assertTrue(network.storage().insert(iron, 5000, false) == 4096, "a 4k cell holds 4096 items");
            helper.assertTrue(network.storage().getCount(iron) == 4096, "network reports the stored count");
            helper.assertTrue(network.storage().extract(iron, 100, false) == 100, "extract returns what was asked");
            helper.assertTrue(network.storage().getCount(iron) == 3996, "count drops after extracting");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void cellTypeLimitIsEnforced(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T64K);
        helper.runAfterDelay(5, () -> {
            StorageNetwork network = network(helper);
            int accepted = 0;
            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                if (network.storage().insert(ItemKey.of(new ItemStack(item)), 1, false) == 1) {
                    accepted++;
                }
                if (accepted > 200) {
                    break;
                }
            }
            helper.assertTrue(accepted == 128, "a cell holds 128 types by default, got " + accepted);
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void cellContentsSurviveRemovalAndReinsertion(GameTestHelper helper) {
        spine(helper);
        DriveBlockEntity drive = driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T16K);
        helper.runAfterDelay(5, () -> {
            ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));
            network(helper).storage().insert(diamond, 777, false);
            ItemStack cell = drive.cells().removeItemNoUpdate(0);
            drive.cells().setChanged();
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(network(helper).storage().getCount(diamond) == 0, "removed cell no longer counts");
                drive.cells().setItem(1, cell);
                // The drive mirrors usage onto cell stacks every 10 ticks.
                helper.runAfterDelay(12, () -> {
                    helper.assertTrue(network(helper).storage().getCount(diamond) == 777, "reinserted cell brings its items back");
                    helper.assertTrue(StorageCellItem.usage(drive.cells().getItem(1)).stored() == 777, "usage is mirrored on the stack");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void storageBusSeesAndUsesChest(GameTestHelper helper) {
        spine(helper);
        // Bus at (5,1,5) facing south into a chest at (5,1,6); the cable at (5,1,4) touches its back.
        facing(helper, new BlockPos(5, 1, 5), NetworkContent.STORAGE_BUS.get(), Direction.SOUTH);
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 6));
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        helper.runAfterDelay(25, () -> {
            StorageNetwork network = network(helper);
            ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
            helper.assertTrue(network.storage().getCount(cobble) == 40, "storage bus exposes the chest");
            helper.assertTrue(network.storage().extract(cobble, 15, false) == 15, "network takes from the chest");
            helper.assertTrue(chest.getItem(0).getCount() == 25, "chest lost what the network took");
            helper.assertTrue(network.storage().insert(ItemKey.of(new ItemStack(Items.STICK)), 10, false) == 10, "network stores into the chest");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void importBusPullsIntoCells(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        facing(helper, new BlockPos(5, 1, 5), NetworkContent.IMPORT_BUS.get(), Direction.SOUTH);
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 6));
        chest.setItem(0, new ItemStack(Items.REDSTONE, 16));
        helper.succeedWhen(() -> {
            helper.assertTrue(chest.isEmpty(), "chest should be emptied");
            helper.assertTrue(network(helper).storage().getCount(ItemKey.of(new ItemStack(Items.REDSTONE))) == 16, "redstone in network");
        });
    }

    @GameTest(template = "floor_9x9")
    public static void exportBusPushesFilteredItems(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        facing(helper, new BlockPos(5, 1, 5), NetworkContent.EXPORT_BUS.get(), Direction.SOUTH);
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.CHEST);
        ExportBusBlockEntity bus = (ExportBusBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 5));
        bus.filter().setItem(0, new ItemStack(Items.GOLD_INGOT));
        bus.upgrades().setItem(0, new ItemStack(CoreContent.STACK_UPGRADE.get()));
        helper.runAfterDelay(5, () -> {
            StorageNetwork network = network(helper);
            network.storage().insert(ItemKey.of(new ItemStack(Items.GOLD_INGOT)), 12, false);
            network.storage().insert(ItemKey.of(new ItemStack(Items.COAL)), 12, false);
        });
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 6));
        helper.succeedWhen(() -> {
            int gold = 0;
            int coal = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(Items.GOLD_INGOT)) {
                    gold += chest.getItem(i).getCount();
                }
                if (chest.getItem(i).is(Items.COAL)) {
                    coal += chest.getItem(i).getCount();
                }
            }
            helper.assertTrue(gold == 12, "all gold exported, got " + gold);
            helper.assertTrue(coal == 0, "coal is not in the filter");
        });
    }

    @GameTest(template = "floor_9x9")
    public static void networkPortAcceptsAndProvidesItems(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        helper.setBlock(new BlockPos(6, 1, 5), NetworkContent.NETWORK_PORT.get());
        helper.runAfterDelay(5, () -> {
            SlotAccess port = Services.PLATFORM.findSlotAccess(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 5)), Direction.UP).orElseThrow();
            ItemStack rest = port.insertItem(0, new ItemStack(Items.EMERALD, 30), false);
            helper.assertTrue(rest.isEmpty(), "port accepts items");
            ItemKey emerald = ItemKey.of(new ItemStack(Items.EMERALD));
            helper.assertTrue(network(helper).storage().getCount(emerald) == 30, "items land in the network");
            int slot = -1;
            for (int i = 0; i < port.getSlots(); i++) {
                if (port.getStackInSlot(i).is(Items.EMERALD)) {
                    slot = i;
                }
            }
            helper.assertTrue(slot >= 0, "port lists the emeralds as a slot");
            helper.assertTrue(port.extractItem(slot, 64, false).getCount() == 30, "port hands out items");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void signalEmitterFollowsThreshold(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        // Emitter above the cable at (4,1,4), pointing up (its back sits on the cable).
        helper.setBlock(new BlockPos(4, 2, 4), NetworkContent.SIGNAL_EMITTER.get().defaultBlockState()
                .setValue(DirectionalDeviceBlock.FACING, Direction.UP));
        SignalEmitterBlockEntity emitter = (SignalEmitterBlockEntity) helper.getBlockEntity(new BlockPos(4, 2, 4));
        emitter.filter().setItem(0, new ItemStack(Items.WHEAT));
        emitter.setThreshold(10);
        helper.runAfterDelay(25, () -> {
            helper.assertBlockProperty(new BlockPos(4, 2, 4), SignalEmitterBlock.POWERED, false);
            network(helper).storage().insert(ItemKey.of(new ItemStack(Items.WHEAT)), 10, false);
            helper.runAfterDelay(25, () -> {
                helper.assertBlockProperty(new BlockPos(4, 2, 4), SignalEmitterBlock.POWERED, true);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void twoCoresConflict(GameTestHelper helper) {
        spine(helper);
        helper.setBlock(new BlockPos(7, 1, 5), NetworkContent.NEXUS_CORE.get());
        helper.setBlock(new BlockPos(7, 1, 4), NetworkContent.NEXUS_CABLE.get());
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(network(helper).state() == StorageNetwork.State.CONFLICT, "two cores on one network conflict");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void differentCableColoursDoNotConnect(GameTestHelper helper) {
        helper.setBlock(CORE, NetworkContent.NEXUS_CORE.get());
        helper.setBlock(new BlockPos(2, 1, 4), NetworkContent.NEXUS_CABLE.get().defaultBlockState().setValue(CableBlock.COLOR, CableBlock.CableColor.of(DyeColor.RED)));
        helper.setBlock(new BlockPos(3, 1, 4), NetworkContent.NEXUS_CABLE.get().defaultBlockState().setValue(CableBlock.COLOR, CableBlock.CableColor.of(DyeColor.BLUE)));
        driveWithCell(helper, new BlockPos(4, 1, 4), CellTier.T4K);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(network(helper).capacity() == 0, "the drive behind a blue cable is not on the red network");
            helper.setBlock(new BlockPos(3, 1, 4), NetworkContent.NEXUS_CABLE.get().defaultBlockState().setValue(CableBlock.COLOR, CableBlock.CableColor.of(DyeColor.RED)));
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(network(helper).capacity() == 4096, "recoloured cable joins the drive");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void breakingCableSplitsNetwork(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(6, 1, 5), CellTier.T4K);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(network(helper).capacity() == 4096, "drive joins");
            helper.setBlock(new BlockPos(4, 1, 4), Blocks.AIR);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(network(helper).capacity() == 0, "drive leaves when the cable breaks");
                DriveBlockEntity drive = (DriveBlockEntity) helper.getBlockEntity(new BlockPos(6, 1, 5));
                helper.assertTrue(drive.getNetwork() == null, "drive has no network");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void craftingTerminalTransfersAndRefills(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        helper.setBlock(new BlockPos(5, 1, 5), NetworkContent.CRAFTING_TERMINAL.get().defaultBlockState()
                .setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        helper.runAfterDelay(5, () -> {
            StorageNetwork network = network(helper);
            ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
            network.storage().insert(planks, 64, false);
            net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().clearContent();
            var terminal = (com.nightbeam.omnivault.network.block.TerminalBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 5));
            var menu = new com.nightbeam.omnivault.network.menu.CraftingTerminalMenu(1, player.getInventory(), terminal);
            player.containerMenu = menu;
            // A recipe viewer asks for a crafting table: planks in the top-left 2x2.
            java.util.List<java.util.List<ItemStack>> slots = new java.util.ArrayList<>();
            for (int i = 0; i < 9; i++) {
                slots.add(i == 0 || i == 1 || i == 3 || i == 4 ? java.util.List.of(new ItemStack(Items.OAK_PLANKS)) : java.util.List.of());
            }
            menu.transferRecipe(player, slots);
            helper.assertTrue(menu.getSlot(menu.resultIndex()).getItem().is(Items.CRAFTING_TABLE), "transfer sets up a crafting table recipe");
            helper.assertTrue(network.storage().getCount(planks) == 60, "transfer took four planks from the network");
            // Shift-click the result: the grid refills from the network until the planks run out.
            menu.clicked(menu.resultIndex(), 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
            helper.assertTrue(player.getInventory().countItem(Items.CRAFTING_TABLE) == 16, "16 tables from 64 planks, got "
                    + player.getInventory().countItem(Items.CRAFTING_TABLE));
            helper.assertTrue(network.storage().getCount(planks) == 0, "all planks used");
            helper.succeed();
        });
    }

    private static DriveBlockEntity driveWithFluidCell(GameTestHelper helper, BlockPos pos, CellTier tier) {
        DriveBlockEntity drive = driveWithCell(helper, pos, CellTier.T4K);
        drive.cells().setItem(0, new ItemStack(NetworkContent.FLUID_CELLS.get(tier).get()));
        return drive;
    }

    @GameTest(template = "floor_9x9")
    public static void fluidCellsStoreFluids(GameTestHelper helper) {
        spine(helper);
        driveWithFluidCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        helper.runAfterDelay(5, () -> {
            var fluids = network(helper).fluidStorage();
            var water = com.nightbeam.omnivault.storage.FluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
            helper.assertTrue(fluids.insert(water, 2_000_000, false) == 1_024_000, "a 4k fluid cell holds 1024 buckets");
            helper.assertTrue(fluids.extract(water, 24_000, false) == 24_000, "fluid comes back out");
            helper.assertTrue(fluids.getAmount(water) == 1_000_000, "remaining amount is tracked");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void importBusDrainsCauldron(GameTestHelper helper) {
        spine(helper);
        driveWithFluidCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        facing(helper, new BlockPos(5, 1, 5), NetworkContent.IMPORT_BUS.get(), Direction.SOUTH);
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        var water = com.nightbeam.omnivault.storage.FluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.CAULDRON, new BlockPos(5, 1, 6));
            helper.assertTrue(network(helper).fluidStorage().getAmount(water) == 1000, "a bucket of water reached the network");
        });
    }

    @GameTest(template = "floor_9x9")
    public static void networkPortExposesFluids(GameTestHelper helper) {
        spine(helper);
        driveWithFluidCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        helper.setBlock(new BlockPos(6, 1, 5), NetworkContent.NETWORK_PORT.get());
        helper.runAfterDelay(5, () -> {
            var tanks = Services.PLATFORM.findFluidAccess(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 5)), Direction.UP).orElseThrow();
            var lava = com.nightbeam.omnivault.storage.FluidKey.of(net.minecraft.world.level.material.Fluids.LAVA);
            helper.assertTrue(tanks.fill(lava, 3000, false) == 3000, "port accepts fluid");
            helper.assertTrue(network(helper).fluidStorage().getAmount(lava) == 3000, "fluid lands in the network");
            helper.assertTrue(tanks.drain(lava, 1000, false) == 1000, "port hands fluid out");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void terminalFillsAndEmptiesBuckets(GameTestHelper helper) {
        spine(helper);
        driveWithFluidCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        helper.setBlock(new BlockPos(5, 1, 5), NetworkContent.TERMINAL.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        helper.runAfterDelay(5, () -> {
            var water = com.nightbeam.omnivault.storage.FluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
            network(helper).fluidStorage().insert(water, 5000, false);
            net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
            // Menus only accept clicks from players within reach of the terminal.
            var standAt = helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 6.5));
            player.moveTo(standAt.x, standAt.y, standAt.z);
            var terminal = (com.nightbeam.omnivault.network.block.TerminalBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 5));
            var menu = new com.nightbeam.omnivault.network.menu.TerminalMenu(NetworkContent.TERMINAL_MENU.get(), 1, player.getInventory(), terminal);
            player.containerMenu = menu;
            menu.setCarried(new ItemStack(Items.BUCKET));
            menu.handleFluidClick(player, water);
            helper.assertTrue(menu.getCarried().is(Items.WATER_BUCKET), "empty bucket was filled");
            helper.assertTrue(network(helper).fluidStorage().getAmount(water) == 4000, "a bucket left the network");
            menu.handleFluidClick(player, null);
            helper.assertTrue(menu.getCarried().is(Items.BUCKET), "full bucket was emptied");
            helper.assertTrue(network(helper).fluidStorage().getAmount(water) == 5000, "the bucket came back");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void destructorBreaksBlocksIntoTheNetwork(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        facing(helper, new BlockPos(5, 1, 5), NetworkContent.DESTRUCTOR.get(), Direction.SOUTH);
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 1, 6));
            helper.assertTrue(network(helper).storage().getCount(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 1, "stone mined into cobblestone");
        });
    }

    @GameTest(template = "floor_9x9")
    public static void destructorLeavesContainersAlone(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        facing(helper, new BlockPos(5, 1, 5), NetworkContent.DESTRUCTOR.get(), Direction.SOUTH);
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.CHEST);
        helper.runAfterDelay(45, () -> {
            helper.assertBlockPresent(Blocks.CHEST, new BlockPos(5, 1, 6));
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void constructorPlacesBlocksFromTheNetwork(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        facing(helper, new BlockPos(5, 1, 5), NetworkContent.CONSTRUCTOR.get(), Direction.SOUTH);
        var constructor = (com.nightbeam.omnivault.network.block.ConstructorBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 5));
        constructor.filter().setItem(0, new ItemStack(Items.OAK_LOG));
        // The template's floor occupies relative y = 1, so clear the space the constructor fills.
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.AIR);
        helper.runAfterDelay(5, () -> network(helper).storage().insert(ItemKey.of(new ItemStack(Items.OAK_LOG)), 3, false));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(5, 1, 6));
            helper.assertTrue(network(helper).storage().getCount(ItemKey.of(new ItemStack(Items.OAK_LOG))) == 2, "one log was used");
        });
    }

    @GameTest(template = "floor_9x9")
    public static void storageMonitorTracksCount(GameTestHelper helper) {
        spine(helper);
        driveWithCell(helper, new BlockPos(3, 1, 5), CellTier.T4K);
        helper.setBlock(new BlockPos(5, 1, 5), NetworkContent.STORAGE_MONITOR.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        var monitor = (com.nightbeam.omnivault.network.block.StorageMonitorBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 5));
        monitor.setDisplayed(new ItemStack(Items.GLOWSTONE_DUST));
        helper.runAfterDelay(5, () -> network(helper).storage().insert(ItemKey.of(new ItemStack(Items.GLOWSTONE_DUST)), 321, false));
        helper.succeedWhen(() -> helper.assertTrue(monitor.count() == 321, "monitor shows the network count"));
    }
}
