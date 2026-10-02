package com.alex.shinyevents.berry;

import com.alex.shinyevents.ShinyEventsMod;
import com.alex.shinyevents.core.EventType;
import com.cobblemon.mod.common.block.BerryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

/** Reuses Cobblemon's growth logic, including its mulch, mutation and yield handling. */
public final class BerryGrowthTicker<T extends BlockEntity> implements BlockEntityTicker<T> {
    private final BlockEntityTicker<T> original;

    public BerryGrowthTicker(BlockEntityTicker<T> original) {
        this.original = original;
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        original.tick(level, pos, state, blockEntity);
        double multiplier = ShinyEventsMod.multiplier(EventType.BERRIES);
        if (multiplier <= 1.0 || !Double.isFinite(multiplier)) return;

        // The ordinary tick can change age, mulch or even replace/remove the block.
        // Always use the latest state before asking Cobblemon to advance it again.
        BlockState currentState = growingState(level, pos, state, blockEntity);
        if (currentState == null) return;
        int extraTicks = BerryTickBudget.extraTicks(multiplier, level.random::nextDouble);
        for (int i = 0; i < extraTicks; i++) {
            original.tick(level, pos, currentState, blockEntity);
            if (i + 1 < extraTicks) {
                currentState = growingState(level, pos, state, blockEntity);
                if (currentState == null) return;
            }
        }
    }

    private static BlockState growingState(
            Level level, BlockPos pos, BlockState originalState, BlockEntity blockEntity) {
        if (level.isClientSide || blockEntity.isRemoved()) return null;
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof BerryBlock)
                || current.getBlock() != originalState.getBlock()
                || current.getValue(BerryBlock.Companion.getIS_ROOTED())
                || current.getValue(BerryBlock.Companion.getAGE()) >= BerryBlock.FRUIT_AGE
                || level.getBlockEntity(pos) != blockEntity) return null;
        return current;
    }
}
