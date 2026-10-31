package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.TickingNode;
import com.nightbeam.omnivault.network.menu.BusMenu;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Breaks the block it faces (if the filter allows it) and stores the drops in the network. Also
 * vacuums up item entities in that space. Unbreakable blocks and block entities with contents are
 * left alone so it can never void a chest.
 */
public class DestructorBlockEntity extends BusBlockEntity implements TickingNode {
    public DestructorBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.DESTRUCTOR_BE.get(), pos, state);
    }

    @Override
    public BusMenu.Kind kind() {
        return BusMenu.Kind.DESTRUCTOR;
    }

    @Override
    public int energyUsage() {
        return 4;
    }

    @Override
    public void networkTick(StorageNetwork network, long gameTime) {
        if (gameTime % interval() != 0 || !redstoneAllows() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos target = worldPosition.relative(facing());
        collectItems(network, serverLevel, target);
        BlockState state = serverLevel.getBlockState(target);
        if (state.isAir() || state.getDestroySpeed(serverLevel, target) < 0 || !state.getFluidState().isEmpty() && state.canBeReplaced()) {
            return;
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(target);
        if (blockEntity != null) {
            return;
        }
        ItemKey asItem = ItemKey.of(new ItemStack(state.getBlock().asItem()));
        if (asItem != null && !passes(asItem)) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, serverLevel, target, null);
        // Only break when every drop fits, so nothing spills.
        for (ItemStack drop : drops) {
            ItemKey key = ItemKey.of(drop);
            if (key != null && network.storage().insert(key, drop.getCount(), true) < drop.getCount()) {
                return;
            }
        }
        serverLevel.destroyBlock(target, false);
        for (ItemStack drop : drops) {
            ItemStack rest = network.insert(drop);
            if (!rest.isEmpty()) {
                Block.popResource(serverLevel, target, rest);
            }
        }
    }

    private void collectItems(StorageNetwork network, ServerLevel level, BlockPos target) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(target), ItemEntity::isAlive)) {
            ItemStack stack = item.getItem();
            ItemKey key = ItemKey.of(stack);
            if (key == null || !passes(key)) {
                continue;
            }
            ItemStack rest = network.insert(stack.copy());
            if (rest.isEmpty()) {
                item.discard();
            } else {
                item.setItem(rest);
            }
        }
    }
}
