package com.nightbeam.omnivault.fabric.mixin;

import com.nightbeam.omnivault.content.BlockUseOverride;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Items such as vault upgrade kits, the Relocator, the OmniTool and the Quantum Link Card act on a
 * block before the block's own right-click (a chest opening, a terminal showing its screen). Skipping
 * the block here, on both sides, lets the game go on to the item's {@code useOn}, as NeoForge's
 * {@code RightClickBlock} event does.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void omnivault$itemActsFirst(ItemStack stack, Level level, Player player, InteractionHand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<ItemInteractionResult> cir) {
        BlockState state = (BlockState) (Object) this;
        if (BlockUseOverride.applies(stack, level, hit.getBlockPos(), state, player)) {
            cir.setReturnValue(ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
