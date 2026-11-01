package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registers the shared GameTests on NeoForge: each {@code @GameTest} method of the classes below becomes
 * a test function and a test instance, named as Fabric names them. Run with ./gradlew :neoforge:runGameTestServer.
 */
@Mod(OmniVault.MOD_ID)
public class NeoForgeGameTests {
    /** The same classes the Fabric {@code fabric-gametest} entrypoint lists, without the Fabric-only ones. */
    private static final List<Class<?>> TESTS = List.of(AutocraftGameTests.class, BackpackGameTests.class, CoreGameTests.class,
            DrawerGameTests.class, MigrationGameTests.class, NetworkGameTests.class, PhysicalGameTests.class, ProgressionGameTests.class,
            ResourceGameTests.class);
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, OmniVault.MOD_ID);
    private static final Map<Class<?>, Object> INSTANCES = new HashMap<>();

    public NeoForgeGameTests(IEventBus modBus) {
        for (Method method : methods()) {
            FUNCTIONS.register(name(method), () -> helper -> invoke(method, helper));
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(NeoForgeGameTests::registerTests);
    }

    private static List<Method> methods() {
        List<Method> methods = new ArrayList<>();
        for (Class<?> type : TESTS) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(GameTest.class)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private static String name(Method method) {
        return (method.getDeclaringClass().getSimpleName() + "_" + method.getName()).replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(OmniVault.id("default"));
        for (Method method : methods()) {
            GameTest test = method.getAnnotation(GameTest.class);
            Identifier id = OmniVault.id(name(method));
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Identifier.parse(test.structure()), test.maxTicks(),
                    test.setupTicks(), test.required(), test.rotation(), test.manualOnly(), test.maxAttempts(), test.requiredSuccesses(),
                    test.skyAccess(), test.padding());
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            Object instance = INSTANCES.computeIfAbsent(method.getDeclaringClass(), NeoForgeGameTests::create);
            method.invoke(instance, helper);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException("Test method " + method.getName() + " failed", e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not run test method " + method.getName(), e);
        }
    }

    private static Object create(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create " + type.getName(), e);
        }
    }
}
