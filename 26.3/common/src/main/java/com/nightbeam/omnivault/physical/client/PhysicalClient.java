package com.nightbeam.omnivault.physical.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.OmniVaultClient;
import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.physical.linked.LinkedFrequency;
import com.nightbeam.omnivault.physical.linked.LinkedPouchItem;
import com.nightbeam.omnivault.physical.vault.VaultTier;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Screens, lid renderers, standalone lid models and item tints for physical storage. The Relocator's
 * full look is a {@code has_component} condition in its item model definition.
 */
public final class PhysicalClient {
    /** Latches of an unbound pouch read as dark steel. */
    private static final int UNBOUND = 0xFF3A414C;

    private PhysicalClient() {
    }

    public static void init() {
        OmniVaultClient.registration(r -> {
            r.screen(PhysicalContent.VAULT_MENU, StorageScreen::new);
            r.screen(PhysicalContent.LINKED_MENU, StorageScreen::new);
            r.screen(PhysicalContent.TRASH_MENU, TrashScreen::new);

            r.blockEntityRenderer(PhysicalContent.VAULT_BE, VaultRenderer::new);
            r.blockEntityRenderer(PhysicalContent.LINKED_BE, LinkedVaultRenderer::new);
            for (VaultTier tier : VaultTier.values()) {
                r.standaloneModel(VaultRenderer.lidModel(tier));
            }
            r.standaloneModel(LinkedVaultRenderer.LID);
            r.standaloneModel(LinkedVaultRenderer.LID_PRIVATE);
            r.standaloneModel(LinkedVaultRenderer.LATCH);

            // Tint indices 1..3 of the pouch and the Linked Vault item are the three latches, left to right.
            r.itemTintSource(OmniVault.id("linked_latch"), LatchTint.MAP_CODEC);
        });
    }

    /** The colour of one latch (0..2, left to right) of a Linked Pouch or Linked Vault item. */
    public record LatchTint(int latch) implements ItemTintSource {
        public static final MapCodec<LatchTint> MAP_CODEC = Codec.intRange(0, 2).fieldOf("latch").xmap(LatchTint::new, LatchTint::latch);

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
            LinkedFrequency frequency = stack.getItem() instanceof LinkedPouchItem ? LinkedPouchItem.frequency(stack)
                    : stack.getOrDefault(PhysicalContent.LINKED_FREQUENCY.get(), LinkedFrequency.DEFAULT);
            return frequency == null ? UNBOUND : LinkedFrequency.tint(frequency.color(latch));
        }

        @Override
        public MapCodec<LatchTint> type() {
            return MAP_CODEC;
        }
    }
}
