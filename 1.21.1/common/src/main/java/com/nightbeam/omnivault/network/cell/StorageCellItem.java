package com.nightbeam.omnivault.network.cell;

import com.nightbeam.omnivault.network.NetworkConfig;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

/** A storage cell. Its contents live in {@link CellData} under the UUID stored on the stack. */
public class StorageCellItem extends Item {
    private final CellTier tier;
    @Nullable
    private final Supplier<? extends Item> matrix;

    public StorageCellItem(CellTier tier, @Nullable Supplier<? extends Item> matrix, Properties properties) {
        super(properties.stacksTo(1).rarity(tier.rarity));
        this.tier = tier;
        this.matrix = matrix;
    }

    public CellTier tier() {
        return tier;
    }

    /** The cell's id, assigning a new one the first time it is needed. */
    public static UUID getOrCreateId(ItemStack stack) {
        UUID id = stack.get(NetworkContent.CELL_ID.get());
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(NetworkContent.CELL_ID.get(), id);
        }
        return id;
    }

    public static CellUsage usage(ItemStack stack) {
        CellUsage usage = stack.get(NetworkContent.CELL_USAGE.get());
        return usage == null ? CellUsage.EMPTY : usage;
    }

    /** 0 = room left, 1 = nearly full, 2 = full (by items or types). Drives show it as LED colours. */
    public static int fillState(ItemStack stack) {
        if (!(stack.getItem() instanceof StorageCellItem cell) || cell.tier.isCreative()) {
            return 0;
        }
        CellUsage usage = usage(stack);
        if (usage.stored() >= cell.tier.capacity || usage.types() >= NetworkConfig.CELL_TYPES.get()) {
            return 2;
        }
        return usage.stored() >= cell.tier.capacity * 3 / 4 ? 1 : 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Sneak-use an empty cell to take it apart into its housing and storage matrix.
        if (!player.isShiftKeyDown() || matrix == null || usage(stack).stored() > 0) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            UUID id = stack.get(NetworkContent.CELL_ID.get());
            if (id != null && level.getServer() != null) {
                CellData data = CellData.get(level.getServer());
                CellData.CellInventory inventory = data.getIfPresent(id);
                if (inventory != null && inventory.stored() > 0) {
                    return InteractionResultHolder.fail(stack);
                }
                data.remove(id);
            }
            player.getInventory().placeItemBackInInventory(new ItemStack(NetworkContent.CELL_HOUSING.get()));
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.8f, 1.3f);
        }
        return InteractionResultHolder.sidedSuccess(new ItemStack(matrix.get()), level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !tier.isCreative() && stack.has(NetworkContent.CELL_USAGE.get()) && usage(stack).stored() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * Math.min(1.0f, usage(stack).stored() / (float) tier.capacity));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return switch (fillState(stack)) {
            case 2 -> 0xFF6B6B;
            case 1 -> 0xF2C14E;
            default -> Mth.hsvToRgb(0.52f, 0.8f, 1.0f);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CellUsage usage = usage(stack);
        if (tier.isCreative()) {
            tooltip.add(Component.translatable("tooltip.omnivault.cell.creative").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.omnivault.cell.items_unbounded", Formatting.full(usage.stored())).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.omnivault.cell.items", Formatting.full(usage.stored()), Formatting.full(tier.capacity)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.omnivault.cell.types", usage.types(), NetworkConfig.CELL_TYPES.get()).withStyle(ChatFormatting.GRAY));
        }
        if (matrix != null && usage.stored() == 0) {
            tooltip.add(Component.translatable("tooltip.omnivault.cell.disassemble").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
