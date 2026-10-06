import com.nightbeam.simpleterminals.registry.ModBlocks;
import com.nightbeam.simpleterminals.registry.ModItems;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Tests the real common registry sources against vanilla 26.2 before registry freeze. */
public class NativeRegistryIdsSmokeTest {
    private static final Identifier TARGET = Identifier.fromNamespaceAndPath(
            "simple_terminals", "crafting_terminal");
    private static final Identifier LOOT_TARGET = Identifier.fromNamespaceAndPath(
            "simple_terminals", "blocks/crafting_terminal");
    private static boolean sawExpectedFailure;
    private static boolean blockRegistered;
    private static boolean itemRegistered;
    private static String mode;

    private static class ExpectedConstructionFailure extends RuntimeException {
        ExpectedConstructionFailure() { super(null, null, false, false); }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectMissingId(Runnable construction, String expectedMessage) {
        try {
            construction.run();
        } catch (ExceptionInInitializerError failure) {
            Throwable cause = failure.getCause();
            check(cause instanceof NullPointerException,
                    "Unexpected construction failure: " + cause);
            check(expectedMessage.equals(cause.getMessage()),
                    "Unexpected missing-ID error: " + cause.getMessage());
            sawExpectedFailure = true;
            System.out.println("PASS: missing-ID properties reproduce " + expectedMessage);
            // Failed item construction leaves an incomplete intrusive holder. Stop the
            // expected-failure JVM here instead of attempting vanilla registry freeze.
            throw new ExpectedConstructionFailure();
        }
        throw new AssertionError("Missing ID unexpectedly succeeded: " + expectedMessage);
    }

    private static void registerBlock() {
        if (mode.equals("without-block-id")) {
            expectMissingId(() -> ModBlocks.init(), "Block id not set");
            return;
        }
        Registry.register(BuiltInRegistries.BLOCK, TARGET, ModBlocks.CRAFTING_TERMINAL);
        check(ModBlocks.CRAFTING_TERMINAL.getLootTable().orElseThrow().identifier().equals(LOOT_TARGET),
                "Default loot ID does not point to the terminal loot table");
        check(ModBlocks.CRAFTING_TERMINAL.getDescriptionId().equals(
                "block.simple_terminals.crafting_terminal"), "Incorrect block description ID");
        blockRegistered = true;
        System.out.println("PASS: common ModBlocks construction and terminal default loot ID");
    }

    private static void registerItem() {
        if (mode.equals("without-block-id")) return;
        check(blockRegistered, "Block loader must run before item loader");
        if (mode.equals("without-item-id")) {
            expectMissingId(() -> ModItems.init(), "Item id not set");
            return;
        }
        Registry.register(BuiltInRegistries.ITEM, TARGET, ModItems.CRAFTING_TERMINAL);
        check(ModItems.CRAFTING_TERMINAL.getBlock() == ModBlocks.CRAFTING_TERMINAL,
                "Block item does not reference the terminal block");
        check(BuiltInRegistries.ITEM.getKey(ModItems.CRAFTING_TERMINAL).equals(TARGET),
                "Incorrect terminal item registry ID");
        check(ModItems.CRAFTING_TERMINAL.builtInRegistryHolder().key().identifier().equals(TARGET),
                "Incorrect terminal item holder ID");
        check(ModItems.CRAFTING_TERMINAL.getDescriptionId().equals(
                "block.simple_terminals.crafting_terminal"), "Incorrect terminal item description ID");
        itemRegistered = true;
        System.out.println("PASS: common ModItems construction, terminal item registry ID, and block description key");
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        check(args.length == 1, "Expected one construction mode");
        mode = args[0];
        check(mode.equals("fixed") || mode.equals("without-block-id")
                || mode.equals("without-item-id"), "Unknown mode: " + mode);
        SharedConstants.tryDetectVersion();

        // Installing callbacks requires accessing vanilla registries before bootstrap.
        // Temporarily permit that class initialization, then restore normal bootstrap.
        Field bootstrapped = field(Bootstrap.class, "isBootstrapped");
        bootstrapped.setBoolean(null, true);
        Map<Identifier, Supplier<?>> loaders = (Map<Identifier, Supplier<?>>)
                field(BuiltInRegistries.class, "LOADERS").get(null);
        Identifier blockKey = Identifier.fromNamespaceAndPath("minecraft", "block");
        Identifier itemKey = Identifier.fromNamespaceAndPath("minecraft", "item");
        Supplier<?> originalBlock = loaders.get(blockKey);
        Supplier<?> originalItem = loaders.get(itemKey);
        check(originalBlock != null && originalItem != null, "Vanilla loader callbacks missing");
        loaders.put(blockKey, () -> {
            Object original = originalBlock.get();
            registerBlock();
            return original;
        });
        loaders.put(itemKey, () -> {
            Object original = originalItem.get();
            registerItem();
            return original;
        });
        bootstrapped.setBoolean(null, false);
        try {
            Bootstrap.bootStrap();
        } catch (ExpectedConstructionFailure expected) {
            check(!mode.equals("fixed") && sawExpectedFailure,
                    "Unexpected aborted bootstrap");
        }

        if (mode.equals("fixed")) {
            check(blockRegistered && itemRegistered, "Terminal registration was incomplete");
            check(BuiltInRegistries.BLOCK.getKey(ModBlocks.CRAFTING_TERMINAL).equals(TARGET),
                    "Incorrect terminal block registry ID after bootstrap");
            System.out.println("PASS: fixed common block and item sources complete native bootstrap");
        } else {
            check(sawExpectedFailure, "Expected missing-ID regression was not exercised");
        }
    }
}
