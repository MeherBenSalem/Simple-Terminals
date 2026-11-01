package net.fabricmc.fabric.api.gametest.v1;

import net.minecraft.world.level.block.Rotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stand-in for Fabric's annotation so the GameTests shared with Fabric compile on NeoForge, where
 * {@code NeoForgeGameTests} registers the annotated methods instead. Same attributes and defaults as
 * Fabric's. Test code only; never shipped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface GameTest {
    String environment() default "minecraft:default";

    String dimension() default "minecraft:overworld";

    String structure() default "fabric-gametest-api-v1:empty";

    int maxTicks() default 20;

    int setupTicks() default 0;

    boolean required() default true;

    Rotation rotation() default Rotation.NONE;

    boolean manualOnly() default false;

    int maxAttempts() default 1;

    int requiredSuccesses() default 1;

    boolean skyAccess() default false;

    int padding() default 1;
}
