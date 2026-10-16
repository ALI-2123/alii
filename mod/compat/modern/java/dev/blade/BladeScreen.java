package dev.blade;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** Screen base with version-neutral input hooks (1.21.11 input methods). */
public abstract class BladeScreen extends Screen {
    protected BladeScreen(Text title) {
        super(title);
    }

    protected boolean onKey(int key) { return false; }
    protected void onKeyUp(int key) { }
    protected boolean onMouseDown(double x, double y, int button) { return false; }
    protected boolean onMouseDrag(double x, double y, int button, double dx, double dy) { return false; }
    protected boolean onMouseUp(double x, double y, int button) { return false; }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (onKey(input.key())) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        onKeyUp(input.key());
        return super.keyReleased(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        return onMouseDown(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (onMouseDrag(click.x(), click.y(), click.button(), offsetX, offsetY)) return true;
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (onMouseUp(click.x(), click.y(), click.button())) return true;
        return super.mouseReleased(click);
    }
}
