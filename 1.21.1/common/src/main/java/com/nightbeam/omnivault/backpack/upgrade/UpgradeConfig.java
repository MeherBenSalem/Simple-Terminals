package com.nightbeam.omnivault.backpack.upgrade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Settings of one upgrade card, stored on the card itself so they travel with it between backpacks.
 *
 * @param filter          {@link #FILTER_SIZE} ghost stacks (empty entries are unused)
 * @param allow           true = allow list, false = deny list
 * @param matchComponents true = filter entries must match data components too (enchantments, damage, names...)
 * @param range           magnet range in blocks, 0 = the configured default
 * @param mode            upgrade-specific mode, see {@link UpgradeType}
 * @param enabled         disabled upgrades do nothing
 */
public record UpgradeConfig(List<ItemStack> filter, boolean allow, boolean matchComponents, int range, int mode, boolean enabled) {
    public static final int FILTER_SIZE = 9;
    public static final UpgradeConfig DEFAULT = new UpgradeConfig(emptyFilter(), true, false, 0, 0, true);

    public static final Codec<UpgradeConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("filter", List.of()).forGetter(UpgradeConfig::filter),
            Codec.BOOL.optionalFieldOf("allow", true).forGetter(UpgradeConfig::allow),
            Codec.BOOL.optionalFieldOf("match_components", false).forGetter(UpgradeConfig::matchComponents),
            Codec.INT.optionalFieldOf("range", 0).forGetter(UpgradeConfig::range),
            Codec.INT.optionalFieldOf("mode", 0).forGetter(UpgradeConfig::mode),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(UpgradeConfig::enabled)
    ).apply(i, UpgradeConfig::new));

    private static final StreamCodec<ByteBuf, Integer> SMALL_INT = ByteBufCodecs.VAR_INT;
    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeConfig> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, UpgradeConfig::filter,
            ByteBufCodecs.BOOL, UpgradeConfig::allow,
            ByteBufCodecs.BOOL, UpgradeConfig::matchComponents,
            SMALL_INT, UpgradeConfig::range,
            SMALL_INT, UpgradeConfig::mode,
            ByteBufCodecs.BOOL, UpgradeConfig::enabled,
            UpgradeConfig::new);

    public UpgradeConfig {
        // Normalise to exactly FILTER_SIZE single-item entries so equal settings compare equal.
        List<ItemStack> normalised = new ArrayList<>(FILTER_SIZE);
        for (int i = 0; i < FILTER_SIZE; i++) {
            ItemStack stack = i < filter.size() ? filter.get(i) : ItemStack.EMPTY;
            normalised.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        filter = Collections.unmodifiableList(normalised);
        range = Math.max(0, range);
        mode = Math.max(0, mode);
    }

    private static List<ItemStack> emptyFilter() {
        return NonNullList.withSize(FILTER_SIZE, ItemStack.EMPTY);
    }

    public boolean filterEmpty() {
        return filter.stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * Whether {@code stack} passes this filter.
     *
     * @param emptyAllowsAll what an empty filter (allow or deny) means: true for helpful upgrades (pickup,
     *                       magnet, restock...), false for destructive ones (void) so a blank card never destroys anything
     */
    public boolean matches(ItemStack stack, boolean emptyAllowsAll) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean listed = false;
        boolean any = false;
        for (ItemStack entry : filter) {
            if (entry.isEmpty()) {
                continue;
            }
            any = true;
            if (matchComponents ? ItemStack.isSameItemSameComponents(entry, stack) : ItemStack.isSameItem(entry, stack)) {
                listed = true;
                break;
            }
        }
        if (!any) {
            return emptyAllowsAll;
        }
        return allow == listed;
    }

    public UpgradeConfig withFilter(int index, ItemStack stack) {
        if (index < 0 || index >= FILTER_SIZE) {
            return this;
        }
        List<ItemStack> copy = new ArrayList<>(filter);
        copy.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        return new UpgradeConfig(copy, allow, matchComponents, range, mode, enabled);
    }

    public UpgradeConfig withAllow(boolean value) {
        return new UpgradeConfig(filter, value, matchComponents, range, mode, enabled);
    }

    public UpgradeConfig withMatchComponents(boolean value) {
        return new UpgradeConfig(filter, allow, value, range, mode, enabled);
    }

    public UpgradeConfig withRange(int value) {
        return new UpgradeConfig(filter, allow, matchComponents, value, mode, enabled);
    }

    public UpgradeConfig withMode(int value) {
        return new UpgradeConfig(filter, allow, matchComponents, range, value, enabled);
    }

    public UpgradeConfig withEnabled(boolean value) {
        return new UpgradeConfig(filter, allow, matchComponents, range, mode, value);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof UpgradeConfig other && allow == other.allow && matchComponents == other.matchComponents
                && range == other.range && mode == other.mode && enabled == other.enabled && ItemStack.listMatches(filter, other.filter));
    }

    @Override
    public int hashCode() {
        int hash = ItemStack.hashStackList(filter);
        hash = 31 * hash + (allow ? 1 : 0);
        hash = 31 * hash + (matchComponents ? 1 : 0);
        hash = 31 * hash + range;
        hash = 31 * hash + mode;
        return 31 * hash + (enabled ? 1 : 0);
    }
}
