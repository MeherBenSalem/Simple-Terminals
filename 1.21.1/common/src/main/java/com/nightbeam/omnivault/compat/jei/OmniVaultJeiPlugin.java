package com.nightbeam.omnivault.compat.jei;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.AutocraftPackets;
import com.nightbeam.omnivault.autocraft.menu.PatternTerminalMenu;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.network.CraftingTransfer;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.menu.CraftingTerminalMenu;
import com.nightbeam.omnivault.network.menu.TerminalMenu;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.ItemKey;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JEI support: move a crafting recipe into a crafting terminal straight from the network, and load
 * any recipe into the Pattern Encoding Terminal.
 */
@JeiPlugin
public class OmniVaultJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return OmniVault.id("jei");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new CraftingTerminalTransfer(registration.getTransferHelper()), RecipeTypes.CRAFTING);
        registration.addUniversalRecipeTransferHandler(new PatternTerminalTransfer(registration.getTransferHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(NetworkContent.CRAFTING_TERMINAL.get()), RecipeTypes.CRAFTING);
        registration.addRecipeCatalyst(new ItemStack(NetworkContent.WIRELESS_TERMINAL.get()), RecipeTypes.CRAFTING);
        registration.addRecipeCatalyst(new ItemStack(AutocraftContent.PATTERN_TERMINAL.get()), RecipeTypes.CRAFTING);
    }

    /** What the player can reach: the network as the terminal last saw it, plus their inventory. */
    private static Map<ItemKey, Long> available(TerminalMenu menu, Player player) {
        Map<ItemKey, Long> available = new HashMap<>();
        for (TerminalMenu.ClientEntry entry : menu.clientEntries().values()) {
            available.merge(entry.key(), entry.count(), Long::sum);
        }
        for (ItemStack stack : player.getInventory().items) {
            ItemKey key = ItemKey.of(stack);
            if (key != null) {
                available.merge(key, (long) stack.getCount(), Long::sum);
            }
        }
        return available;
    }

    private record CraftingTerminalTransfer(IRecipeTransferHandlerHelper helper)
            implements IRecipeTransferHandler<CraftingTerminalMenu, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends CraftingTerminalMenu> getContainerClass() {
            return CraftingTerminalMenu.class;
        }

        @Override
        public Optional<MenuType<CraftingTerminalMenu>> getMenuType() {
            return Optional.of(NetworkContent.CRAFTING_TERMINAL_MENU.get());
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(CraftingTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slotsView,
                                                   Player player, boolean maxTransfer, boolean doTransfer) {
            List<IRecipeSlotView> inputs = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
            Map<ItemKey, Long> available = available(menu, player);
            List<List<ItemStack>> slots = new ArrayList<>();
            List<IRecipeSlotView> missing = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                IRecipeSlotView view = i < inputs.size() ? inputs.get(i) : null;
                List<ItemStack> candidates = view == null ? List.of()
                        : view.getItemStacks().limit(CraftingTransfer.MAX_CANDIDATES).map(ItemStack::copy).toList();
                slots.add(candidates);
                if (candidates.isEmpty()) {
                    continue;
                }
                boolean found = false;
                for (ItemStack candidate : candidates) {
                    ItemKey key = ItemKey.of(candidate);
                    if (key != null && available.getOrDefault(key, 0L) > 0) {
                        available.merge(key, -1L, Long::sum);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    missing.add(view);
                }
            }
            if (!missing.isEmpty()) {
                return helper.createUserErrorForMissingSlots(Component.translatable("gui.omnivault.jei.missing"), missing);
            }
            if (doTransfer) {
                Services.PLATFORM.sendToServer(new CraftingTransfer(menu.containerId, slots));
            }
            return null;
        }
    }

    /**
     * Any recipe into the Pattern Encoding Terminal. Crafting recipes fill the 3×3 grid in crafting
     * mode; every other recipe (furnaces, other mods' machines) becomes a processing recipe with its
     * item inputs and outputs. Where a slot accepts several items, one the network holds is chosen.
     */
    private record PatternTerminalTransfer(IRecipeTransferHandlerHelper helper)
            implements IUniversalRecipeTransferHandler<PatternTerminalMenu> {
        @Override
        public Class<? extends PatternTerminalMenu> getContainerClass() {
            return PatternTerminalMenu.class;
        }

        @Override
        public Optional<MenuType<PatternTerminalMenu>> getMenuType() {
            return Optional.of(AutocraftContent.PATTERN_TERMINAL_MENU.get());
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(PatternTerminalMenu menu, Object recipe, IRecipeSlotsView slotsView,
                                                   Player player, boolean maxTransfer, boolean doTransfer) {
            boolean crafting = recipe instanceof RecipeHolder<?> holder && holder.value() instanceof CraftingRecipe;
            Map<ItemKey, Long> available = available(menu, player);
            List<ItemStack> inputs = new ArrayList<>();
            for (IRecipeSlotView view : slotsView.getSlotViews(RecipeIngredientRole.INPUT)) {
                ItemStack choice = choose(view, available);
                // A crafting grid keeps its empty cells so the shape survives; machines just list items.
                if (crafting || !choice.isEmpty()) {
                    inputs.add(choice);
                }
            }
            List<ItemStack> outputs = new ArrayList<>();
            for (IRecipeSlotView view : slotsView.getSlotViews(RecipeIngredientRole.OUTPUT)) {
                ItemStack choice = choose(view, available);
                if (!choice.isEmpty()) {
                    outputs.add(choice);
                }
            }
            if (inputs.stream().allMatch(ItemStack::isEmpty) || (!crafting && outputs.isEmpty())) {
                return helper.createUserErrorWithTooltip(Component.translatable("gui.omnivault.jei.pattern_unsupported"));
            }
            if (inputs.size() > PatternData.MAX_INPUTS || (!crafting && outputs.size() > PatternData.MAX_OUTPUTS)) {
                return helper.createUserErrorWithTooltip(Component.translatable("gui.omnivault.jei.pattern_too_big"));
            }
            if (doTransfer) {
                Services.PLATFORM.sendToServer(new AutocraftPackets.PatternTransfer(menu.containerId, !crafting, inputs,
                        crafting ? List.of() : outputs));
            }
            return null;
        }

        /** The slot's item: one the player can reach if there is a choice, otherwise the one JEI shows. */
        private static ItemStack choose(IRecipeSlotView view, Map<ItemKey, Long> available) {
            List<ItemStack> candidates = view.getItemStacks().limit(CraftingTransfer.MAX_CANDIDATES).toList();
            for (ItemStack candidate : candidates) {
                ItemKey key = ItemKey.of(candidate);
                if (key != null && available.getOrDefault(key, 0L) > 0) {
                    return candidate.copy();
                }
            }
            return view.getDisplayedItemStack().or(() -> candidates.stream().findFirst()).map(ItemStack::copy).orElse(ItemStack.EMPTY);
        }
    }
}
