package villagertalk.villagertalk.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import villagertalk.villagertalk.VillagerTalk;

/**
 * VillagerEntityMixin
 * Mixin to call the onVillagerTradeOpen method when the VillagerEntity begins a trade
 */

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin{
    @Inject(at = @At("HEAD"), method = "beginTradeWith(Lnet/minecraft/entity/player/PlayerEntity;)V")
    private void beginTradeWith(PlayerEntity customer, CallbackInfo info) {
        VillagerTalk.onVillagerTradeOpen(customer,  (VillagerEntity)(Object)this);
    }
}
