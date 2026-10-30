package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.CraftingManager;
import com.nightbeam.omnivault.autocraft.block.AssemblerBlockEntity;
import com.nightbeam.omnivault.autocraft.block.AutocraftDeviceBlock;
import com.nightbeam.omnivault.autocraft.block.CraftingProcessorBlockEntity;
import com.nightbeam.omnivault.autocraft.block.PatternProviderBlockEntity;
import com.nightbeam.omnivault.autocraft.block.PatternTerminalBlockEntity;
import com.nightbeam.omnivault.autocraft.menu.PatternTerminalMenu;
import com.nightbeam.omnivault.autocraft.job.CraftingJob;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.autocraft.pattern.PatternItem;
import com.nightbeam.omnivault.autocraft.plan.CraftingPlan;
import com.nightbeam.omnivault.autocraft.plan.PlanError;
import com.nightbeam.omnivault.content.CoreContent;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.CraftingHooks;
import com.nightbeam.omnivault.network.block.DirectionalDeviceBlock;
import com.nightbeam.omnivault.network.block.DriveBlockEntity;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import com.nightbeam.omnivault.network.block.OrientedDeviceBlock;
import com.nightbeam.omnivault.network.cell.CellTier;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@GameTestHolder(OmniVault.MOD_ID)
@PrefixGameTestTemplate(false)
public class AutocraftGameTests {
    private static final BlockPos CORE = new BlockPos(1, 1, 4);
    private static final BlockPos DRIVE = new BlockPos(3, 1, 5);
    private static final BlockPos PROCESSOR = new BlockPos(2, 1, 5);
    private static final BlockPos ASSEMBLER = new BlockPos(4, 1, 5);
    /** Faces north, away from the spine; its back touches the cable at (6,1,4). */
    private static final BlockPos PROVIDER = new BlockPos(6, 1, 3);

    // -- world building ----------------------------------------------------------------------------

