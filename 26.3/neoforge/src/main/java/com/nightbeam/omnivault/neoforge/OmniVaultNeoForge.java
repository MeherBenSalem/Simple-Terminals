package com.nightbeam.omnivault.neoforge;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.neoforge.drawer.DrawerNeoForgeEvents;
import com.nightbeam.omnivault.neoforge.transfer.FluidAccessHandler;
import com.nightbeam.omnivault.neoforge.transfer.NetworkItemHandler;
import com.nightbeam.omnivault.neoforge.transfer.ProviderEnergyHandler;
import com.nightbeam.omnivault.neoforge.transfer.SlotAccessItemHandler;
import com.nightbeam.omnivault.packet.PacketRegistrar;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mod(OmniVault.MOD_ID)
public class OmniVaultNeoForge {
    private static final String PROTOCOL_VERSION = "2";

    public OmniVaultNeoForge(IEventBus modBus) {
        OmniVaultCommon.init();
        NeoForgeRegistryHelper.attach(modBus);
        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::registerAliases);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> OmniVaultCommon.serverTick(event.getServer()));
        PhysicalNeoForge.init();
        BackpackNeoForgeEvents.register();
        DrawerNeoForgeEvents.register();
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(OmniVault.MOD_ID).versioned(PROTOCOL_VERSION);
        OmniVaultCommon.registerPackets(new PacketRegistrar() {
            @Override
            public <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
                registrar.playToServer(type, codec, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handler.accept(payload, player);
                    }
                }));
            }

            @Override
            public <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
                registrar.playToClient(type, codec, (payload, context) -> context.enqueueWork(() -> handler.accept(payload)));
            }
        });
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (Supplier<? extends BlockEntityType<? extends ModCapabilities.ItemProvider>> type : ModCapabilities.itemProviders()) {
            registerItems(event, type.get());
        }
        for (Supplier<? extends BlockEntityType<? extends ModCapabilities.EnergyProvider>> type : ModCapabilities.energyProviders()) {
            registerEnergy(event, type.get());
        }
        for (Supplier<? extends BlockEntityType<? extends ModCapabilities.FluidProvider>> type : ModCapabilities.fluidProviders()) {
            registerFluids(event, type.get());
        }
    }

    private static <T extends BlockEntity & ModCapabilities.ItemProvider> void registerItems(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
        @SuppressWarnings("unchecked")
        BlockEntityType<T> typed = (BlockEntityType<T>) type;
        event.registerBlockEntity(Capabilities.Item.BLOCK, typed, (blockEntity, side) -> {
            SlotAccess access = blockEntity.getSlotAccess(side);
            if (access == null) {
                return null;
            }
            ItemStorage network = access.networkStorage();
            return network != null
                    ? new NetworkItemHandler(access, network)
                    : new SlotAccessItemHandler(access, stack -> drop(blockEntity, stack));
        });
    }

    private static <T extends BlockEntity & ModCapabilities.FluidProvider> void registerFluids(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
        @SuppressWarnings("unchecked")
        BlockEntityType<T> typed = (BlockEntityType<T>) type;
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, typed, (blockEntity, side) -> {
            var access = blockEntity.getFluidAccess(side);
            return access == null ? null : new FluidAccessHandler(access);
        });
    }

    private static <T extends BlockEntity & ModCapabilities.EnergyProvider> void registerEnergy(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
        @SuppressWarnings("unchecked")
        BlockEntityType<T> typed = (BlockEntityType<T>) type;
        event.registerBlockEntity(Capabilities.Energy.BLOCK, typed,
                (blockEntity, side) -> blockEntity.exposesEnergy(side) ? new ProviderEnergyHandler(blockEntity) : null);
    }

    private static void drop(BlockEntity blockEntity, ItemStack stack) {
        Level level = blockEntity.getLevel();
        if (level != null && !stack.isEmpty()) {
            BlockPos pos = blockEntity.getBlockPos();
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
        }
    }

    /** Worlds made with Simple Terminals keep their terminals: the old ID now points at the new block. */
    private void registerAliases(RegisterEvent event) {
        Identifier legacy = Identifier.fromNamespaceAndPath(OmniVault.LEGACY_MOD_ID, "crafting_terminal");
        Identifier current = OmniVault.id("crafting_terminal");
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            BuiltInRegistries.BLOCK.addAlias(legacy, current);
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            BuiltInRegistries.ITEM.addAlias(legacy, current);
        }
    }
}
