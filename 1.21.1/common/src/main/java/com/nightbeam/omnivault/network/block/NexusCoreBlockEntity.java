package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkConfig;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.NetworkManager;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.NetworkNode;
import com.nightbeam.omnivault.registry.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The heart of a network. Exactly one per network; two connected cores put both in conflict. */
public class NexusCoreBlockEntity extends BlockEntity implements NetworkNode, ModCapabilities.EnergyProvider {
    @Nullable
    private StorageNetwork network;
    private long energy;

    public NexusCoreBlockEntity(BlockPos pos, BlockState state) {
        this(NetworkContent.NEXUS_CORE_BE.get(), pos, state);
    }

    protected NexusCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Basic roots (the early-game Storage Hub) run a network of plain inventories: no cells, no
     * autocrafting, no wireless and no energy. A Nexus Core lifts every limit.
     */
    public boolean isBasic() {
        return false;
    }

    /** The network this core owns; created on first use on the server. */
    public StorageNetwork network() {
        if ((network == null || network.isRemoved()) && level instanceof ServerLevel serverLevel && !isRemoved()) {
            network = new StorageNetwork(serverLevel, this);
        }
        return network;
    }

    @Override
    @Nullable
    public StorageNetwork getNetwork() {
        return network();
    }

    @Override
    public void setNetwork(@Nullable StorageNetwork ignored) {
    }

    @Override
    public int energyUsage() {
        return NetworkConfig.CORE_USAGE.get();
    }

    @Override
    public void onNetworkStateChanged() {
        if (level != null && !level.isClientSide && network != null) {
            BlockState state = getBlockState();
            boolean active = network.isActive();
            if (state.hasProperty(DeviceBlock.ACTIVE) && state.getValue(DeviceBlock.ACTIVE) != active) {
                level.setBlock(worldPosition, state.setValue(DeviceBlock.ACTIVE, active), 3);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NexusCoreBlockEntity core) {
        StorageNetwork net = core.network();
        if (net == null) {
            return;
        }
        if (NetworkConfig.ENERGY.get() && !core.isBasic()) {
            int usage = net.energyUsage();
            if (core.energy >= usage) {
                core.energy -= usage;
                net.setPowered(true);
            } else {
                net.setPowered(false);
            }
            core.setChanged();
        } else {
            net.setPowered(true);
        }
        net.tick(level.getGameTime());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (network != null) {
            network.destroy();
            network = null;
        }
        NetworkManager.queueUpdate(level, worldPosition);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        NetworkManager.queueUpdate(level, worldPosition);
    }

    // -- energy ------------------------------------------------------------------------------------

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        if (!canReceiveEnergy()) {
            return 0;
        }
        long space = NetworkConfig.CORE_BUFFER.get() - energy;
        int accepted = (int) Math.max(0, Math.min(space, amount));
        if (!simulate && accepted > 0) {
            energy += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, energy);
    }

    @Override
    public int getMaxEnergyStored() {
        return NetworkConfig.CORE_BUFFER.get();
    }

    @Override
    public boolean canReceiveEnergy() {
        return NetworkConfig.ENERGY.get() && !isBasic();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("energy", energy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = tag.getLong("energy");
    }
}
