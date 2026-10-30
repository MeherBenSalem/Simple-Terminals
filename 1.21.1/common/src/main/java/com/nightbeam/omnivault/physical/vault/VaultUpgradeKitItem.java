package com.nightbeam.omnivault.physical.vault;

import com.nightbeam.omnivault.physical.BlockUseOverride;
import com.nightbeam.omnivault.physical.PhysicalConfig;
import com.nightbeam.omnivault.physical.PhysicalContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Upgrades a placed vault one tier in place, keeping its contents, name and facing. The copper kit also
 * converts vanilla chests and barrels (anything in {@code #omnivault:vault_upgradeable} whose inventory
 * fits). The kit is consumed unless the player is in creative mode.
 */
public class VaultUpgradeKitItem extends Item implements BlockUseOverride {
    private final VaultTier target;

    public VaultUpgradeKitItem(VaultTier target, Properties properties) {
        super(properties);
        this.target = target;
    }

    public VaultTier target() {
        return target;
    }

    /** True if this kit applies to the block (without checking contents or viewers). */
    public boolean accepts(BlockState state) {
        if (state.getBlock() instanceof VaultBlock vault) {
            return vault.tier() == target.previous();
        }
        return target.previous() == null && PhysicalConfig.VANILLA_KITS.get() && state.is(PhysicalContent.VAULT_UPGRADEABLE);
    }

    @Override
    public boolean overridesBlockUse(ItemStack stack, Level level, BlockPos pos, BlockState state, Player player) {
        // On any vault the kit wins, so a wrong-tier kit explains itself instead of opening the GUI.
        return accepts(state) || state.getBlock() instanceof VaultBlock;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (!accepts(state)) {
            if (state.getBlock() instanceof VaultBlock && player != null && !level.isClientSide) {
                VaultTier from = target.previous();
                player.displayClientMessage(Component.translatable("message.omnivault.vault_kit.wrong_tier",
                        Component.translatable(from == null ? "block.minecraft.chest" : "block.omnivault." + from.vaultName())).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Component failure = upgrade(level, pos, state, target, player);
        if (failure != null) {
            if (player != null) {
                player.displayClientMessage(failure.copy().withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Replaces the vault (or vanilla container) at {@code pos} with a {@code target} vault holding the same
     * items. Server side only.
     *
     * @return null on success, otherwise the reason it was refused.
     */
    @Nullable
    public static Component upgrade(Level level, BlockPos pos, BlockState state, VaultTier target, @Nullable Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            return Component.translatable("message.omnivault.vault_kit.unsupported");
        }
        if (container.getContainerSize() > target.size()) {
            return Component.translatable("message.omnivault.vault_kit.too_big");
        }
        if (isInUse(level, pos, state, blockEntity)) {
            return Component.translatable("message.omnivault.vault_kit.in_use");
        }
        List<ItemStack> contents = new ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            contents.add(container.getItem(slot).copy());
        }
        Component name = blockEntity instanceof Nameable nameable && nameable.hasCustomName() ? nameable.getCustomName() : null;
        // Empty the old inventory first so removing the block does not spill anything.
        container.clearContent();

        BlockState upgraded = PhysicalContent.vault(target).defaultBlockState()
                .setValue(VaultBlock.FACING, facingOf(state, player))
                .setValue(VaultBlock.WATERLOGGED, state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED));
        level.setBlock(pos, upgraded, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof VaultBlockEntity vault)) {
            // Should never happen; put the items back on the ground rather than lose them.
            contents.forEach(stack -> Block.popResource(level, pos, stack));
            return null;
        }
        for (int slot = 0; slot < contents.size(); slot++) {
            vault.setItem(slot, contents.get(slot));
        }
        vault.setCustomName(name);
        vault.setChanged();

        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.1f);
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.BLOCKS, 0.6f, 1.2f);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 16, 0.4, 0.3, 0.4, 0.05);
        }
        return null;
    }

    private static boolean isInUse(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (blockEntity instanceof VaultBlockEntity vault) {
            return vault.lid().viewers() > 0;
        }
        if (blockEntity instanceof ChestBlockEntity) {
            return ChestBlockEntity.getOpenCount(level, pos) > 0;
        }
        return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }

    private static Direction facingOf(BlockState state, @Nullable Player player) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING).getAxis().isHorizontal()) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return player != null ? player.getDirection().getOpposite() : Direction.NORTH;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        VaultTier from = target.previous();
        Component source = from == null
                ? Component.translatable("item.omnivault.vault_kit.from_vanilla")
                : Component.translatable("block.omnivault." + from.vaultName());
        tooltip.add(Component.translatable("item.omnivault.vault_kit.tooltip", source,
                Component.translatable("block.omnivault." + target.vaultName())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.omnivault.vault_kit.tooltip.keeps").withStyle(ChatFormatting.DARK_GRAY));
    }
}
