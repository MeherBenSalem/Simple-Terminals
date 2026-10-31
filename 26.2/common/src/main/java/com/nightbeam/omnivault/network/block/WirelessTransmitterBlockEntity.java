package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.content.CoreContent;
import com.nightbeam.omnivault.network.NetworkConfig;
import com.nightbeam.omnivault.network.NetworkContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Extends where wireless terminals reach this network. Range Upgrades are clicked in. */
public class WirelessTransmitterBlockEntity extends NetworkNodeBlockEntity {
    public static final int MAX_UPGRADES = 4;
    private int rangeUpgrades;

    public WirelessTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.WIRELESS_TRANSMITTER_BE.get(), pos, state);
    }

    @Override
    public int energyUsage() {
        return 8 + rangeUpgrades * 4;
    }

    public int rangeUpgrades() {
        return rangeUpgrades;
    }

    public int range() {
        return NetworkConfig.TRANSMITTER_RANGE.get() + rangeUpgrades * NetworkConfig.RANGE_PER_UPGRADE.get();
    }

    public boolean addUpgrade() {
        if (rangeUpgrades >= MAX_UPGRADES) {
            return false;
        }
        rangeUpgrades++;
        setChanged();
        return true;
    }

    public void dropUpgrades(Level level, BlockPos pos) {
        if (rangeUpgrades > 0) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(CoreContent.RANGE_UPGRADE.get(), rangeUpgrades));
            rangeUpgrades = 0;
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            dropUpgrades(level, pos);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("range_upgrades", rangeUpgrades);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        rangeUpgrades = Math.min(MAX_UPGRADES, input.getIntOr("range_upgrades", 0));
    }
}
