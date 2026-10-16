package dev.blade;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Screen base with version-neutral input hooks (1.21.1 and 1.21.4 input methods). */
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (onKey(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        onKeyUp(keyCode);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return onMouseDown(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (onMouseDrag(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (onMouseUp(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
