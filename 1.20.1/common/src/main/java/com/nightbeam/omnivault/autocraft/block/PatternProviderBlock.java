package com.nightbeam.omnivault.autocraft.block;

import com.nightbeam.omnivault.network.block.DirectionalDeviceBlock;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A full-block device whose working face points at a machine (any of six directions). Placing it
 * against a block makes it face that block; the OmniTool turns it.
 */
public class PatternProviderBlock extends DirectionalDeviceBlock {
    private static final double[][] FULL = {{0, 0, 0, 16, 16, 16}};

    private final IPlatformHelper.BlockEntityFactory<?> factory;

    public PatternProviderBlock(IPlatformHelper.BlockEntityFactory<?> factory, Properties properties) {
        super(factory, FULL, properties);
        this.factory = factory;
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }
}
