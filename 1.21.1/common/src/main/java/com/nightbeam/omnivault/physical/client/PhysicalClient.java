package com.nightbeam.omnivault.physical.client;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.OmniVaultClient;
import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.physical.linked.LinkedFrequency;
import com.nightbeam.omnivault.physical.linked.LinkedPouchItem;
import com.nightbeam.omnivault.physical.relocator.RelocatorItem;
import com.nightbeam.omnivault.physical.vault.VaultTier;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Screens, lid renderers, standalone lid models and item tints for physical storage. */
public final class PhysicalClient {
    public static final ResourceLocation FULL = OmniVault.id("full");
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

            // Tint indices 1..3 are the three latches, left to right.
            r.itemColor((stack, tintIndex) -> latchTint(stack, tintIndex, LinkedPouchItem.frequency(stack), UNBOUND), PhysicalContent.LINKED_POUCH);
            r.itemColor((stack, tintIndex) -> latchTint(stack, tintIndex,
                    stack.getOrDefault(PhysicalContent.LINKED_FREQUENCY.get(), LinkedFrequency.DEFAULT), UNBOUND), PhysicalContent.LINKED_VAULT);

            ClampedItemPropertyFunction full = (stack, level, entity, seed) -> RelocatorItem.carried(stack) != null ? 1.0f : 0.0f;
            r.itemProperty(PhysicalContent.RELOCATOR, FULL, full);
            r.itemProperty(PhysicalContent.NETHERITE_RELOCATOR, FULL, full);
        });
    }

    private static int latchTint(ItemStack stack, int tintIndex, LinkedFrequency frequency, int fallback) {
        if (tintIndex < 1 || tintIndex > 3) {
            return 0xFFFFFFFF;
        }
        return frequency == null ? fallback : LinkedFrequency.tint(frequency.color(tintIndex - 1));
    }
}
