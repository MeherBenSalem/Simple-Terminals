package com.nightbeam.omnivault.backpack.upgrade;

import com.nightbeam.omnivault.backpack.BackpackBlockEntity;
import com.nightbeam.omnivault.backpack.BackpackConfig;
import com.nightbeam.omnivault.backpack.BackpackContent;
import com.nightbeam.omnivault.backpack.BackpackData;
import com.nightbeam.omnivault.backpack.BackpackInventory;
import com.nightbeam.omnivault.backpack.BackpackSummary;
import com.nightbeam.omnivault.backpack.Backpacks;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.SlotAccess;
import com.nightbeam.omnivault.storage.SlotAccessStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What each backpack upgrade does. Everything here runs on the logical server. */
public final class BackpackUpgrades {
    private static final Map<ItemKey, Optional<Compression>> COMPRESSIONS = new HashMap<>();
    @Nullable
    private static RecipeManager compressionRecipes;

    private BackpackUpgrades() {
    }

    // ---------------------------------------------------------------------------------------------
    // Ticking
    // ---------------------------------------------------------------------------------------------

    /** Called every tick for each backpack in a player's inventory (including the worn one). */
    public static void tickCarried(ItemStack stack, ServerLevel level, Player player) {
        BackpackData data = Backpacks.existingData(level.getServer(), stack);
        if (data == null) {
            return;
        }
        int age = player.tickCount;
        if (age % 10 == 0) {
            Backpacks.refreshSummary(stack, data);
        }
        UpgradeConfig magnet = data.config(UpgradeType.MAGNET);
        if (magnet != null && age % BackpackConfig.MAGNET_INTERVAL.get() == 0 && !player.isSpectator()) {
            magnet(level, player, magnet);
        }
        UpgradeConfig feeding = data.config(UpgradeType.FEEDING);
        if (feeding != null && age % BackpackConfig.FEEDING_INTERVAL.get() == 0) {
            feed(level, player, data, feeding);
        }
        UpgradeConfig restock = data.config(UpgradeType.RESTOCK);
        if (restock != null && age % BackpackConfig.RESTOCK_INTERVAL.get() == 0) {
            restock(player, data, restock);
        }
        UpgradeConfig compacting = data.config(UpgradeType.COMPACTING);
        if (compacting != null && age % BackpackConfig.COMPACTING_INTERVAL.get() == 0) {
            compact(level, data, compacting);
        }
    }

