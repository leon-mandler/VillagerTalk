package villagertalk.villagertalk.mixin.client;

import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Deprecated(forRemoval = true)
@Mixin(ScreenHandlerType.class)
public class ScreenHandlerTypeMixin{

    @Accessor("MERCHANT")
    public static void setScreenHandlerTypeMerchant(ScreenHandlerType<MerchantScreenHandler> merchant){
        throw new AssertionError();
    }

    @Invoker("register")
    private static <T extends ScreenHandler> ScreenHandlerType<T> screenHandlerTypeRegister(String id, ScreenHandlerType.Factory<T> factory) {
        throw new AssertionError();
    }
}
