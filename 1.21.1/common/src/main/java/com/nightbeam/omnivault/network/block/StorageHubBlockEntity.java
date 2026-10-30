package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The early-game network root: every inventory it or an Inventory Cable touches becomes one storage. */
public class StorageHubBlockEntity extends NexusCoreBlockEntity {
    public StorageHubBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.STORAGE_HUB_BE.get(), pos, state);
    }

    @Override
    public boolean isBasic() {
        return true;
    }

    @Override
    public int energyUsage() {
        return 0;
    }
}
