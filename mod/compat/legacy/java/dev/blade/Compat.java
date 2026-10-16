package dev.blade;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.text.Text;

/** Code that differs between Minecraft versions (1.21.1 and 1.21.4). */
final class Compat {
    private Compat() {}

    static KeyBinding registerKey(String translationKey, int glfwKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, "category.blade"));
    }

    static void drawScaledCentered(DrawContext ctx, TextRenderer font, String text, int cx, int y, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawCenteredTextWithShadow(font, Text.literal(text), Math.round(cx / scale), Math.round(y / scale), color);
        ctx.getMatrices().pop();
    }

    static String effectName(StatusEffectInstance e) {
        return Text.translatable(e.getTranslationKey()).getString();
    }

    static void particle(ClientWorld w, ParticleEffect fx, double x, double y, double z, double vx, double vy, double vz) {
        w.addParticle(fx, x, y, z, vx, vy, vz);
    }
}
