package dev.blade;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The Blade menu: PvP HUD, cosmetics, friends and chat, on the red and black fade theme. */
public class BladeMenuScreen extends BladeScreen {
    public enum Tab { HUD, PVP, LOOKS, FRIENDS, CHAT }

    private static final int PW = 340;

    private Tab tab;
    private int px;
    private int py;
    private int ph;
    private TextFieldWidget field;
    private String friendDraft = "";
    private String channel = "global";
    private HudModules.Module dragging;
    private boolean shiftArmed;
    private int lastSignature;

    public BladeMenuScreen(Tab tab) {
        super(Text.literal("Blade"));
        this.tab = tab;
    }

    @Override
    protected void init() {
        build();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int signature() {
        Social s = Social.get();
        return s.friends().hashCode() * 31 + s.requests().hashCode();
    }

    @Override
    public void tick() {
        int now = signature();
        if (tab == Tab.FRIENDS && now != lastSignature) build();
    }

    private void build() {
        clearChildren();
        field = null;
        ph = Math.min(height - 50, 300);
        px = (width - PW) / 2;
        py = (height - ph) / 2 + 8;
        lastSignature = signature();

        Tab[] tabs = Tab.values();
        String[] names = {"HUD", "PvP", "Looks", "Friends", "Chat"};
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addDrawableChild(new BladeButton(px + 10 + i * 64, py + 10, 60, 18, names[i], () -> {
                tab = t;
                build();
            }).selected(t == tab));
        }

        int cy = py + 38;
        switch (tab) {
            case HUD -> buildHud(cy);
            case PVP -> buildPvp(cy);
            case LOOKS -> buildCosmetics(cy);
            case FRIENDS -> buildFriends(cy);
            case CHAT -> buildChat(cy);
        }
    }

    // ---------- HUD tab ----------

    private void buildHud(int cy) {
        List<HudModules.Module> all = HudModules.ALL;
        for (int i = 0; i < all.size(); i++) {
            HudModules.Module m = all.get(i);
            int x = px + 10 + (i % 3) * 108;
            int y = cy + (i / 3) * 22;
            BladeButton[] self = new BladeButton[1];
            self[0] = new BladeButton(x, y, 104, 18, m.label, () -> {
                m.enabled = !m.enabled;
                HudModules.save();
                self[0].selected(m.enabled);
            }).selected(m.enabled);
            addDrawableChild(self[0]);
        }
    }

    // ---------- PvP tab ----------

    private void buildPvp(int cy) {
        BladeButton[] sprint = new BladeButton[1];
        sprint[0] = new BladeButton(px + 10, cy, PW - 20, 20, "Toggle Sprint: " + (PvpMods.toggleSprint ? "ON" : "OFF"), () -> {
            PvpMods.toggleSprint = !PvpMods.toggleSprint;
            PvpMods.save();
            sprint[0].setLabel("Toggle Sprint: " + (PvpMods.toggleSprint ? "ON" : "OFF"));
        });
        addDrawableChild(sprint[0]);

        BladeButton[] zoom = new BladeButton[1];
        zoom[0] = new BladeButton(px + 10, cy + 24, PW - 20, 20, "Zoom (hold C): " + (PvpMods.zoomEnabled ? "ON" : "OFF"), () -> {
            PvpMods.zoomEnabled = !PvpMods.zoomEnabled;
            PvpMods.save();
            zoom[0].setLabel("Zoom (hold C): " + (PvpMods.zoomEnabled ? "ON" : "OFF"));
        });
        addDrawableChild(zoom[0]);

        BladeButton[] fov = new BladeButton[1];
        fov[0] = new BladeButton(px + 10, cy + 48, PW - 20, 20, "Zoom strength (FOV): " + PvpMods.zoomFov, () -> {
            PvpMods.zoomFov = PvpMods.zoomFov >= 60 ? 30 : PvpMods.zoomFov + 10;
            PvpMods.save();
            fov[0].setLabel("Zoom strength (FOV): " + PvpMods.zoomFov);
        });
        addDrawableChild(fov[0]);
    }

    // ---------- Cosmetics tab ----------

    private void buildCosmetics(int cy) {
        addCycle(cy, "Wings", Cosmetics.WINGS, () -> Profile.cosmetics().wings, v -> Profile.cosmetics().wings = v);
        addCycle(cy + 24, "Blade", Cosmetics.BLADE, () -> Profile.cosmetics().blade, v -> Profile.cosmetics().blade = v);
        addCycle(cy + 48, "Aura", Cosmetics.AURA, () -> Profile.cosmetics().aura, v -> Profile.cosmetics().aura = v);
        addCycle(cy + 72, "Trail", Cosmetics.TRAIL, () -> Profile.cosmetics().trail, v -> Profile.cosmetics().trail = v);
    }

