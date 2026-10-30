package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.drawer.AbstractDrawerBlockEntity;
import com.nightbeam.omnivault.drawer.CompactingDrawerBlockEntity;
import com.nightbeam.omnivault.drawer.DrawerBlock;
import com.nightbeam.omnivault.drawer.DrawerConfig;
import com.nightbeam.omnivault.drawer.DrawerContent;
import com.nightbeam.omnivault.drawer.DrawerContents;
import com.nightbeam.omnivault.drawer.DrawerControllerBlockEntity;
import com.nightbeam.omnivault.drawer.DrawerInteraction;
import com.nightbeam.omnivault.drawer.DrawerLayout;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(OmniVault.MOD_ID)
@PrefixGameTestTemplate(false)
public class DrawerGameTests {
    /** On top of the template floor, which occupies relative y = 1. */
    private static final BlockPos POS = new BlockPos(4, 2, 4);

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static SlotAccess handler(GameTestHelper helper, BlockPos pos) {
        SlotAccess handler = TestPlatform.INSTANCE.items(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
        if (handler == null) {
            throw new GameTestAssertException("No item handler at " + pos);
        }
        return handler;
    }

    private static <T extends BlockEntity> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        BlockEntity be = helper.getBlockEntity(pos);
        if (!type.isInstance(be)) {
            throw new GameTestAssertException("Expected " + type.getSimpleName() + " at " + pos + " but found " + be);
        }
        return type.cast(be);
    }

