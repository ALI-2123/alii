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
import net.minecraft.util.Identifier;

/** Code that differs between Minecraft versions (1.21.11). */
final class Compat {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("blade", "main"));

    private Compat() {}

    static KeyBinding registerKey(String translationKey, int glfwKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, CATEGORY));
    }

    static void drawScaledCentered(DrawContext ctx, TextRenderer font, String text, int cx, int y, float scale, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        ctx.drawCenteredTextWithShadow(font, Text.literal(text), Math.round(cx / scale), Math.round(y / scale), color);
        ctx.getMatrices().popMatrix();
    }

    static String effectName(StatusEffectInstance e) {
        return e.getEffectType().value().getName().getString();
    }

    static void particle(ClientWorld w, ParticleEffect fx, double x, double y, double z, double vx, double vy, double vz) {
        w.addParticleClient(fx, x, y, z, vx, vy, vz);
    }
}
