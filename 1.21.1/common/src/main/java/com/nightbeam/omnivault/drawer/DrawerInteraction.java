package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** What players do to drawers with their hands. Loader event hooks call into here too. */
public final class DrawerInteraction {
    private DrawerInteraction() {
    }

    /**
     * Face pixels (u from the left, v from the top, 0..16, as the player sees the front) of a point on
     * a block whose front faces {@code facing}.
     */
    public static double[] faceUv(Direction facing, BlockPos pos, Vec3 hit) {
        double x = hit.x - pos.getX();
        double y = hit.y - pos.getY();
        double z = hit.z - pos.getZ();
        double u = switch (facing) {
            case SOUTH -> x;
            case WEST -> z;
            case EAST -> 1 - z;
            default -> 1 - x; // NORTH
        };
        return new double[]{clamp(u) * 16, clamp(1 - y) * 16};
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    /** The compartment under a hit on the front face, or -1 if the hit is on another face. */
    public static int compartmentAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (!(state.getBlock() instanceof DrawerBlock drawer) || hit.getDirection() != state.getValue(DrawerBlock.FACING)) {
            return -1;
        }
        double[] uv = faceUv(hit.getDirection(), pos, hit.getLocation());
        return drawer.layout().compartmentAt(uv[0], uv[1]);
    }

    /** Ray-traces the player's view onto the block, for clicks that carry no hit position (left clicks). */
    @Nullable
    public static BlockHitResult pick(Player player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.blockInteractionRange() + 1.0));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
    }

    /**
     * A right-click on a compartment: inserts the held stack, or on a quick second click every item
     * from the inventory the drawer already holds.
     */
    public static void rightClick(Level level, AbstractDrawerBlockEntity drawer, Player player, InteractionHand hand, int slot) {
        if (slot < 0 || slot >= drawer.compartments()) {
            return;
        }
        boolean second = drawer.registerClick(player, level.getGameTime());
        long inserted;
        if (second) {
            inserted = insertInventory(drawer, player);
        } else {
            inserted = insertHeld(drawer, player, hand, slot);
        }
        if (inserted > 0) {
            level.playSound(null, drawer.getBlockPos(), SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    /** Inserts the held stack into one compartment. @return items taken. */
    public static long insertHeld(AbstractDrawerBlockEntity drawer, Player player, InteractionHand hand, int slot) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return 0;
        }
        ItemKey key = ItemKey.of(held);
        long accepted = drawer.insert(slot, key, held.getCount(), false);
        if (accepted <= 0 && drawer.keyAt(slot) != null && !key.equals(drawer.keyAt(slot))) {
            // Clicked the wrong compartment: fall back to the one that holds this item, if any.
            accepted = drawer.holds(key) ? drawer.insertAny(key, held.getCount(), false) : 0;
        }
        if (accepted > 0) {
            held.shrink((int) Math.min(accepted, held.getCount()));
        }
        return accepted;
    }

    /** Moves every stack from the player's inventory that the drawer holds into it. @return items taken. */
    public static long insertInventory(AbstractDrawerBlockEntity drawer, Player player) {
        Inventory inventory = player.getInventory();
        long total = 0;
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.of(stack);
            if (!drawer.holds(key)) {
                continue;
            }
            long accepted = drawer.insertAny(key, stack.getCount(), false);
            if (accepted > 0) {
                stack.shrink((int) Math.min(accepted, stack.getCount()));
                total += accepted;
            }
        }
        if (total > 0) {
            inventory.setChanged();
        }
        return total;
    }

    /** Takes a stack (or one item) from a compartment and hands it to the player. */
    public static boolean extractToPlayer(AbstractDrawerBlockEntity drawer, Player player, int slot, boolean single) {
        if (slot < 0 || slot >= drawer.compartments()) {
            return false;
        }
        ItemKey key = drawer.keyAt(slot);
        if (key == null) {
            return false;
        }
        long taken = drawer.extract(slot, single ? 1 : key.maxStackSize(), false);
        if (taken <= 0) {
            return false;
        }
        ItemStack stack = key.toStack((int) taken);
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
        Level level = player.level();
        level.playSound(null, drawer.getBlockPos(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
        return true;
    }

    /** A left-click (attack) on a drawer: extracts from the compartment the player looks at. Server side. */
    public static boolean leftClick(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof AbstractDrawerBlockEntity drawer)) {
            return false;
        }
        BlockHitResult hit = pick(player, pos);
        if (hit == null) {
            return false;
        }
        int slot = compartmentAt(state, pos, hit);
        return slot >= 0 && extractToPlayer(drawer, player, slot, player.isShiftKeyDown());
    }

    /**
     * Loader hook for the start of a left click on a block. Creative players would break the drawer
     * instantly, so a click on a drawer's front is cancelled and treated as an extraction instead.
     *
     * @return true if the loader must cancel the click (on both sides).
     */
    public static boolean onLeftClickBlock(Player player, Level level, BlockPos pos, @Nullable Direction face) {
        if (!player.isCreative()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DrawerBlock) || face != state.getValue(DrawerBlock.FACING)) {
            return false;
        }
        if (!level.isClientSide) {
            leftClick(level, pos, state, player);
        }
        return true;
    }

    /** Toggles the lock and tells the player. */
    public static void toggleLock(AbstractDrawerBlockEntity drawer, Player player) {
        boolean locked = !drawer.isLocked();
        drawer.setLocked(locked);
        player.displayClientMessage(Component.translatable(locked ? "message.omnivault.drawer.locked" : "message.omnivault.drawer.unlocked"), true);
        player.level().playSound(null, drawer.getBlockPos(), locked ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 0.5f, 1.4f);
    }
}
