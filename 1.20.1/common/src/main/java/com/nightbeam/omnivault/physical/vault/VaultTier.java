package com.nightbeam.omnivault.physical.vault;

import com.mojang.serialization.Codec;
import com.nightbeam.omnivault.physical.StorageLayout;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

/** Vault tiers, smallest first. Grids widen instead of scrolling: 9×5, 9×6, 9×9, 12×9, 15×9, 18×9. */
public enum VaultTier implements StringRepresentable {
    COPPER("copper", 9, 5, 3.0f, 6.0f, MapColor.COLOR_ORANGE, SoundType.COPPER, 0xFFD9874E, 1.05f),
    IRON("iron", 9, 6, 4.0f, 6.0f, MapColor.METAL, SoundType.METAL, 0xFFC4C9CF, 1.0f),
    GOLD("gold", 9, 9, 4.0f, 6.0f, MapColor.GOLD, SoundType.METAL, 0xFFF5C542, 1.1f),
    DIAMOND("diamond", 12, 9, 5.0f, 6.0f, MapColor.DIAMOND, SoundType.METAL, 0xFF4DE8E0, 1.15f),
    NETHERITE("netherite", 15, 9, 6.0f, 1200.0f, MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK, 0xFF8A7A94, 0.85f),
    OMNIUM("omnium", 18, 9, 6.0f, 1200.0f, MapColor.COLOR_PURPLE, SoundType.NETHERITE_BLOCK, 0xFFA46BFF, 0.95f);

    public static final Codec<VaultTier> CODEC = StringRepresentable.fromEnum(VaultTier::values);

    private final String name;
    private final StorageLayout layout;
    private final float hardness;
    private final float resistance;
    private final MapColor mapColor;
    private final SoundType sound;
    private final int uiColor;
    private final float lidPitch;

    VaultTier(String name, int columns, int rows, float hardness, float resistance, MapColor mapColor, SoundType sound, int uiColor, float lidPitch) {
        this.name = name;
        this.layout = new StorageLayout(columns, rows);
        this.hardness = hardness;
        this.resistance = resistance;
        this.mapColor = mapColor;
        this.sound = sound;
        this.uiColor = uiColor;
        this.lidPitch = lidPitch;
    }

    public static VaultTier byId(int id) {
        VaultTier[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Registry path of this tier's vault block, e.g. {@code diamond_vault}. */
    public String vaultName() {
        return name + "_vault";
    }

    /** Registry path of the kit that upgrades the previous tier into this one, e.g. {@code diamond_vault_kit}. */
    public String kitName() {
        return name + "_vault_kit";
    }

    public StorageLayout layout() {
        return layout;
    }

    public int size() {
        return layout.size();
    }

    public boolean blastResistant() {
        return resistance >= 1200.0f;
    }

    public int uiColor() {
        return uiColor;
    }

    public float lidPitch() {
        return lidPitch;
    }

    @Nullable
    public VaultTier previous() {
        return ordinal() == 0 ? null : values()[ordinal() - 1];
    }

    @Nullable
    public VaultTier next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    public BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(hardness, resistance)
                .sound(sound)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }
}
