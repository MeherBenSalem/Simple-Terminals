package net.neoforged.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stand-in for NeoForge's annotation so the GameTests shared with NeoForge compile on Fabric, where
 * the {@code fabric-gametest} entrypoint lists the test classes instead. Test code only; never shipped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GameTestHolder {
    String value();
}
