package com.nightbeam.omnivault.forge;

import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.FluidKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * A vanilla cauldron as a one-bucket tank of water or lava, which later Forge versions (and Fabric's
 * Transfer API) provide themselves. Like those, it is filled and drained a whole bucket at a time.
 */
record CauldronFluidAccess(Level level, BlockPos pos) implements FluidAccess {
    @Nullable
    static CauldronFluidAccess of(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.LAVA_CAULDRON)
                ? new CauldronFluidAccess(level, pos) : null;
    }

    @Nullable
    private Fluid fluid() {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON)) {
            return Fluids.WATER;
        }
        return state.is(Blocks.LAVA_CAULDRON) ? Fluids.LAVA : null;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Nullable
    @Override
    public FluidKey getFluid(int tank) {
        Fluid fluid = fluid();
        return fluid == null ? null : FluidKey.of(fluid);
    }

    @Override
    public long getAmount(int tank) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON)) {
            return FluidKey.BUCKET * (long) state.getValue(LayeredCauldronBlock.LEVEL) / LayeredCauldronBlock.MAX_FILL_LEVEL;
        }
        return state.is(Blocks.LAVA_CAULDRON) ? FluidKey.BUCKET : 0;
    }

    @Override
    public long getCapacity(int tank) {
        return FluidKey.BUCKET;
    }

    @Override
    public long fill(FluidKey key, long amount, boolean simulate) {
        Block filled = key.fluid() == Fluids.WATER ? Blocks.WATER_CAULDRON : key.fluid() == Fluids.LAVA ? Blocks.LAVA_CAULDRON : null;
        if (filled == null || amount < FluidKey.BUCKET || !level.getBlockState(pos).is(Blocks.CAULDRON)) {
            return 0;
        }
        if (!simulate) {
            BlockState state = filled.defaultBlockState();
            if (state.hasProperty(LayeredCauldronBlock.LEVEL)) {
                state = state.setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
            }
            level.setBlockAndUpdate(pos, state);
        }
        return FluidKey.BUCKET;
    }

    @Override
    public long drain(FluidKey key, long amount, boolean simulate) {
        if (amount < FluidKey.BUCKET || key.fluid() != fluid() || getAmount(0) < FluidKey.BUCKET) {
            return 0;
        }
        if (!simulate) {
            level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        }
        return FluidKey.BUCKET;
    }
}
