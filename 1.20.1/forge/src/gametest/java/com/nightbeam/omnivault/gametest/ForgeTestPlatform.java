package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.forge.ItemHandlerSlotAccess;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegistryManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ForgeTestPlatform implements TestPlatform {
    @Nullable
    @Override
    public SlotAccess items(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        IItemHandler handler = blockEntity == null ? null : blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
        return handler == null ? null : new ItemHandlerSlotAccess(handler);
    }

    @Override
    public boolean leftClickBlock(Player player, BlockPos pos, Direction face) {
        return ForgeHooks.onLeftClickBlock(player, pos, face, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK).isCanceled();
    }

    @Override
    public boolean extractionCappedAtAStack() {
        return true;
    }

    /** Fires the missing-mappings event a world with the old id fires on load, and reports the remap. */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T legacyEntry(ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
        IForgeRegistry<T> forgeRegistry = RegistryManager.ACTIVE.getRegistry((ResourceKey<Registry<T>>) registry);
        RecordingMapping<T> mapping = new RecordingMapping<>(forgeRegistry, id);
        MinecraftForge.EVENT_BUS.post(new MissingMappingsEvent(registry, forgeRegistry, List.of(mapping)));
        return mapping.target;
    }

    private static final class RecordingMapping<T> extends MissingMappingsEvent.Mapping<T> {
        @Nullable
        private T target;

        RecordingMapping(IForgeRegistry<T> registry, ResourceLocation key) {
            super(registry, registry, key, -1);
        }

        @Override
        public void remap(T target) {
            super.remap(target);
            this.target = target;
        }
    }
}
