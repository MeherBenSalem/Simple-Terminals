package com.nightbeam.omnivault.content;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.function.Supplier;

/**
 * The result of using an item on a block, as later Minecraft versions split a block's right-click into
 * an item step and an empty-hand step. Minecraft 1.20.1 has one {@code Block#use}; our blocks forward it
 * to {@link #use}, which runs the two steps in the same order and with the same outcomes.
 */
public enum ItemInteractionResult {
    SUCCESS,
    CONSUME,
    CONSUME_PARTIAL,
    /** The item does nothing here: carry on with the empty-hand interaction (main hand only). */
    PASS_TO_DEFAULT_BLOCK_INTERACTION,
    /** Skip the block entirely so the item's own {@code useOn} runs. */
    SKIP_DEFAULT_BLOCK_INTERACTION,
    FAIL;

    public static ItemInteractionResult sidedSuccess(boolean clientSide) {
        return clientSide ? SUCCESS : CONSUME;
    }

    public InteractionResult result() {
        return switch (this) {
            case SUCCESS -> InteractionResult.SUCCESS;
            case CONSUME -> InteractionResult.CONSUME;
            case CONSUME_PARTIAL -> InteractionResult.CONSUME_PARTIAL;
            case PASS_TO_DEFAULT_BLOCK_INTERACTION, SKIP_DEFAULT_BLOCK_INTERACTION -> InteractionResult.PASS;
            case FAIL -> InteractionResult.FAIL;
        };
    }

    /** {@code Block#use}: the item step's result, or the empty-hand step when it passes to it. */
    public static InteractionResult use(ItemInteractionResult itemResult, InteractionHand hand, Supplier<InteractionResult> withoutItem) {
        if (itemResult == PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == InteractionHand.MAIN_HAND) {
            return withoutItem.get();
        }
        return itemResult.result();
    }
}
