package com.nightbeam.omnivault.backpack;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * A shaped recipe that turns a backpack into the next tier. The result keeps all the NBT of the
 * backpack in the grid, so the UUID (and with it every item inside) and the name carry over.
 */
public class BackpackUpgradeRecipe extends ShapedRecipe {
    private final ItemStack output;

    public BackpackUpgradeRecipe(ShapedRecipe shaped) {
        super(shaped.getId(), shaped.getGroup(), shaped.category(), shaped.getWidth(), shaped.getHeight(), shaped.getIngredients(),
                shaped.getResultItem(RegistryAccess.EMPTY), shaped.showNotification());
        this.output = shaped.getResultItem(RegistryAccess.EMPTY);
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        for (ItemStack stack : input.getItems()) {
            if (Backpacks.isBackpack(stack)) {
                ItemStack upgraded = new ItemStack(output.getItem());
                if (stack.getTag() != null) {
                    upgraded.setTag(stack.getTag().copy());
                }
                if (output.getTag() != null) {
                    upgraded.getOrCreateTag().merge(output.getTag());
                }
                CompoundTag display = upgraded.getTagElement("display");
                if (display != null && !(upgraded.getItem() instanceof DyeableLeatherItem)) {
                    display.remove("color");
                }
                return upgraded;
            }
        }
        return super.assemble(input, registries);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BackpackContent.UPGRADE_RECIPE.get();
    }

    /** Reads and writes the recipe exactly like a shaped recipe. */
    public static class Serializer implements RecipeSerializer<BackpackUpgradeRecipe> {
        @Override
        public BackpackUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new BackpackUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public BackpackUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new BackpackUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, BackpackUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
    }
}
