package dev.blade;

import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Button base with a version-neutral click hook (1.21.1 and 1.21.4). */
public abstract class ButtonBase extends ClickableWidget {
    protected ButtonBase(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    protected abstract void fire();

    @Override
    public void onClick(double mouseX, double mouseY) {
        fire();
    }
}
