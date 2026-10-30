package com.nightbeam.omnivault.backpack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * A tiny copy of what a backpack holds, kept on the item so clients can show tooltips without the
 * contents ever being sent: used slots, total slots and the installed upgrade cards.
 */
public record BackpackSummary(int used, int slots, List<Item> upgrades) {
    public static final BackpackSummary EMPTY = new BackpackSummary(0, 0, List.of());

    public static final Codec<BackpackSummary> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("used").forGetter(BackpackSummary::used),
            Codec.INT.fieldOf("slots").forGetter(BackpackSummary::slots),
            BuiltInRegistries.ITEM.byNameCodec().listOf().optionalFieldOf("upgrades", List.of()).forGetter(BackpackSummary::upgrades)
    ).apply(i, BackpackSummary::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackSummary> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BackpackSummary::used,
            ByteBufCodecs.VAR_INT, BackpackSummary::slots,
            ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list()), BackpackSummary::upgrades,
            BackpackSummary::new);

    public BackpackSummary {
        upgrades = List.copyOf(upgrades);
    }

    public static BackpackSummary of(BackpackData data) {
        return new BackpackSummary(data.items().usedSlots(), data.items().getContainerSize(),
                data.installedUpgrades().stream().map(stack -> stack.getItem()).toList());
    }
}
