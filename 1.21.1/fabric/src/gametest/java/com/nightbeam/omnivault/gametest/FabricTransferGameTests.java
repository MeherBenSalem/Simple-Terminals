package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.CraftingManager;
import com.nightbeam.omnivault.autocraft.block.PatternProviderBlockEntity;
import com.nightbeam.omnivault.autocraft.job.CraftingJob;
import com.nightbeam.omnivault.autocraft.menu.PatternTerminalMenu;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.autocraft.pattern.PatternItem;
import com.nightbeam.omnivault.drawer.AbstractDrawerBlockEntity;
import com.nightbeam.omnivault.drawer.DrawerContent;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.DirectionalDeviceBlock;
import com.nightbeam.omnivault.network.block.DriveBlockEntity;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.network.cell.CellTier;
import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.physical.vault.VaultTier;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.ItemKey;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Fabric only: our storage adapters under Fabric's transactions, where callers routinely test a
 * move, abort it and do it for real. Nothing may be lost or duplicated on the way.
 */
public class FabricTransferGameTests {
    private static final BlockPos CORE = new BlockPos(1, 1, 4);
    private static final ItemVariant COBBLE = ItemVariant.of(Items.COBBLESTONE);

    private static void spine(GameTestHelper helper) {
        helper.setBlock(CORE, NetworkContent.NEXUS_CORE.get());
        for (int x = 2; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), NetworkContent.NEXUS_CABLE.get());
        }
    }

    private static StorageNetwork network(GameTestHelper helper) {
        return ((NexusCoreBlockEntity) helper.getBlockEntity(CORE)).network();
    }

    private static void drive(GameTestHelper helper, BlockPos pos, ItemStack cell) {
        helper.setBlock(pos, NetworkContent.DRIVE.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        ((DriveBlockEntity) helper.getBlockEntity(pos)).cells().setItem(0, cell);
    }

    private static Storage<ItemVariant> items(GameTestHelper helper, BlockPos pos) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(storage != null, "no Fabric item storage at " + pos);
        return storage;
    }

    private static long count(Storage<ItemVariant> storage, ItemVariant variant) {
        long total = 0;
        for (var view : storage) {
            if (view.getResource().equals(variant)) {
                total += view.getAmount();
            }
        }
        return total;
    }

    /** Fabric wraps errors during a move in a generic crash; report what actually went wrong. */
    private static long move(Storage<ItemVariant> from, Storage<ItemVariant> to, java.util.function.Predicate<ItemVariant> filter,
                             long maxAmount, @org.jetbrains.annotations.Nullable Transaction transaction) {
        try {
            return StorageUtil.move(from, to, filter, maxAmount, transaction);
        } catch (RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            StackTraceElement[] trace = root.getStackTrace();
            throw new GameTestAssertException("move failed: " + root + (trace.length > 0 ? " at " + trace[0] : ""));
        }
    }

    private static void noDroppedItems(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "nothing should be dropped");
    }

    @GameTest(template = Templates.FLOOR)
    public static void abortedTransactionsLeaveVaultsAsTheyWere(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, PhysicalContent.vault(VaultTier.COPPER));
        Storage<ItemVariant> vault = items(helper, pos);
        try (Transaction tx = Transaction.openOuter()) {
            helper.assertTrue(vault.insert(COBBLE, 100, tx) == 100, "vault takes 100 cobblestone");
            helper.assertTrue(count(vault, COBBLE) == 100, "the change shows inside the transaction");
        }
        helper.assertTrue(count(vault, COBBLE) == 0, "aborting undid the insert");
        try (Transaction tx = Transaction.openOuter()) {
            vault.insert(COBBLE, 100, tx);
            tx.commit();
        }
        try (Transaction tx = Transaction.openOuter()) {
            helper.assertTrue(vault.extract(COBBLE, 40, tx) == 40, "vault gives 40");
        }
        helper.assertTrue(count(vault, COBBLE) == 100, "aborting undid the extract");
        // A real move, the way a Fabric hopper does it: a test extraction that is aborted, then the move.
        BlockPos chestPos = new BlockPos(6, 1, 4);
        helper.setBlock(chestPos, Blocks.CHEST);
        long moved = move(vault, items(helper, chestPos), variant -> true, 64, null);
        helper.assertTrue(moved == 64, "moved " + moved);
        helper.assertTrue(count(vault, COBBLE) == 36 && ((ChestBlockEntity) helper.getBlockEntity(chestPos)).getItem(0).getCount() == 64,
                "36 stay in the vault, 64 reach the chest");
        noDroppedItems(helper);
        helper.succeed();
    }

    @GameTest(template = Templates.FLOOR)
    public static void voidDrawersKeepTheirItemsWhenAMoveIsAborted(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, DrawerContent.DRAWER_1X1.get());
        AbstractDrawerBlockEntity drawer = (AbstractDrawerBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(drawer.installUpgrade(new ItemStack(DrawerContent.VOID_UPGRADE.get())), "void upgrade installed");
        Storage<ItemVariant> storage = items(helper, pos);
        long capacity = storage.iterator().next().getCapacity();
        try (Transaction tx = Transaction.openOuter()) {
            storage.insert(COBBLE, capacity, tx);
            tx.commit();
        }
        // Full: a void drawer accepts more and destroys it. Aborting that must not take stored items away.
        try (Transaction tx = Transaction.openOuter()) {
            helper.assertTrue(storage.insert(COBBLE, 500, tx) == 500, "a full void drawer still accepts");
        }
        helper.assertTrue(count(storage, COBBLE) == capacity, "still full after the aborted insert: " + count(storage, COBBLE));
        helper.succeed();
    }

    @GameTest(template = Templates.FLOOR)
    public static void networkPortMovesNeverDuplicateThroughStorageBuses(GameTestHelper helper) {
        spine(helper);
        // The network's only storage is a chest behind a Storage Bus: everything the port hands out
        // comes from another block's Fabric storage, which rolls back on its own.
        helper.setBlock(new BlockPos(5, 1, 5), NetworkContent.STORAGE_BUS.get().defaultBlockState().setValue(DirectionalDeviceBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.CHEST);
        ChestBlockEntity source = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 6));
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        BlockPos portPos = new BlockPos(6, 1, 5);
        helper.setBlock(portPos, NetworkContent.NETWORK_PORT.get());
        BlockPos targetPos = new BlockPos(6, 1, 7);
        helper.setBlock(targetPos, Blocks.CHEST);
        helper.runAfterDelay(25, () -> {
            Storage<ItemVariant> port = items(helper, portPos);
            helper.assertTrue(count(port, COBBLE) == 40, "the port shows the chest's cobblestone");
            for (int i = 0; i < 3; i++) {
                try (Transaction tx = Transaction.openOuter()) {
                    helper.assertTrue(port.extract(COBBLE, 10, tx) == 10, "the port offers 10");
                }
            }
            helper.assertTrue(source.getItem(0).getCount() == 40, "aborted extractions changed nothing");
            long moved = move(port, items(helper, targetPos), variant -> true, Long.MAX_VALUE, null);
            ChestBlockEntity target = (ChestBlockEntity) helper.getBlockEntity(targetPos);
            helper.assertTrue(moved == 40, "moved " + moved);
            helper.assertTrue(source.getItem(0).isEmpty() && target.getItem(0).getCount() == 40,
                    "all 40 moved once: source " + source.getItem(0) + ", target " + target.getItem(0));
            helper.assertTrue(network(helper).storage().getCount(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 0, "the network is empty");
            noDroppedItems(helper);
            helper.succeed();
        });
    }

    @GameTest(template = Templates.FLOOR)
    public static void networkPortInsertsRollBackAndRespectCapacity(GameTestHelper helper) {
        spine(helper);
        drive(helper, new BlockPos(3, 1, 5), new ItemStack(NetworkContent.CELLS.get(CellTier.T4K).get()));
        BlockPos portPos = new BlockPos(6, 1, 5);
        helper.setBlock(portPos, NetworkContent.NETWORK_PORT.get());
        helper.runAfterDelay(5, () -> {
            Storage<ItemVariant> port = items(helper, portPos);
            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            ItemVariant ironVariant = ItemVariant.of(Items.IRON_INGOT);
            try (Transaction tx = Transaction.openOuter()) {
                helper.assertTrue(port.insert(ironVariant, 50, tx) == 50, "the port takes 50");
                helper.assertTrue(network(helper).storage().getCount(iron) == 50, "the change shows inside the transaction");
            }
            helper.assertTrue(network(helper).storage().getCount(iron) == 0, "aborting took the insert back");
            // Two inserts in one transaction must not promise more than a 4k cell holds.
            try (Transaction tx = Transaction.openOuter()) {
                long first = port.insert(ironVariant, 3000, tx);
                long second = port.insert(ironVariant, 3000, tx);
                helper.assertTrue(first == 3000 && second == 1096, "promised " + first + " + " + second);
                tx.commit();
            }
            helper.assertTrue(network(helper).storage().getCount(iron) == 4096, "4096 stored on commit");
            noDroppedItems(helper);
            helper.succeed();
        });
    }

    @GameTest(template = Templates.FLOOR)
    public static void craftingJobsOnlyGetItemsFromCommittedTransactions(GameTestHelper helper) {
        spine(helper);
        drive(helper, new BlockPos(3, 1, 5), new ItemStack(NetworkContent.CELLS.get(CellTier.T64K).get()));
        helper.setBlock(new BlockPos(2, 1, 5), AutocraftContent.CRAFTING_PROCESSOR.get());
        // The "machine" is a chest: a processing pattern turns 2 cobblestone into 1 gravel.
        BlockPos providerPos = new BlockPos(5, 1, 5);
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.CHEST);
        helper.setBlock(providerPos, AutocraftContent.PATTERN_PROVIDER.get().defaultBlockState().setValue(DirectionalDeviceBlock.FACING, Direction.SOUTH));
        ((PatternProviderBlockEntity) helper.getBlockEntity(providerPos)).patterns().setItem(0,
                PatternItem.encode(PatternData.processing(List.of(new ItemStack(Items.COBBLESTONE, 2)), List.of(new ItemStack(Items.GRAVEL)))));
        helper.runAfterDelay(5, () -> {
            network(helper).storage().insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 2, false);
            CraftingManager.StartResult result = CraftingManager.of(network(helper)).start(ItemKey.of(new ItemStack(Items.GRAVEL)), 1, null);
            helper.assertTrue(result.started(), "job starts, error " + result.plan().error());
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(result.job().status() == CraftingJob.Status.WAITING, "job waits for the machine, got " + result.job().status());
                Storage<ItemVariant> provider = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(providerPos), Direction.SOUTH);
                helper.assertTrue(provider != null, "the provider exposes Fabric storage");
                ItemVariant gravel = ItemVariant.of(Items.GRAVEL);
                try (Transaction tx = Transaction.openOuter()) {
                    helper.assertTrue(provider.insert(gravel, 1, tx) == 1, "the provider takes the result");
                }
                helper.assertTrue(result.job().status() == CraftingJob.Status.WAITING, "an aborted insert doesn't feed the job");
                helper.assertTrue(network(helper).storage().getCount(ItemKey.of(new ItemStack(Items.GRAVEL))) == 0, "and leaves no gravel behind");
                try (Transaction tx = Transaction.openOuter()) {
                    provider.insert(gravel, 1, tx);
                    tx.commit();
                }
                helper.succeedWhen(() -> {
                    helper.assertTrue(CraftingManager.of(network(helper)).busyProcessors().isEmpty(), "the job finished");
                    helper.assertTrue(network(helper).storage().getCount(ItemKey.of(new ItemStack(Items.GRAVEL))) == 1, "one gravel in the network");
                });
            });
        });
    }

    @GameTest(template = Templates.FLOOR)
    public static void networkPortFluidsMoveInWholeMillibuckets(GameTestHelper helper) {
        spine(helper);
        drive(helper, new BlockPos(3, 1, 5), new ItemStack(NetworkContent.FLUID_CELLS.get(CellTier.T4K).get()));
        BlockPos portPos = new BlockPos(6, 1, 5);
        helper.setBlock(portPos, NetworkContent.NETWORK_PORT.get());
        helper.runAfterDelay(5, () -> {
            Storage<FluidVariant> port = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(portPos), Direction.UP);
            helper.assertTrue(port != null, "the port exposes Fabric fluid storage");
            FluidVariant water = FluidVariant.of(Fluids.WATER);
            FluidKey waterKey = FluidKey.of(Fluids.WATER);
            try (Transaction tx = Transaction.openOuter()) {
                helper.assertTrue(port.insert(water, FluidConstants.BUCKET, tx) == FluidConstants.BUCKET, "a bucket goes in");
                helper.assertTrue(port.insert(water, 80, tx) == 0, "less than a millibucket doesn't");
                tx.commit();
            }
            helper.assertTrue(network(helper).fluidStorage().getAmount(waterKey) == 1000, "1000 mB stored");
            try (Transaction tx = Transaction.openOuter()) {
                helper.assertTrue(port.extract(water, FluidConstants.BUCKET / 2, tx) == FluidConstants.BUCKET / 2, "half a bucket comes out");
                tx.commit();
            }
            helper.assertTrue(network(helper).fluidStorage().getAmount(waterKey) == 500, "500 mB left");
            helper.succeed();
        });
    }

    @GameTest(template = Templates.FLOOR)
    public static void menusRebuildTheirOpeningDataOnTheClient(GameTestHelper helper) {
        // The client half of a menu reads a buffer rebuilt from the bytes the server sent.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        @SuppressWarnings("unchecked")
        ExtendedScreenHandlerType<PatternTerminalMenu, byte[]> type =
                (ExtendedScreenHandlerType<PatternTerminalMenu, byte[]>) AutocraftContent.PATTERN_TERMINAL_MENU.get();
        PatternTerminalMenu processing = type.create(7, player.getInventory(), new byte[]{1});
        PatternTerminalMenu crafting = type.create(8, player.getInventory(), new byte[]{0});
        helper.assertTrue(processing.processing() && !crafting.processing(), "the menus read their mode from the data");
        helper.succeed();
    }
}
