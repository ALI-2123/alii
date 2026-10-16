package dev.blade;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/** The PvP HUD modules. Open the Blade menu (Right Shift) to toggle them and drag them around. */
public final class HudModules {
    public abstract static class Module {
        public final String id;
        public final String label;
        public boolean enabled = true;
        public int x;
        public int y;

        protected Module(String id, String label, int x, int y) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
        }

        public abstract int width(MinecraftClient mc);

        public abstract int height(MinecraftClient mc);

        public abstract void render(DrawContext ctx, MinecraftClient mc);
    }

    /** One line of text in a themed box. */
    private static final class Line extends Module {
        private final Function<MinecraftClient, String> text;

        Line(String id, String label, int x, int y, Function<MinecraftClient, String> text) {
            super(id, label, x, y);
            this.text = text;
        }

        @Override
        public int width(MinecraftClient mc) {
            return mc.textRenderer.getWidth(text.apply(mc)) + 12;
        }

        @Override
        public int height(MinecraftClient mc) {
            return 14;
        }

        @Override
        public void render(DrawContext ctx, MinecraftClient mc) {
            String value = text.apply(mc);
            Theme.hudBox(ctx, x, y, width(mc), 14);
            ctx.drawText(mc.textRenderer, value, x + 7, y + 3, Theme.TEXT, true);
        }
    }

    private static final class Keystrokes extends Module {
        Keystrokes(int x, int y) {
            super("keystrokes", "Keystrokes", x, y);
        }

        @Override
        public int width(MinecraftClient mc) {
            return 68;
        }

        @Override
        public int height(MinecraftClient mc) {
            return 80;
        }

        private void key(DrawContext ctx, MinecraftClient mc, int kx, int ky, int w, int h, String label, boolean down) {
            if (down) ctx.fillGradient(kx, ky, kx + w, ky + h, 0xE0FF4438, 0xE08A0000);
            else ctx.fillGradient(kx, ky, kx + w, ky + h, 0xA0450000, 0xA0000000);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(label), kx + w / 2, ky + (h - 8) / 2 + 1, Theme.TEXT);
        }

        @Override
        public void render(DrawContext ctx, MinecraftClient mc) {
            var o = mc.options;
            key(ctx, mc, x + 24, y, 20, 20, "W", o.forwardKey.isPressed());
            key(ctx, mc, x, y + 22, 20, 20, "A", o.leftKey.isPressed());
            key(ctx, mc, x + 22, y + 22, 24, 20, "S", o.backKey.isPressed());
            key(ctx, mc, x + 48, y + 22, 20, 20, "D", o.rightKey.isPressed());
            key(ctx, mc, x, y + 44, 33, 20, "LMB", o.attackKey.isPressed());
            key(ctx, mc, x + 35, y + 44, 33, 20, "RMB", o.useKey.isPressed());
            key(ctx, mc, x, y + 66, 68, 14, "SPACE", o.jumpKey.isPressed());
        }
    }

    private static final class Armor extends Module {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        Armor(int x, int y) {
            super("armor", "Armor", x, y);
        }

        private int count(MinecraftClient mc) {
            int n = 0;
            if (mc.player != null) for (EquipmentSlot slot : SLOTS) if (!mc.player.getEquippedStack(slot).isEmpty()) n++;
            return n;
        }

        @Override
        public int width(MinecraftClient mc) {
            return 66;
        }

        @Override
        public int height(MinecraftClient mc) {
            return Math.max(1, count(mc)) * 18;
        }

        @Override
        public void render(DrawContext ctx, MinecraftClient mc) {
            if (mc.player == null) return;
            int row = 0;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (stack.isEmpty()) continue;
                int ry = y + row * 18;
                Theme.hudBox(ctx, x, ry, 66, 17);
                ctx.drawItem(stack, x + 3, ry);
                if (stack.isDamageable()) {
                    int left = stack.getMaxDamage() - stack.getDamage();
                    ctx.drawText(mc.textRenderer, String.valueOf(left), x + 22, ry + 5, Theme.TEXT, true);
                }
                row++;
            }
            if (row == 0 && mc.currentScreen instanceof BladeMenuScreen) {
                Theme.hudBox(ctx, x, y, 66, 17);
                ctx.drawText(mc.textRenderer, "Armor", x + 8, y + 5, Theme.DIM, true);
            }
        }
    }

    private static final class Potions extends Module {
        Potions(int x, int y) {
            super("potions", "Potions", x, y);
        }

        @Override
        public int width(MinecraftClient mc) {
            return 110;
        }

        @Override
        public int height(MinecraftClient mc) {
            int n = mc.player == null ? 0 : mc.player.getStatusEffects().size();
            return Math.max(1, n) * 14;
        }

        @Override
        public void render(DrawContext ctx, MinecraftClient mc) {
            if (mc.player == null) return;
            int row = 0;
            for (StatusEffectInstance e : mc.player.getStatusEffects()) {
                String name = Compat.effectName(e) + " " + (e.getAmplifier() + 1);
                int secs = e.getDuration() / 20;
                String time = e.isInfinite() ? "**" : String.format("%d:%02d", secs / 60, secs % 60);
                int ry = y + row * 14;
                Theme.hudBox(ctx, x, ry, 110, 13);
                ctx.drawText(mc.textRenderer, name, x + 6, ry + 3, Theme.TEXT, true);
                ctx.drawText(mc.textRenderer, time, x + 110 - mc.textRenderer.getWidth(time) - 4, ry + 3, Theme.HOT, true);
                row++;
            }
            if (row == 0 && mc.currentScreen instanceof BladeMenuScreen) {
                Theme.hudBox(ctx, x, y, 110, 13);
                ctx.drawText(mc.textRenderer, "Potions", x + 6, y + 3, Theme.DIM, true);
            }
        }
    }

    /** Health bar for the last player or mob you hit. */
    private static final class Target extends Module {
        Target(int x, int y) {
            super("target", "Target", x, y);
        }

        @Override
        public int width(MinecraftClient mc) {
            return 120;
        }

        @Override
        public int height(MinecraftClient mc) {
            return 26;
        }

        @Override
        public void render(DrawContext ctx, MinecraftClient mc) {
            LivingEntity t = PvpMods.target;
            boolean live = t != null && t.isAlive() && System.currentTimeMillis() - PvpMods.targetAt < 5000;
            boolean preview = mc.currentScreen instanceof BladeMenuScreen;
            if (!live && !preview) return;
            Theme.hudBox(ctx, x, y, 120, 26);
            String name = live ? t.getName().getString() : "Target";
            float hp = live ? t.getHealth() : 20f;
            float max = live ? Math.max(1f, t.getMaxHealth()) : 20f;
            ctx.drawText(mc.textRenderer, name, x + 7, y + 4, Theme.TEXT, true);
            String hearts = String.format("%.1f", hp / 2f);
            ctx.drawText(mc.textRenderer, hearts, x + 114 - mc.textRenderer.getWidth(hearts), y + 4, Theme.HOT, true);
            ctx.fill(x + 7, y + 16, x + 113, y + 21, 0xFF1A0000);
            int fill = (int) (106 * Math.max(0f, Math.min(1f, hp / max)));
            ctx.fillGradient(x + 7, y + 16, x + 7 + fill, y + 21, 0xFFFF4438, 0xFF8A0000);
        }
    }

    /** Counts of the items that matter in a fight. */
    private static final class Counters extends Module {
        Counters(int x, int y) {
            super("items", "Item counts", x, y);
        }

        private int[] counts(MinecraftClient mc) {
            int[] c = new int[5];
            if (mc.player == null) return c;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.isEmpty()) continue;
                if (s.isOf(Items.ENDER_PEARL)) c[0] += s.getCount();
                else if (s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE)) c[1] += s.getCount();
                else if (s.isOf(Items.TOTEM_OF_UNDYING)) c[2] += s.getCount();
                else if (s.isOf(Items.SPLASH_POTION)) c[3] += s.getCount();
                else if (s.isOf(Items.END_CRYSTAL)) c[4] += s.getCount();
            }
            return c;
        }

        private int rows(MinecraftClient mc) {
            int n = 0;
            for (int v : counts(mc)) if (v > 0) n++;
            return n;
        }

        @Override
        public int width(MinecraftClient mc) {
            return 46;
        }

        @Override
        public int height(MinecraftClient mc) {
            return Math.max(1, rows(mc)) * 18;
        }

        @Override
        public void render(DrawContext ctx, MinecraftClient mc) {
            ItemStack[] icons = {
                    new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.TOTEM_OF_UNDYING),
                    new ItemStack(Items.SPLASH_POTION), new ItemStack(Items.END_CRYSTAL)};
            int[] c = counts(mc);
            int row = 0;
            for (int i = 0; i < c.length; i++) {
                if (c[i] <= 0) continue;
                int ry = y + row * 18;
                Theme.hudBox(ctx, x, ry, 46, 17);
                ctx.drawItem(icons[i], x + 3, ry);
                ctx.drawText(mc.textRenderer, "x" + c[i], x + 22, ry + 5, Theme.TEXT, true);
                row++;
            }
            if (row == 0 && mc.currentScreen instanceof BladeMenuScreen) {
                Theme.hudBox(ctx, x, y, 46, 17);
                ctx.drawText(mc.textRenderer, "Items", x + 8, y + 5, Theme.DIM, true);
            }
        }
    }

    public static final List<Module> ALL = new ArrayList<>();

    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    private static boolean leftDown;
    private static boolean rightDown;

    static {
        ALL.add(new Line("fps", "FPS", 6, 6, mc -> "FPS " + mc.getCurrentFps()));
        ALL.add(new Line("cps", "CPS", 6, 24, mc -> "CPS " + cps(LEFT) + " | " + cps(RIGHT)));
        ALL.add(new Line("ping", "Ping", 6, 42, mc -> {
            PlayerListEntry entry = mc.getNetworkHandler() == null || mc.player == null
                    ? null
                    : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            return "Ping " + (entry == null ? "--" : entry.getLatency() + " ms");
        }));
        ALL.add(new Line("coords", "Coordinates", 6, 60, mc ->
                mc.player == null ? "XYZ" : "XYZ " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ()));
        ALL.add(new Keystrokes(6, 84));
        ALL.add(new Armor(6, 170));
        ALL.add(new Potions(80, 6));
        ALL.add(new Line("combo", "Combo", 200, 6, mc -> "Combo " + PvpMods.combo));
        ALL.add(new Line("speed", "Speed", 200, 24, mc -> String.format("Speed %.1f b/s", PvpMods.speed)));
        ALL.add(new Line("direction", "Direction", 200, 42, mc ->
                mc.player == null ? "Facing" : "Facing " + mc.player.getHorizontalFacing().asString()));
        ALL.add(new Line("clock", "Clock", 200, 60, mc -> LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))));
        ALL.add(new Line("memory", "Memory", 200, 78, mc -> {
            Runtime rt = Runtime.getRuntime();
            return "RAM " + (rt.totalMemory() - rt.freeMemory()) / 1048576 + "/" + rt.maxMemory() / 1048576 + " MB";
        }));
        ALL.add(new Line("sprint", "Sprint", 200, 96, mc ->
                "Sprint: " + (!PvpMods.toggleSprint ? "Hold" : mc.player != null && mc.player.isSprinting() ? "Toggled" : "Toggle ready")));
        ALL.add(new Target(200, 118));
        ALL.add(new Counters(80, 170));
    }

    private HudModules() {}

    private static int cps(Deque<Long> clicks) {
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
        return clicks.size();
    }

    private static void pollMouse(MinecraftClient mc) {
        if (mc.currentScreen != null) return;
        long window = mc.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (left && !leftDown) LEFT.addLast(System.currentTimeMillis());
        if (right && !rightDown) RIGHT.addLast(System.currentTimeMillis());
        leftDown = left;
        rightDown = right;
    }

    public static void renderAll(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        pollMouse(mc);
        for (Module m : ALL) if (m.enabled) m.render(ctx, mc);
    }

    public static void load() {
        JsonObject root = Profile.readObject(Profile.dir().resolve("hud.json"));
        for (Module m : ALL) {
            if (!root.has(m.id) || !root.get(m.id).isJsonObject()) continue;
            JsonObject o = root.getAsJsonObject(m.id);
            if (o.has("enabled")) m.enabled = o.get("enabled").getAsBoolean();
            if (o.has("x")) m.x = o.get("x").getAsInt();
            if (o.has("y")) m.y = o.get("y").getAsInt();
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Module m : ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled);
            o.addProperty("x", m.x);
            o.addProperty("y", m.y);
            root.add(m.id, o);
        }
        Profile.writeJson(Profile.dir().resolve("hud.json"), root);
    }
}
