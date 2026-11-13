package land.beezz.keys;

import land.beezz.keys.mixin.OptionsSubScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.lang.ref.WeakReference;

/**
 * BeezzKeys on Forge: "Key Binds" in the controls opens a drawn keyboard instead of the plain list. The same as the
 * Fabric entry point, with Forge's events. Client only: on a server it does nothing.
 */
@Mod("beezzkeys")
public final class BeezzKeys {
    /** The vanilla screen the player asked for with the "List" button; it is left alone. */
    private static WeakReference<Screen> vanillaWanted = new WeakReference<>(null);

    public BeezzKeys() {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) Macros.tick(Minecraft.getInstance());
        });
        MinecraftForge.EVENT_BUS.addListener(BeezzKeys::afterInit);
    }

    private static void afterInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof KeyBindsScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        Screen parent = ((OptionsSubScreenAccessor) screen).beezzkeys$lastScreen();
        if (vanillaWanted.get() == screen) {
            event.addListener(Button.builder(Component.translatable("beezzkeys.button.keyboard"),
                    b -> Compat.setScreen(mc, new KeyboardScreen(parent))).bounds(6, 6, 90, 20).build());
        } else {
            mc.execute(() -> {
                if (Compat.screen(mc) == screen) Compat.setScreen(mc, new KeyboardScreen(parent));
            });
        }
    }

    static void openVanilla(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        KeyBindsScreen screen = new KeyBindsScreen(parent, mc.options);
        vanillaWanted = new WeakReference<>(screen);
        Compat.setScreen(mc, screen);
    }
}
