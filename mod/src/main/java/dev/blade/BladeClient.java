package dev.blade;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BladeClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("blade");

    private static KeyBinding menuKey;
    private static KeyBinding chatKey;

    @Override
    public void onInitializeClient() {
        Profile.load();
        HudModules.load();
        PvpMods.init();
        Social.get().start();

        menuKey = Compat.registerKey("key.blade.menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        chatKey = Compat.registerKey("key.blade.chat", GLFW.GLFW_KEY_Y);
        PvpMods.zoomKey = Compat.registerKey("key.blade.zoom", GLFW.GLFW_KEY_C);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PvpMods.tick(client);
            while (menuKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) client.setScreen(new BladeMenuScreen(BladeMenuScreen.Tab.HUD));
            }
            while (chatKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) client.setScreen(new BladeMenuScreen(BladeMenuScreen.Tab.CHAT));
            }
            // Swap the vanilla title screen for the Blade one.
            if (client.currentScreen != null && client.currentScreen.getClass() == TitleScreen.class) {
                client.setScreen(new BladeTitleScreen());
            }
            Cosmetics.tick(client);
        });

        // Tint the pause menu red and black.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof GameMenuScreen) {
                ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> Theme.overlay(ctx, s.width, s.height));
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> HudModules.renderAll(ctx));
    }
}
