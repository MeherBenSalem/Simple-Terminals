package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.TickingNode;
import com.nightbeam.omnivault.network.menu.BusMenu;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Places blocks from the network into the empty space it faces, cycling through the blocks listed in
 * its filter.
 */
public class ConstructorBlockEntity extends BusBlockEntity implements TickingNode {
    private int next;

    public ConstructorBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.CONSTRUCTOR_BE.get(), pos, state);
    }

    @Override
    public BusMenu.Kind kind() {
        return BusMenu.Kind.CONSTRUCTOR;
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
        if (!serverLevel.getBlockState(target).canBeReplaced()) {
            return;
        }
        for (int i = 0; i < FILTER_SLOTS; i++) {
            int slot = (next + i) % FILTER_SLOTS;
            ItemStack entry = filter.getItem(slot);
            if (!(entry.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            ItemKey key = ItemKey.of(entry);
            BlockState state = blockItem.getBlock().defaultBlockState();
            if (!state.canSurvive(serverLevel, target) || network.storage().extract(key, 1, true) < 1) {
                continue;
            }
            network.storage().extract(key, 1, false);
            BlockState placed = Block.updateFromNeighbourShapes(state, serverLevel, target);
            serverLevel.setBlock(target, placed, Block.UPDATE_ALL);
            SoundType sound = placed.getSoundType();
            serverLevel.playSound(null, target, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
            serverLevel.gameEvent(GameEvent.BLOCK_PLACE, target, GameEvent.Context.of(placed));
            next = (slot + 1) % FILTER_SLOTS;
            return;
        }
    }
}
