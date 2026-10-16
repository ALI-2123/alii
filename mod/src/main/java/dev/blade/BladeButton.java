package dev.blade;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

/** A red and black fade button. */
public class BladeButton extends ButtonBase {
    private final Runnable action;
    private boolean selected;

    public BladeButton(int x, int y, int width, int height, String label, Runnable action) {
        super(x, y, width, height, Text.literal(label));
        this.action = action;
    }

    public BladeButton selected(boolean value) {
        this.selected = value;
        return this;
    }

    public void setLabel(String label) {
        setMessage(Text.literal(label));
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean hot = isHovered() || selected;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        // see-through red glass, brighter when hovered
        ctx.fillGradient(x, y, x + w, y + h, hot ? 0xB8D01818 : 0x88500000, hot ? 0xB8700000 : 0x88140000);
        ctx.fill(x, y + h - 1, x + w, y + h, hot ? Theme.HOT : 0xAA8A0000);
        ctx.fillGradient(x, y, x + 2, y + h, hot ? 0xFFFF6A5E : 0xAAFF2A1A, hot ? 0xFFB00000 : 0x66660000);
        if (hot) ctx.fillGradient(x, y, x + w, y + h / 2, 0x28FFFFFF, 0x00FFFFFF);
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.drawCenteredTextWithShadow(mc.textRenderer, getMessage(), x + getWidth() / 2, y + (getHeight() - 8) / 2, Theme.TEXT);
    }

    @Override
    protected void fire() {
        action.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
