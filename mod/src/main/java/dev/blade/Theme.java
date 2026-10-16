package dev.blade;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Blade look: see-through red and black glass, a glowing black-to-red blade in the middle,
 * a slow pulse and a shine that runs up the blade. Uses only fill and fillGradient so it works on every version.
 */
public final class Theme {
    public static final int TEXT = 0xFFFFFFFF;
    public static final int DIM = 0xFFC9B7B4;
    public static final int HOT = 0xFFFF4438;

    private Theme() {}

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 255;
            int cb = (b >>> shift) & 255;
            out |= ((int) (ca + (cb - ca) * t) & 255) << shift;
        }
        return out;
    }

    public static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** 0..1 slow breathing value. */
    public static float pulse() {
        return 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 650.0);
    }

    /** In a world the screen is see-through so you can still see the game. On the title screen it is solid. */
    public static void background(DrawContext ctx, int w, int h) {
        boolean inWorld = MinecraftClient.getInstance().world != null;
        if (inWorld) {
            ctx.fillGradient(0, 0, w, h, 0x66C00000, 0x99000000);
        } else {
            ctx.fillGradient(0, 0, w, h, 0xFFB00000, 0xFF000000);
        }
        // breathing glow at the top and a dark floor at the bottom
        ctx.fillGradient(0, 0, w, h / 4, argb(30 + (int) (40 * pulse()), 0xFF2A1A), 0x00FF2A1A);
        ctx.fillGradient(0, h - h / 5, w, h, 0x00000000, inWorld ? 0x66000000 : 0xAA000000);
        // thin glowing edges
        ctx.fillGradient(0, 0, 2, h, 0x00FF2A1A, 0x66FF2A1A);
        ctx.fillGradient(w - 2, 0, w, h, 0x00FF2A1A, 0x66FF2A1A);
    }

    /** A glowing blade pointing up, fading from black to red along its length, with a shine that travels up it. */
    public static void blade(DrawContext ctx, int cx, int top, int height, int half) {
        float p = pulse();
        // soft glow around the whole blade
        for (int i = 4; i >= 1; i--) {
            int spread = half + i * 5;
            ctx.fillGradient(cx - spread, top, cx + spread, top + height, 0x00FF2A1A, argb(8 + (int) (10 * p), 0xFF2A1A));
        }
        for (int y = 0; y < height; y += 2) {
            float t = y / (float) height;
            int w;
            float tone;
            if (t < 0.12f) {
                w = Math.max(1, Math.round(half * (t / 0.12f)));
                tone = t / 0.7f;
            } else if (t < 0.70f) {
                w = half;
                tone = t / 0.7f;
            } else if (t < 0.745f) {
                w = Math.round(half * 3.2f);
                tone = 1f;
            } else if (t < 0.95f) {
                w = Math.max(2, Math.round(half * 0.36f));
                tone = 0.3f;
            } else {
                w = Math.round(half * 0.7f);
                tone = 0.8f;
            }
            int left = lerp(0xD0000000, 0xE08A0000, tone);
            int right = lerp(0xD03A0000, 0xF0FF4438, tone);
            ctx.fill(cx - w, top + y, cx, top + y + 2, left);
            ctx.fill(cx, top + y, cx + w, top + y + 2, right);
            if (t >= 0.12f && t < 0.70f) ctx.fill(cx, top + y, cx + 1, top + y + 2, 0xFF000000);
        }
        // shine running up the blade
        int bladeLen = Math.round(height * 0.70f);
        int shine = bladeLen - (int) ((System.currentTimeMillis() / 9) % (bladeLen + 120));
        if (shine > 0 && shine < bladeLen) {
            ctx.fillGradient(cx - half, top + shine, cx + half, top + shine + 14, 0x00FFFFFF, 0x55FFFFFF);
            ctx.fillGradient(cx - half, top + shine + 14, cx + half, top + shine + 28, 0x55FFFFFF, 0x00FFFFFF);
        }
    }

    /** See-through dark glass panel with a glowing red top bar. */
    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0x78000000);
        ctx.fillGradient(x, y, x + w, y + h, 0x30FF2A1A, 0x00000000);
        ctx.fillGradient(x, y, x + w, y + 3, 0xFFFF4438, 0xFFB00000);
        int edge = argb(110 + (int) (80 * pulse()), 0xFF2A1A);
        ctx.fill(x, y + h - 1, x + w, y + h, edge);
        ctx.fillGradient(x, y, x + 1, y + h, edge, 0x22FF2A1A);
        ctx.fillGradient(x + w - 1, y, x + w, y + h, edge, 0x22FF2A1A);
    }

    /** Background for HUD modules. */
    public static void hudBox(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fillGradient(x, y, x + w, y + h, 0x90450000, 0x90000000);
        ctx.fillGradient(x, y, x + 2, y + h, HOT, 0xFF8A0000);
    }

    /** Light red and black tint drawn over vanilla screens such as the pause menu. */
    public static void overlay(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, 0x40C00000, 0x60000000);
        ctx.fillGradient(0, 0, w, h / 5, argb(25 + (int) (30 * pulse()), 0xFF2A1A), 0x00FF2A1A);
        ctx.fillGradient(w / 2 - 1, 0, w / 2 + 1, h, 0x00FF2A1A, 0x44FF2A1A);
    }

    /** One pixel outline (DrawContext.drawBorder is not in every Minecraft version). */
    public static void border(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    // ---------- title screen: black sword with a red glow, like the launcher home ----------

    private static int hash(int i, int k) {
        int x = i * 374761393 + k * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        return (x ^ (x >>> 16)) & 0x7FFFFFFF;
    }

    /** Black to deep red, with a red glow in the bottom right corner and floating embers. */
    public static void titleBackground(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, 0xFF000000, 0xFF2A0000);
        int from = (int) (w * 0.35f);
        for (int x = from; x < w; x += 6) {
            float t = (x - from) / (float) (w - from);
            ctx.fillGradient(x, (int) (h * 0.4f), x + 6, h, 0x00B00000, argb((int) (150 * t * t), 0xB00000));
        }
        ctx.fillGradient(0, 0, w, 2, 0x00FF2A1A, 0x00FF2A1A);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 46; i++) {
            int x = hash(i, 1) % Math.max(1, w);
            int speed = 4 + hash(i, 3) % 11;
            int y = h - (int) (((now * speed / 1000L) + hash(i, 2)) % Math.max(1, h));
            int size = (i % 5 == 0) ? 2 : 1;
            int a = 50 + (int) (90 * (0.5 + 0.5 * Math.sin(now / 500.0 + i)));
            ctx.fill(x, y, x + size, y + size, argb(a, 0xFF3A2A));
        }
    }

    // sword shape in units from the tip (568 units tall)
    private static float swordHalfWidth(float y) {
        if (y < 0 || y > 568) return 0;
        if (y < 60) return 20f * (y / 60f);
        if (y < 385) return 20f;
        if (y < 435) {
            float t = (y - 385) / 50f;
            return t < 0.3f ? 83f * (0.7f + t) : 83f * (1f - (t - 0.3f) / 0.7f * 0.82f);
        }
        if (y < 525) return 8f;
        float t = (y - 525) / 43f;
        return 16f * (1f - Math.abs(2f * t - 1f));
    }

    private static void silhouette(DrawContext ctx, int cx, int top, float u, int grow, int color) {
        int total = Math.round(568 * u);
        for (int y = -grow; y < total + grow; y += 2) {
            float hw = swordHalfWidth(Math.max(0, Math.min(568, y / u)));
            if (hw <= 0 && (y < 0 || y > total)) hw = 1;
            int half = Math.round(hw * u) + grow;
            if (half <= 0) continue;
            ctx.fill(cx - half, top + y, cx + half, top + y + 2, color);
        }
    }

    /** A black sword (blade, crossguard, wrapped grip, diamond pommel) with a pulsing red glow. */
    public static void sword(DrawContext ctx, int cx, int top, int height) {
        float u = height / 568f;
        float p = pulse();
        for (int i = 7; i >= 1; i--) {
            int grow = Math.max(2, Math.round(i * 3.2f * Math.max(1f, u * 1.4f)));
            silhouette(ctx, cx, top, u, grow, argb(10 + (int) (6 * p) + (8 - i) * 2, 0xFF2A1A));
        }
        silhouette(ctx, cx, top, u, 0, 0xFF060000);
        // faint centre line down the blade and a dark red edge
        ctx.fill(cx, top + Math.round(70 * u), cx + 1, top + Math.round(380 * u), 0x55FF2A1A);
        int bladeHalf = Math.round(20 * u);
        ctx.fill(cx + bladeHalf - 1, top + Math.round(62 * u), cx + bladeHalf, top + Math.round(384 * u), 0x88660000);
        // red wraps on the grip
        int gripHalf = Math.max(2, Math.round(8 * u));
        for (int k = 0; k < 5; k++) {
            int y0 = top + Math.round((445 + k * 18) * u);
            int step = Math.max(2, gripHalf / 2);
            for (int b = 0; b < 4; b++) {
                int x = cx - gripHalf + b * step;
                ctx.fill(x, y0 + (3 - b) * Math.max(1, Math.round(2 * u)), x + step, y0 + (4 - b) * Math.max(1, Math.round(2 * u)), 0xFFC00000);
            }
        }
        // shine across the blade
        int bladeLen = Math.round(325 * u);
        int shine = bladeLen - (int) ((System.currentTimeMillis() / 8) % (bladeLen + 160));
        if (shine > 0 && shine < bladeLen) {
            int sy = top + Math.round(60 * u) + shine;
            ctx.fillGradient(cx - bladeHalf, sy, cx + bladeHalf, sy + 12, 0x00FF4438, 0x66FF4438);
            ctx.fillGradient(cx - bladeHalf, sy + 12, cx + bladeHalf, sy + 24, 0x66FF4438, 0x00FF4438);
        }
    }
}
