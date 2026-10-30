package com.nightbeam.omnivault.autocraft.block;

import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.network.block.DeviceBlock;
import com.nightbeam.omnivault.network.block.MenuDevice;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A full-block network device that connects on every side (assembler, crafting processor). Besides
 * {@code active} (on a running network) it has {@code working}: crafting right now, or holding a job.
 */
public class AutocraftDeviceBlock extends DeviceBlock {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    private final IPlatformHelper.BlockEntityFactory<?> factory;

    public AutocraftDeviceBlock(IPlatformHelper.BlockEntityFactory<?> factory, Properties properties) {
        super(properties);
        this.factory = factory;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(WORKING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new AutocraftDeviceBlock(factory, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, WORKING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.create(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MenuDevice device) {
            device.openMenu(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MenuDevice device) {
            device.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
