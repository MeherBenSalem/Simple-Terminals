package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgradeItem;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeConfig;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeType;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.registry.ModRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Registry entries of the backpack feature: six tiers of backpacks, their upgrade cards and data. */
public final class BackpackContent {
    // Data components ----------------------------------------------------------------------------
    /** Identifies a backpack's contents in {@link BackpackStorage}. */
    public static final Supplier<DataComponentType<UUID>> BACKPACK_ID = ModRegistry.component("backpack_id",
            b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    /** Used/total slots and installed upgrades, for client tooltips. */
    public static final Supplier<DataComponentType<BackpackSummary>> SUMMARY = ModRegistry.component("backpack_summary",
            b -> b.persistent(BackpackSummary.CODEC).networkSynchronized(BackpackSummary.STREAM_CODEC));
    /** Filter and settings stored on an upgrade card. */
    public static final Supplier<DataComponentType<UpgradeConfig>> UPGRADE_CONFIG = ModRegistry.component("upgrade_config",
            b -> b.persistent(UpgradeConfig.CODEC).networkSynchronized(UpgradeConfig.STREAM_CODEC));

    // Backpacks ----------------------------------------------------------------------------------
    private static final Map<BackpackTier, Supplier<BackpackBlock>> BLOCKS = new EnumMap<>(BackpackTier.class);
    private static final Map<BackpackTier, Supplier<BackpackItem>> ITEMS = new EnumMap<>(BackpackTier.class);

    static {
        for (BackpackTier tier : BackpackTier.values()) {
            Supplier<BackpackBlock> block = ModRegistry.block(tier.id(), () -> new BackpackBlock(tier, BlockBehaviour.Properties.of()
                    .mapColor(tier == BackpackTier.NETHERITE ? MapColor.COLOR_BLACK : MapColor.COLOR_BROWN)
                    .strength(0.8f, 1200.0f).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)));
            BLOCKS.put(tier, block);
            ITEMS.put(tier, ModRegistry.item(tier.id(), () -> {
                Item.Properties properties = new Item.Properties().rarity(tier.rarity());
                if (tier.fireResistant()) {
                    properties.fireResistant();
                }
                return new BackpackItem(block.get(), tier, properties);
            }));
        }
    }

    public static final Supplier<BlockEntityType<BackpackBlockEntity>> BLOCK_ENTITY = ModRegistry.blockEntity("backpack",
            BackpackBlockEntity::new, allBlocks());
    public static final Supplier<MenuType<BackpackMenu>> MENU = ModRegistry.menu("backpack", BackpackMenu::fromNetwork);

    // Upgrades -----------------------------------------------------------------------------------
    private static final Map<UpgradeType, Supplier<BackpackUpgradeItem>> UPGRADES = new EnumMap<>(UpgradeType.class);

    static {
        for (UpgradeType type : UpgradeType.values()) {
            Rarity rarity = type == UpgradeType.EVERLASTING ? Rarity.RARE : Rarity.COMMON;
            UPGRADES.put(type, ModRegistry.item(type.itemId(), () -> new BackpackUpgradeItem(type, new Item.Properties().rarity(rarity))));
        }
    }

    public static final Supplier<RecipeSerializer<BackpackUpgradeRecipe>> UPGRADE_RECIPE = Services.REGISTRY.register(
            Registries.RECIPE_SERIALIZER, "backpack_upgrade", BackpackUpgradeRecipe.Serializer::new);

    // Tags ---------------------------------------------------------------------------------------
    /** Items (beyond backpacks, shulker boxes and filled containers) that may not be stored in a backpack. */
    public static final TagKey<Item> NOT_ALLOWED_IN_BACKPACKS = TagKey.create(Registries.ITEM, OmniVault.id("not_allowed_in_backpacks"));
    public static final TagKey<Item> BACKPACKS = TagKey.create(Registries.ITEM, OmniVault.id("backpacks"));

    private BackpackContent() {
    }

    @SuppressWarnings("unchecked")
    private static Supplier<BackpackBlock>[] allBlocks() {
        return BLOCKS.values().toArray(new Supplier[0]);
    }

    public static BackpackBlock block(BackpackTier tier) {
        return BLOCKS.get(tier).get();
    }

    public static BackpackItem item(BackpackTier tier) {
        return ITEMS.get(tier).get();
    }

    public static Supplier<BackpackItem> itemSupplier(BackpackTier tier) {
        return ITEMS.get(tier);
    }

    public static Supplier<BackpackBlock> blockSupplier(BackpackTier tier) {
        return BLOCKS.get(tier);
    }

    public static BackpackUpgradeItem upgrade(UpgradeType type) {
        return UPGRADES.get(type).get();
    }

    public static Map<UpgradeType, Supplier<BackpackUpgradeItem>> upgrades() {
        return Collections.unmodifiableMap(UPGRADES);
    }

    public static void init() {
        BackpackConfig.init();
        ModCapabilities.exposeItems(BLOCK_ENTITY);
        OmniVaultCommon.packets(BackpackPayloads::register);
    }
}
