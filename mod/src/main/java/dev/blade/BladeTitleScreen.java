package dev.blade;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;

/**
 * The in-game start menu, laid out like the launcher home: top bar with tabs, account and version on the left,
 * the big black sword in the middle with the play button across it, red fade and embers behind.
 */
public class BladeTitleScreen extends BladeScreen {
    private static final int BAR = 26;

    private int playX, playY, playW, playH;
    private String logo = "B L A D E";

    public BladeTitleScreen() {
        super(Text.literal("Blade"));
    }

    private static String modVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(c -> c.getMetadata().getVersion().getFriendlyString().split("\\+")[0])
                .orElse("?");
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();

        // top bar tabs, centred like the launcher
        String[] names = {"Play", "Multiplayer", "Cosmetics", "Friends", "Chat", "Options"};
        Runnable[] actions = {
            () -> { },
            () -> mc.setScreen(new MultiplayerScreen(this)),
            () -> mc.setScreen(new BladeMenuScreen(BladeMenuScreen.Tab.LOOKS)),
            () -> mc.setScreen(new BladeMenuScreen(BladeMenuScreen.Tab.FRIENDS)),
            () -> mc.setScreen(new BladeMenuScreen(BladeMenuScreen.Tab.CHAT)),
            () -> mc.setScreen(new OptionsScreen(this, mc.options))
        };
        // pick a tab padding that fits this window (GUI scale changes the width a lot)
        int quitLabel = mc.textRenderer.getWidth("Quit");
        int pad = 8;
        int start = 0;
        int[] widths = new int[names.length];
        for (int cand : new int[] {22, 14, 8}) {
            pad = cand;
            int tot = 0;
            for (int i = 0; i < names.length; i++) {
                widths[i] = mc.textRenderer.getWidth(names[i]) + pad;
                tot += widths[i];
            }
            start = (width - tot) / 2;
            if (start >= 70 && start + tot <= width - (quitLabel + pad) - 12) break;
        }
        int x = start;
        for (int i = 0; i < names.length; i++) {
            addDrawableChild(new TabButton(x, 0, widths[i], BAR, names[i], actions[i], i == 0));
            x += widths[i];
        }
        int quitW = quitLabel + pad;
        addDrawableChild(new TabButton(width - quitW - 6, 0, quitW, BAR, "Quit", mc::scheduleStop, false));
        String full = "B L A D E   C L I E N T";
        logo = (10 + mc.textRenderer.getWidth(full) + 10 <= start) ? full : "B L A D E";

        // the play button across the middle of the sword
        int top = BAR + 8;
        int sh = height - BAR - 16;
        playW = Math.max(110, Math.min(190, width / 4));
        playH = 30;
        playX = width / 2 - playW / 2;
        playY = top + Math.round(sh * 0.38f);
        addDrawableChild(new PlayButton(playX, playY, playW, playH, "P L A Y", () -> mc.setScreen(new SelectWorldScreen(this))));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Blade draws its own background in render().
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Theme.titleBackground(ctx, width, height);
        Theme.sword(ctx, width / 2, BAR + 8, height - BAR - 16);

        // top bar
        ctx.fillGradient(0, 0, width, BAR, 0xE6000000, 0xB0000000);
        ctx.fillGradient(0, BAR - 1, width, BAR + 6, 0x66FF2A1A, 0x00FF2A1A);
        ctx.fill(0, BAR - 1, width, BAR, 0x66FF2A1A);
        ctx.drawText(textRenderer, logo, 10, (BAR - 8) / 2, Theme.TEXT, true);

        // left column: account and version
        int colW = Math.max(110, Math.min(160, width / 2 - 100 - Math.max(10, width / 18)));
        int x = Math.max(10, width / 18);
        int y = Math.max(BAR + 20, height * 36 / 100);
        String name = mc.getSession().getUsername();
        ctx.drawText(textRenderer, "Account", x, y, Theme.TEXT, true);
        box(ctx, x, y + 12, colW, 24);
        ctx.drawText(textRenderer, name, x + 7, y + 12 + 8, Theme.TEXT, true);
        int pillW = textRenderer.getWidth("Blade") + 12;
        ctx.fill(x + colW - pillW - 5, y + 12 + 5, x + colW - 5, y + 12 + 19, 0xFFD40000);
        ctx.drawText(textRenderer, "Blade", x + colW - pillW - 5 + 6, y + 12 + 8, Theme.TEXT, false);
        ctx.drawText(textRenderer, "Account is set in the launcher", x, y + 42, Theme.DIM, false);

        ctx.drawText(textRenderer, "Version", x, y + 62, Theme.TEXT, true);
        box(ctx, x, y + 74, colW, 24);
        ctx.drawText(textRenderer, "Minecraft " + modVersion("minecraft"), x + 7, y + 74 + 8, Theme.TEXT, true);
        ctx.drawText(textRenderer, "Loader: Fabric   Blade " + modVersion("blade"), x, y + 104, Theme.DIM, false);

        // progress style bar under the play button, like the launcher
        int barY = playY + playH + 7;
        int a = 170 + (int) (70 * Theme.pulse());
        ctx.fillGradient(playX - 3, barY - 3, playX + playW + 3, barY + 6, 0x00FF2A1A, Theme.argb(40, 0xFF2A1A));
        ctx.fill(playX, barY, playX + playW, barY + 3, Theme.argb(a, 0xE01010));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Ready to play"), width / 2, barY + 9, Theme.DIM);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private static void box(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0x88000000);
        ctx.fillGradient(x, y, x + w, y + h, 0x22FF2A1A, 0x00000000);
        Theme.border(ctx, x, y, w, h, 0x55FF4438);
    }

    /** The slanted play button, shaped like the one in the launcher. */
    private static final class PlayButton extends BladeButton {
        PlayButton(int x, int y, int w, int h, String label, Runnable action) {
            super(x, y, w, h, label, action);
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean hot = isHovered();
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            int slant = h / 3;
            for (int r = 0; r < h; r++) {
                float t = r / (float) h;
                int right = x + w - Math.round(slant * (1f - t));
                int c = Theme.lerp(hot ? 0xE0801414 : 0xC03C1212, hot ? 0xE0400000 : 0xC0200000, t);
                ctx.fill(x, y + r, right, y + r + 1, c);
            }
            ctx.fill(x, y, x + 2, y + h, hot ? Theme.HOT : 0xAAFF2A1A);
            if (hot) ctx.fill(x, y + h - 1, x + w - 1, y + h, Theme.HOT);
            Compat.drawScaledCentered(ctx, MinecraftClient.getInstance().textRenderer, getMessage().getString(), x + w / 2, y + (h - 12) / 2, 1.5f, hot ? Theme.TEXT : 0xFFE6D8D5);
        }
    }

    /** A top bar tab: plain text, underlined in red when it is the current one. */
    private static final class TabButton extends BladeButton {
        private final boolean current;

        TabButton(int x, int y, int w, int h, String label, Runnable action, boolean current) {
            super(x, y, w, h, label, action);
            this.current = current;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean hot = isHovered() || current;
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            if (hot) {
                ctx.fillGradient(x + 4, y + 4, x + w - 4, y + h - 1, 0x00FF2A1A, 0x33FF2A1A);
                ctx.fill(x + 6, y + h - 3, x + w - 6, y + h - 1, Theme.HOT);
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            ctx.drawCenteredTextWithShadow(mc.textRenderer, getMessage(), x + w / 2, y + (h - 8) / 2 - 1, hot ? Theme.TEXT : Theme.DIM);
        }
    }
}
