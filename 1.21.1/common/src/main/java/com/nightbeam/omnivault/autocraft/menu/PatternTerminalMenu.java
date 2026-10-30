package com.nightbeam.omnivault.autocraft.menu;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.block.PatternTerminalBlockEntity;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.autocraft.pattern.PatternItem;
import com.nightbeam.omnivault.menu.GhostSlot;
import com.nightbeam.omnivault.network.menu.TerminalHost;
import com.nightbeam.omnivault.network.menu.TerminalMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pattern Encoding Terminal: the full network grid above, and below it a ghost 3×3 input grid,
 * up to three ghost outputs (processing mode) or the recipe's result (crafting mode), a blank
 * pattern slot and the encoded pattern slot. Ghost slots never take items: click with an item to
 * record it. In processing mode left-click records the whole carried stack, right-click adds one,
 * right-click empty-handed removes one, and scrolling over a slot changes its amount.
 */
public class PatternTerminalMenu extends TerminalMenu {
    public static final int CRAFT_X = 26;
    public static final int OUTPUT_X = 108;
    public static final int PATTERN_X = 152;
    public static final int BUTTON_ENCODE = 0;
    public static final int BUTTON_MODE = 1;
    public static final int BUTTON_CLEAR = 2;
    public static final int BUTTON_STATUS = 3;
    /** 100 + ghost index × 2 (+1 to decrease): change a processing amount by one. */
    public static final int BUTTON_ADJUST = 100;

    /** What the menu edits; the Pattern Encoding Terminal's block entity on the server. */
    public interface Workbench {
        SimpleContainer inputs();

        SimpleContainer outputs();

        SimpleContainer patterns();

        boolean processing();

        void setProcessing(boolean processing);
    }

    private final Workbench bench;
    private final SimpleContainer result = new SimpleContainer(1);
    private final DataSlot mode = DataSlot.standalone();
    private final List<PatternGhostSlot> ghosts = new ArrayList<>();
    private final int resultIndex;
    private final int blankIndex;
    private final int encodedIndex;

