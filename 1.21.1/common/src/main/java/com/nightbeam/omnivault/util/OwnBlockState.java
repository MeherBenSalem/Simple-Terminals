package com.nightbeam.omnivault.util;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Changing a block entity's own block state, only while that block entity is still the one in the world. */
public final class OwnBlockState {
    private OwnBlockState() {
    }

    /**
     * A device that updates its state (active, working, powered) during a network update can run after the player
     * broke it. Writing the state then put the block back after it had already dropped as an item, duplicating it.
     */
    public static boolean set(BlockEntity entity, BlockState state, int flags) {
        var level = entity.getLevel();
        if (level == null || entity.isRemoved() || level.getBlockEntity(entity.getBlockPos()) != entity
                || !level.getBlockState(entity.getBlockPos()).is(state.getBlock())) {
            return false;
        }
        return level.setBlock(entity.getBlockPos(), state, flags);
    }
}
