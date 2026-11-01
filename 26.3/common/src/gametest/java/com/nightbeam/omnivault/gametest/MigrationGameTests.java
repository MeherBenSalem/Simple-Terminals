package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.backpack.BackpackData;
import com.nightbeam.omnivault.backpack.BackpackStorage;
import com.nightbeam.omnivault.network.cell.CellData;
import com.nightbeam.omnivault.network.cell.FluidCellData;
import com.nightbeam.omnivault.physical.linked.LinkedFrequency;
import com.nightbeam.omnivault.physical.linked.LinkedVaultData;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.util.LegacySavedData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A world upgraded from 1.21.1 still has our saved data in 1.21.1's files (data/omnivault_*.dat), written
 * the way 1.21.1 wrote them. Each test writes such a file into a scratch data folder, loads it the way the
 * server does, saves it and loads it again from the new file alone.
 */
public class MigrationGameTests {
    /** The data version 1.21.1 saves with. */
    private static final int V1_21_1 = 3955;
    private static final UUID CELL = UUID.fromString("0b1e9a52-37c1-4c8e-9d8b-5a1f0c6e2d41");
    private static final UUID BACKPACK = UUID.fromString("6f3d2c1b-8a9e-4f70-b5d4-3c2e1a0f9b87");

    @GameTest(structure = Templates.FLOOR, maxTicks = Templates.TICKS)
    public void storageCellsComeOverFrom1211(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // 1.21.1 saved each cell as a list of {item, count}; the sword still has 1.21.1's enchantment layout.
        ListTag items = new ListTag();
        items.add(entry(stack("minecraft:cobblestone", 1), 3_000_000_000L));
        CompoundTag sword = stack("minecraft:diamond_sword", 1);
        CompoundTag levels = new CompoundTag();
        levels.putInt("minecraft:sharpness", 3);
        CompoundTag enchantments = new CompoundTag();
        enchantments.put("levels", levels);
        enchantments.putBoolean("show_in_tooltip", true);
        sword.put("components", tag("minecraft:enchantments", enchantments));
        items.add(entry(sword, 5));
        CompoundTag cells = new CompoundTag();
        cells.put(CELL.toString(), items);
        CompoundTag fluidCell = new CompoundTag();
        fluidCell.putLong("minecraft:water", 2_500);
        CompoundTag fluidCells = new CompoundTag();
        fluidCells.put(CELL.toString(), fluidCell);

        ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
        enchanted.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
        ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
        ItemKey swordKey = ItemKey.of(enchanted);
        roundTrip(helper, CellData.LEGACY, "omnivault_cells", tag("cells", cells), data -> {
            CellData.CellInventory cell = data.getIfPresent(CELL);
            check(helper, cell != null, "the cell came over");
            check(helper, cell.get(cobble) == 3_000_000_000L, "billions of cobblestone came over, got " + cell.get(cobble));
            check(helper, cell.get(swordKey) == 5, "the enchanted swords came over with their enchantment, got " + cell.get(swordKey));
            check(helper, cell.stored() == 3_000_000_005L, "the cell's total is right");
        });
        roundTrip(helper, FluidCellData.LEGACY, "omnivault_fluid_cells", tag("cells", fluidCells), data -> {
            FluidCellData.Tank tank = data.getIfPresent(CELL);
            check(helper, tank != null && tank.get(FluidKey.of(Fluids.WATER)) == 2_500, "the fluid cell kept its water");
        });
        helper.succeed();
    }

    @GameTest(structure = Templates.FLOOR, maxTicks = Templates.TICKS)
    public void backpacksAndLinkedVaultsComeOverFrom1211(GameTestHelper helper) {
        // 1.21.1 saved a backpack as {Id, Items: {Size, Items}, Upgrades: {Size, Items}}; names were JSON text then.
        CompoundTag lunch = slot(stack("minecraft:bread", 12), 4);
        lunch.put("components", tag("minecraft:custom_name", StringTag.valueOf("\"Lunch\"")));
        CompoundTag backpack = new CompoundTag();
        backpack.put("Id", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, BACKPACK).getOrThrow());
        backpack.put("Items", contents(27, slot(stack("minecraft:torch", 40), 0), lunch));
        backpack.put("Upgrades", contents(1, slot(stack("omnivault:magnet_upgrade", 1), 0)));
        ListTag backpacks = new ListTag();
        backpacks.add(backpack);