    private void addCycle(int y, String name, String[] options, java.util.function.Supplier<String> get, java.util.function.Consumer<String> set) {
        BladeButton[] self = new BladeButton[1];
        self[0] = new BladeButton(px + 10, y, PW - 20, 20, name + ": " + get.get(), () -> {
            set.accept(Cosmetics.next(options, get.get()));
            Profile.saveCosmetics();
            self[0].setLabel(name + ": " + get.get());
        });
        addDrawableChild(self[0]);
    }

    // ---------- Friends tab ----------

    private void buildFriends(int cy) {
        field = new TextFieldWidget(textRenderer, px + 10, cy, 236, 18, Text.literal("Friend name"));
        field.setMaxLength(16);
        field.setText(friendDraft);
        field.setChangedListener(value -> friendDraft = value);
        addDrawableChild(field);
        setFocused(field);
        field.setFocused(true);
        addDrawableChild(new BladeButton(px + 250, cy, 80, 18, "Add", this::submitFriend));

        Social s = Social.get();
        int y = cy + 28;
        int max = py + ph - 24;
        for (String name : s.requests()) {
            if (y + 20 > max) break;
            addDrawableChild(new BladeButton(px + 190, y, 70, 18, "Accept", () -> s.accept(name)));
            addDrawableChild(new BladeButton(px + 264, y, 66, 18, "Decline", () -> s.decline(name)));
            y += 22;
        }
        for (Social.Friend f : s.friends()) {
            if (y + 20 > max) break;
            addDrawableChild(new BladeButton(px + 220, y, 50, 18, "Msg", () -> {
                channel = f.name();
                tab = Tab.CHAT;
                build();
            }));
            addDrawableChild(new BladeButton(px + 274, y, 56, 18, "Remove", () -> s.remove(f.name())));
            y += 22;
        }
    }

    private void submitFriend() {
        String name = friendDraft.trim();
        if (name.isEmpty()) return;
        Social.get().addFriend(name);
        friendDraft = "";
        if (field != null) field.setText("");
    }

    // ---------- Chat tab ----------

    private void buildChat(int cy) {
        List<String> channels = new ArrayList<>();
        channels.add("global");
        for (Social.Friend f : Social.get().friends()) channels.add(f.name());
        if (!channels.contains(channel)) channel = "global";
        BladeButton[] self = new BladeButton[1];
        self[0] = new BladeButton(px + 10, cy, PW - 20, 18, "Channel: " + (channel.equals("global") ? "Global" : channel), () -> {
            int i = channels.indexOf(channel);
            channel = channels.get((i + 1) % channels.size());
            self[0].setLabel("Channel: " + (channel.equals("global") ? "Global" : channel));
        });
        addDrawableChild(self[0]);

        field = new TextFieldWidget(textRenderer, px + 10, py + ph - 26, 250, 18, Text.literal("Message"));
        field.setMaxLength(256);
        addDrawableChild(field);
        setFocused(field);
        field.setFocused(true);
        addDrawableChild(new BladeButton(px + 264, py + ph - 26, 66, 18, "Send", this::submitChat));
    }

    private void submitChat() {
        if (field == null) return;
        String text = field.getText().trim();
        if (text.isEmpty()) return;
        Social.get().send(text, channel.equals("global") ? null : channel);
        field.setText("");
    }

    // ---------- input ----------