    private static void eq(long actual, long expected, String what) {
        if (actual != expected) {
            throw new GameTestAssertException(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void stack(ItemStack stack, Item item, int count, String what) {
        if (count == 0) {
            check(stack.isEmpty(), what + ": expected empty but was " + stack);
            return;
        }
        check(stack.is(item) && stack.getCount() == count, what + ": expected " + count + " " + item + " but was " + stack);
    }

    private static int fill(SlotAccess handler, int slot, Item item, int total) {
        int remaining = total;
        int leftover = 0;
        while (remaining > 0) {
            int batch = Math.min(64, remaining);
            leftover += handler.insertItem(slot, new ItemStack(item, batch), false).getCount();
            remaining -= batch;
        }
        return leftover;
    }

    // ---------------------------------------------------------------------------------------------
    // Standard drawers
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = Templates.FLOOR)
    public static void drawerCapabilityInsertExtract(GameTestHelper helper) {
        helper.setBlock(POS, DrawerContent.DRAWER_1X1.get());
        SlotAccess handler = handler(helper, POS);
        int limit = DrawerConfig.SINGLE_STACKS.get() * 64;
        eq(handler.getSlots(), 1, "1x1 slots");
        eq(handler.getSlotLimit(0), limit, "1x1 slot limit");

        eq(fill(handler, 0, Items.COBBLESTONE, 192), 0, "leftover after 192 cobblestone");
        stack(handler.getStackInSlot(0), Items.COBBLESTONE, 192, "after insert");
        stack(handler.insertItem(0, new ItemStack(Items.DIRT, 10), false), Items.DIRT, 10, "other item rejected");

        stack(handler.extractItem(0, 64, true), Items.COBBLESTONE, 64, "simulated extract");
        eq(handler.getStackInSlot(0).getCount(), 192, "count after simulated extract");
        // NeoForge handlers hand out one stack per call; Fabric storages give what was asked for.
        int asked = TestPlatform.INSTANCE.extractionCappedAtAStack() ? 100 : 64;
        stack(handler.extractItem(0, asked, false), Items.COBBLESTONE, 64, "extract gives at most a stack per call");
        eq(handler.getStackInSlot(0).getCount(), 128, "count after extract");

        int leftover = fill(handler, 0, Items.COBBLESTONE, limit);
        eq(leftover, 128, "overflow returned without a void upgrade");
        eq(handler.getStackInSlot(0).getCount(), limit, "full drawer");

        // A 2x2 drawer has four smaller compartments, and automation keeps one type in one compartment.
        BlockPos quadPos = POS.east(2);
        helper.setBlock(quadPos, DrawerContent.DRAWER_2X2.get());
        SlotAccess quad = handler(helper, quadPos);
        eq(quad.getSlots(), 4, "2x2 slots");
        eq(quad.getSlotLimit(3), DrawerConfig.QUAD_STACKS.get() * 64, "2x2 slot limit");
        eq(quad.getSlotLimit(0), DrawerConfig.QUAD_STACKS.get() * 64, "2x2 slot limit");
        stack(quad.insertItem(0, new ItemStack(Items.SAND, 5), false), Items.SAND, 0, "sand into compartment 0");
        stack(quad.insertItem(1, new ItemStack(Items.SAND, 5), false), Items.SAND, 5, "sand refused by empty compartment 1");
        stack(quad.insertItem(1, new ItemStack(Items.GRAVEL, 7), false), Items.GRAVEL, 0, "gravel into compartment 1");
        stack(quad.getStackInSlot(0), Items.SAND, 5, "compartment 0");
        stack(quad.getStackInSlot(1), Items.GRAVEL, 7, "compartment 1");
        // Ender pearls stack to 16, so their compartment holds 16 per stack.
        quad.insertItem(2, new ItemStack(Items.ENDER_PEARL, 1), false);
        eq(quad.getSlotLimit(2), DrawerConfig.QUAD_STACKS.get() * 16, "slot limit follows the item's stack size");
        helper.succeed();
    }

    @GameTest(template = Templates.FLOOR)
    public static void capacityUpgradeRaisesCapacity(GameTestHelper helper) {
        helper.setBlock(POS, DrawerContent.DRAWER_1X2.get());
        AbstractDrawerBlockEntity drawer = blockEntity(helper, POS, AbstractDrawerBlockEntity.class);
        SlotAccess handler = handler(helper, POS);
        int base = DrawerConfig.DOUBLE_STACKS.get() * 64;
        eq(handler.getSlotLimit(0), base, "base limit");

        check(drawer.installUpgrade(new ItemStack(DrawerContent.IRON_UPGRADE.get())), "iron upgrade installs");
        eq(drawer.capacityMultiplier(), 2, "multiplier with iron");
        eq(handler.getSlotLimit(0), base * 2L, "limit with iron");
        check(drawer.installUpgrade(new ItemStack(DrawerContent.GOLD_UPGRADE.get())), "gold upgrade installs");
        eq(drawer.capacityMultiplier(), 6, "multiplier with iron + gold");
        eq(handler.getSlotLimit(1), base * 6L, "limit with iron + gold");

        eq(fill(handler, 0, Items.STONE, base * 3), 0, "fits three times the base capacity");
        eq(drawer.countAt(0), base * 3L, "stored");
        // Removing the gold upgrade would leave room for only 2x: refused. Removing iron leaves 4x: allowed.
        int goldSlot = drawer.upgrades().getItem(0).is(DrawerContent.GOLD_UPGRADE.get()) ? 0 : 1;
        check(!drawer.canRemoveUpgrade(goldSlot), "gold upgrade cannot be removed while the drawer holds more than 2x");
        check(drawer.canRemoveUpgrade(1 - goldSlot), "iron upgrade can be removed");
        drawer.upgrades().setItem(1 - goldSlot, ItemStack.EMPTY);
        eq(drawer.capacityMultiplier(), 4, "multiplier with gold only");
        helper.succeed();
    }

    @GameTest(template = Templates.FLOOR)
    public static void voidUpgradeVoidsOverflow(GameTestHelper helper) {
        helper.setBlock(POS, DrawerContent.DRAWER_2X2.get());
        AbstractDrawerBlockEntity drawer = blockEntity(helper, POS, AbstractDrawerBlockEntity.class);
        SlotAccess handler = handler(helper, POS);
        int limit = DrawerConfig.QUAD_STACKS.get() * 64;
        check(drawer.installUpgrade(new ItemStack(DrawerContent.VOID_UPGRADE.get())), "void upgrade installs");
        check(!drawer.installUpgrade(new ItemStack(DrawerContent.VOID_UPGRADE.get())), "only one void upgrade");
        check(drawer.isVoiding(), "drawer voids");

        eq(fill(handler, 0, Items.COBBLESTONE, limit), 0, "fill to capacity");
        stack(handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false), Items.COBBLESTONE, 0, "overflow is voided");
        eq(drawer.countAt(0), limit, "count stays at capacity");
        // Items no compartment holds are not voided; they go to a free compartment or are refused.
        stack(handler.insertItem(0, new ItemStack(Items.DIRT, 3), false), Items.DIRT, 3, "other item refused by a full compartment");
        stack(handler.insertItem(1, new ItemStack(Items.DIRT, 3), false), Items.DIRT, 0, "other item into a free compartment");
        eq(drawer.countAt(1), 3, "dirt stored");
        helper.succeed();
    }

    @GameTest(template = Templates.FLOOR)
    public static void lockKeepsTypeWhenEmpty(GameTestHelper helper) {
        helper.setBlock(POS, DrawerContent.DRAWER_1X1.get());
        AbstractDrawerBlockEntity drawer = blockEntity(helper, POS, AbstractDrawerBlockEntity.class);
        SlotAccess handler = handler(helper, POS);
        handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false);
        drawer.setLocked(true);
        stack(handler.extractItem(0, 64, false), Items.IRON_INGOT, 10, "extract everything");
        check(ItemKey.of(new ItemStack(Items.IRON_INGOT)).equals(drawer.keyAt(0)), "locked drawer remembers iron ingots");
        stack(handler.insertItem(0, new ItemStack(Items.GOLD_INGOT, 4), false), Items.GOLD_INGOT, 4, "locked drawer refuses gold");
        stack(handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false), Items.IRON_INGOT, 0, "locked drawer takes iron");
        handler.extractItem(0, 4, false);
        drawer.setLocked(false);
        check(drawer.keyAt(0) == null, "unlocking an empty drawer forgets the type");
        stack(handler.insertItem(0, new ItemStack(Items.GOLD_INGOT, 4), false), Items.GOLD_INGOT, 0, "unlocked drawer takes gold");
        helper.succeed();
    }

    @GameTest(template = Templates.FLOOR)
    public static void drawerKeepsContentsWhenBroken(GameTestHelper helper) {
        helper.setBlock(POS, DrawerContent.DRAWER_1X2.get());
        AbstractDrawerBlockEntity drawer = blockEntity(helper, POS, AbstractDrawerBlockEntity.class);
        handler(helper, POS).insertItem(1, new ItemStack(Items.REDSTONE, 50), false);
        drawer.installUpgrade(new ItemStack(DrawerContent.IRON_UPGRADE.get()));
        drawer.setLocked(true);

        BlockState state = helper.getBlockState(POS);
        List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(POS), drawer);
        eq(drops.size(), 1, "drops");
        ItemStack drop = drops.get(0);
        check(drop.is(DrawerContent.DRAWER_1X2.get().asItem()), "drops the drawer, got " + drop);
        DrawerContents contents = drop.get(DrawerContent.DRAWER_CONTENTS.get());
        check(contents != null && contents.entries().size() == 2, "drop carries contents: " + contents);
        check(contents.entries().get(1).key().map(k -> k.item() == Items.REDSTONE).orElse(false), "redstone kept");
        eq(contents.entries().get(1).count(), 50, "redstone count kept");
        check(Boolean.TRUE.equals(drop.get(DrawerContent.DRAWER_LOCKED.get())), "lock kept");
        ItemContainerContents upgrades = drop.get(DrawerContent.DRAWER_UPGRADES.get());
        check(upgrades != null && upgrades.stream().anyMatch(s -> s.is(DrawerContent.IRON_UPGRADE.get())), "upgrade kept");

        // Placing the item again restores everything.
        BlockPos other = POS.east(2);
        helper.setBlock(other, DrawerContent.DRAWER_1X2.get());
        AbstractDrawerBlockEntity placed = blockEntity(helper, other, AbstractDrawerBlockEntity.class);
        placed.applyComponentsFromItemStack(drop);
        eq(placed.countAt(1), 50, "restored redstone");
        check(placed.isLocked(), "restored lock");
        eq(placed.capacityMultiplier(), 2, "restored upgrade");
        helper.succeed();
    }

    @GameTest(template = Templates.FLOOR)
    public static void frontHitSelectsCompartment(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos pos = POS;
            helper.setBlock(pos, DrawerContent.DRAWER_2X2.get().defaultBlockState().setValue(DrawerBlock.FACING, facing));
            BlockPos abs = helper.absolutePos(pos);
            BlockState state = helper.getLevel().getBlockState(abs);
            Direction left = facing.getClockWise();
            Vec3 front = Vec3.atCenterOf(abs).add(facing.getStepX() * 0.5, 0, facing.getStepZ() * 0.5);
            int[][] expected = {{1, 1, 0}, {-1, 1, 1}, {1, -1, 2}, {-1, -1, 3}};
            for (int[] e : expected) {
                Vec3 point = front.add(left.getStepX() * 0.25 * e[0], 0.25 * e[1], left.getStepZ() * 0.25 * e[0]);
                int slot = DrawerInteraction.compartmentAt(state, abs, new BlockHitResult(point, facing, abs, false));
                eq(slot, e[2], "compartment for facing " + facing + " at " + e[0] + "," + e[1]);
            }
            int side = DrawerInteraction.compartmentAt(state, abs, new BlockHitResult(front, facing.getClockWise(), abs, false));
            eq(side, -1, "side face is not a compartment");
        }
        // The compacting drawer's top tier spans the whole top half.
        eq(DrawerLayout.COMPACTING.compartmentAt(2, 2), 0, "compacting top-left");
        eq(DrawerLayout.COMPACTING.compartmentAt(14, 2), 0, "compacting top-right");
        eq(DrawerLayout.COMPACTING.compartmentAt(2, 14), 1, "compacting bottom-left");
        eq(DrawerLayout.COMPACTING.compartmentAt(14, 14), 2, "compacting bottom-right");
        helper.succeed();
    }

    @GameTest(template = Templates.FLOOR)
    public static void rightClickAndDoubleClickInsert(GameTestHelper helper) {
        helper.setBlock(POS, DrawerContent.DRAWER_1X1.get());
        AbstractDrawerBlockEntity drawer = blockEntity(helper, POS, AbstractDrawerBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG, 64));
        player.getInventory().setItem(10, new ItemStack(Items.OAK_LOG, 64));
        player.getInventory().setItem(20, new ItemStack(Items.OAK_LOG, 30));
        player.getInventory().setItem(21, new ItemStack(Items.BIRCH_LOG, 30));

        DrawerInteraction.rightClick(helper.getLevel(), drawer, player, InteractionHand.MAIN_HAND, 0);
        eq(drawer.countAt(0), 64, "first click inserts the held stack");
        check(player.getMainHandItem().isEmpty(), "hand emptied");
        DrawerInteraction.rightClick(helper.getLevel(), drawer, player, InteractionHand.MAIN_HAND, 0);
        eq(drawer.countAt(0), 158, "quick second click inserts every matching stack");
        check(player.getInventory().getItem(10).isEmpty() && player.getInventory().getItem(20).isEmpty(), "oak logs moved");
        eq(player.getInventory().getItem(21).getCount(), 30, "birch logs stay");
        helper.succeed();
    }

    /** A mock player standing 1.5 blocks in front of a north-facing block, eyes level with it, looking at it. */
    private static Player lookingAtFront(GameTestHelper helper, GameType mode, BlockPos abs) {
        Player player = helper.makeMockPlayer(mode);
        player.moveTo(abs.getX() + 0.5, abs.getY() + 0.5 - player.getEyeHeight(), abs.getZ() - 1.5, 0f, 0f);
        player.setYHeadRot(0f); // living entities look along their head rotation
        BlockHitResult pick = DrawerInteraction.pick(player, abs);
        check(pick != null && pick.getDirection() == Direction.NORTH, "mock player looks at the drawer front, got " + pick);
        return player;
    }

    @GameTest(template = Templates.FLOOR)
    public static void leftClickExtractsAndCreativeDoesNotBreak(GameTestHelper helper) {
        BlockPos pos = POS;
        // The mock players stand (head height) in the two cells in front of the drawer: make sure they are air.
        helper.setBlock(pos.north(), Blocks.AIR);
        helper.setBlock(pos.north(2), Blocks.AIR);
        helper.setBlock(pos, DrawerContent.DRAWER_1X1.get()); // faces north
        AbstractDrawerBlockEntity drawer = blockEntity(helper, pos, AbstractDrawerBlockEntity.class);
        handler(helper, pos).insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
        handler(helper, pos).insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
        BlockPos abs = helper.absolutePos(pos);

        // Survival: the left click reaches Block#attack, which extracts from the compartment looked at.
        Player survival = lookingAtFront(helper, GameType.SURVIVAL, abs);
        helper.getLevel().getBlockState(abs).attack(helper.getLevel(), abs, survival);
        eq(drawer.countAt(0), 64, "left click takes a stack");
        eq(survival.getInventory().countItem(Items.COBBLESTONE), 64, "player received the stack");
        survival.setShiftKeyDown(true);
        helper.getLevel().getBlockState(abs).attack(helper.getLevel(), abs, survival);
        eq(drawer.countAt(0), 63, "sneak left click takes one");

        // Creative: the click on the front is cancelled (so the drawer cannot break) and extracts instead.
        Player creative = lookingAtFront(helper, GameType.CREATIVE, abs);
        check(TestPlatform.INSTANCE.leftClickBlock(creative, abs, Direction.NORTH), "creative left click on the front is cancelled");
        eq(drawer.countAt(0), 0, "creative left click extracted the rest");
        eq(creative.getInventory().countItem(Items.COBBLESTONE), 63, "creative player received the items");
        check(!TestPlatform.INSTANCE.leftClickBlock(creative, abs, Direction.EAST), "creative left click on a side still breaks normally");
        helper.assertBlockPresent(DrawerContent.DRAWER_1X1.get(), pos);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Compacting drawer
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = Templates.FLOOR)
    public static void compactingDrawerConvertsIron(GameTestHelper helper) {
        helper.setBlock(POS, DrawerContent.COMPACTING_DRAWER.get());
        CompactingDrawerBlockEntity drawer = blockEntity(helper, POS, CompactingDrawerBlockEntity.class);
        SlotAccess handler = handler(helper, POS);
        eq(handler.getSlots(), 3, "compacting slots");

        stack(handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 18), false), Items.IRON_INGOT, 0, "ingots set up the chain");
        eq(drawer.tiers(), 3, "iron has three tiers");
        stack(handler.getStackInSlot(0), Items.IRON_BLOCK, 2, "blocks");
        stack(handler.getStackInSlot(1), Items.IRON_INGOT, 18, "ingots");
        stack(handler.getStackInSlot(2), Items.IRON_NUGGET, 162, "nuggets");
        eq(drawer.amount(), 162, "stored once in nuggets");

        stack(handler.insertItem(2, new ItemStack(Items.IRON_NUGGET, 9), false), Items.IRON_NUGGET, 0, "nuggets in");
        stack(handler.getStackInSlot(1), Items.IRON_INGOT, 19, "nine nuggets make an ingot");
        stack(handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 1), false), Items.IRON_INGOT, 1, "ingot refused by the block slot");

        stack(handler.extractItem(0, 1, false), Items.IRON_BLOCK, 1, "take a block");
        stack(handler.getStackInSlot(1), Items.IRON_INGOT, 10, "ingots left after taking a block");
        stack(handler.extractItem(2, 5, false), Items.IRON_NUGGET, 5, "take nuggets");
        stack(handler.getStackInSlot(1), Items.IRON_INGOT, 9, "breaking into an ingot for nuggets");
        stack(handler.getStackInSlot(2), Items.IRON_NUGGET, 85, "nuggets left");
        stack(handler.getStackInSlot(0), Items.IRON_BLOCK, 1, "85 nuggets still make a block");

        long capacityNuggets = (long) DrawerConfig.COMPACTING_STACKS.get() * 64 * 81;
        eq(handler.getSlotLimit(2), capacityNuggets, "capacity counted in the base item");
        eq(handler.getSlotLimit(0), DrawerConfig.COMPACTING_STACKS.get() * 64, "capacity in blocks");

        // Emptying an unlocked compacting drawer forgets the chain; quartz then uses the 4-way rule.
        handler.extractItem(2, 64, false);
        handler.extractItem(2, 64, false);
        eq(drawer.amount(), 0, "emptied");
        eq(drawer.tiers(), 0, "chain forgotten");
        stack(handler.insertItem(1, new ItemStack(Items.QUARTZ_BLOCK, 3), false), Items.QUARTZ_BLOCK, 0, "quartz blocks in");
        eq(drawer.tiers(), 2, "quartz has two tiers");
        stack(handler.getStackInSlot(1), Items.QUARTZ, 12, "each quartz block is four quartz");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Controller
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = Templates.FLOOR)
    public static void controllerRoutesToMatchingDrawer(GameTestHelper helper) {
        BlockPos controllerPos = POS;
        BlockPos emptyPos = POS.west();
        BlockPos cobblePos = POS.east();
        BlockPos trimPos = POS.south();
        BlockPos dirtPos = POS.south(2);
        BlockPos compactingPos = POS.south(2).east();
        helper.setBlock(controllerPos, DrawerContent.DRAWER_CONTROLLER.get());
        helper.setBlock(emptyPos, DrawerContent.DRAWER_1X1.get());
        helper.setBlock(cobblePos, DrawerContent.DRAWER_1X2.get());
        helper.setBlock(trimPos, DrawerContent.DRAWER_TRIM.get());
        helper.setBlock(dirtPos, DrawerContent.DRAWER_1X1.get());
        helper.setBlock(compactingPos, DrawerContent.COMPACTING_DRAWER.get());
        handler(helper, cobblePos).insertItem(1, new ItemStack(Items.COBBLESTONE, 5), false);
        handler(helper, dirtPos).insertItem(0, new ItemStack(Items.DIRT, 1), false);
        handler(helper, compactingPos).insertItem(0, new ItemStack(Items.GOLD_INGOT, 1), false);

        DrawerControllerBlockEntity controller = blockEntity(helper, controllerPos, DrawerControllerBlockEntity.class);
        eq(controller.drawers().size(), 4, "drawers connected (directly, through a trim and through another drawer)");
        SlotAccess handler = handler(helper, controllerPos);
        eq(handler.getSlots(), 1 + 1 + 2 + 1 + 3, "routing slot plus every compartment");

        stack(handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false), Items.COBBLESTONE, 0, "cobblestone routed");
        eq(blockEntity(helper, cobblePos, AbstractDrawerBlockEntity.class).countAt(1), 69, "cobblestone went to the cobblestone compartment");
        eq(blockEntity(helper, emptyPos, AbstractDrawerBlockEntity.class).countAt(0), 0, "empty drawer untouched");

        stack(handler.insertItem(0, new ItemStack(Items.DIRT, 10), false), Items.DIRT, 0, "dirt routed");
        eq(blockEntity(helper, dirtPos, AbstractDrawerBlockEntity.class).countAt(0), 11, "dirt reached the drawer behind the trim");

        stack(handler.insertItem(0, new ItemStack(Items.GOLD_NUGGET, 9), false), Items.GOLD_NUGGET, 0, "gold nuggets routed");
        eq(blockEntity(helper, compactingPos, CompactingDrawerBlockEntity.class).countAt(1), 2, "nuggets compacted into the gold drawer");

        stack(handler.insertItem(0, new ItemStack(Items.SAND, 3), false), Items.SAND, 0, "new item routed");
        AbstractDrawerBlockEntity empty = blockEntity(helper, emptyPos, AbstractDrawerBlockEntity.class);
        eq(empty.countAt(0), 3, "a new item goes to an empty drawer");

        eq(controller.extract(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 100, false), 69, "extract through the controller");
        helper.succeed();
    }
}