    public static PatternTerminalMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        return new PatternTerminalMenu(containerId, inventory, null, new LocalBench(data.readBoolean()));
    }

    public PatternTerminalMenu(int containerId, Inventory inventory, PatternTerminalBlockEntity terminal) {
        this(containerId, inventory, terminal, terminal);
    }

    private PatternTerminalMenu(int containerId, Inventory inventory, @Nullable TerminalHost host, Workbench bench) {
        super(AutocraftContent.PATTERN_TERMINAL_MENU.get(), containerId, inventory, host, 4);
        this.bench = bench;
        mode.set(bench.processing() ? 1 : 0);
        int top = craftTop();
        for (int i = 0; i < PatternData.MAX_INPUTS; i++) {
            PatternGhostSlot slot = new PatternGhostSlot(bench.inputs(), i, CRAFT_X + (i % 3) * 18, top + (i / 3) * 18, false);
            ghosts.add(slot);
            addSlot(slot);
        }
        for (int i = 0; i < PatternData.MAX_OUTPUTS; i++) {
            PatternGhostSlot slot = new PatternGhostSlot(bench.outputs(), i, OUTPUT_X, top + i * 18, true);
            ghosts.add(slot);
            addSlot(slot);
        }
        resultIndex = slots.size();
        addSlot(new Slot(result, 0, OUTPUT_X, top + 18) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean isActive() {
                return !processing();
            }
        });
        blankIndex = slots.size();
        addSlot(new Slot(bench.patterns(), 0, PATTERN_X, top) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PatternItem.isBlank(stack);
            }
        });
        encodedIndex = slots.size();
        addSlot(new Slot(bench.patterns(), 1, PATTERN_X, top + 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PatternItem.isEncoded(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
                super.setByPlayer(newStack, oldStack);
                // Dropping an encoded pattern in loads it, so it can be edited and re-encoded.
                PatternData data = PatternItem.data(newStack);
                if (data != null && !ItemStack.isSameItemSameComponents(newStack, oldStack)) {
                    load(data);
                }
            }
        });
        addPlayerInventory(inventory, 8, playerInventoryY());
        addDataSlot(mode);
        updateResult();
    }

    public int craftTop() {
        return gridBottom() + 10;
    }

    @Override
    public int playerInventoryY() {
        return craftTop() + 3 * 18 + 18;
    }

    public boolean processing() {
        return mode.get() == 1;
    }

    public List<PatternGhostSlot> ghosts() {
        return ghosts;
    }

    public int resultIndex() {
        return resultIndex;
    }

    // -- editing -------------------------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            if (slot instanceof PatternGhostSlot ghost) {
                if (ghost.isActive()) {
                    editGhost(ghost, button, clickType);
                }
                return;
            }
            if (slotId == resultIndex) {
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void editGhost(PatternGhostSlot ghost, int button, ClickType clickType) {
        ItemStack carried = getCarried();
        ItemStack current = ghost.getItem();
        boolean amounts = processing();
        ItemStack next;
        if (clickType == ClickType.QUICK_MOVE) {
            next = ItemStack.EMPTY;
        } else if (clickType == ClickType.PICKUP) {
            if (carried.isEmpty()) {
                next = amounts && button == 1 && current.getCount() > 1 ? current.copyWithCount(current.getCount() - 1) : ItemStack.EMPTY;
            } else if (!amounts) {
                next = carried.copyWithCount(1);
            } else if (button == 1) {
                next = ItemStack.isSameItemSameComponents(current, carried)
                        ? current.copyWithCount(Math.min(current.getCount() + 1, PatternData.MAX_SLOT_AMOUNT))
                        : carried.copyWithCount(1);
            } else {
                next = carried.copyWithCount(Math.min(carried.getCount(), PatternData.MAX_SLOT_AMOUNT));
            }
        } else {
            return;
        }
        ghost.set(next);
        updateResult();
    }

    private void adjust(int ghostIndex, boolean decrease) {
        if (!processing() || ghostIndex < 0 || ghostIndex >= ghosts.size()) {
            return;
        }
        PatternGhostSlot ghost = ghosts.get(ghostIndex);
        ItemStack current = ghost.getItem();
        if (current.isEmpty()) {
            return;
        }
        int count = current.getCount() + (decrease ? -1 : 1);
        ghost.set(count <= 0 ? ItemStack.EMPTY : current.copyWithCount(Math.min(count, PatternData.MAX_SLOT_AMOUNT)));
    }

    private void setProcessing(boolean processing) {
        bench.setProcessing(processing);
        mode.set(processing ? 1 : 0);
        updateResult();
    }

    private void load(PatternData data) {
        setProcessing(data.processing());
        for (int i = 0; i < PatternData.MAX_INPUTS; i++) {
            bench.inputs().setItem(i, i < data.inputs().size() ? data.inputs().get(i).copy() : ItemStack.EMPTY);
        }
        for (int i = 0; i < PatternData.MAX_OUTPUTS; i++) {
            bench.outputs().setItem(i, data.processing() && i < data.outputs().size() ? data.outputs().get(i).copy() : ItemStack.EMPTY);
        }
        updateResult();
    }

    private List<ItemStack> grid() {
        List<ItemStack> grid = new ArrayList<>(PatternData.MAX_INPUTS);
        for (int i = 0; i < PatternData.MAX_INPUTS; i++) {
            ItemStack stack = bench.inputs().getItem(i);
            grid.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        return grid;
    }

    private static List<ItemStack> contents(Container container) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            out.add(container.getItem(i).copy());
        }
        return out;
    }

    /** The crafting recipe's result for the current grid (server only), or empty. */
    private ItemStack craftingResult() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemStack.EMPTY;
        }
        CraftingInput input = CraftingInput.of(3, 3, grid());
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return serverPlayer.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, serverPlayer.level())
                .map(holder -> holder.value().assemble(input, serverPlayer.level().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private void updateResult() {
        if (player instanceof ServerPlayer) {
            result.setItem(0, processing() ? ItemStack.EMPTY : craftingResult());
        }
    }

    /** Writes the current recipe onto a blank pattern, or over the pattern already in the output slot. */
    private void encode(ServerPlayer player) {
        PatternData data;
        if (processing()) {
            data = PatternData.processing(contents(bench.inputs()), contents(bench.outputs()));
            if (data == null) {
                fail(player, "message.omnivault.pattern.need_processing");
                return;
            }
        } else {
            ItemStack made = craftingResult();
            if (made.isEmpty()) {
                fail(player, "message.omnivault.pattern.no_recipe");
                return;
            }
            data = PatternData.crafting(grid(), made);
        }
        SimpleContainer patterns = bench.patterns();
        ItemStack existing = patterns.getItem(1);
        if (existing.getItem() instanceof PatternItem item && item.kind() != PatternItem.Kind.BLANK) {
            patterns.setItem(1, PatternItem.encode(data));
        } else if (existing.isEmpty()) {
            ItemStack blank = patterns.getItem(0);
            if (!PatternItem.isBlank(blank)) {
                fail(player, "message.omnivault.pattern.need_blank");
                return;
            }
            blank.shrink(1);
            patterns.setItem(0, blank.isEmpty() ? ItemStack.EMPTY : blank);
            patterns.setItem(1, PatternItem.encode(data));
        } else {
            fail(player, "message.omnivault.pattern.output_full");
            return;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        switch (id) {
            case BUTTON_ENCODE -> encode(serverPlayer);
            case BUTTON_MODE -> setProcessing(!processing());
            case BUTTON_CLEAR -> {
                bench.inputs().clearContent();
                bench.outputs().clearContent();
                updateResult();
            }
            case BUTTON_STATUS -> {
                if (host instanceof PatternTerminalBlockEntity terminal) {
                    CraftingStatusMenu.open(serverPlayer, terminal::getNetwork, terminal::stillValid, () -> terminal.openMenu(serverPlayer));
                }
            }
            default -> {
                if (id >= BUTTON_ADJUST && id < BUTTON_ADJUST + ghosts.size() * 2) {
                    adjust((id - BUTTON_ADJUST) / 2, (id - BUTTON_ADJUST) % 2 == 1);
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= playerStart && index < playerEnd()) {
            Slot slot = slots.get(index);
            ItemStack stack = slot.getItem();
            int target = PatternItem.isBlank(stack) ? blankIndex : PatternItem.isEncoded(stack) ? encodedIndex : -1;
            if (target >= 0) {
                if (moveItemStackTo(stack, target, target + 1, false)) {
                    slot.setChanged();
                }
                return ItemStack.EMPTY;
            }
        }
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    /** A ghost slot of the workbench; output ghosts only show in processing mode. */
    public class PatternGhostSlot extends GhostSlot {
        private final boolean output;

        PatternGhostSlot(Container container, int slot, int x, int y, boolean output) {
            super(container, slot, x, y);
            this.output = output;
        }

        public boolean output() {
            return output;
        }

        @Override
        public boolean isActive() {
            return !output || processing();
        }
    }

    /** Client-side stand-in: slot contents and the mode arrive through normal menu syncing. */
    private static final class LocalBench implements Workbench {
        private final SimpleContainer inputs = new SimpleContainer(PatternData.MAX_INPUTS);
        private final SimpleContainer outputs = new SimpleContainer(PatternData.MAX_OUTPUTS);
        private final SimpleContainer patterns = new SimpleContainer(2);
        private boolean processing;

        LocalBench(boolean processing) {
            this.processing = processing;
        }

        @Override
        public SimpleContainer inputs() {
            return inputs;
        }

        @Override
        public SimpleContainer outputs() {
            return outputs;
        }

        @Override
        public SimpleContainer patterns() {
            return patterns;
        }

        @Override
        public boolean processing() {
            return processing;
        }

        @Override
        public void setProcessing(boolean processing) {
            this.processing = processing;
        }
    }
}
