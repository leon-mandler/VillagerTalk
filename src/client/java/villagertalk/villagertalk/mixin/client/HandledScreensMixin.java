package villagertalk.villagertalk.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import villagertalk.villagertalk.VillagerChatUI.ExtendedMerchantScreen;

/**
 * Mixin that redirects the registration of the MerchantScreen to use ExtendedMerchantScreen instead.
 * Prevents MerchantScreenHandler from using MerchantScreen instead.
 */

@Mixin(HandledScreens.class)
public class HandledScreensMixin{

    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreens;register(Lnet/minecraft/screen/ScreenHandlerType;Lnet/minecraft/client/gui/screen/ingame/HandledScreens$Provider;)V"))
    private static <M extends ScreenHandler, U extends Screen & ScreenHandlerProvider<M>> void redirectMerchantRegister(ScreenHandlerType<? extends M> type, HandledScreens.Provider<M, U> provider){
        if(type == ScreenHandlerType.MERCHANT){
            HandledScreens.register(ScreenHandlerType.MERCHANT, ExtendedMerchantScreen::new);
        } else{
            HandledScreens.register(type, provider);
        }
    }
}
