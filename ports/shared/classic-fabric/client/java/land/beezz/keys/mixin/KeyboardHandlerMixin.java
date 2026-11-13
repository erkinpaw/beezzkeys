package land.beezz.keys.mixin;

import land.beezz.keys.Macros;
import land.beezz.keys.input.KeyEvent;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player's own commands: a key press in the game sends the command bound to it (game versions before 1.21.9). */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void beezzkeys$macro(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (Macros.onKey(action, new KeyEvent(key, scancode, modifiers))) ci.cancel();
    }
}
