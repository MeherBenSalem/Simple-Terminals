package com.nightbeam.omnivault.physical;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Viewer counting and lid animation shared by every vault, built on the same vanilla pieces chests use:
 * {@link ContainerOpenersCounter} on the server (sounds, game events, block event 1 with the viewer
 * count) and {@link ChestLidController} on the client.
 */
public final class VaultLid {
    public static final int EVENT_VIEWERS = 1;

    private final BlockEntity owner;
    private final ChestLidController controller = new ChestLidController();
    private final ContainerOpenersCounter counter;

    public VaultLid(BlockEntity owner, SoundEvent openSound, SoundEvent closeSound, float pitch) {
        this.owner = owner;
        this.counter = new ContainerOpenersCounter() {
            @Override
            protected void onOpen(Level level, BlockPos pos, BlockState state) {
                play(level, pos, openSound, pitch);
            }

            @Override
            protected void onClose(Level level, BlockPos pos, BlockState state) {
                play(level, pos, closeSound, pitch);
            }

            @Override
            protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int count) {
                level.blockEvent(pos, state.getBlock(), EVENT_VIEWERS, count);
            }

            @Override
            protected boolean isOwnContainer(Player player) {
                return player.containerMenu instanceof StorageMenu menu && menu.source() == VaultLid.this.owner;
            }
        };
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float pitch) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, 0.5f,
                pitch * (level.random.nextFloat() * 0.1f + 0.9f));
    }

    public void open(Player player) {
        if (!owner.isRemoved() && !player.isSpectator() && owner.getLevel() != null) {
            counter.incrementOpeners(player, owner.getLevel(), owner.getBlockPos(), owner.getBlockState());
        }
    }

    public void close(Player player) {
        if (!owner.isRemoved() && !player.isSpectator() && owner.getLevel() != null) {
            counter.decrementOpeners(player, owner.getLevel(), owner.getBlockPos(), owner.getBlockState());
        }
    }

    /** Called from the block's scheduled tick, like vanilla chests, to drop viewers who walked away. */
    public void recheck() {
        if (!owner.isRemoved() && owner.getLevel() != null) {
            counter.recheckOpeners(owner.getLevel(), owner.getBlockPos(), owner.getBlockState());
        }
    }

    public int viewers() {
        return counter.getOpenerCount();
    }

    /** @return true if the block event was ours. */
    public boolean onBlockEvent(int id, int param) {
        if (id == EVENT_VIEWERS) {
            controller.shouldBeOpen(param > 0);
            return true;
        }
        return false;
    }

    public void tick() {
        controller.tickLid();
    }

    public float openness(float partialTick) {
        return controller.getOpenness(partialTick);
    }
}
