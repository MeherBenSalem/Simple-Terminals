package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.backpack.BackpackBlockEntity;
import com.nightbeam.omnivault.backpack.BackpackContent;
import com.nightbeam.omnivault.backpack.BackpackData;
import com.nightbeam.omnivault.backpack.BackpackMenu;
import com.nightbeam.omnivault.backpack.BackpackSlotAccess;
import com.nightbeam.omnivault.backpack.BackpackSource;
import com.nightbeam.omnivault.backpack.BackpackStorage;
import com.nightbeam.omnivault.backpack.BackpackSummary;
import com.nightbeam.omnivault.backpack.BackpackTier;
import com.nightbeam.omnivault.backpack.Backpacks;
import com.nightbeam.omnivault.backpack.BackpackHooks;
import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgradeItem;
import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgrades;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeConfig;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeType;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(OmniVault.MOD_ID)
@PrefixGameTestTemplate(false)
public class BackpackGameTests {
    private static ItemStack backpack(BackpackTier tier) {
        return new ItemStack(BackpackContent.item(tier));
    }

    private static BackpackData data(GameTestHelper helper, ItemStack stack) {
        return Backpacks.data(helper.getLevel().getServer(), stack);
    }

    private static ItemStack card(UpgradeType type, UpgradeConfig config) {
        ItemStack card = new ItemStack(BackpackContent.upgrade(type));
        BackpackUpgradeItem.setConfig(card, config);
        return card;
    }

    private static void install(BackpackData data, int slot, UpgradeType type, UpgradeConfig config) {
        data.upgrades().setItem(slot, card(type, config));
    }

    private static int count(BackpackData data, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < data.items().getContainerSize(); i++) {
            if (data.items().getItem(i).is(item)) {
                total += data.items().getItem(i).getCount();
            }
        }
        return total;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    /** The UUID on the item is the only link to the contents: copies (and serialised copies) see the same items. */
    @GameTest(template = Templates.FLOOR)
    public static void contentsPersistByUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack stack = backpack(BackpackTier.LEATHER);
        check(helper, Backpacks.getId(stack) == null, "fresh backpack already has a UUID");
        BackpackData data = data(helper, stack);
        UUID id = Backpacks.getId(stack);
        check(helper, id != null, "opening the contents did not assign a UUID");
        data.items().insert(new ItemStack(Items.DIAMOND, 40), false);
        data.items().setItem(26, new ItemStack(Items.TORCH, 5));

        ItemStack copy = stack.copy();
        ItemStack reloaded = ItemStack.parse(level.registryAccess(), stack.save(level.registryAccess())).orElseThrow();
        for (ItemStack other : List.of(copy, reloaded)) {
            check(helper, id.equals(Backpacks.getId(other)), "copy lost the backpack UUID");
            BackpackData seen = Backpacks.existingData(level.getServer(), other);
            check(helper, seen == data, "copy does not resolve to the same contents");
            check(helper, count(seen, Items.DIAMOND) == 40 && count(seen, Items.TORCH) == 5, "copy sees different contents");
        }

        // The saved data survives a save/load round trip.
        BackpackStorage storage = BackpackStorage.get(level.getServer());
        BackpackStorage loaded = BackpackStorage.load(storage.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        BackpackData restored = loaded.get(id);
        check(helper, restored != null && count(restored, Items.DIAMOND) == 40 && restored.items().getItem(26).is(Items.TORCH),
                "contents did not survive saving");

        // The tooltip summary is kept small and accurate.
        Backpacks.refreshSummary(stack, data);
        BackpackSummary summary = stack.get(BackpackContent.SUMMARY.get());
        check(helper, summary != null && summary.used() == 2 && summary.slots() == 27, "summary is wrong: " + summary);
        check(helper, !stack.has(DataComponents.CONTAINER), "contents must not be stored on the item");
        helper.succeed();
    }

