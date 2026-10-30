package com.nightbeam.omnivault.storage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/** A fluid type (always the source fluid). Amounts that go with it are in millibuckets. */
public record FluidKey(Fluid fluid) {
    public static final int BUCKET = 1000;
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidKey> STREAM_CODEC =
            ByteBufCodecs.registry(net.minecraft.core.registries.Registries.FLUID).map(FluidKey::new, FluidKey::fluid);

    public FluidKey {
        if (fluid instanceof FlowingFluid flowing) {
            fluid = flowing.getSource();
        }
    }

    @Nullable
    public static FluidKey of(Fluid fluid) {
        return fluid == null || fluid == Fluids.EMPTY ? null : new FluidKey(fluid);
    }

    public ResourceLocation id() {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    /** The bucket item for this fluid, used as its icon; an empty bucket if it has none. */
    public ItemStack icon() {
        ItemStack bucket = new ItemStack(fluid.getBucket());
        return bucket.isEmpty() ? new ItemStack(Items.BUCKET) : bucket;
    }

    private static java.util.Map<net.minecraft.world.item.Item, FluidKey> buckets;

    /** The fluid a filled bucket item holds, or null for anything else (including the empty bucket). */
    @Nullable
    public static FluidKey fromBucket(net.minecraft.world.item.Item item) {
        if (buckets == null) {
            java.util.Map<net.minecraft.world.item.Item, FluidKey> map = new java.util.HashMap<>();
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                if (fluid.isSource(fluid.defaultFluidState()) && fluid.getBucket() != Items.AIR && fluid.getBucket() != Items.BUCKET) {
                    map.putIfAbsent(fluid.getBucket(), new FluidKey(fluid));
                }
            }
            buckets = map;
        }
        return buckets.get(item);
    }

    @Nullable
    public static FluidKey load(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.FLUID.containsKey(location)) {
            return null;
        }
        return of(BuiltInRegistries.FLUID.get(location));
    }
}