    /** Core, a cable run along z = 4, a drive with a 64k cell; optionally a processor and an assembler. */
    private static void network(GameTestHelper helper, boolean processor, boolean assembler) {
        helper.setBlock(CORE, NetworkContent.NEXUS_CORE.get());
        for (int x = 2; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), NetworkContent.NEXUS_CABLE.get());
        }
        helper.setBlock(DRIVE, NetworkContent.DRIVE.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        ((DriveBlockEntity) helper.getBlockEntity(DRIVE)).cells().setItem(0, new ItemStack(NetworkContent.CELLS.get(CellTier.T64K).get()));
        if (processor) {
            helper.setBlock(PROCESSOR, AutocraftContent.CRAFTING_PROCESSOR.get());
        }
        if (assembler) {
            helper.setBlock(ASSEMBLER, AutocraftContent.ASSEMBLER.get());
        }
    }

    private static PatternProviderBlockEntity provider(GameTestHelper helper, BlockPos pos, Direction facing, ItemStack... patterns) {
        helper.setBlock(pos, AutocraftContent.PATTERN_PROVIDER.get().defaultBlockState().setValue(DirectionalDeviceBlock.FACING, facing));
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(pos);
        for (int i = 0; i < patterns.length; i++) {
            provider.patterns().setItem(i, patterns[i]);
        }
        return provider;
    }

    private static StorageNetwork net(GameTestHelper helper) {
        return ((NexusCoreBlockEntity) helper.getBlockEntity(CORE)).network();
    }

    private static CraftingManager crafting(GameTestHelper helper) {
        return CraftingManager.of(net(helper));
    }

    private static ItemKey key(Item item) {
        return ItemKey.of(new ItemStack(item));
    }

    private static long count(GameTestHelper helper, Item item) {
        return net(helper).storage().getCount(key(item));
    }

    private static void store(GameTestHelper helper, Item item, int amount) {
        long stored = net(helper).storage().insert(key(item), amount, false);
        helper.assertTrue(stored == amount, "could not store " + amount + " " + item);
    }

    /** A crafting pattern whose result comes from the real recipe, like the encoding terminal does it. */
    private static ItemStack craftingPattern(GameTestHelper helper, Item... grid) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : grid) {
            stacks.add(item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
        }
        while (stacks.size() < 9) {
            stacks.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, stacks);
        ItemStack result = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()))
                .orElseThrow(() -> new IllegalStateException("no recipe for " + Arrays.toString(grid)));
        return PatternItem.encode(PatternData.crafting(stacks, result));
    }

    private static ItemStack processingPattern(ItemStack input, ItemStack output) {
        return PatternItem.encode(PatternData.processing(List.of(input), List.of(output)));
    }

    private static ItemStack planksPattern(GameTestHelper helper) {
        return craftingPattern(helper, Items.OAK_LOG);
    }

    private static ItemStack chestPattern(GameTestHelper helper) {
        Item p = Items.OAK_PLANKS;
        return craftingPattern(helper, p, p, p, p, Items.AIR, p, p, p, p);
    }

    private static boolean idle(GameTestHelper helper) {
        return crafting(helper).busyProcessors().isEmpty();
    }

    // -- tests -------------------------------------------------------------------------------------

    @GameTest(template = "floor_9x9")
    public static void craftablesListPatternOutputs(GameTestHelper helper) {
        network(helper, true, true);
        provider(helper, PROVIDER, Direction.NORTH, planksPattern(helper), chestPattern(helper),
                processingPattern(new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT)));
        helper.runAfterDelay(5, () -> {
            var craftables = CraftingHooks.get().craftables(net(helper));
            helper.assertTrue(craftables.contains(key(Items.OAK_PLANKS)), "planks are craftable");
            helper.assertTrue(craftables.contains(key(Items.CHEST)), "chests are craftable");
            helper.assertTrue(craftables.contains(key(Items.IRON_INGOT)), "processing outputs are craftable");
            helper.assertTrue(!craftables.contains(key(Items.OAK_LOG)), "inputs are not craftable");
            helper.assertTrue(craftables.size() == 3, "exactly the three pattern outputs, got " + craftables);
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void planUsesStockThenCraftsTheRest(GameTestHelper helper) {
        network(helper, true, true);
        provider(helper, PROVIDER, Direction.NORTH, chestPattern(helper), planksPattern(helper));
        helper.runAfterDelay(5, () -> {
            store(helper, Items.OAK_LOG, 8);
            store(helper, Items.OAK_PLANKS, 4);
            CraftingPlan plan = crafting(helper).plan(key(Items.CHEST), 2);
            helper.assertTrue(plan.canStart(), "plan should be startable, error " + plan.error() + " missing " + plan.missing());
            helper.assertTrue(plan.used(key(Items.OAK_PLANKS)) == 4, "uses the 4 stored planks, got " + plan.used(key(Items.OAK_PLANKS)));
            helper.assertTrue(plan.crafted(key(Items.OAK_PLANKS)) == 12, "crafts the other 12 planks, got " + plan.crafted(key(Items.OAK_PLANKS)));
            helper.assertTrue(plan.used(key(Items.OAK_LOG)) == 3, "3 logs make 12 planks, got " + plan.used(key(Items.OAK_LOG)));
            helper.assertTrue(plan.crafted(key(Items.CHEST)) == 2, "crafts 2 chests");
            helper.assertTrue(plan.used(key(Items.CHEST)) == 0, "the requested item is always crafted");
            helper.assertTrue(plan.missing().isEmpty(), "nothing missing");
            helper.assertTrue(plan.steps().size() == 2, "two batched steps, got " + plan.steps().size());
            helper.assertTrue(!plan.steps().get(0).pattern().outputs().get(0).key().equals(key(Items.CHEST)), "ingredients come first");
            helper.assertTrue(plan.totalCrafts() == 5, "3 plank runs + 2 chest runs, got " + plan.totalCrafts());
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void jobCraftsChestsFromLogs(GameTestHelper helper) {
        network(helper, true, true);
        provider(helper, PROVIDER, Direction.NORTH, chestPattern(helper), planksPattern(helper));
        helper.runAfterDelay(5, () -> {
            store(helper, Items.OAK_LOG, 6);
            CraftingManager.StartResult result = crafting(helper).start(key(Items.CHEST), 2, null);
            helper.assertTrue(result.started(), "job should start, error " + result.plan().error());
            helper.assertTrue(count(helper, Items.OAK_LOG) == 2, "4 logs are reserved by the job, 2 stay stored");
            helper.succeedWhen(() -> {
                helper.assertTrue(count(helper, Items.CHEST) == 2, "2 chests in the network, got " + count(helper, Items.CHEST));
                helper.assertTrue(count(helper, Items.OAK_PLANKS) == 0, "every plank was used");
                helper.assertTrue(count(helper, Items.OAK_LOG) == 2, "unreserved logs untouched");
                helper.assertTrue(idle(helper), "processor is free again");
                helper.assertBlockProperty(PROCESSOR, AutocraftDeviceBlock.WORKING, false);
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void missingIngredientsBlockTheJob(GameTestHelper helper) {
        network(helper, true, true);
        provider(helper, PROVIDER, Direction.NORTH, chestPattern(helper), planksPattern(helper));
        helper.runAfterDelay(5, () -> {
            store(helper, Items.OAK_LOG, 4);
            CraftingPlan plan = crafting(helper).plan(key(Items.CHEST), 3);
            helper.assertTrue(!plan.canStart(), "3 chests need 6 logs");
            helper.assertTrue(plan.error() == PlanError.MISSING, "reported as missing, got " + plan.error());
            helper.assertTrue(plan.missing(key(Items.OAK_LOG)) == 2, "2 logs missing, got " + plan.missing());
            helper.assertTrue(plan.used(key(Items.OAK_LOG)) == 4, "the 4 stored logs would be used");
            CraftingManager.StartResult result = crafting(helper).start(key(Items.CHEST), 3, null);
            helper.assertTrue(!result.started(), "job refuses to start");
            helper.assertTrue(count(helper, Items.OAK_LOG) == 4, "nothing was reserved");
            helper.assertTrue(idle(helper), "no job runs");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void requestsNeedACraftingProcessor(GameTestHelper helper) {
        network(helper, false, true);
        provider(helper, PROVIDER, Direction.NORTH, planksPattern(helper));
        helper.runAfterDelay(5, () -> {
            store(helper, Items.OAK_LOG, 4);
            CraftingPlan plan = crafting(helper).plan(key(Items.OAK_PLANKS), 4);
            helper.assertTrue(plan.error() == PlanError.NO_PROCESSOR, "no processor is reported, got " + plan.error());
            helper.assertTrue(!crafting(helper).start(key(Items.OAK_PLANKS), 4, null).started(), "job refused");
            helper.assertTrue(count(helper, Items.OAK_LOG) == 4, "nothing was reserved");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9")
    public static void cancellingReturnsReservedItems(GameTestHelper helper) {
        // No assembler: the job reserves its logs and then cannot make progress.
        network(helper, true, false);
        provider(helper, PROVIDER, Direction.NORTH, chestPattern(helper), planksPattern(helper));
        helper.runAfterDelay(5, () -> {
            store(helper, Items.OAK_LOG, 5);
            CraftingManager.StartResult result = crafting(helper).start(key(Items.CHEST), 1, null);
            helper.assertTrue(result.started(), "job starts, error " + result.plan().error());
            helper.assertTrue(count(helper, Items.OAK_LOG) == 3, "2 logs reserved");
            helper.runAfterDelay(10, () -> {
                CraftingJob job = result.job();
                helper.assertTrue(job.status() == CraftingJob.Status.NO_ASSEMBLER, "job waits for an assembler, got " + job.status());
                helper.assertBlockProperty(PROCESSOR, AutocraftDeviceBlock.WORKING, true);
                // The job, with everything it holds, survives a save and load.
                CraftingJob copy = CraftingJob.load(job.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                helper.assertTrue(copy != null && copy.id().equals(job.id()) && copy.target().equals(job.target()) && copy.amount() == 1,
                        "job identity survives saving");
                helper.assertTrue(copy.held(key(Items.OAK_LOG)) == 2, "reserved logs survive saving");
                helper.assertTrue(copy.steps().size() == 2 && copy.steps().get(0).remaining() == job.steps().get(0).remaining(),
                        "steps survive saving");
                helper.assertTrue(crafting(helper).cancel(job.id()), "cancel finds the job");
                helper.assertTrue(count(helper, Items.OAK_LOG) == 5, "reserved logs are back, got " + count(helper, Items.OAK_LOG));
                helper.assertTrue(idle(helper), "processor is free");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void craftingRemaindersReturnToTheNetwork(GameTestHelper helper) {
        network(helper, true, true);
        Item m = Items.MILK_BUCKET;
        Item w = Items.WHEAT;
        provider(helper, PROVIDER, Direction.NORTH, craftingPattern(helper, m, m, m, Items.SUGAR, Items.EGG, Items.SUGAR, w, w, w));
        helper.runAfterDelay(5, () -> {
            store(helper, Items.MILK_BUCKET, 3);
            store(helper, Items.SUGAR, 2);
            store(helper, Items.EGG, 1);
            store(helper, Items.WHEAT, 3);
            helper.assertTrue(crafting(helper).start(key(Items.CAKE), 1, null).started(), "cake job starts");
            helper.succeedWhen(() -> {
                helper.assertTrue(count(helper, Items.CAKE) == 1, "a cake was made");
                helper.assertTrue(count(helper, Items.BUCKET) == 3, "the empty buckets come back, got " + count(helper, Items.BUCKET));
                helper.assertTrue(count(helper, Items.MILK_BUCKET) == 0, "milk used");
                helper.assertTrue(idle(helper), "job finished");
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void plannerPicksAWorkingPatternAndSurvivesCycles(GameTestHelper helper) {
        network(helper, true, true);
        provider(helper, PROVIDER, Direction.NORTH,
                craftingPattern(helper, Items.OAK_WOOD), planksPattern(helper),
                craftingPattern(helper, Items.IRON_NUGGET, Items.IRON_NUGGET, Items.IRON_NUGGET, Items.IRON_NUGGET, Items.IRON_NUGGET,
                        Items.IRON_NUGGET, Items.IRON_NUGGET, Items.IRON_NUGGET, Items.IRON_NUGGET),
                craftingPattern(helper, Items.IRON_INGOT));
        helper.runAfterDelay(5, () -> {
            store(helper, Items.OAK_LOG, 2);
            CraftingPlan planks = crafting(helper).plan(key(Items.OAK_PLANKS), 8);
            helper.assertTrue(planks.canStart(), "the log pattern works although the wood pattern comes first");
            helper.assertTrue(planks.used(key(Items.OAK_LOG)) == 2 && planks.used(key(Items.OAK_WOOD)) == 0, "logs are used, not wood");
            CraftingPlan ingot = crafting(helper).plan(key(Items.IRON_INGOT), 1);
            helper.assertTrue(ingot.error() == PlanError.MISSING, "ingot <-> nuggets with nothing stored is missing, not endless");
            helper.assertTrue(!ingot.missing().isEmpty(), "something is reported missing");
            helper.succeed();
        });
    }

    @GameTest(template = "floor_9x9", timeoutTicks = 1200)
    public static void providerSmeltsRawIronInAFurnace(GameTestHelper helper) {
        network(helper, true, false);
        // A furnace above the floor, fed from above by a provider and emptied from below by an import bus;
        // a cable column at x = 5, z = 4 connects both to the spine.
        BlockPos furnace = new BlockPos(5, 3, 5);
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(5, y, 4), NetworkContent.NEXUS_CABLE.get());
        }
        helper.setBlock(new BlockPos(5, 2, 5), NetworkContent.IMPORT_BUS.get().defaultBlockState().setValue(DirectionalDeviceBlock.FACING, Direction.UP));
        helper.setBlock(furnace, Blocks.FURNACE);
        ((FurnaceBlockEntity) helper.getBlockEntity(furnace)).setItem(1, new ItemStack(Items.COAL, 4));
        provider(helper, new BlockPos(5, 4, 5), Direction.DOWN, processingPattern(new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT)));
        helper.runAfterDelay(5, () -> {
            store(helper, Items.RAW_IRON, 3);
            CraftingManager.StartResult result = crafting(helper).start(key(Items.IRON_INGOT), 3, null);
            helper.assertTrue(result.started(), "smelting job starts, error " + result.plan().error());
            helper.assertTrue(count(helper, Items.RAW_IRON) == 0, "raw iron reserved");
            helper.succeedWhen(() -> {
                helper.assertTrue(count(helper, Items.IRON_INGOT) == 3, "3 ingots in the network, got " + count(helper, Items.IRON_INGOT));
                helper.assertTrue(count(helper, Items.RAW_IRON) == 0, "no raw iron left");
                helper.assertTrue(idle(helper), "job finished");
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void machinesCanPushResultsIntoTheProvider(GameTestHelper helper) {
        network(helper, true, false);
        // The "machine" is a chest the test operates by hand: it turns 2 cobblestone into 1 gravel.
        BlockPos providerPos = new BlockPos(5, 1, 5);
        BlockPos chestPos = new BlockPos(5, 1, 6);
        helper.setBlock(chestPos, Blocks.CHEST);
        provider(helper, providerPos, Direction.SOUTH, processingPattern(new ItemStack(Items.COBBLESTONE, 2), new ItemStack(Items.GRAVEL)));
        helper.runAfterDelay(5, () -> {
            store(helper, Items.COBBLESTONE, 4);
            CraftingManager.StartResult result = crafting(helper).start(key(Items.GRAVEL), 2, null);
            helper.assertTrue(result.started(), "job starts, error " + result.plan().error());
            helper.runAfterDelay(5, () -> {
                ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
                int cobble = 0;
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    if (chest.getItem(i).is(Items.COBBLESTONE)) {
                        cobble += chest.getItem(i).getCount();
                    }
                }
                helper.assertTrue(cobble == 4, "both runs were pushed into the machine, got " + cobble);
                helper.assertTrue(result.job().status() == CraftingJob.Status.WAITING, "job waits for the machine, got " + result.job().status());
                chest.clearContent();
                SlotAccess handler = Services.PLATFORM.findSlotAccess(helper.getLevel(), helper.absolutePos(providerPos), Direction.SOUTH).orElseThrow();
                helper.assertTrue(handler.insertItem(0, new ItemStack(Items.GRAVEL, 2), false).isEmpty(), "provider accepts the results");
                helper.succeedWhen(() -> {
                    helper.assertTrue(count(helper, Items.GRAVEL) == 2, "gravel delivered to the network, got " + count(helper, Items.GRAVEL));
                    helper.assertTrue(count(helper, Items.COBBLESTONE) == 0, "cobblestone consumed");
                    helper.assertTrue(idle(helper), "job finished");
                });
            });
        });
    }

    @GameTest(template = "floor_9x9")
    public static void patternTerminalEncodesPatterns(GameTestHelper helper) {
        network(helper, true, true);
        BlockPos pos = new BlockPos(6, 1, 5);
        helper.setBlock(pos, AutocraftContent.PATTERN_TERMINAL.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        PatternTerminalBlockEntity terminal = (PatternTerminalBlockEntity) helper.getBlockEntity(pos);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PatternTerminalMenu menu = new PatternTerminalMenu(1, player.getInventory(), terminal);
        terminal.patterns().setItem(0, new ItemStack(AutocraftContent.BLANK_PATTERN.get(), 2));
        // Crafting mode: one log in the grid encodes "1 oak log -> 4 oak planks".
        terminal.inputs().setItem(4, new ItemStack(Items.OAK_LOG));
        menu.clickMenuButton(player, PatternTerminalMenu.BUTTON_ENCODE);
        ItemStack encoded = terminal.patterns().getItem(1);
        PatternData crafting = PatternItem.data(encoded);
        helper.assertTrue(encoded.is(AutocraftContent.CRAFTING_PATTERN.get()) && crafting != null, "a crafting pattern was encoded");
        helper.assertTrue(crafting.primaryOutput().is(Items.OAK_PLANKS) && crafting.primaryOutput().getCount() == 4, "it makes 4 planks");
        helper.assertTrue(terminal.patterns().getItem(0).getCount() == 1, "one blank pattern was used");
        // Processing mode overwrites the pattern in the output slot: 2 raw iron -> 2 iron ingots.
        menu.clickMenuButton(player, PatternTerminalMenu.BUTTON_MODE);
        helper.assertTrue(menu.processing() && terminal.processing(), "mode switched to processing");
        menu.clickMenuButton(player, PatternTerminalMenu.BUTTON_CLEAR);
        terminal.inputs().setItem(0, new ItemStack(Items.RAW_IRON, 2));
        terminal.outputs().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        menu.clickMenuButton(player, PatternTerminalMenu.BUTTON_ENCODE);
        ItemStack processing = terminal.patterns().getItem(1);
        PatternData data = PatternItem.data(processing);
        helper.assertTrue(processing.is(AutocraftContent.PROCESSING_PATTERN.get()) && data != null && data.processing(), "re-encoded as processing");
        helper.assertTrue(data.inputs().size() == 1 && data.inputs().get(0).getCount() == 2 && data.outputs().get(0).is(Items.IRON_INGOT),
                "processing pattern keeps amounts");
        helper.assertTrue(terminal.patterns().getItem(0).getCount() == 1, "overwriting does not use another blank");
        // Sneak-use wipes an encoded pattern back to a blank one.
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, processing.copy());
        ItemStack wiped = processing.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(wiped.is(AutocraftContent.BLANK_PATTERN.get()), "sneak-use clears the pattern");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9")
    public static void recipeViewerTransfersLoadThePatternTerminal(GameTestHelper helper) {
        network(helper, true, true);
        BlockPos pos = new BlockPos(6, 1, 5);
        helper.setBlock(pos, AutocraftContent.PATTERN_TERMINAL.get().defaultBlockState().setValue(OrientedDeviceBlock.FACING, Direction.SOUTH));
        PatternTerminalBlockEntity terminal = (PatternTerminalBlockEntity) helper.getBlockEntity(pos);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PatternTerminalMenu menu = new PatternTerminalMenu(1, player.getInventory(), terminal);
        // A furnace recipe arrives as a processing recipe, amounts capped at one pattern slot's limit.
        menu.transfer(true, List.of(new ItemStack(Items.RAW_GOLD, 100)), List.of(new ItemStack(Items.GOLD_INGOT)));
        helper.assertTrue(menu.processing() && terminal.processing(), "processing mode");
        helper.assertTrue(terminal.inputs().getItem(0).is(Items.RAW_GOLD) && terminal.inputs().getItem(0).getCount() == PatternData.MAX_SLOT_AMOUNT,
                "input amount clamped");
        helper.assertTrue(terminal.outputs().getItem(0).is(Items.GOLD_INGOT), "output loaded");
        // A crafting recipe switches back, keeps its shape, drops outputs and uses one of each item.
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.OAK_PLANKS, 5));
        grid.set(3, new ItemStack(Items.OAK_PLANKS));
        menu.transfer(false, grid, List.of(new ItemStack(Items.DIAMOND)));
        helper.assertFalse(menu.processing(), "crafting mode");
        helper.assertTrue(terminal.inputs().getItem(0).getCount() == 1 && terminal.inputs().getItem(3).is(Items.OAK_PLANKS)
                && terminal.inputs().getItem(1).isEmpty(), "grid shape kept, one item per cell");
        helper.assertTrue(terminal.outputs().getItem(0).isEmpty(), "crafting transfers set no outputs");
        terminal.patterns().setItem(0, new ItemStack(AutocraftContent.BLANK_PATTERN.get()));
        menu.clickMenuButton(player, PatternTerminalMenu.BUTTON_ENCODE);
        PatternData data = PatternItem.data(terminal.patterns().getItem(1));
        helper.assertTrue(data != null && data.primaryOutput().is(Items.STICK), "the transferred grid encodes sticks");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9")
    public static void speedUpgradesMakeAssemblersFaster(GameTestHelper helper) {
        network(helper, true, true);
        AssemblerBlockEntity assembler = (AssemblerBlockEntity) helper.getBlockEntity(ASSEMBLER);
        int base = assembler.craftsPerCycle();
        assembler.upgrades().setItem(0, new ItemStack(CoreContent.SPEED_UPGRADE.get()));
        assembler.upgrades().setItem(1, new ItemStack(CoreContent.SPEED_UPGRADE.get()));
        helper.assertTrue(assembler.craftsPerCycle() == base * 4, "two speed upgrades quadruple the crafts per cycle");
        CraftingProcessorBlockEntity processor = (CraftingProcessorBlockEntity) helper.getBlockEntity(PROCESSOR);
        helper.assertTrue(!processor.isBusy(), "a new processor is idle");
        helper.succeed();
    }
}