    /** A placed backpack exposes the same inventory as an item handler, and breaking it returns the same backpack. */
    @GameTest(template = Templates.FLOOR)
    public static void placedBackpackCapabilityAndDrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack stack = backpack(BackpackTier.LEATHER);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3C44AA, true));
        BackpackData data = data(helper, stack);
        UUID id = Backpacks.getId(stack);
        data.items().insert(new ItemStack(Items.DIAMOND, 5), false);

        // The template floor is at relative y = 1; the backpack stands on it.
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos.above(), Blocks.AIR);
        helper.setBlock(pos, BackpackContent.block(BackpackTier.LEATHER));
        BlockPos abs = helper.absolutePos(pos);
        if (!(level.getBlockEntity(abs) instanceof BackpackBlockEntity blockEntity)) {
            throw new IllegalStateException("no backpack block entity");
        }
        blockEntity.setBackpack(stack);

        SlotAccess handler = TestPlatform.INSTANCE.items(level, abs, null);
        check(helper, handler != null, "placed backpack exposes no item handler");
        check(helper, handler.getSlots() == 27, "handler has " + handler.getSlots() + " slots");
        int diamonds = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(Items.DIAMOND)) {
                diamonds += handler.getStackInSlot(i).getCount();
            }
        }
        check(helper, diamonds == 5, "handler does not show the backpack's diamonds");
        ItemStack rest = TestInventories.insert(handler, new ItemStack(Items.IRON_INGOT, 10));
        check(helper, rest.isEmpty() && count(data, Items.IRON_INGOT) == 10, "items inserted through the handler did not reach the backpack");
        ItemStack rejected = TestInventories.insert(handler, backpack(BackpackTier.IRON));
        check(helper, rejected.getCount() == 1, "the handler accepted a backpack");
        check(helper, Services.PLATFORM.findSlotAccess(level, abs, null).isPresent(), "common slot access lookup failed");

        level.destroyBlock(abs, true);
        helper.assertBlockNotPresent(BackpackContent.block(BackpackTier.LEATHER), pos);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2));
        List<ItemStack> backpacks = new ArrayList<>();
        drops.forEach(entity -> {
            if (Backpacks.isBackpack(entity.getItem())) {
                backpacks.add(entity.getItem());
            }
        });
        check(helper, backpacks.size() == 1, "breaking dropped " + backpacks.size() + " backpacks");
        ItemStack dropped = backpacks.get(0);
        check(helper, dropped.is(BackpackContent.item(BackpackTier.LEATHER)), "dropped the wrong tier");
        check(helper, id.equals(Backpacks.getId(dropped)), "dropped backpack has a different UUID");
        check(helper, DyedItemColor.getOrDefault(dropped, 0) == 0xFF3C44AA, "dropped backpack lost its colour");
        BackpackData after = Backpacks.existingData(level.getServer(), dropped);
        check(helper, after != null && count(after, Items.DIAMOND) == 5 && count(after, Items.IRON_INGOT) == 10, "dropped backpack lost its contents");
        helper.succeed();
    }

    /** Pickup takes what passes its filter, Void destroys what passes its filter, everything else is left alone. */
    @GameTest(template = Templates.FLOOR)
    public static void pickupAndVoidFilters(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = backpack(BackpackTier.IRON);
        BackpackData data = data(helper, stack);
        install(data, 0, UpgradeType.PICKUP, UpgradeConfig.DEFAULT.withFilter(0, new ItemStack(Items.COBBLESTONE)));
        install(data, 1, UpgradeType.VOID, UpgradeConfig.DEFAULT.withFilter(0, new ItemStack(Items.DIRT)));
        player.getInventory().setItem(3, stack);

        ItemStack cobble = BackpackUpgrades.absorb(player, new ItemStack(Items.COBBLESTONE, 16));
        check(helper, cobble.isEmpty() && count(data, Items.COBBLESTONE) == 16, "pickup did not store filtered cobblestone");
        ItemStack dirt = BackpackUpgrades.absorb(player, new ItemStack(Items.DIRT, 8));
        check(helper, dirt.isEmpty() && count(data, Items.DIRT) == 0, "void did not destroy filtered dirt");
        ItemStack stone = BackpackUpgrades.absorb(player, new ItemStack(Items.STONE, 4));
        check(helper, stone.getCount() == 4 && count(data, Items.STONE) == 0, "unfiltered stone was taken");

        // An empty void filter never destroys anything, whatever its mode.
        data.upgrades().setItem(1, card(UpgradeType.VOID, UpgradeConfig.DEFAULT.withAllow(false)));
        check(helper, BackpackUpgrades.absorb(player, new ItemStack(Items.STONE, 4)).getCount() == 4, "a blank void card destroyed items");
        // Deny list: pickup takes everything except the listed items.
        data.upgrades().setItem(0, card(UpgradeType.PICKUP, UpgradeConfig.DEFAULT.withAllow(false).withFilter(0, new ItemStack(Items.GRAVEL))));
        check(helper, BackpackUpgrades.absorb(player, new ItemStack(Items.SAND, 3)).isEmpty(), "deny list refused sand");
        check(helper, BackpackUpgrades.absorb(player, new ItemStack(Items.GRAVEL, 3)).getCount() == 3, "deny list took gravel");
        // Overflow void keeps filtered items until full.
        data.upgrades().setItem(1, card(UpgradeType.VOID, UpgradeConfig.DEFAULT.withMode(1).withFilter(0, new ItemStack(Items.GRAVEL))));
        check(helper, BackpackUpgrades.absorb(player, new ItemStack(Items.GRAVEL, 3)).isEmpty() && count(data, Items.GRAVEL) == 3,
                "overflow void did not keep gravel while there was room");
        // Disabled cards do nothing.
        data.upgrades().setItem(0, card(UpgradeType.PICKUP, UpgradeConfig.DEFAULT.withEnabled(false)));
        check(helper, BackpackUpgrades.absorb(player, new ItemStack(Items.SAND, 3)).getCount() == 3, "a disabled pickup card took sand");

        // The loader hook consumes a touched item entity completely.
        data.upgrades().setItem(0, card(UpgradeType.PICKUP, UpgradeConfig.DEFAULT));
        BlockPos spawn = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.AIR);
        ItemEntity entity = new ItemEntity(helper.getLevel(), spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, new ItemStack(Items.OAK_LOG, 12));
        helper.getLevel().addFreshEntity(entity);
        check(helper, BackpackHooks.onItemPickup(player, entity), "hook did not report a full pickup");
        check(helper, entity.isRemoved() && count(data, Items.OAK_LOG) == 12, "hook did not move the logs into the backpack");
        helper.succeed();
    }

    /** Compacting crafts 9 iron ingots into a block with the server's recipes, chaining nuggets -> ingots -> blocks. */
    @GameTest(template = Templates.FLOOR)
    public static void compactingMakesBlocks(GameTestHelper helper) {
        BackpackData data = data(helper, backpack(BackpackTier.COPPER));
        UpgradeConfig config = UpgradeConfig.DEFAULT;
        data.items().insert(new ItemStack(Items.IRON_INGOT, 9), false);
        check(helper, BackpackUpgrades.compact(helper.getLevel(), data, config), "compacting crafted nothing");
        check(helper, count(data, Items.IRON_INGOT) == 0 && count(data, Items.IRON_BLOCK) == 1, "9 iron ingots did not become an iron block");

        data.items().insert(new ItemStack(Items.GOLD_NUGGET, 84), false);
        BackpackUpgrades.compact(helper.getLevel(), data, config);
        check(helper, count(data, Items.GOLD_BLOCK) == 1 && count(data, Items.GOLD_NUGGET) == 3 && count(data, Items.GOLD_INGOT) == 0,
                "gold nuggets did not chain into a block");

        // Not reversible (sand -> sandstone) and not filtered items stay as they are.
        data.items().insert(new ItemStack(Items.SAND, 8), false);
        data.items().insert(new ItemStack(Items.REDSTONE, 9), false);
        BackpackUpgrades.compact(helper.getLevel(), data, config.withFilter(0, new ItemStack(Items.SAND)));
        check(helper, count(data, Items.SAND) == 8 && count(data, Items.REDSTONE) == 9, "compacting touched sand or unfiltered redstone");
        helper.succeed();
    }

    /** Upgrading the tier (crafting and smithing) keeps the UUID, so every item stays inside. */
    @GameTest(template = Templates.FLOOR)
    public static void tierUpgradeKeepsContents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack leather = backpack(BackpackTier.LEATHER);
        leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0xFF0000, true));
        BackpackData data = data(helper, leather);
        UUID id = Backpacks.getId(leather);
        data.items().setItem(26, new ItemStack(Items.EMERALD, 7));

        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(i == 4 ? leather : new ItemStack(Items.COPPER_INGOT));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        check(helper, recipe.isPresent(), "no copper backpack recipe matched");
        ItemStack copper = recipe.get().value().assemble(input, level.registryAccess());
        check(helper, copper.is(BackpackContent.item(BackpackTier.COPPER)), "recipe made " + copper);
        check(helper, id.equals(Backpacks.getId(copper)), "tier upgrade changed the UUID");
        check(helper, !copper.has(DataComponents.DYED_COLOR), "only leather backpacks stay dyed");
        BackpackData upgraded = Backpacks.data(level.getServer(), copper);
        check(helper, upgraded == data && data.items().getContainerSize() == 36 && data.upgrades().getContainerSize() == 2,
                "upgraded backpack did not grow to 36 slots / 2 upgrade slots");
        check(helper, data.items().getItem(26).is(Items.EMERALD), "upgraded backpack lost its emeralds");

        ItemStack diamond = copper.transmuteCopy(BackpackContent.item(BackpackTier.DIAMOND), 1);
        SmithingRecipeInput smithing = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), diamond, new ItemStack(Items.NETHERITE_INGOT));
        var smith = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, smithing, level);
        check(helper, smith.isPresent(), "no netherite backpack smithing recipe");
        ItemStack netherite = smith.get().value().assemble(smithing, level.registryAccess());
        check(helper, netherite.is(BackpackContent.item(BackpackTier.NETHERITE)) && id.equals(Backpacks.getId(netherite)), "smithing lost the UUID");
        check(helper, netherite.has(DataComponents.FIRE_RESISTANT) || netherite.getItem().components().has(DataComponents.FIRE_RESISTANT),
                "netherite backpack is not fire resistant");
        check(helper, Backpacks.data(level.getServer(), netherite).items().getContainerSize() == 120, "netherite backpack does not have 120 slots");
        helper.succeed();
    }

    /** Backpacks (and shulker boxes) never go inside backpacks: not by rule, slot, shift-click or automation. */
    @GameTest(template = Templates.FLOOR)
    public static void backpackInBackpackRejected(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = backpack(BackpackTier.LEATHER);
        BackpackData data = data(helper, stack);
        UUID id = Backpacks.getId(stack);
        player.getInventory().setItem(0, stack);
        ItemStack other = backpack(BackpackTier.GOLD);
        player.getInventory().setItem(5, other);
        player.getInventory().setItem(6, new ItemStack(Items.DIRT, 20));

        check(helper, !Backpacks.canStore(other) && !Backpacks.canStore(new ItemStack(Items.SHULKER_BOX)), "rule accepts backpacks or shulker boxes");
        check(helper, Backpacks.canStore(new ItemStack(Items.DIRT)), "rule rejects dirt");
        check(helper, data.items().insert(other, false).getCount() == 1, "inventory accepted a backpack");
        check(helper, !new BackpackSlotAccess(data.items()).insertItem(0, other, false).isEmpty(), "slot access accepted a backpack");

        BackpackMenu menu = new BackpackMenu(1, player.getInventory(), data, BackpackTier.LEATHER, new BackpackSource.Held(0), id);
        check(helper, !menu.getSlot(0).mayPlace(other), "backpack slot accepts a backpack");
        menu.quickMoveStack(player, indexOfInventorySlot(menu, player, 5));
        check(helper, player.getInventory().getItem(5).is(other.getItem()) && data.items().isEmpty(), "shift-click moved a backpack inside");
        menu.quickMoveStack(player, indexOfInventorySlot(menu, player, 6));
        check(helper, count(data, Items.DIRT) == 20, "shift-click did not move dirt in");
        check(helper, menu.lockedMenuSlot() == indexOfInventorySlot(menu, player, 0), "the open backpack's slot is not locked");
        helper.succeed();
    }

    private static int indexOfInventorySlot(BackpackMenu menu, Player player, int inventorySlot) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container == player.getInventory() && menu.slots.get(i).getContainerSlot() == inventorySlot) {
                return i;
            }
        }
        throw new IllegalStateException("inventory slot " + inventorySlot + " is not in the menu");
    }

    /** Big backpacks scroll: the visible slots swap which backing slot they show. */
    @GameTest(template = Templates.FLOOR)
    public static void virtualSlotsFollowScroll(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = backpack(BackpackTier.NETHERITE);
        BackpackData data = data(helper, stack);
        player.getInventory().setItem(0, stack);
        data.items().setItem(100, new ItemStack(Items.NETHER_STAR));
        data.items().setItem(5, new ItemStack(Items.APPLE));
        BackpackMenu menu = new BackpackMenu(1, player.getInventory(), data, BackpackTier.NETHERITE, new BackpackSource.Held(0), Backpacks.getId(stack));
        check(helper, menu.layout().visibleSlots() == 72 && menu.layout().maxScroll() == 4, "unexpected layout " + menu.layout());
        check(helper, menu.getSlot(5).getItem().is(Items.APPLE), "row 0 does not show slot 5");
        menu.clickMenuButton(player, BackpackMenu.SCROLL_BUTTON + 4);
        check(helper, menu.scrollRow() == 4, "scroll row not applied");
        check(helper, menu.getSlot(100 - 48).getItem().is(Items.NETHER_STAR), "scrolled slot does not show backing slot 100");
        menu.getSlot(52).set(ItemStack.EMPTY);
        check(helper, data.items().getItem(100).isEmpty(), "writing a scrolled slot did not reach backing slot 100");
        menu.clickMenuButton(player, BackpackMenu.SCROLL_BUTTON + 99);
        check(helper, menu.scrollRow() == 4, "scroll row was not clamped");

        // Gold: 81 slots in rows of 12, so the last row is partial and its missing slots are inactive.
        ItemStack gold = backpack(BackpackTier.GOLD);
        BackpackData goldData = data(helper, gold);
        BackpackMenu goldMenu = new BackpackMenu(2, player.getInventory(), goldData, BackpackTier.GOLD, new BackpackSource.Held(1), Backpacks.getId(gold));
        player.getInventory().setItem(1, gold);
        goldMenu.clickMenuButton(player, BackpackMenu.SCROLL_BUTTON + 1);
        check(helper, goldMenu.getSlot(68).isActive() && !goldMenu.getSlot(69).isActive(), "partial last row is not handled");
        helper.succeed();
    }

    /** Feeding eats safe food when hungry and never poisonous food; Restock tops up the hotbar. */
    @GameTest(template = Templates.FLOOR)
    public static void feedingAndRestock(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = backpack(BackpackTier.IRON);
        BackpackData data = data(helper, stack);
        data.items().setItem(0, new ItemStack(Items.ROTTEN_FLESH, 4));
        data.items().setItem(1, new ItemStack(Items.BREAD, 2));
        data.items().setItem(2, new ItemStack(Items.TORCH, 64));
        player.getInventory().setItem(8, stack);
        player.getFoodData().setFoodLevel(10);
        boolean ate = BackpackUpgrades.feed(helper.getLevel(), player, data, UpgradeConfig.DEFAULT);
        check(helper, ate && player.getFoodData().getFoodLevel() > 10, "feeding did not eat");
        check(helper, count(data, Items.ROTTEN_FLESH) == 4 && count(data, Items.BREAD) == 1, "feeding ate the wrong food");

        player.getInventory().setItem(0, new ItemStack(Items.TORCH, 10));
        BackpackUpgrades.restock(player, data, UpgradeConfig.DEFAULT);
        check(helper, player.getInventory().getItem(0).getCount() == 64 && count(data, Items.TORCH) == 10, "restock did not top up the torches");
        helper.succeed();
    }

    /** Deposit empties filtered items into a container; Everlasting makes a dropped backpack indestructible. */
    @GameTest(template = Templates.FLOOR)
    public static void depositAndEverlasting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack stack = backpack(BackpackTier.GOLD);
        BackpackData data = data(helper, stack);
        data.items().insert(new ItemStack(Items.COBBLESTONE, 70), false);
        data.items().insert(new ItemStack(Items.DIAMOND, 3), false);
        BlockPos chest = new BlockPos(2, 2, 2);
        helper.setBlock(chest, Blocks.CHEST);
        var target = Services.PLATFORM.findSlotAccess(level, helper.absolutePos(chest), null).orElseThrow();
        int moved = BackpackUpgrades.deposit(data, UpgradeConfig.DEFAULT.withFilter(0, new ItemStack(Items.COBBLESTONE)), target);
        check(helper, moved == 70 && count(data, Items.COBBLESTONE) == 0 && count(data, Items.DIAMOND) == 3, "deposit moved " + moved);

        install(data, 0, UpgradeType.EVERLASTING, UpgradeConfig.DEFAULT);
        helper.setBlock(new BlockPos(5, 2, 5), Blocks.AIR);
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
        ItemEntity entity = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, stack.copy());
        level.addFreshEntity(entity);
        // EntityJoinLevelEvent -> BackpackHooks.onItemEntityJoin
        check(helper, entity.isInvulnerable() && entity.getAge() == -32768, "everlasting backpack can still despawn or be destroyed");
        entity.discard();
        helper.succeed();
    }
}
