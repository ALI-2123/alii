package dev.blade;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.Random;

/** Wings, blade, aura and trail, drawn with particles around each player who has them. */
public final class Cosmetics {
    public static final String[] AURA = {"none", "flame", "ember", "frost", "void", "blood", "blade"};
    public static final String[] WINGS = {"none", "blade", "crimson", "shadow"};
    public static final String[] BLADE = {"none", "blade", "crimson", "shadow"};
    public static final String[] TRAIL = {"none", "spark", "smoke", "slash"};

    /** One player's choices. */
    public static final class Set {
        public String aura = "none";
        public String wings = "none";
        public String blade = "none";
        public String trail = "none";
    }

    private static final Random RNG = new Random();
    private static int tick;

    private Cosmetics() {}

    private static int[] palette(String name) {
        return switch (name) {
            case "crimson" -> new int[]{0x5A0000, 0xFF6A5A};
            case "shadow" -> new int[]{0x050000, 0x7A0000};
            default -> new int[]{0x0A0000, 0xFF2A1A}; // blade: black fading to red
        };
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return (r << 16) | (g << 8) | bl;
    }

    private static ParticleEffect dust(int rgb, float scale) {
        return Dust.of(rgb, scale);
    }

    public static String next(String[] options, String current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) return options[(i + 1) % options.length];
        }
        return options[0];
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        tick++;
        if (tick % 4 != 0) return;
        String me = mc.player.getName().getString();
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p.isInvisible() || p.squaredDistanceTo(mc.player) > 48 * 48) continue;
            boolean own = p == mc.player;
            if (own && firstPerson) continue;
            String name = p.getName().getString();
            Set set = name.equalsIgnoreCase(me) ? Profile.cosmetics() : Social.get().cosmeticsOf(name);
            if (set != null) draw(mc.world, p, set);
        }
    }

    private static void at(ClientWorld w, PlayerEntity p, double sin, double cos, double lx, double ly, double lz, ParticleEffect fx) {
        // local x is the player's right, local z is behind the player
        double x = p.getX() - cos * lx + sin * lz;
        double z = p.getZ() - sin * lx - cos * lz;
        double y = p.getY() + ly - (p.isSneaking() ? 0.2 : 0);
        Compat.particle(w, fx, x, y, z, 0, 0, 0);
    }

    private static void draw(ClientWorld w, AbstractClientPlayerEntity p, Set s) {
        double yaw = Math.toRadians(p.bodyYaw);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        if (!"none".equals(s.wings)) wings(w, p, sin, cos, palette(s.wings));
        if (!"none".equals(s.blade)) blade(w, p, sin, cos, palette(s.blade));
        if (!"none".equals(s.aura)) aura(w, p, s.aura);
        if (!"none".equals(s.trail)) trail(w, p, s.trail);
    }

    private static void wings(ClientWorld w, PlayerEntity p, double sin, double cos, int[] pal) {
        double flap = 0.92 + 0.08 * Math.sin(tick * 0.2);
        int feathers = 5;
        double[] tipX = new double[feathers];
        double[] tipY = new double[feathers];
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < feathers; i++) {
                double angle = Math.toRadians(75 - i * 24); // from nearly up to out and down
                double length = 0.8 + 0.7 * Math.sin(Math.PI * (i + 0.5) / feathers);
                for (double r = 0.15; r <= length; r += 0.2) {
                    float t = (float) (r / length);
                    double lx = side * (0.2 + Math.cos(angle) * r * flap);
                    double ly = 1.45 + Math.sin(angle) * r;
                    double lz = 0.2 + r * 0.25;
                    at(w, p, sin, cos, lx, ly, lz, dust(mix(pal[0], pal[1], t), 0.8f));
                }
                tipX[i] = 0.2 + Math.cos(angle) * length * flap;
                tipY[i] = 1.45 + Math.sin(angle) * length;
            }
            // the edge of the wing between feather tips
            for (int i = 0; i < feathers - 1; i++) {
                double mx = (tipX[i] + tipX[i + 1]) / 2;
                double my = (tipY[i] + tipY[i + 1]) / 2;
                at(w, p, sin, cos, side * mx, my, 0.4, dust(pal[1], 0.7f));
            }
        }
    }

    private static void blade(ClientWorld w, PlayerEntity p, double sin, double cos, int[] pal) {
        double sx = -0.3;
        double sy = 0.5;
        double dx = 0.7;
        double dy = 1.5;
        double len = Math.sqrt(dx * dx + dy * dy);
        double px = -dy / len;
        double py = dx / len;
        int n = 16;
        for (int k = 0; k <= n; k++) {
            float t = k / (float) n;
            double lx = sx + dx * t;
            double ly = sy + dy * t;
            int color = t < 0.2f ? 0x140000 : mix(pal[0], pal[1], (t - 0.2f) / 0.8f);
            at(w, p, sin, cos, lx, ly, 0.3, dust(color, 0.9f));
            if (k == 3) { // crossguard
                at(w, p, sin, cos, lx + px * 0.13, ly + py * 0.13, 0.3, dust(pal[1], 0.8f));
                at(w, p, sin, cos, lx - px * 0.13, ly - py * 0.13, 0.3, dust(pal[1], 0.8f));
            }
        }
    }

    private static void aura(ClientWorld w, PlayerEntity p, String kind) {
        for (int i = 0; i < 3; i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double x = p.getX() + Math.cos(a) * 0.6;
            double z = p.getZ() + Math.sin(a) * 0.6;
            double y = p.getY() + RNG.nextDouble() * 1.9;
            ParticleEffect fx = switch (kind) {
                case "flame" -> ParticleTypes.FLAME;
                case "ember" -> ParticleTypes.SMALL_FLAME;
                case "frost" -> ParticleTypes.SNOWFLAKE;
                case "void" -> ParticleTypes.PORTAL;
                case "blood" -> dust(0x7A0000, 1.0f);
                default -> dust(RNG.nextBoolean() ? 0x0A0000 : 0xFF2A1A, 1.0f);
            };
            Compat.particle(w, fx, x, y, z, 0, 0.01, 0);
        }
    }

    private static final java.util.Map<java.util.UUID, double[]> LAST = new java.util.HashMap<>();

    private static void trail(ClientWorld w, PlayerEntity p, String kind) {
        // Remember where this player was last time, instead of reading a field that changed names between versions.
        double[] last = LAST.put(p.getUuid(), new double[] {p.getX(), p.getZ()});
        if (last == null) return;
        double dx = p.getX() - last[0];
        double dz = p.getZ() - last[1];
        if (dx * dx + dz * dz < 0.002) return;
        ParticleEffect fx = switch (kind) {
            case "spark" -> ParticleTypes.ELECTRIC_SPARK;
            case "smoke" -> ParticleTypes.SMOKE;
            default -> dust(0xFF2A1A, 1.2f);
        };
        Compat.particle(w, fx, p.getX(), p.getY() + 0.1, p.getZ(), 0, 0, 0);
    }
}
