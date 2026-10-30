package com.nightbeam.omnivault.network.cell;

import com.nightbeam.omnivault.network.NetworkConfig;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.util.Formatting;
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

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** A fluid cell. Capacity is a quarter of the matching item cell's count, in buckets. */
public class FluidCellItem extends Item implements CellItem {
    private final CellTier tier;
    @Nullable
    private final Supplier<? extends Item> matrix;

    public FluidCellItem(CellTier tier, @Nullable Supplier<? extends Item> matrix, Properties properties) {
        super(properties.stacksTo(1).rarity(tier.rarity));
        this.tier = tier;
        this.matrix = matrix;
    }

    @Override
    public CellTier tier() {
        return tier;
    }

    @Override
    public long capacity() {
        return tier.isCreative() ? tier.capacity : tier.capacity * 250L;
    }

    @Override
    public int typeLimit() {
        return tier.isCreative() ? Integer.MAX_VALUE : NetworkConfig.FLUID_CELL_TYPES.get();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || matrix == null || StorageCellItem.usage(stack).stored() > 0) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            UUID id = stack.get(NetworkContent.CELL_ID.get());
            if (id != null && level.getServer() != null) {
                FluidCellData data = FluidCellData.get(level.getServer());
                FluidCellData.Tank tank = data.getIfPresent(id);
                if (tank != null && tank.stored() > 0) {
                    return InteractionResultHolder.fail(stack);
                }
                data.remove(id);
            }
            player.getInventory().placeItemBackInInventory(new ItemStack(NetworkContent.FLUID_CELL_HOUSING.get()));
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.8f, 1.3f);
        }
        return InteractionResultHolder.sidedSuccess(new ItemStack(matrix.get()), level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !tier.isCreative() && StorageCellItem.usage(stack).stored() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * Math.min(1.0f, StorageCellItem.usage(stack).stored() / (float) capacity()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return StorageCellItem.fillState(stack) == 2 ? 0xFF6B6B : 0x3F76E4;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CellUsage usage = StorageCellItem.usage(stack);
        if (tier.isCreative()) {
            tooltip.add(Component.translatable("tooltip.omnivault.cell.creative").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.omnivault.fluid_cell.buckets_unbounded", Formatting.full(usage.stored() / 1000)).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.omnivault.fluid_cell.buckets", Formatting.full(usage.stored() / 1000),
                    Formatting.full(capacity() / 1000)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.omnivault.cell.types", usage.types(), typeLimit()).withStyle(ChatFormatting.GRAY));
        }
        if (matrix != null && usage.stored() == 0) {
            tooltip.add(Component.translatable("tooltip.omnivault.cell.disassemble").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
