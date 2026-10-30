package com.nightbeam.omnivault.physical.linked;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A Linked Vault channel: three latch colours plus, for private channels, the owner. Every vault and
 * pouch with the same {@link #key()} shares one inventory.
 */
public record LinkedFrequency(DyeColor first, DyeColor second, DyeColor third, Optional<UUID> owner, String ownerName) {
    public static final LinkedFrequency DEFAULT = new LinkedFrequency(DyeColor.WHITE, DyeColor.WHITE, DyeColor.WHITE, Optional.empty(), "");

    public static final Codec<LinkedFrequency> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DyeColor.CODEC.fieldOf("first").forGetter(LinkedFrequency::first),
            DyeColor.CODEC.fieldOf("second").forGetter(LinkedFrequency::second),
            DyeColor.CODEC.fieldOf("third").forGetter(LinkedFrequency::third),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(LinkedFrequency::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(LinkedFrequency::ownerName)
    ).apply(instance, LinkedFrequency::new));

    public static final StreamCodec<ByteBuf, LinkedFrequency> STREAM_CODEC = StreamCodec.composite(
            DyeColor.STREAM_CODEC, LinkedFrequency::first,
            DyeColor.STREAM_CODEC, LinkedFrequency::second,
            DyeColor.STREAM_CODEC, LinkedFrequency::third,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), LinkedFrequency::owner,
            ByteBufCodecs.STRING_UTF8, LinkedFrequency::ownerName,
            LinkedFrequency::new);

    public DyeColor color(int latch) {
        return switch (latch) {
            case 0 -> first;
            case 1 -> second;
            default -> third;
        };
    }

    public LinkedFrequency withColor(int latch, DyeColor color) {
        return switch (latch) {
            case 0 -> new LinkedFrequency(color, second, third, owner, ownerName);
            case 1 -> new LinkedFrequency(first, color, third, owner, ownerName);
            default -> new LinkedFrequency(first, second, color, owner, ownerName);
        };
    }

    public LinkedFrequency withOwner(UUID uuid, String name) {
        return new LinkedFrequency(first, second, third, Optional.of(uuid), name);
    }

    public LinkedFrequency asPublic() {
        return new LinkedFrequency(first, second, third, Optional.empty(), "");
    }

    public boolean isPrivate() {
        return owner.isPresent();
    }

    public boolean isOwner(UUID uuid) {
        return owner.map(uuid::equals).orElse(true);
    }

    /** The storage key: colours, plus the owner for private channels. */
    public String key() {
        String colors = first.getName() + "/" + second.getName() + "/" + third.getName();
        return owner.map(uuid -> colors + "@" + uuid).orElse(colors);
    }

    public static Component colorName(DyeColor color) {
        return Component.translatable("color.minecraft." + color.getName());
    }

    /** "White · Red · Blue" with each name in a readable tint of its colour. */
    public MutableComponent describe() {
        MutableComponent text = Component.empty();
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            }
            DyeColor color = color(i);
            text.append(colorName(color).copy().withStyle(style -> style.withColor(readable(color))));
        }
        return text;
    }

    public void appendTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.omnivault.linked.frequency", describe()).withStyle(ChatFormatting.GRAY));
        if (isPrivate()) {
            tooltip.add(Component.translatable("tooltip.omnivault.linked.private", ownerName.isEmpty() ? "?" : ownerName)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static int readable(DyeColor color) {
        int rgb = color.getTextColor();
        return color == DyeColor.BLACK ? 0x6A6A6A : rgb;
    }

    /** Opaque ARGB tint for a latch (used by renderers and item colours). */
    public static int tint(DyeColor color) {
        return 0xFF000000 | color.getTextureDiffuseColor();
    }
}
