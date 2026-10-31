package com.nightbeam.omnivault.registry;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.nightbeam.omnivault.content.CoreContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;
import java.util.Set;

/**
 * Copies a broken block entity's {@link ImplicitComponents} onto the dropped item: the 1.20.1 version of
 * the {@code minecraft:copy_components} loot function, with the same {@code source} and {@code include}
 * fields. {@code minecraft:custom_name} copies the block entity's custom name.
 */
public class CopyComponentsFunction extends LootItemConditionalFunction {
    private static final String CUSTOM_NAME = "minecraft:custom_name";

    private final List<String> include;

    CopyComponentsFunction(LootItemCondition[] conditions, List<String> include) {
        super(conditions);
        this.include = ImmutableList.copyOf(include);
    }

    @Override
    public LootItemFunctionType getType() {
        return CoreContent.COPY_COMPONENTS.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.BLOCK_ENTITY);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        BlockEntity blockEntity = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
        if (!(blockEntity instanceof ImplicitComponents holder)) {
            return stack;
        }
        ItemStack collected = new ItemStack(stack.getItem());
        holder.collectImplicitComponents(collected);
        CompoundTag data = collected.getTag();
        for (String component : include) {
            if (CUSTOM_NAME.equals(component)) {
                if (collected.hasCustomHoverName()) {
                    stack.setHoverName(collected.getHoverName());
                }
            } else if (data != null && data.contains(component)) {
                stack.getOrCreateTag().put(component, data.get(component).copy());
            }
        }
        return stack;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<CopyComponentsFunction> {
        @Override
        public void serialize(JsonObject json, CopyComponentsFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
            json.addProperty("source", "block_entity");
            JsonArray include = new JsonArray();
            function.include.forEach(include::add);
            json.add("include", include);
        }

        @Override
        public CopyComponentsFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            String source = GsonHelper.getAsString(json, "source", "block_entity");
            if (!"block_entity".equals(source)) {
                throw new IllegalArgumentException("Unsupported copy_components source " + source);
            }
            ImmutableList.Builder<String> include = ImmutableList.builder();
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "include", new JsonArray())) {
                include.add(GsonHelper.convertToString(element, "include"));
            }
            return new CopyComponentsFunction(conditions, include.build());
        }
    }
}