    /** Called every tick by a placed backpack. */
    public static void tickPlaced(ServerLevel level, BlockPos pos, BackpackBlockEntity blockEntity) {
        long time = level.getGameTime() + pos.asLong();
        BackpackData data = blockEntity.data();
        if (data == null) {
            return;
        }
        blockEntity.notifyIfContentsChanged(data);
        if (time % 20 == 0) {
            BackpackSummary before = blockEntity.getBackpack().get(BackpackContent.SUMMARY.get());
            Backpacks.refreshSummary(blockEntity.getBackpack(), data);
            if (before == null || !before.equals(blockEntity.getBackpack().get(BackpackContent.SUMMARY.get()))) {
                blockEntity.setChanged();
            }
        }
        if (!BackpackConfig.PLACED_UPGRADES.get()) {
            return;
        }
        UpgradeConfig magnet = data.config(UpgradeType.MAGNET);
        if (magnet != null && time % BackpackConfig.MAGNET_INTERVAL.get() == 0) {
            AABB box = new AABB(pos).inflate(range(magnet));
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay())) {
                if (!magnet.matches(entity.getItem(), true)) {
                    continue;
                }
                ItemStack rest = absorbInto(data, entity.getItem(), false);
                if (rest.getCount() != entity.getItem().getCount()) {
                    if (rest.isEmpty()) {
                        entity.discard();
                    } else {
                        entity.setItem(rest);
                    }
                }
            }
        }
        UpgradeConfig compacting = data.config(UpgradeType.COMPACTING);
        if (compacting != null && time % BackpackConfig.COMPACTING_INTERVAL.get() == 0) {
            compact(level, data, compacting);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Pickup and void
    // ---------------------------------------------------------------------------------------------

    /**
     * Offers a stack the player is picking up to their backpacks. Void upgrades (destroy mode) go first, then
     * pickup upgrades store what matches, then void upgrades in overflow mode store what fits and destroy the rest.
     *
     * @return what is left for the player's own inventory ({@code stack} is not modified)
     */
    public static ItemStack absorb(Player player, ItemStack stack) {
        if (stack.isEmpty() || player.level().isClientSide || Backpacks.isBackpack(stack)) {
            return stack.copy();
        }
        List<BackpackData> backpacks = new ArrayList<>();
        for (int slot : Backpacks.carriedSlots(player)) {
            BackpackData data = Backpacks.existingData(player.level().getServer(), player.getInventory().getItem(slot));
            if (data != null && !data.installedUpgrades().isEmpty()) {
                backpacks.add(data);
            }
        }
        ItemStack rest = stack.copy();
        for (BackpackData data : backpacks) {
            UpgradeConfig voiding = data.config(UpgradeType.VOID);
            if (voiding != null && voiding.mode() == 0 && voiding.matches(rest, false)) {
                return ItemStack.EMPTY;
            }
        }
        for (BackpackData data : backpacks) {
            UpgradeConfig pickup = data.config(UpgradeType.PICKUP);
            if (pickup != null && pickup.matches(rest, true)) {
                rest = data.items().insert(rest, false);
                if (rest.isEmpty()) {
                    return rest;
                }
            }
        }
        for (BackpackData data : backpacks) {
            UpgradeConfig voiding = data.config(UpgradeType.VOID);
            if (voiding != null && voiding.mode() == 1 && voiding.matches(rest, false)) {
                data.items().insert(rest, false);
                return ItemStack.EMPTY;
            }
        }
        return rest;
    }

    /** The same rules for one backpack (a placed one, whose magnet stores directly). */
    public static ItemStack absorbInto(BackpackData data, ItemStack stack, boolean requirePickup) {
        UpgradeConfig voiding = data.config(UpgradeType.VOID);
        if (voiding != null && voiding.mode() == 0 && voiding.matches(stack, false)) {
            return ItemStack.EMPTY;
        }
        UpgradeConfig pickup = data.config(UpgradeType.PICKUP);
        ItemStack rest = stack.copy();
        if (!requirePickup || (pickup != null && pickup.matches(rest, true))) {
            rest = data.items().insert(rest, false);
        }
        if (!rest.isEmpty() && voiding != null && voiding.mode() == 1 && voiding.matches(rest, false)) {
            data.items().insert(rest, false);
            return ItemStack.EMPTY;
        }
        return rest;
    }

    // ---------------------------------------------------------------------------------------------
    // Magnet
    // ---------------------------------------------------------------------------------------------

    public static int range(UpgradeConfig config) {
        int max = BackpackConfig.MAGNET_MAX_RANGE.get();
        return Math.max(1, Math.min(max, config.range() > 0 ? config.range() : BackpackConfig.MAGNET_DEFAULT_RANGE.get()));
    }

    private static void magnet(ServerLevel level, Player player, UpgradeConfig config) {
        AABB box = player.getBoundingBox().inflate(range(config));
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay())) {
            if (config.matches(entity.getItem(), true)) {
                pull(entity, player);
            }
        }
        if (BackpackConfig.MAGNET_PULLS_XP.get()) {
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box, Entity::isAlive)) {
                pull(orb, player);
            }
        }
    }

    private static void pull(Entity entity, Player player) {
        Vec3 target = new Vec3(player.getX(), player.getY() + 0.25, player.getZ());
        Vec3 delta = target.subtract(entity.position());
        double distance = delta.length();
        if (distance < 1.5) {
            entity.setPos(target.x, player.getY(), target.z);
            entity.setDeltaMovement(Vec3.ZERO);
        } else {
            entity.setDeltaMovement(delta.scale(Math.min(0.9, 0.35 + distance * 0.04) / distance));
        }
        entity.hasImpulse = true;
    }

    // ---------------------------------------------------------------------------------------------
    // Feeding
    // ---------------------------------------------------------------------------------------------

    /** Food that can hurt (poison, hunger, nausea...) is never eaten automatically. */
    public static boolean isHarmful(ItemStack stack, FoodProperties food) {
        for (FoodProperties.PossibleEffect effect : food.effects()) {
            if (effect.effect().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                return true;
            }
        }
        SuspiciousStewEffects stew = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stew != null) {
            for (SuspiciousStewEffects.Entry entry : stew.effects()) {
                MobEffectInstance instance = entry.createEffectInstance();
                if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Eats at most one item. Returns true if something was eaten. */
    public static boolean feed(ServerLevel level, Player player, BackpackData data, UpgradeConfig config) {
        FoodData food = player.getFoodData();
        if (!food.needsFood() || player.isCreative() || player.isSpectator()) {
            return false;
        }
        int missing = 20 - food.getFoodLevel();
        boolean hurt = player.getHealth() < player.getMaxHealth();
        BackpackInventory items = data.items();
        for (int slot = 0; slot < items.getContainerSize(); slot++) {
            ItemStack stack = items.getItem(slot);
            FoodProperties properties = stack.get(DataComponents.FOOD);
            if (properties == null || !config.matches(stack, true) || isHarmful(stack, properties)) {
                continue;
            }
            // Eat when the food fits entirely, or when hurt / starving and anything helps.
            if (properties.nutrition() > missing && !(hurt && missing >= 2) && food.getFoodLevel() > 6) {
                continue;
            }
            ItemStack eaten = items.removeItem(slot, 1);
            ItemStack leftover = player.eat(level, eaten, properties);
            if (!leftover.isEmpty()) {
                ItemStack rest = items.insert(leftover, false);
                if (!rest.isEmpty() && !player.getInventory().add(rest)) {
                    player.drop(rest, false);
                }
            }
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Restock
    // ---------------------------------------------------------------------------------------------

    /** Tops up hotbar and off-hand stacks with matching items from the backpack. */
    public static void restock(Player player, BackpackData data, UpgradeConfig config) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot <= Inventory.getSelectionSize(); slot++) {
            int index = slot == Inventory.getSelectionSize() ? Inventory.SLOT_OFFHAND : slot;
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty() || !stack.isStackable() || stack.getCount() >= stack.getMaxStackSize()
                    || Backpacks.isBackpack(stack) || !config.matches(stack, true)) {
                continue;
            }
            ItemStack taken = data.items().extract(stack, stack.getMaxStackSize() - stack.getCount());
            if (!taken.isEmpty()) {
                stack.grow(taken.getCount());
                inventory.setChanged();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Compacting
    // ---------------------------------------------------------------------------------------------

    /** A 3x3 (size 9) or 2x2 (size 4) recipe that packs one item into another and can be undone. */
    public record Compression(ItemStack result, int size) {
    }

    /** Runs compacting once the contents changed since the last pass. Returns true if anything was crafted. */
    public static boolean compact(ServerLevel level, BackpackData data, UpgradeConfig config) {
        if (data.compactedVersion() == data.version()) {
            return false;
        }
        boolean any = false;
        boolean changed = true;
        for (int pass = 0; changed && pass < 8; pass++) {
            changed = false;
            Map<ItemKey, Integer> counts = new LinkedHashMap<>();
            BackpackInventory items = data.items();
            for (int slot = 0; slot < items.getContainerSize(); slot++) {
                ItemStack stack = items.getItem(slot);
                if (!stack.isEmpty() && config.matches(stack, true)) {
                    counts.merge(ItemKey.of(stack), stack.getCount(), Integer::sum);
                }
            }
            for (Map.Entry<ItemKey, Integer> entry : counts.entrySet()) {
                if (entry.getValue() < 4) {
                    continue;
                }
                Compression compression = compression(level, entry.getKey().view());
                if (compression == null || (config.mode() == 1 && compression.size() != 9) || entry.getValue() < compression.size()) {
                    continue;
                }
                if (craft(items, entry.getKey(), entry.getValue() / compression.size(), compression)) {
                    changed = true;
                    any = true;
                }
            }
        }
        data.markCompacted();
        return any;
    }

    private static boolean craft(BackpackInventory items, ItemKey ingredient, int crafts, Compression compression) {
        List<ItemStack> snapshot = items.snapshot();
        while (crafts > 0) {
            items.extract(ingredient.view(), crafts * compression.size());
            ItemStack rest = items.insert(compression.result().copyWithCount(crafts * compression.result().getCount()), false);
            if (rest.isEmpty()) {
                return true;
            }
            // Not enough room for every result: undo and try fewer crafts.
            items.restore(snapshot);
            crafts -= Math.max(1, (rest.getCount() + compression.result().getCount() - 1) / compression.result().getCount());
        }
        return false;
    }

    /** The reversible 3x3 (preferred) or 2x2 packing recipe for {@code ingredient}, cached per recipe reload. */
    @Nullable
    public static Compression compression(Level level, ItemStack ingredient) {
        RecipeManager recipes = level.getRecipeManager();
        if (recipes != compressionRecipes) {
            COMPRESSIONS.clear();
            compressionRecipes = recipes;
        }
        ItemKey key = ItemKey.of(ingredient);
        if (key == null) {
            return null;
        }
        return COMPRESSIONS.computeIfAbsent(key, k -> Optional.ofNullable(findCompression(level, k.view()))).orElse(null);
    }

    @Nullable
    private static Compression findCompression(Level level, ItemStack ingredient) {
        for (int side = 3; side >= 2; side--) {
            CraftingInput input = CraftingInput.of(side, side, Collections.nCopies(side * side, ingredient.copyWithCount(1)));
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            if (recipe.isEmpty()) {
                continue;
            }
            ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
            if (result.isEmpty() || Backpacks.isBackpack(result) || !Backpacks.canStore(result)) {
                continue;
            }
            // Only reversible packing (ingots <-> blocks), so compacting never loses anything.
            CraftingInput back = CraftingInput.of(1, 1, List.of(result.copyWithCount(1)));
            Optional<RecipeHolder<CraftingRecipe>> unpack = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, back, level);
            if (unpack.isEmpty()) {
                continue;
            }
            ItemStack unpacked = unpack.get().value().assemble(back, level.registryAccess());
            if (ItemStack.isSameItemSameComponents(unpacked, ingredient) && unpacked.getCount() == side * side / result.getCount()) {
                return new Compression(result, side * side);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Deposit
    // ---------------------------------------------------------------------------------------------

    /**
     * Sneak-right-click on a container with a backpack that has a Deposit Upgrade: moves matching items into it.
     *
     * @return PASS when the backpack should be placed instead
     */
    public static InteractionResult tryDeposit(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        if (player == null || level.getBlockEntity(pos) == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            BackpackSummary summary = stack.get(BackpackContent.SUMMARY.get());
            boolean hasDeposit = summary != null && summary.upgrades().contains(BackpackContent.upgrade(UpgradeType.DEPOSIT));
            return hasDeposit ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        BackpackData data = Backpacks.existingData(level.getServer(), stack);
        UpgradeConfig config = data == null ? null : data.config(UpgradeType.DEPOSIT);
        if (config == null) {
            return InteractionResult.PASS;
        }
        Optional<SlotAccess> target = Services.PLATFORM.findSlotAccess(level, pos, context.getClickedFace());
        if (target.isEmpty()) {
            return InteractionResult.PASS;
        }
        int moved = deposit(data, config, target.get());
        player.displayClientMessage(Component.translatable("message.omnivault.backpack.deposited", moved), true);
        level.playSound(null, pos, moved > 0 ? SoundEvents.BUNDLE_DROP_CONTENTS : SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1.0f);
        return InteractionResult.CONSUME;
    }

    /** Moves every matching stack it can from the backpack into {@code target}; returns the number of items moved. */
    public static int deposit(BackpackData data, UpgradeConfig config, SlotAccess target) {
        SlotAccessStorage storage = new SlotAccessStorage(target);
        BackpackInventory items = data.items();
        int moved = 0;
        for (int slot = 0; slot < items.getContainerSize(); slot++) {
            ItemStack stack = items.getItem(slot);
            if (stack.isEmpty() || !config.matches(stack, true)) {
                continue;
            }
            ItemKey key = ItemKey.of(stack);
            if (config.mode() == 1 && storage.getCount(key) <= 0) {
                continue;
            }
            long accepted = storage.insert(key, stack.getCount(), false);
            if (accepted > 0) {
                items.removeItem(slot, (int) accepted);
                moved += (int) accepted;
            }
        }
        return moved;
    }

    // ---------------------------------------------------------------------------------------------
    // Everlasting
    // ---------------------------------------------------------------------------------------------

    /** Whether a dropped backpack with these contents should never despawn or be destroyed. */
    public static boolean isEverlasting(BackpackData data) {
        return data.hasUpgrade(UpgradeType.EVERLASTING);
    }
}
