package land.beezz.keys.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import land.beezz.keys.Macros;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player's own commands: a key press in the game sends the command bound to it. */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void beezzkeys$macro(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (Macros.onKey(action, event)) ci.cancel();
    }
}
