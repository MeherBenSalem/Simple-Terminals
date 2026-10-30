package com.nightbeam.omnivault.backpack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * A shaped recipe that turns a backpack into the next tier. The result keeps every data component of
 * the backpack in the grid, so the UUID (and with it every item inside) and the name carry over.
 */
public class BackpackUpgradeRecipe extends ShapedRecipe {
    private final ShapedRecipePattern shape;
    private final ItemStack output;

    public BackpackUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.shape = pattern;
        this.output = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (ItemStack stack : input.items()) {
            if (Backpacks.isBackpack(stack)) {
                ItemStack upgraded = stack.transmuteCopy(output.getItem(), 1);
                upgraded.applyComponents(output.getComponentsPatch());
                if (!upgraded.is(ItemTags.DYEABLE)) {
                    upgraded.remove(DataComponents.DYED_COLOR);
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

    public static class Serializer implements RecipeSerializer<BackpackUpgradeRecipe> {
        private static final MapCodec<BackpackUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.EQUIPMENT).forGetter(ShapedRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.shape),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.output),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification)
        ).apply(i, BackpackUpgradeRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, recipe) -> {
                    buf.writeUtf(recipe.getGroup());
                    buf.writeEnum(recipe.category());
                    ShapedRecipePattern.STREAM_CODEC.encode(buf, recipe.shape);
                    ItemStack.STREAM_CODEC.encode(buf, recipe.output);
                    buf.writeBoolean(recipe.showNotification());
                },
                buf -> new BackpackUpgradeRecipe(buf.readUtf(), buf.readEnum(CraftingBookCategory.class),
                        ShapedRecipePattern.STREAM_CODEC.decode(buf), ItemStack.STREAM_CODEC.decode(buf), buf.readBoolean()));

        @Override
        public MapCodec<BackpackUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