        LinkedFrequency frequency = new LinkedFrequency(DyeColor.RED, DyeColor.LIME, DyeColor.BLUE, Optional.empty(), "");
        CompoundTag inventory = new CompoundTag();
        inventory.putString("Key", frequency.key());
        ListTag vaultItems = new ListTag();
        vaultItems.add(slot(stack("minecraft:emerald", 7), 26));
        inventory.put("Items", vaultItems);
        ListTag inventories = new ListTag();
        inventories.add(inventory);

        roundTrip(helper, BackpackStorage.LEGACY, "omnivault_backpacks", tag("Backpacks", backpacks), data -> {
            BackpackData restored = data.get(BACKPACK);
            check(helper, restored != null, "the backpack came over");
            check(helper, restored.items().getContainerSize() == 27 && restored.items().getItem(0).is(Items.TORCH)
                    && restored.items().getItem(0).getCount() == 40, "the torches came over");
            ItemStack bread = restored.items().getItem(4);
            check(helper, bread.is(Items.BREAD) && bread.getCount() == 12 && "Lunch".equals(bread.getHoverName().getString()),
                    "the named bread came over with its name, got " + bread + " " + bread.getHoverName().getString());
            check(helper, !restored.upgrades().getItem(0).isEmpty(), "the upgrade came over");
        });
        roundTrip(helper, LinkedVaultData.LEGACY, "omnivault_linked_vaults", tag("Inventories", inventories), data -> {
            ItemStack emeralds = data.inventory(frequency).getItem(26);
            check(helper, emeralds.is(Items.EMERALD) && emeralds.getCount() == 7, "the linked vault kept its emeralds, got " + emeralds);
        });
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Writes {@code data} as 1.21.1's {@code legacyName}.dat into a scratch data folder, loads it, checks it, saves it in
     * the new place, removes the old file and checks what loads from the new one.
     */
    private static <T extends SavedData> void roundTrip(GameTestHelper helper, LegacySavedData<T> legacy, String legacyName,
                                                        CompoundTag data, Consumer<T> checks) {
        MinecraftServer server = helper.getLevel().getServer();
        Path folder = null;
        try {
            folder = Files.createTempDirectory("omnivault-migration");
            CompoundTag file = new CompoundTag();
            file.put("data", data);
            file.putInt("DataVersion", V1_21_1);
            Path old = folder.resolve(legacyName + ".dat");
            NbtIo.writeCompressed(file, old);

            SavedDataStorage storage = new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess());
            checks.accept(legacy.get(server, storage, folder));
            storage.saveAndJoin();
            Path saved = legacy.type().id().withSuffix(".dat").resolveAgainst(folder);
            check(helper, Files.isRegularFile(saved), "nothing was saved to " + folder.relativize(saved));
            check(helper, Files.isRegularFile(old), "the 1.21.1 file is kept");

            Files.delete(old);
            checks.accept(legacy.get(server, new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess()), folder));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            delete(folder);
        }
    }

    private static CompoundTag tag(String key, Tag value) {
        CompoundTag tag = new CompoundTag();
        tag.put(key, value);
        return tag;
    }

    /** An item stack as 1.21.1 saved it. */
    private static CompoundTag stack(String id, int count) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", id);
        stack.putInt("count", count);
        return stack;
    }

    private static CompoundTag slot(CompoundTag stack, int slot) {
        stack.putByte("Slot", (byte) slot);
        return stack;
    }

    private static CompoundTag entry(CompoundTag item, long count) {
        CompoundTag entry = new CompoundTag();
        entry.put("item", item);
        entry.putLong("count", count);
        return entry;
    }

    /** A backpack inventory as 1.21.1 saved it. */
    private static CompoundTag contents(int size, CompoundTag... stacks) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Size", size);
        ListTag items = new ListTag();
        for (CompoundTag stack : stacks) {
            items.add(stack);
        }
        tag.put("Items", items);
        return tag;
    }

    private static void delete(Path folder) {
        if (folder == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // A scratch folder in the system's temp directory.
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
