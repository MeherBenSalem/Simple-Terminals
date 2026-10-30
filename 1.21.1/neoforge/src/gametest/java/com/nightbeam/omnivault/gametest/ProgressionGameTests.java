package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.block.AssemblerBlockEntity;
import com.nightbeam.omnivault.content.CoreContent;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.DriveBlockEntity;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.network.block.QuantumBridgeBlockEntity;
import com.nightbeam.omnivault.network.cell.CellTier;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Early game (Storage Hub, Inventory Cable) and late game (Singularity cells, Quantum Bridge, Quantum Assembler). */
@GameTestHolder(OmniVault.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProgressionGameTests {
    private static final ItemKey COBBLE = ItemKey.of(new ItemStack(Items.COBBLESTONE));

    private static StorageNetwork networkAt(GameTestHelper helper, BlockPos pos) {
        return ((NexusCoreBlockEntity) helper.getBlockEntity(pos)).network();
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos, int cobble) {
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, cobble));
        return chest;
    }

    @GameTest(template = "floor_9x9")
    public static void storageHubJoinsTouchingChests(GameTestHelper helper) {
        BlockPos hub = new BlockPos(2, 2, 4);
        helper.setBlock(hub, NetworkContent.STORAGE_HUB.get());
        chest(helper, new BlockPos(2, 2, 3), 10);
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 4), NetworkContent.INVENTORY_CABLE.get());
        }
        chest(helper, new BlockPos(5, 2, 5), 20);
        helper.runAfterDelay(25, () -> {
            StorageNetwork network = networkAt(helper, hub);
            helper.assertTrue(network.isBasic() && network.isActive(), "a hub runs a basic network");
            helper.assertTrue(network.storage().getCount(COBBLE) == 30, "both chests counted, got " + network.storage().getCount(COBBLE));
            helper.assertTrue(network.storage().extract(COBBLE, 25, false) == 25, "items come out of the chests");
            // A chest placed later next to the cable joins too.
            chest(helper, new BlockPos(4, 2, 3), 7);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(network.storage().getCount(COBBLE) == 12, "new chest joined, got " + network.storage().getCount(COBBLE));
                helper.succeed();
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void doubleChestCountsOnce(GameTestHelper helper) {
        BlockPos hub = new BlockPos(2, 2, 4);
        helper.setBlock(hub, NetworkContent.STORAGE_HUB.get());
        helper.setBlock(new BlockPos(3, 2, 4), NetworkContent.INVENTORY_CABLE.get());
        // A double chest touching both the hub and the cable.
        helper.setBlock(new BlockPos(2, 2, 5), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        helper.setBlock(new BlockPos(3, 2, 5), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        ((ChestBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 5))).setItem(0, new ItemStack(Items.COBBLESTONE, 9));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(networkAt(helper, hub).storage().getCount(COBBLE) == 9, "double chest counted once, got "
                    + networkAt(helper, hub).storage().getCount(COBBLE));
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void storageHubLimitsAdvancedFeatures(GameTestHelper helper) {
        BlockPos hub = new BlockPos(2, 2, 4);
        helper.setBlock(hub, NetworkContent.STORAGE_HUB.get());
        helper.setBlock(new BlockPos(3, 2, 4), NetworkContent.DRIVE.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        ((DriveBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 4))).cells().setItem(0, new ItemStack(NetworkContent.CELLS.get(CellTier.T4K).get()));
        helper.runAfterDelay(5, () -> {
            StorageNetwork network = networkAt(helper, hub);
            helper.assertTrue(network.storage().insert(COBBLE, 10, false) == 0, "cells don't work on a Storage Hub network");
            helper.assertFalse(network.supportsAdvancedFeatures(), "no autocrafting or wireless on a hub");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void inventoryCableWorksOnNexusNetworks(GameTestHelper helper) {
        BlockPos core = new BlockPos(2, 2, 4);
        helper.setBlock(core, NetworkContent.NEXUS_CORE.get());
        helper.setBlock(new BlockPos(3, 2, 4), NetworkContent.NEXUS_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 4), NetworkContent.INVENTORY_CABLE.get());
        chest(helper, new BlockPos(4, 2, 5), 33);
        // A chest next to plain Nexus Cable is not included.
        chest(helper, new BlockPos(3, 2, 3), 50);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(networkAt(helper, core).storage().getCount(COBBLE) == 33, "only the inventory cable's chest joins, got "
                    + networkAt(helper, core).storage().getCount(COBBLE));
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void singularityCellHoldsBillions(GameTestHelper helper) {
        BlockPos core = new BlockPos(2, 2, 4);
        helper.setBlock(core, NetworkContent.NEXUS_CORE.get());
        helper.setBlock(new BlockPos(3, 2, 4), NetworkContent.DRIVE.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        ((DriveBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 4))).cells().setItem(0, new ItemStack(NetworkContent.CELLS.get(CellTier.SINGULARITY).get()));
        helper.runAfterDelay(5, () -> {
            long accepted = networkAt(helper, core).storage().insert(COBBLE, 3_000_000_000L, false);
            helper.assertTrue(accepted == 2_147_483_648L, "a singularity cell holds 2^31 items, took " + accepted);
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void quantumBridgeCrossesDimensions(GameTestHelper helper) {
        BlockPos core = new BlockPos(2, 2, 4);
        helper.setBlock(core, NetworkContent.NEXUS_CORE.get());
        helper.setBlock(new BlockPos(3, 2, 4), NetworkContent.QUANTUM_BRIDGE.get());
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos far = new BlockPos(16_000, 100, 16_000);
        nether.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        nether.setBlockAndUpdate(far, NetworkContent.QUANTUM_BRIDGE.get().defaultBlockState());
        BlockPos farDrive = far.east();
        nether.setBlockAndUpdate(farDrive, NetworkContent.DRIVE.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        ((DriveBlockEntity) nether.getBlockEntity(farDrive)).cells().setItem(0, new ItemStack(NetworkContent.CELLS.get(CellTier.T4K).get()));
        QuantumBridgeBlockEntity here = (QuantumBridgeBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 4));
        QuantumBridgeBlockEntity there = (QuantumBridgeBlockEntity) nether.getBlockEntity(far);
        here.setPartner(GlobalPos.of(Level.NETHER, far));
        there.setPartner(here.self());
        helper.runAfterDelay(10, () -> {
            StorageNetwork network = networkAt(helper, core);
            helper.assertTrue(network.capacity() == 4096, "the drive in the Nether joined through the bridge");
            helper.assertTrue(network.storage().insert(COBBLE, 100, false) == 100, "items stored across dimensions");
            // Unlinking splits the network again.
            com.nightbeam.omnivault.network.block.QuantumBridgeBlock.unlink(here);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(networkAt(helper, core).capacity() == 0, "unlinked bridge drops the far drive");
                nether.setBlockAndUpdate(farDrive, Blocks.AIR.defaultBlockState());
                nether.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
                nether.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void quantumAssemblerIsSixteenTimesFaster(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 2), AutocraftContent.ASSEMBLER.get());
        helper.setBlock(new BlockPos(4, 2, 2), AutocraftContent.QUANTUM_ASSEMBLER.get());
        int normal = ((AssemblerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 2))).craftsPerCycle();
        int quantum = ((AssemblerBlockEntity) helper.getBlockEntity(new BlockPos(4, 2, 2))).craftsPerCycle();
        helper.assertTrue(quantum == normal * 16, "quantum " + quantum + " vs normal " + normal);
        helper.succeed();
    }

    /** A plain (not sneaking) right-click with the main hand, through the server's real interaction path. */
    private static void rightClick(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    @GameTest(template = "floor_9x9")
    public static void plainRightClicksReachTheLinkCardAndOmniTool(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = new BlockPos(6, 2, 6);
        helper.setBlock(a, NetworkContent.QUANTUM_BRIDGE.get());
        helper.setBlock(b, NetworkContent.QUANTUM_BRIDGE.get());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NetworkContent.QUANTUM_LINK_CARD.get(), 2));
        rightClick(helper, player, a);
        ItemStack recorded = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.has(NetworkContent.BRIDGE_LINK.get())) {
                recorded = stack;
            }
        }
        helper.assertFalse(recorded.isEmpty(), "a plain right-click records the first bridge on a card");
        player.setItemInHand(InteractionHand.MAIN_HAND, recorded.copy());
        rightClick(helper, player, b);
        QuantumBridgeBlockEntity first = (QuantumBridgeBlockEntity) helper.getBlockEntity(a);
        QuantumBridgeBlockEntity second = (QuantumBridgeBlockEntity) helper.getBlockEntity(b);
        helper.assertTrue(first.linkedPartner() == second && second.linkedPartner() == first, "clicking the second bridge entangles the pair");
        // The OmniTool turns a terminal instead of opening it, and breaks the bridge link.
        BlockPos terminal = new BlockPos(4, 2, 4);
        helper.setBlock(terminal, NetworkContent.TERMINAL.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CoreContent.OMNI_TOOL.get()));
        rightClick(helper, player, terminal);
        helper.assertTrue(helper.getBlockState(terminal).getValue(OrientedDeviceBlock.FACING) != Direction.SOUTH, "the OmniTool rotated the terminal");
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "no terminal screen opened");
        rightClick(helper, player, a);
        helper.assertTrue(first.partner() == null && second.partner() == null, "the OmniTool unlinked both bridges");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9")
    public static void storageBusAndInventoryCableDoNotDoubleCount(GameTestHelper helper) {
        BlockPos hub = new BlockPos(2, 2, 4);
        helper.setBlock(hub, NetworkContent.STORAGE_HUB.get());
        helper.setBlock(new BlockPos(3, 2, 4), NetworkContent.INVENTORY_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 4), NetworkContent.INVENTORY_CABLE.get());
        // The chest touches the inventory cable at (3,2,4) and is faced by a storage bus fed from (4,2,4).
        chest(helper, new BlockPos(3, 2, 5), 15);
        helper.setBlock(new BlockPos(4, 2, 5), NetworkContent.STORAGE_BUS.get().defaultBlockState()
                .setValue(com.nightbeam.omnivault.network.block.DirectionalDeviceBlock.FACING, Direction.WEST));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(networkAt(helper, hub).storage().getCount(COBBLE) == 15, "chest counted once, got "
                    + networkAt(helper, hub).storage().getCount(COBBLE));
            helper.succeed();
        });
    }
}