    @Override
    protected boolean onKey(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (tab == Tab.CHAT) {
                submitChat();
                return true;
            }
            if (tab == Tab.FRIENDS) {
                submitFriend();
                return true;
            }
        }
        // Right Shift closes the menu, except while you are typing (it is also a shift key).
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT && shiftArmed && field == null) {
            close();
            return true;
        }
        return false;
    }

    @Override
    protected void onKeyUp(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) shiftArmed = true;
    }

    @Override
    protected boolean onMouseDown(double mouseX, double mouseY, int button) {
        if (tab == Tab.HUD && button == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            List<HudModules.Module> all = HudModules.ALL;
            for (int i = all.size() - 1; i >= 0; i--) {
                HudModules.Module m = all.get(i);
                if (!m.enabled) continue;
                if (mouseX >= m.x && mouseX <= m.x + m.width(mc) && mouseY >= m.y && mouseY <= m.y + m.height(mc)) {
                    dragging = m;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseDrag(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        dragging.x = (int) Math.max(0, Math.min(width - dragging.width(mc), dragging.x + deltaX));
        dragging.y = (int) Math.max(0, Math.min(height - dragging.height(mc), dragging.y + deltaY));
        return true;
    }

    @Override
    protected boolean onMouseUp(double mouseX, double mouseY, int button) {
        if (dragging == null) return false;
        dragging = null;
        HudModules.save();
        return true;
    }

    // ---------- drawing ----------

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Blade draws its own background in render().
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Theme.background(ctx, width, height);
        Theme.blade(ctx, width / 2, 8, height - 16, 9);
        HudModules.renderAll(ctx);
        Theme.panel(ctx, px, py, PW, ph);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("BLADE CLIENT"), width / 2, py - 11, Theme.TEXT);

        MinecraftClient mc = MinecraftClient.getInstance();
        int cy = py + 38;
        switch (tab) {
            case HUD -> {
                int rows = (HudModules.ALL.size() + 2) / 3;
                ctx.drawText(textRenderer, "Drag any module on screen to move it.", px + 10, cy + rows * 22 + 6, Theme.DIM, false);
                ctx.drawText(textRenderer, "Right Shift or Esc closes this menu.", px + 10, cy + rows * 22 + 18, Theme.DIM, false);
                for (HudModules.Module m : HudModules.ALL) {
                    if (m.enabled) Theme.border(ctx, m.x - 1, m.y - 1, m.width(mc) + 2, m.height(mc) + 2, m == dragging ? Theme.TEXT : Theme.HOT);
                }
            }
            case PVP -> {
                ctx.drawText(textRenderer, "Combo, Speed, Target, Item counts and more are on the HUD tab.", px + 10, cy + 80, Theme.DIM, false);
                ctx.drawText(textRenderer, "Change the zoom key in Controls (category Blade).", px + 10, cy + 92, Theme.DIM, false);
            }
            case LOOKS -> {
                ctx.drawText(textRenderer, "Click an item to change it. Other Blade players", px + 10, cy + 100, Theme.DIM, false);
                ctx.drawText(textRenderer, "see your wings, blade, aura and trail.", px + 10, cy + 112, Theme.DIM, false);
            }
            case FRIENDS -> drawFriends(ctx, cy);
            case CHAT -> drawChat(ctx, cy);
        }
        super.render(ctx, mouseX, mouseY, delta);
        if (field != null && field.getText().isEmpty()) {
            boolean friends = tab == Tab.FRIENDS;
            ctx.drawText(textRenderer, friends ? "Add a friend by name" : "Message", px + 14, (friends ? cy : py + ph - 26) + 5, Theme.DIM, false);
        }
    }

    private void drawFriends(DrawContext ctx, int cy) {
        Social s = Social.get();
        ctx.drawText(textRenderer, s.connected() ? "Connected as " + s.me() : "Not connected", px + 10, py + ph - 14, s.connected() ? 0xFF54E07A : Theme.DIM, false);
        if (!s.error().isEmpty()) ctx.drawText(textRenderer, s.error(), px + 150, py + ph - 14, 0xFFFF8A80, false);
        int y = cy + 28;
        int max = py + ph - 24;
        for (String name : s.requests()) {
            if (y + 20 > max) break;
            ctx.drawText(textRenderer, name + " (request)", px + 12, y + 5, Theme.TEXT, true);
            y += 22;
        }
        for (Social.Friend f : s.friends()) {
            if (y + 20 > max) break;
            ctx.fill(px + 12, y + 6, px + 18, y + 12, f.online() ? 0xFF54E07A : 0xFF5A3B38);
            ctx.drawText(textRenderer, f.name(), px + 24, y + 5, Theme.TEXT, true);
            y += 22;
        }
        if (s.requests().isEmpty() && s.friends().isEmpty()) {
            ctx.drawText(textRenderer, "No friends yet. Add someone by name above.", px + 12, y + 4, Theme.DIM, false);
        }
    }

    private void drawChat(DrawContext ctx, int cy) {
        Social s = Social.get();
        List<Social.Msg> messages = channel.equals("global") ? s.global() : s.dm(channel);
        int top = cy + 24;
        int bottom = py + ph - 32;
        int maxLines = Math.max(1, (bottom - top) / 10);
        List<OrderedText> lines = new ArrayList<>();
        for (Social.Msg m : messages) {
            lines.addAll(textRenderer.wrapLines(Text.literal(m.from() + ": " + m.text()), PW - 24));
        }
        if (!s.connected()) {
            lines.clear();
            lines.addAll(textRenderer.wrapLines(Text.literal(s.error().isEmpty() ? "Connecting…" : s.error()), PW - 24));
        } else if (lines.isEmpty()) {
            lines.addAll(textRenderer.wrapLines(Text.literal("No messages yet. Say hi."), PW - 24));
        }
        int start = Math.max(0, lines.size() - maxLines);
        int y = top;
        for (int i = start; i < lines.size(); i++) {
            ctx.drawText(textRenderer, lines.get(i), px + 12, y, Theme.TEXT, true);
            y += 10;
        }
    }
}
