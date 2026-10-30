package com.nightbeam.omnivault.physical.linked;

import com.mojang.serialization.DataResult;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.physical.ContainerSlotAccess;
import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.physical.StorageMenu;
import com.nightbeam.omnivault.physical.VaultLid;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A Linked Vault: holds only its frequency; the items live in {@link LinkedVaultData}. */
public class LinkedVaultBlockEntity extends BlockEntity implements LidBlockEntity, Nameable, StorageMenu.Source, ModCapabilities.ItemProvider {
    private final VaultLid lid = new VaultLid(this, SoundEvents.ENDER_CHEST_OPEN, SoundEvents.ENDER_CHEST_CLOSE, 1.0f);
    private LinkedFrequency frequency = LinkedFrequency.DEFAULT;
    @Nullable
    private Component customName;
    private long seenVersion = Long.MIN_VALUE;

    public LinkedVaultBlockEntity(BlockPos pos, BlockState state) {
        super(PhysicalContent.LINKED_BE.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LinkedVaultBlockEntity vault) {
        vault.lid.tick();
    }

    /** Comparators next to a Linked Vault follow changes made through any vault or pouch on the channel. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, LinkedVaultBlockEntity vault) {
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        LinkedVaultData.Inventory inventory = vault.inventory();
        if (inventory != null && inventory.version() != vault.seenVersion) {
            vault.seenVersion = inventory.version();
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    public LinkedFrequency frequency() {
        return frequency;
    }

    public void setFrequency(LinkedFrequency frequency) {
        this.frequency = frequency;
        this.seenVersion = Long.MIN_VALUE;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public boolean canUse(Player player) {
        return frequency.isOwner(player.getUUID());
    }

    /** The shared inventory for the current frequency; null on the client. */
    @Nullable
    public LinkedVaultData.Inventory inventory() {
        if (level == null || level.isClientSide || level.getServer() == null) {
            return null;
        }
        return LinkedVaultData.get(level.getServer()).inventory(frequency);
    }

    public VaultLid lid() {
        return lid;
    }

    public void openMenu(ServerPlayer player) {
        LinkedAccess.open(player, frequency, getDisplayName(), this);
    }

    @Override
    public float getOpenNess(float partialTick) {
        return lid.openness(partialTick);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return lid.onBlockEvent(id, param) || super.triggerEvent(id, param);
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && Container.stillValidBlockEntity(this, player) && canUse(player);
    }

    @Override
    public void startOpen(Player player) {
        lid.open(player);
    }

    @Override
    public void stopOpen(Player player) {
        lid.close(player);
    }

    @Nullable
    @Override
    public SlotAccess getSlotAccess(@Nullable Direction side) {
        if (level == null || level.isClientSide) {
            return null;
        }
        // Resolved per call so a frequency change is picked up by cached capabilities too.
        return new ContainerSlotAccess(() -> {
            LinkedVaultData.Inventory inventory = inventory();
            return inventory != null ? inventory : new net.minecraft.world.SimpleContainer(LinkedVaultData.SIZE);
        });
    }

    // -- Naming -----------------------------------------------------------------------------------------

    @Override
    public Component getName() {
        return customName != null ? customName : Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return customName;
    }

    @Override
    public Component getDisplayName() {
        return getName();
    }

    // -- Persistence and sync -----------------------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        frequency = tag.contains("Frequency")
                ? LinkedFrequency.CODEC.parse(NbtOps.INSTANCE, tag.get("Frequency")).resultOrPartial(OmniVault.LOG::warn).orElse(LinkedFrequency.DEFAULT)
                : LinkedFrequency.DEFAULT;
        customName = tag.contains("CustomName", Tag.TAG_STRING) ? parseCustomNameSafe(tag.getString("CustomName"), registries) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        DataResult<Tag> encoded = LinkedFrequency.CODEC.encodeStart(NbtOps.INSTANCE, frequency);
        encoded.resultOrPartial(OmniVault.LOG::warn).ifPresent(value -> tag.put("Frequency", value));
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        frequency = input.getOrDefault(PhysicalContent.LINKED_FREQUENCY.get(), LinkedFrequency.DEFAULT);
        customName = input.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(PhysicalContent.LINKED_FREQUENCY.get(), frequency);
        components.set(DataComponents.CUSTOM_NAME, customName);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Frequency");
        tag.remove("CustomName");
    }
}
