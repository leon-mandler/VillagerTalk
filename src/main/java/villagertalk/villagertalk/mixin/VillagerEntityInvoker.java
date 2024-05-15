package villagertalk.villagertalk.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VillagerEntity.class)
public interface VillagerEntityInvoker{

    @Invoker("clearSpecialPrices")
    public void invokeClearSpecialPrices();

    @Invoker("sendOffersToCustomer")
    public void sendOffersToCustomer();
}
