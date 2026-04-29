package com.nightbeam.simpleterminals.platform;

import com.nightbeam.simpleterminals.ForgeNetworking;
import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import com.nightbeam.simpleterminals.platform.services.IPlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.NetworkHooks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ForgePlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Optional<Container> findPlatformContainer(Level level, BlockPos targetPos, Direction accessSide) {
        BlockEntity blockEntity = level.getBlockEntity(targetPos);
        if (blockEntity == null) {
            return Optional.empty();
        }

        Optional<Container> directHandler = getItemHandlerContainer(blockEntity, accessSide);
        if (directHandler.isPresent()) {
            return directHandler;
        }

        if (isSophisticatedStorageController(blockEntity)) {
            return getSophisticatedControllerContainer(level, blockEntity, accessSide);
        }

        return Optional.empty();
    }

    @Override
    public void openCraftingTerminalMenu(ServerPlayer player, BlockPos targetPos, Direction accessSide, int slotCount, Component title) {
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return CraftingTerminalMenu.createServer(containerId, inventory, targetPos, accessSide);
            }
        }, buffer -> buffer.writeVarInt(slotCount));
    }

    @Override
    public void sendCraftingTerminalScroll(int containerId, int offsetRows) {
        ForgeNetworking.sendScrollToServer(containerId, offsetRows);
    }

    @Override
    public void sendCraftingTerminalSearch(int containerId, String query) {
        ForgeNetworking.sendSearchToServer(containerId, query);
    }

    private static Optional<Container> getItemHandlerContainer(BlockEntity blockEntity, Direction accessSide) {
        Optional<Container> sided = resolveItemHandler(blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, accessSide), blockEntity);
        if (sided.isPresent()) {
            return sided;
        }
        Optional<Container> unsided = resolveItemHandler(blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null), blockEntity);
        if (unsided.isPresent()) {
            return unsided;
        }
        for (Direction direction : Direction.values()) {
            Optional<Container> directional = resolveItemHandler(blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction), blockEntity);
            if (directional.isPresent()) {
                return directional;
            }
        }
        return Optional.empty();
    }

    private static Optional<Container> resolveItemHandler(LazyOptional<IItemHandler> optional, BlockEntity owner) {
        return optional.resolve()
                .filter(handler -> handler.getSlots() > 0)
                .map(handler -> new ItemHandlerContainer(handler, owner));
    }

    private static boolean isSophisticatedStorageController(BlockEntity blockEntity) {
        return "net.p3pp3rf1y.sophisticatedstorage.block.ControllerBlockEntity".equals(blockEntity.getClass().getName());
    }

    private static Optional<Container> getSophisticatedControllerContainer(Level level, BlockEntity controller, Direction accessSide) {
        Collection<?> storagePositions = getStoragePositions(controller);
        if (storagePositions.isEmpty()) {
            return Optional.empty();
        }

        List<Container> containers = new ArrayList<>();
        for (Object value : storagePositions) {
            if (value instanceof BlockPos storagePos) {
                BlockEntity storageBlockEntity = level.getBlockEntity(storagePos);
                if (storageBlockEntity != null) {
                    getItemHandlerContainer(storageBlockEntity, accessSide).ifPresent(containers::add);
                }
            }
        }

        if (containers.isEmpty()) {
            return Optional.empty();
        }
        if (containers.size() == 1) {
            return Optional.of(containers.get(0));
        }
        return Optional.of(new CombinedContainer(containers, controller));
    }

    private static Collection<?> getStoragePositions(BlockEntity controller) {
        try {
            Method method = controller.getClass().getMethod("getStoragePositions");
            Object result = method.invoke(controller);
            if (result instanceof Collection<?> collection) {
                return collection;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return List.of();
    }

    private static class ItemHandlerContainer implements Container {
        private final IItemHandler handler;
        private final BlockEntity owner;

        ItemHandlerContainer(IItemHandler handler, BlockEntity owner) {
            this.handler = handler;
            this.owner = owner;
        }

        @Override
        public int getContainerSize() {
            return handler.getSlots();
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < getContainerSize(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return isValidSlot(slot) ? handler.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return isValidSlot(slot) ? handler.extractItem(slot, amount, false) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = getItem(slot);
            return stack.isEmpty() ? ItemStack.EMPTY : removeItem(slot, stack.getCount());
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (!isValidSlot(slot)) {
                return;
            }
            if (handler instanceof IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot, stack);
                setChanged();
                return;
            }

            ItemStack current = handler.getStackInSlot(slot).copy();
            if (ItemStack.matches(current, stack) && current.getCount() == stack.getCount()) {
                return;
            }

            ItemStack extracted = handler.extractItem(slot, current.getCount(), false);
            ItemStack remainder = handler.insertItem(slot, stack.copy(), false);
            if (!remainder.isEmpty()) {
                handler.insertItem(slot, extracted, false);
            }
            setChanged();
        }

        @Override
        public void setChanged() {
            owner.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return owner.getLevel() != null && !owner.isRemoved()
                    && player.distanceToSqr(owner.getBlockPos().getX() + 0.5D, owner.getBlockPos().getY() + 0.5D, owner.getBlockPos().getZ() + 0.5D) <= 64.0D;
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < getContainerSize(); slot++) {
                setItem(slot, ItemStack.EMPTY);
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isValidSlot(slot) && handler.insertItem(slot, stack.copy(), true).getCount() < stack.getCount();
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        private boolean isValidSlot(int slot) {
            return slot >= 0 && slot < getContainerSize();
        }
    }

    private static class CombinedContainer implements Container {
        private final List<Container> containers;
        private final BlockEntity owner;
        private final int size;

        CombinedContainer(List<Container> containers, BlockEntity owner) {
            this.containers = List.copyOf(containers);
            this.owner = owner;
            int totalSize = 0;
            for (Container container : containers) {
                totalSize += container.getContainerSize();
            }
            size = totalSize;
        }

        @Override
        public int getContainerSize() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            for (Container container : containers) {
                if (!container.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            SlotRef ref = resolve(slot);
            return ref == null ? ItemStack.EMPTY : ref.container.getItem(ref.slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            SlotRef ref = resolve(slot);
            return ref == null ? ItemStack.EMPTY : ref.container.removeItem(ref.slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            SlotRef ref = resolve(slot);
            return ref == null ? ItemStack.EMPTY : ref.container.removeItemNoUpdate(ref.slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            SlotRef ref = resolve(slot);
            if (ref != null) {
                ref.container.setItem(ref.slot, stack);
            }
        }

        @Override
        public void setChanged() {
            containers.forEach(Container::setChanged);
            owner.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return owner.getLevel() != null && !owner.isRemoved()
                    && containers.stream().allMatch(container -> container.stillValid(player));
        }

        @Override
        public void clearContent() {
            containers.forEach(Container::clearContent);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            SlotRef ref = resolve(slot);
            return ref != null && ref.container.canPlaceItem(ref.slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return containers.stream().mapToInt(Container::getMaxStackSize).min().orElse(64);
        }

        private SlotRef resolve(int slot) {
            if (slot < 0) {
                return null;
            }
            int remaining = slot;
            for (Container container : containers) {
                int containerSize = container.getContainerSize();
                if (remaining < containerSize) {
                    return new SlotRef(container, remaining);
                }
                remaining -= containerSize;
            }
            return null;
        }

        private record SlotRef(Container container, int slot) {
        }
    }
}
