package villagertalk.villagertalk.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.MerchantScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import villagertalk.villagertalk.VillagerTalk;

@Mixin(MerchantScreenHandler.class)
public abstract class MerchantScreenHandlerMixin {
    @Inject(at = @At("HEAD"), method = "onClosed(Lnet/minecraft/entity/player/PlayerEntity;)V")
    private void closeHandledScreen(PlayerEntity player, CallbackInfo info) {
        if(!player.getWorld().isClient()){
            VillagerTalk.onVillagerTradeClose(player);
        }
    }
}
