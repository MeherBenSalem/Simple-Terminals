package com.nightbeam.omnivault.physical;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.physical.linked.LinkedFrequency;
import com.nightbeam.omnivault.physical.linked.LinkedPouchItem;
import com.nightbeam.omnivault.physical.linked.LinkedVaultBlock;
import com.nightbeam.omnivault.physical.linked.LinkedVaultBlockEntity;
import com.nightbeam.omnivault.physical.linked.LinkedVaultItem;
import com.nightbeam.omnivault.physical.relocator.RelocatedBlock;
import com.nightbeam.omnivault.physical.relocator.RelocatorItem;
import com.nightbeam.omnivault.physical.trash.TrashCanBlock;
import com.nightbeam.omnivault.physical.trash.TrashCanBlockEntity;
import com.nightbeam.omnivault.physical.trash.TrashMenu;
import com.nightbeam.omnivault.physical.vault.VaultBlock;
import com.nightbeam.omnivault.physical.vault.VaultBlockEntity;
import com.nightbeam.omnivault.physical.vault.VaultTier;
import com.nightbeam.omnivault.physical.vault.VaultUpgradeKitItem;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.registry.ModRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Physical storage: tiered vaults and their upgrade kits, Linked Vaults and Pouches, the Trash Can and
 * the Relocator.
 */
public final class PhysicalContent {
    public static final TagKey<Block> RELOCATOR_DENYLIST = TagKey.create(Registries.BLOCK, OmniVault.id("relocator_denylist"));
    public static final TagKey<Block> VAULT_UPGRADEABLE = TagKey.create(Registries.BLOCK, OmniVault.id("vault_upgradeable"));

    public static final Supplier<DataComponentType<LinkedFrequency>> LINKED_FREQUENCY = ModRegistry.component("linked_frequency",
            builder -> builder.persistent(LinkedFrequency.CODEC).networkSynchronized(LinkedFrequency.STREAM_CODEC));
    public static final Supplier<DataComponentType<RelocatedBlock>> RELOCATED_BLOCK = ModRegistry.component("relocated_block",
            builder -> builder.persistent(RelocatedBlock.CODEC).networkSynchronized(RelocatedBlock.STREAM_CODEC));

    private static final Map<VaultTier, Supplier<VaultBlock>> VAULTS = new EnumMap<>(VaultTier.class);
    private static final Map<VaultTier, Supplier<VaultUpgradeKitItem>> KITS = new EnumMap<>(VaultTier.class);

    static {
        for (VaultTier tier : VaultTier.values()) {
            VAULTS.put(tier, ModRegistry.blockWithItem(tier.vaultName(), () -> new VaultBlock(tier, tier.properties()),
                    (Item.Properties properties) -> tier.blastResistant() ? properties.fireResistant() : properties));
        }
        for (VaultTier tier : VaultTier.values()) {
            KITS.put(tier, ModRegistry.item(tier.kitName(), () -> new VaultUpgradeKitItem(tier, new Item.Properties().stacksTo(16)
                    .rarity(tier == VaultTier.OMNIUM ? Rarity.RARE : tier.blastResistant() ? Rarity.UNCOMMON : Rarity.COMMON))));
        }
    }

    public static final Supplier<LinkedVaultBlock> LINKED_VAULT = ModRegistry.blockWithItem("linked_vault",
            () -> new LinkedVaultBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(22.5f, 600.0f)
                    .sound(SoundType.NETHERITE_BLOCK).lightLevel(state -> 5).noOcclusion().pushReaction(PushReaction.BLOCK)),
            (LinkedVaultBlock block) -> new LinkedVaultItem(block, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Supplier<LinkedPouchItem> LINKED_POUCH = ModRegistry.item("linked_pouch",
            () -> new LinkedPouchItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Supplier<TrashCanBlock> TRASH_CAN = ModRegistry.blockWithItem("trash_can",
            () -> new TrashCanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .sound(SoundType.METAL).lightLevel(state -> 3).noOcclusion()));
    public static final Supplier<RelocatorItem> RELOCATOR = ModRegistry.item("relocator",
            () -> new RelocatorItem(false, new Item.Properties().durability(PhysicalConfig.RELOCATOR_USES.get())));
    public static final Supplier<RelocatorItem> NETHERITE_RELOCATOR = ModRegistry.item("netherite_relocator",
            () -> new RelocatorItem(true, new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.RARE)));

    @SuppressWarnings("unchecked")
    public static final Supplier<BlockEntityType<VaultBlockEntity>> VAULT_BE = ModRegistry.blockEntity("vault", VaultBlockEntity::new,
            (Supplier<? extends Block>[]) VAULTS.values().toArray(new Supplier<?>[0]));
    public static final Supplier<BlockEntityType<LinkedVaultBlockEntity>> LINKED_BE = ModRegistry.blockEntity("linked_vault",
            LinkedVaultBlockEntity::new, LINKED_VAULT);
    public static final Supplier<BlockEntityType<TrashCanBlockEntity>> TRASH_BE = ModRegistry.blockEntity("trash_can",
            TrashCanBlockEntity::new, TRASH_CAN);

    public static final Supplier<MenuType<StorageMenu>> VAULT_MENU = ModRegistry.menu("vault", StorageMenu::vaultClient);
    public static final Supplier<MenuType<StorageMenu>> LINKED_MENU = ModRegistry.menu("linked_vault", StorageMenu::linkedClient);
    public static final Supplier<MenuType<TrashMenu>> TRASH_MENU = ModRegistry.menu("trash_can", TrashMenu::client);

    private PhysicalContent() {
    }

    public static VaultBlock vault(VaultTier tier) {
        return VAULTS.get(tier).get();
    }

    public static Supplier<VaultBlock> vaultSupplier(VaultTier tier) {
        return VAULTS.get(tier);
    }

    public static VaultUpgradeKitItem kit(VaultTier tier) {
        return KITS.get(tier).get();
    }

    public static void init() {
        PhysicalConfig.init();
        ModCapabilities.exposeItems(VAULT_BE);
        ModCapabilities.exposeItems(LINKED_BE);
        ModCapabilities.exposeItems(TRASH_BE);
    }
}
