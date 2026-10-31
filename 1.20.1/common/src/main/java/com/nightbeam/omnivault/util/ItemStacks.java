package com.nightbeam.omnivault.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Item stack helpers that later Minecraft versions have on {@code ItemStack} itself. */
public final class ItemStacks {
    private ItemStacks() {
    }

    /** Uses up {@code amount} items, unless a creative player is using them. */
    public static void consume(ItemStack stack, int amount, @Nullable LivingEntity user) {
        if (!(user instanceof Player player && player.getAbilities().instabuild)) {
            stack.shrink(amount);
        }
    }

    /** A hash consistent with {@link ItemStack#isSameItemSameTags}. */
    public static int hashItemAndTag(ItemStack stack) {
        return stack.isEmpty() ? 0 : 31 * stack.getItem().hashCode() + Objects.hashCode(stack.getTag());
    }

    public static int hashStackList(List<ItemStack> stacks) {
        int hash = 0;
        for (ItemStack stack : stacks) {
            hash = 31 * hash + hashItemAndTag(stack);
        }
        return hash;
    }

    /** Same size, and every pair of stacks has the same item, NBT and count. */
    public static boolean listMatches(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }
}
