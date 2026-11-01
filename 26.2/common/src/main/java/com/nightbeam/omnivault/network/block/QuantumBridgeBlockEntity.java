package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * One half of an entangled pair. While both halves are loaded, the network continues through the
 * pair as if they were touching, across any distance and between dimensions.
 */
public class QuantumBridgeBlockEntity extends NetworkNodeBlockEntity {
    @Nullable
    private GlobalPos partner;

    public QuantumBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.QUANTUM_BRIDGE_BE.get(), pos, state);
    }

    @Override
    public int energyUsage() {
        return 32;
    }

    @Nullable
    public GlobalPos partner() {
        return partner;
    }

    public GlobalPos self() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    public void setPartner(@Nullable GlobalPos partner) {
        this.partner = partner;
        setChanged();
        NetworkManager.queueUpdate(level, worldPosition);
    }

    /** The other half, if it is loaded and still entangled with this one. */
    @Nullable
    public QuantumBridgeBlockEntity linkedPartner() {
        if (partner == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        ServerLevel other = serverLevel.getServer().getLevel(partner.dimension());
        if (other == null || !other.isLoaded(partner.pos())) {
            return null;
        }
        if (other.getBlockEntity(partner.pos()) instanceof QuantumBridgeBlockEntity bridge && self().equals(bridge.partner)) {
            return bridge;
        }
        return null;
    }

    /** Breaking one half leaves the other half unlinked. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        QuantumBridgeBlockEntity other = linkedPartner();
        if (other != null) {
            other.setPartner(null);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("partner", GlobalPos.CODEC, partner);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        partner = input.read("partner", GlobalPos.CODEC).orElse(null);
    }
}
