package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.physical.StorageMenu;
import com.nightbeam.omnivault.physical.linked.LinkedFrequency;
import com.nightbeam.omnivault.physical.linked.LinkedVaultBlockEntity;
import com.nightbeam.omnivault.physical.linked.LinkedVaultData;
import com.nightbeam.omnivault.physical.relocator.RelocatedBlock;
import com.nightbeam.omnivault.physical.relocator.RelocatorItem;
import com.nightbeam.omnivault.physical.trash.TrashCanBlockEntity;
import com.nightbeam.omnivault.physical.vault.VaultBlock;
import com.nightbeam.omnivault.physical.vault.VaultBlockEntity;
import com.nightbeam.omnivault.physical.vault.VaultTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.Optional;

@GameTestHolder(OmniVault.MOD_ID)
@PrefixGameTestTemplate(false)
public class PhysicalGameTests {
    /**
     * In GameTestHelper coordinates the template floor is relative y = 1, so fixtures stand on it at y = 2.
     * Every cell a test relies on is set (or cleared) explicitly.
     */
    private static final int ON_FLOOR = 2;
    private static final BlockPos POS = new BlockPos(4, ON_FLOOR, 4);

    // -- helpers -----------------------------------------------------------------------------------------

    /** Right-clicks {@code rel}'s {@code face} with the player's main-hand item, the way the item would be used. */
    private static InteractionResult use(GameTestHelper helper, Player player, BlockPos rel, Direction face) {
        BlockPos abs = helper.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(face, 0.5), face, abs, false);
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static Player holding(GameTestHelper helper, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos rel, Direction side) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(rel), side);
        if (handler == null) {
            helper.fail("No item handler capability", rel);
        }
        return handler;
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) {
                total += container.getItem(i).getCount();
            }
        }
        return total;
    }

    private static int count(IItemHandler handler, Item item) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) {
                total += handler.getStackInSlot(i).getCount();
            }
        }
        return total;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    // -- tiered vaults -----------------------------------------------------------------------------------

    @GameTest(template = "floor_9x9")
    public static void vaultUpgradeKeepsContents(GameTestHelper helper) {
        helper.setBlock(POS, PhysicalContent.vault(VaultTier.COPPER).defaultBlockState().setValue(VaultBlock.FACING, Direction.EAST));
        VaultBlockEntity copper = helper.getBlockEntity(POS);
        check(helper, copper.getContainerSize() == 45, "Copper vault should have 45 slots, has " + copper.getContainerSize());
        copper.setItem(0, new ItemStack(Items.DIAMOND, 7));
        copper.setItem(44, new ItemStack(Items.OAK_LOG, 64));
        copper.setCustomName(Component.literal("Loot"));

        Player player = holding(helper, new ItemStack(PhysicalContent.kit(VaultTier.GOLD), 1));
        check(helper, !use(helper, player, POS, Direction.UP).consumesAction(), "A gold kit must not fit a copper vault");
        helper.assertBlockPresent(PhysicalContent.vault(VaultTier.COPPER), POS);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhysicalContent.kit(VaultTier.IRON), 2));
        check(helper, use(helper, player, POS, Direction.UP).consumesAction(), "Iron kit should upgrade a copper vault");
        helper.assertBlockPresent(PhysicalContent.vault(VaultTier.IRON), POS);
        VaultBlockEntity iron = helper.getBlockEntity(POS);
        check(helper, iron.getContainerSize() == 54, "Iron vault should have 54 slots");
        check(helper, iron.getItem(0).is(Items.DIAMOND) && iron.getItem(0).getCount() == 7, "Slot 0 lost its diamonds");
        check(helper, iron.getItem(44).is(Items.OAK_LOG) && iron.getItem(44).getCount() == 64, "Slot 44 lost its logs");
        check(helper, iron.getCustomName() != null && iron.getCustomName().getString().equals("Loot"), "Custom name was lost");
        check(helper, iron.getBlockState().getValue(VaultBlock.FACING) == Direction.EAST, "Facing was lost");
        check(helper, player.getMainHandItem().getCount() == 1, "Kit should be consumed");
        helper.assertItemEntityNotPresent(Items.DIAMOND, POS, 3);

        // Walk the remaining tiers with fresh kits and make sure nothing is ever lost.
        for (VaultTier tier : new VaultTier[]{VaultTier.GOLD, VaultTier.DIAMOND, VaultTier.NETHERITE, VaultTier.OMNIUM}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhysicalContent.kit(tier)));
            check(helper, use(helper, player, POS, Direction.UP).consumesAction(), tier + " kit should apply");
            helper.assertBlockPresent(PhysicalContent.vault(tier), POS);
        }
        VaultBlockEntity omnium = helper.getBlockEntity(POS);
        check(helper, omnium.getContainerSize() == 162, "Omnium vault should have 162 slots");
        check(helper, count(omnium, Items.DIAMOND) == 7 && count(omnium, Items.OAK_LOG) == 64, "Items lost along the upgrade chain");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9")
    public static void vanillaChestUpgradesToCopperVault(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        ChestBlockEntity chest = helper.getBlockEntity(POS);
        chest.setItem(5, new ItemStack(Items.APPLE, 3));
        chest.setItem(26, new ItemStack(Items.BONE, 12));

        Player player = holding(helper, new ItemStack(PhysicalContent.kit(VaultTier.COPPER), 3));
        check(helper, use(helper, player, POS, Direction.UP).consumesAction(), "Copper kit should upgrade a chest");
        helper.assertBlockPresent(PhysicalContent.vault(VaultTier.COPPER), POS);
        VaultBlockEntity vault = helper.getBlockEntity(POS);
        check(helper, vault.getItem(5).is(Items.APPLE) && vault.getItem(5).getCount() == 3, "Apples moved or lost");
        check(helper, vault.getItem(26).is(Items.BONE) && vault.getItem(26).getCount() == 12, "Bones moved or lost");
        check(helper, vault.getBlockState().getValue(VaultBlock.FACING) == Direction.WEST, "Chest facing was not kept");
        check(helper, player.getMainHandItem().getCount() == 2, "Kit should be consumed");
        helper.assertItemEntityNotPresent(Items.APPLE, POS, 3);

        // Barrels too.
        BlockPos barrelPos = POS.east(2);
        helper.setBlock(barrelPos, Blocks.BARREL);
        Container barrel = helper.getBlockEntity(barrelPos);
        barrel.setItem(0, new ItemStack(Items.STRING, 9));
        check(helper, use(helper, player, barrelPos, Direction.UP).consumesAction(), "Copper kit should upgrade a barrel");
        VaultBlockEntity fromBarrel = helper.getBlockEntity(barrelPos);
        check(helper, count(fromBarrel, Items.STRING) == 9, "Barrel contents lost");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9")
    public static void vaultCapabilityInsertExtract(GameTestHelper helper) {
        helper.setBlock(POS, PhysicalContent.vault(VaultTier.GOLD));
        IItemHandler handler = items(helper, POS, Direction.UP);
        check(helper, handler.getSlots() == 81, "Gold vault should expose 81 slots, got " + handler.getSlots());

        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.IRON_INGOT, 64), false);
        remainder = remainder.isEmpty() ? ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.IRON_INGOT, 36), false) : remainder;
        check(helper, remainder.isEmpty(), "Insert should be accepted completely");
        VaultBlockEntity vault = helper.getBlockEntity(POS);
        check(helper, count(vault, Items.IRON_INGOT) == 100, "Vault should hold 100 ingots, holds " + count(vault, Items.IRON_INGOT));

        ItemStack simulated = handler.extractItem(0, 64, true);
        check(helper, simulated.getCount() == 64 && count(vault, Items.IRON_INGOT) == 100, "Simulated extraction must not change contents");
        ItemStack extracted = handler.extractItem(0, 10, false);
        check(helper, extracted.is(Items.IRON_INGOT) && extracted.getCount() == 10, "Should extract 10 ingots");
        check(helper, count(vault, Items.IRON_INGOT) == 90, "Vault should hold 90 ingots after extraction");

        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(POS));
        check(helper, state.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(POS)) > 0, "Comparator output should be positive");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9", timeoutTicks = 100)
    public static void hopperFillsVault(GameTestHelper helper) {
        helper.setBlock(POS, PhysicalContent.vault(VaultTier.COPPER));
        helper.setBlock(POS.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity hopper = helper.getBlockEntity(POS.above());
        hopper.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        helper.succeedWhen(() -> {
            VaultBlockEntity vault = helper.getBlockEntity(POS);
            helper.assertTrue(count(vault, Items.COBBLESTONE) == 4, "Hopper has not emptied into the vault yet");
        });
    }

    @GameTest(template = "floor_9x9")
    public static void vaultSortAndViewers(GameTestHelper helper) {
        helper.setBlock(POS, PhysicalContent.vault(VaultTier.COPPER));
        VaultBlockEntity vault = helper.getBlockEntity(POS);
        vault.setItem(30, new ItemStack(Items.DIRT, 5));
        vault.setItem(3, new ItemStack(Items.DIRT, 3));
        vault.setItem(17, new ItemStack(Items.DIAMOND, 1));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StorageMenu menu = (StorageMenu) vault.createMenu(1, player.getInventory(), player);
        check(helper, vault.lid().viewers() == 1, "Opening the menu should count a viewer");
        menu.sortSection(0);
        check(helper, !vault.getItem(0).isEmpty() && !vault.getItem(1).isEmpty() && vault.getItem(2).isEmpty(),
                "Sort should compact everything into the first slots");
        check(helper, count(vault, Items.DIRT) == 8 && count(vault, Items.DIAMOND) == 1, "Sort must not change totals");
        menu.removed(player);
        check(helper, vault.lid().viewers() == 0, "Closing the menu should remove the viewer");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9")
    public static void vaultBreakDropsContents(GameTestHelper helper) {
        helper.setBlock(POS, PhysicalContent.vault(VaultTier.COPPER));
        VaultBlockEntity vault = helper.getBlockEntity(POS);
        vault.setItem(12, new ItemStack(Items.REDSTONE, 20));
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.assertBlockNotPresent(PhysicalContent.vault(VaultTier.COPPER), POS);
        helper.assertItemEntityPresent(Items.REDSTONE, POS, 2);
        helper.assertItemEntityPresent(PhysicalContent.vault(VaultTier.COPPER).asItem(), POS, 2);
        check(helper, PhysicalContent.vault(VaultTier.NETHERITE).getExplosionResistance() >= 1200f, "Netherite vaults must be blast resistant");
        check(helper, PhysicalContent.vault(VaultTier.OMNIUM).getExplosionResistance() >= 1200f, "Omnium vaults must be blast resistant");
        check(helper, PhysicalContent.vault(VaultTier.DIAMOND).getExplosionResistance() < 1200f, "Diamond vaults are not blast resistant");
        helper.succeed();
    }

    // -- linked storage ----------------------------------------------------------------------------------

    @GameTest(template = "floor_9x9")
    public static void linkedVaultsShareContents(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, ON_FLOOR, 2);
        BlockPos b = new BlockPos(6, ON_FLOOR, 6);
        BlockPos other = new BlockPos(2, ON_FLOOR, 6);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        // A private channel owned by this test's mock player, so runs never see each other's items.
        LinkedFrequency frequency = new LinkedFrequency(DyeColor.RED, DyeColor.LIME, DyeColor.BLUE, Optional.empty(), "")
                .withOwner(owner.getUUID(), "gametest");
        for (BlockPos pos : new BlockPos[]{a, b, other}) {
            helper.setBlock(pos, PhysicalContent.LINKED_VAULT.get());
        }
        LinkedVaultBlockEntity vaultA = helper.getBlockEntity(a);
        LinkedVaultBlockEntity vaultB = helper.getBlockEntity(b);
        LinkedVaultBlockEntity vaultOther = helper.getBlockEntity(other);
        vaultA.setFrequency(frequency);
        vaultB.setFrequency(frequency);
        vaultOther.setFrequency(frequency.withColor(2, DyeColor.YELLOW));

        IItemHandler handlerA = items(helper, a, Direction.NORTH);
        IItemHandler handlerB = items(helper, b, null);
        IItemHandler handlerOther = items(helper, other, Direction.UP);
        check(helper, handlerA.getSlots() == LinkedVaultData.SIZE, "Linked vaults have 27 slots");
        check(helper, ItemHandlerHelper.insertItemStacked(handlerA, new ItemStack(Items.EMERALD, 12), false).isEmpty(), "Insert into A failed");
        check(helper, count(handlerB, Items.EMERALD) == 12, "Vault B should see A's emeralds");
        check(helper, count(handlerOther, Items.EMERALD) == 0, "A different channel must not see them");
        check(helper, helper.getLevel().getBlockState(helper.absolutePos(b)).getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(b)) > 0,
                "Vault B's comparator output should reflect the shared contents");

        ItemStack taken = ItemStack.EMPTY;
        for (int slot = 0; slot < handlerB.getSlots() && taken.isEmpty(); slot++) {
            taken = handlerB.extractItem(slot, 12, false);
        }
        check(helper, taken.is(Items.EMERALD) && taken.getCount() == 12, "Extracting from B should yield the emeralds");
        check(helper, count(handlerA, Items.EMERALD) == 0, "Vault A should now be empty");

        // A pouch bound to vault A opens the same inventory.
        ItemStack pouch = new ItemStack(PhysicalContent.LINKED_POUCH.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, pouch);
        owner.setShiftKeyDown(true);
        check(helper, use(helper, owner, a, Direction.UP).consumesAction(), "Sneak-using a pouch on a vault should bind it");
        LinkedFrequency bound = pouch.get(PhysicalContent.LINKED_FREQUENCY.get());
        check(helper, bound != null && bound.key().equals(frequency.key()), "Pouch should be bound to vault A's channel");
        LinkedVaultData.Inventory shared = LinkedVaultData.get(helper.getLevel().getServer()).inventory(bound);
        shared.setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 5));
        check(helper, count(handlerA, Items.GLOWSTONE_DUST) == 5, "Items added through the pouch's channel should appear in the vault");

        // Strangers can neither open nor bind a private channel.
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        check(helper, !vaultA.canUse(stranger), "A stranger must not use a private vault");
        helper.succeed();
    }

    // -- trash can -----------------------------------------------------------------------------------------

    @GameTest(template = "floor_9x9")
    public static void trashCanVoids(GameTestHelper helper) {
        helper.setBlock(POS, PhysicalContent.TRASH_CAN.get());
        IItemHandler handler = items(helper, POS, Direction.UP);
        check(helper, handler.insertItem(0, new ItemStack(Items.DIRT, 64), false).isEmpty(), "Trash should accept everything");
        check(helper, handler.getStackInSlot(0).isEmpty(), "Trash should stay empty");
        TrashCanBlockEntity trash = helper.getBlockEntity(POS);
        check(helper, trash.inventory().lastVoided().is(Items.DIRT) && trash.inventory().lastVoided().getCount() == 64,
                "Last voided item should be remembered");

        // GUI input slot voids too, and the last item can be recovered.
        trash.inventory().input.setItem(0, new ItemStack(Items.GOLD_NUGGET, 3));
        check(helper, trash.inventory().input.getItem(0).isEmpty(), "Input slot must never hold items");
        ItemStack recovered = trash.inventory().recovery.removeItem(0, 3);
        check(helper, recovered.is(Items.GOLD_NUGGET) && recovered.getCount() == 3, "Last voided item should be recoverable");
        check(helper, trash.inventory().lastVoided().isEmpty(), "Recovering must not duplicate");

        // Allow-list filter: only stone may be voided by automation.
        trash.inventory().filterContainer.setItem(0, new ItemStack(Items.STONE, 1));
        check(helper, handler.insertItem(0, new ItemStack(Items.DIRT, 5), false).getCount() == 5, "Allow-list should reject dirt");
        check(helper, handler.insertItem(0, new ItemStack(Items.STONE, 5), false).isEmpty(), "Allow-list should void stone");
        trash.inventory().setAllowList(false);
        check(helper, handler.insertItem(0, new ItemStack(Items.STONE, 5), false).getCount() == 5, "Deny-list should reject stone");
        check(helper, handler.insertItem(0, new ItemStack(Items.DIRT, 5), false).isEmpty(), "Deny-list should void dirt");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9", timeoutTicks = 100)
    public static void hopperEmptiesIntoTrash(GameTestHelper helper) {
        helper.setBlock(POS, PhysicalContent.TRASH_CAN.get());
        helper.setBlock(POS.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity hopper = helper.getBlockEntity(POS.above());
        hopper.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 3));
        helper.succeedWhen(() -> {
            helper.assertTrue(hopper.isEmpty(), "Hopper should have pushed everything into the trash");
            TrashCanBlockEntity trash = helper.getBlockEntity(POS);
            helper.assertTrue(trash.inventory().lastVoided().is(Items.ROTTEN_FLESH), "Trash should have voided the flesh");
        });
    }

    // -- relocator -----------------------------------------------------------------------------------------

    @GameTest(template = "floor_9x9")
    public static void relocatorMovesChestWithContents(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(POS);
        chest.setItem(3, new ItemStack(Items.GOLD_INGOT, 9));
        chest.setItem(20, new ItemStack(Items.ENDER_PEARL, 4));

        ItemStack relocator = new ItemStack(PhysicalContent.RELOCATOR.get());
        Player player = holding(helper, relocator);
        check(helper, use(helper, player, POS, Direction.UP).consumesAction(), "Relocator should pick up the chest");
        helper.assertBlockNotPresent(Blocks.CHEST, POS);
        helper.assertItemEntityNotPresent(Items.GOLD_INGOT, POS, 3);
        RelocatedBlock carried = RelocatorItem.carried(relocator);
        check(helper, carried != null && carried.state().is(Blocks.CHEST), "Relocator should hold the chest");

        BlockPos target = new BlockPos(2, ON_FLOOR, 6);
        helper.setBlock(target, Blocks.AIR);
        helper.setBlock(target.below(), Blocks.POLISHED_ANDESITE);
        check(helper, use(helper, player, target.below(), Direction.UP).consumesAction(), "Relocator should place the chest");
        helper.assertBlockPresent(Blocks.CHEST, target);
        ChestBlockEntity moved = helper.getBlockEntity(target);
        check(helper, moved.getItem(3).is(Items.GOLD_INGOT) && moved.getItem(3).getCount() == 9, "Gold was not kept");
        check(helper, moved.getItem(20).is(Items.ENDER_PEARL) && moved.getItem(20).getCount() == 4, "Pearls were not kept");
        check(helper, RelocatorItem.carried(relocator) == null, "Relocator should be empty again");
        check(helper, relocator.getDamageValue() == 1, "One move should cost one durability");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9")
    public static void relocatorMovesVaultAndRespectsDenylist(GameTestHelper helper) {
        helper.setBlock(POS, PhysicalContent.vault(VaultTier.DIAMOND));
        VaultBlockEntity vault = helper.getBlockEntity(POS);
        vault.setItem(100, new ItemStack(Items.NETHER_STAR, 2));

        ItemStack relocator = new ItemStack(PhysicalContent.NETHERITE_RELOCATOR.get());
        Player player = holding(helper, relocator);
        check(helper, use(helper, player, POS, Direction.UP).consumesAction(), "Relocator should pick up the vault");
        helper.assertItemEntityNotPresent(Items.NETHER_STAR, POS, 3);
        BlockPos target = new BlockPos(6, ON_FLOOR, 2);
        helper.setBlock(target, Blocks.AIR);
        helper.setBlock(target.below(), Blocks.POLISHED_ANDESITE);
        check(helper, use(helper, player, target.below(), Direction.UP).consumesAction(), "Relocator should place the vault");
        helper.assertBlockPresent(PhysicalContent.vault(VaultTier.DIAMOND), target);
        VaultBlockEntity moved = helper.getBlockEntity(target);
        check(helper, moved.getItem(100).is(Items.NETHER_STAR) && moved.getItem(100).getCount() == 2, "Vault contents were not kept");
        check(helper, !relocator.isDamageableItem(), "The netherite relocator never wears out");

        BlockPos spawner = new BlockPos(6, ON_FLOOR, 6);
        helper.setBlock(spawner, Blocks.SPAWNER);
        check(helper, !use(helper, player, spawner, Direction.UP).consumesAction(), "Spawners are on the denylist");
        helper.assertBlockPresent(Blocks.SPAWNER, spawner);
        check(helper, RelocatorItem.carried(relocator) == null, "Nothing should have been picked up");
        helper.succeed();
    }
}
