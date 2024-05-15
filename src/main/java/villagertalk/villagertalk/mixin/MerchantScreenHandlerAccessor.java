package villagertalk.villagertalk.mixin;

import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.village.MerchantInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * MerchantScreenHandlerMixin
 * Mixin to call the onVillagerTradeClose method when the MerchantScreen is closed
 */
@Mixin(MerchantScreenHandler.class)
public interface MerchantScreenHandlerAccessor{

    @Accessor("merchantInventory")
    MerchantInventory getMerchantInventory();
}
