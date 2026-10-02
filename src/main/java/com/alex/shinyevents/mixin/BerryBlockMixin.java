package com.alex.shinyevents.mixin;

import com.alex.shinyevents.berry.BerryGrowthTicker;
import com.cobblemon.mod.common.block.BerryBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BerryBlock.class)
public abstract class BerryBlockMixin {
    // getTicker overrides a Minecraft method, so this selector must be remapped.
    @Inject(method = "getTicker", at = @At("RETURN"), cancellable = true)
    private <T extends BlockEntity> void shinyEvents$wrapBerryTicker(
            Level level, BlockState state, BlockEntityType<T> type,
            CallbackInfoReturnable<BlockEntityTicker<T>> callback) {
        BlockEntityTicker<T> ticker = callback.getReturnValue();
        if (!level.isClientSide && ticker != null && !(ticker instanceof BerryGrowthTicker<?>)) {
            callback.setReturnValue(new BerryGrowthTicker<>(ticker));
        }
    }
}
