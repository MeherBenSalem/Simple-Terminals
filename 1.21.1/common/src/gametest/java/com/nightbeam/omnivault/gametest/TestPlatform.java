package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ServiceLoader;

/**
 * What the shared GameTests need from the loader they run on: block inventories the way other mods
 * see them (NeoForge capabilities, Fabric storages) and the loader's left-click hook.
 */
public interface TestPlatform {
    TestPlatform INSTANCE = ServiceLoader.load(TestPlatform.class, TestPlatform.class.getClassLoader()).findFirst()
            .orElseThrow(() -> new IllegalStateException("No TestPlatform for this loader"));

    /**
     * The block's inventory through the loader's own item lookup, wrapped back into a slot view but
     * never unwrapped to our inventory, so the loader adapter is what gets tested.
     */
    @Nullable
    SlotAccess items(ServerLevel level, BlockPos pos, @Nullable Direction side);

    /** Fires the loader's left-click-block hook as a click starts; true when the click was cancelled. */
    boolean leftClickBlock(Player player, BlockPos pos, Direction face);

    /** NeoForge item handlers give out at most one stack per extraction; Fabric storages don't. */
    boolean extractionCappedAtAStack();
}
