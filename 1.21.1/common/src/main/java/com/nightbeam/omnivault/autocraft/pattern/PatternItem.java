package com.nightbeam.omnivault.autocraft.pattern;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blank and encoded patterns. Encoded patterns carry {@link PatternData} in the
 * {@code omnivault:pattern} component; sneak-using one wipes it back to a blank pattern.
 */
public class PatternItem extends Item {
    public enum Kind {
        BLANK, CRAFTING, PROCESSING
    }

    private final Kind kind;

    public PatternItem(Kind kind, Properties properties) {
        super(kind == Kind.BLANK ? properties : properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static boolean isBlank(ItemStack stack) {
        return stack.getItem() instanceof PatternItem item && item.kind == Kind.BLANK;
    }

    public static boolean isEncoded(ItemStack stack) {
        return stack.getItem() instanceof PatternItem item && item.kind != Kind.BLANK && data(stack) != null;
    }

    @Nullable
    public static PatternData data(ItemStack stack) {
        if (!(stack.getItem() instanceof PatternItem item) || item.kind == Kind.BLANK) {
            return null;
        }
        PatternData data = stack.get(AutocraftContent.PATTERN.get());
        return data != null && data.isValid() ? data : null;
    }

    /** A new encoded pattern of the right kind for {@code data}. */
    public static ItemStack encode(PatternData data) {
        ItemStack stack = new ItemStack(data.processing() ? AutocraftContent.PROCESSING_PATTERN.get() : AutocraftContent.CRAFTING_PATTERN.get());
        stack.set(AutocraftContent.PATTERN.get(), data);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (kind == Kind.BLANK || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.4f);
            player.displayClientMessage(Component.translatable("message.omnivault.pattern.cleared").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResultHolder.sidedSuccess(new ItemStack(AutocraftContent.BLANK_PATTERN.get(), stack.getCount()), level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (kind == Kind.BLANK) {
            tooltip.add(Component.translatable("tooltip.omnivault.pattern.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        PatternData data = data(stack);
        if (data == null) {
            tooltip.add(Component.translatable("tooltip.omnivault.pattern.invalid").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.translatable("tooltip.omnivault.pattern.produces").withStyle(ChatFormatting.AQUA));
        for (Map.Entry<ItemKey, Long> entry : aggregate(data.outputs()).entrySet()) {
            tooltip.add(line(entry.getKey(), entry.getValue(), ChatFormatting.WHITE));
        }
        tooltip.add(Component.translatable("tooltip.omnivault.pattern.consumes").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (Map.Entry<ItemKey, Long> entry : aggregate(data.inputs()).entrySet()) {
            tooltip.add(line(entry.getKey(), entry.getValue(), ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.omnivault.pattern.clear").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component line(ItemKey key, long amount, ChatFormatting color) {
        return Component.translatable("tooltip.omnivault.pattern.entry", amount, key.view().getHoverName()).withStyle(color);
    }

    private static Map<ItemKey, Long> aggregate(List<ItemStack> stacks) {
        Map<ItemKey, Long> out = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            ItemKey key = ItemKey.of(stack);
            if (key != null) {
                out.merge(key, (long) stack.getCount(), Long::sum);
            }
        }
        return out;
    }
}
