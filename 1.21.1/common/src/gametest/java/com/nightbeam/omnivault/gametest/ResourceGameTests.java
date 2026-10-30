package com.nightbeam.omnivault.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightbeam.omnivault.OmniVault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Checks every registered OmniVault block and item against the resources that ship with the mod. */
@GameTestHolder(OmniVault.MOD_ID)
@PrefixGameTestTemplate(false)
public class ResourceGameTests {
    private static boolean exists(String path) {
        try (InputStream in = OmniVault.class.getResourceAsStream("/" + path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonObject lang() throws Exception {
        try (InputStream in = OmniVault.class.getResourceAsStream("/assets/omnivault/lang/en_us.json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @GameTest(template = Templates.FLOOR)
    public static void everyBlockAndItemHasResources(GameTestHelper helper) throws Exception {
        JsonObject lang = lang();
        List<String> problems = new ArrayList<>();
        int blocks = 0;
        int items = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(OmniVault.MOD_ID)) {
                continue;
            }
            blocks++;
            if (!exists("assets/omnivault/blockstates/" + id.getPath() + ".json")) {
                problems.add("no blockstate for block " + id);
            }
            if (helper.getLevel().getServer().reloadableRegistries().getLootTable(block.getLootTable()) == LootTable.EMPTY) {
                problems.add("no loot table for block " + id);
            }
            if (!lang.has(block.getDescriptionId())) {
                problems.add("no lang entry " + block.getDescriptionId());
            }
        }
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(OmniVault.MOD_ID)) {
                continue;
            }
            items++;
            if (!exists("assets/omnivault/models/item/" + id.getPath() + ".json")) {
                problems.add("no item model for " + id);
            }
            if (!lang.has(item.getDescriptionId())) {
                problems.add("no lang entry " + item.getDescriptionId());
            }
        }
        helper.assertTrue(blocks >= 30 && items >= 90, "expected the full registry, found " + blocks + " blocks and " + items + " items");
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        OmniVault.LOG.info("Resource check: {} blocks and {} items all have their resources", blocks, items);
        helper.succeed();
    }
}
