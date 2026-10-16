package dev.blade;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;

/** PvP helpers: Toggle Sprint, Zoom, and the numbers behind the Combo, Speed and Target HUD modules. */
public final class PvpMods {
    public static boolean toggleSprint = false;
    public static boolean zoomEnabled = true;
    public static int zoomFov = 30;

    public static int combo;
    public static double speed;
    public static LivingEntity target;
    public static long targetAt;

    private static long comboAt;
    private static double lastX, lastZ;
    private static boolean haveLast;
    private static int lastHurt;
    private static boolean zooming;
    private static int savedFov = 70;
    public static KeyBinding zoomKey;

    private PvpMods() {}

    public static void init() {
        load();
        // Counts your own hits (the same hook also fires inside a single player world, so only count this player)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (player == mc.player && entity instanceof LivingEntity living) {
                target = living;
                targetAt = System.currentTimeMillis();
                combo++;
                comboAt = targetAt;
            }
            return ActionResult.PASS;
        });
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            if (zooming) stopZoom(mc);
            haveLast = false;
            return;
        }

        long now = System.currentTimeMillis();
        if (p.hurtTime > 0 && lastHurt == 0) combo = 0; // you got hit, the combo is over
        lastHurt = p.hurtTime;
        if (combo > 0 && now - comboAt > 2000) combo = 0;

        // Speed from how far we moved since the last tick (the old prevX field is not in every version)
        double dx = haveLast ? p.getX() - lastX : 0;
        double dz = haveLast ? p.getZ() - lastZ : 0;
        lastX = p.getX();
        lastZ = p.getZ();
        haveLast = true;
        speed = Math.sqrt(dx * dx + dz * dz) * 20;

        if (toggleSprint && mc.options.forwardKey.isPressed() && !p.isSneaking() && !p.horizontalCollision
                && !p.isUsingItem() && p.getHungerManager().getFoodLevel() > 6) {
            p.setSprinting(true);
        }

        boolean down = zoomKey != null && zoomKey.isPressed() && mc.currentScreen == null;
        if (zoomEnabled && down && !zooming) {
            savedFov = mc.options.getFov().getValue();
            mc.options.getFov().setValue(zoomFov);
            zooming = true;
        } else if (zooming && (!down || !zoomEnabled)) {
            stopZoom(mc);
        }
    }

    private static void stopZoom(MinecraftClient mc) {
        mc.options.getFov().setValue(savedFov);
        zooming = false;
    }

    private static void load() {
        JsonObject o = Profile.readObject(Profile.dir().resolve("pvp.json"));
        if (o.has("toggleSprint")) toggleSprint = o.get("toggleSprint").getAsBoolean();
        if (o.has("zoom")) zoomEnabled = o.get("zoom").getAsBoolean();
        if (o.has("zoomFov")) zoomFov = Math.max(30, Math.min(60, o.get("zoomFov").getAsInt()));
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("toggleSprint", toggleSprint);
        o.addProperty("zoom", zoomEnabled);
        o.addProperty("zoomFov", zoomFov);
        Profile.writeJson(Profile.dir().resolve("pvp.json"), o);
    }
}
