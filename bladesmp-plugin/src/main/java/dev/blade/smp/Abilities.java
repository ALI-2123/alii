package dev.blade.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** The Blade abilities: Phantom Cloak (level 1+), mini blades (level 2+) and the black hole (level 3). */
public final class Abilities {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};

    private static final class Session {
        long end;
        int blades;
        BukkitTask task;
    }

    private final JavaPlugin plugin;
    private final BladeItem items;
    private final Random random = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> stunnedUntil = new HashMap<>();

    public Abilities(JavaPlugin plugin, BladeItem items) {
        this.plugin = plugin;
        this.items = items;
    }

    // ---------- state helpers ----------

    public boolean isStunned(Player p) {
        Long until = stunnedUntil.get(p.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            stunnedUntil.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean isCloaked(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    public long cooldownLeftSeconds(Player p) {
        Long until = cooldownUntil.get(p.getUniqueId());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    public void forget(Player p) {
        stunnedUntil.remove(p.getUniqueId());
        Session s = sessions.remove(p.getUniqueId());
        if (s != null && s.task != null) s.task.cancel();
    }

    // ---------- right click ----------

    public void use(Player p, int level) {
        if (level < 1 || isStunned(p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            if (level >= 2 && s.blades > 0) {
                s.blades--;
                throwMiniBlade(p);
                p.sendActionBar(Component.text("Mini blades left: " + s.blades, NamedTextColor.DARK_RED));
            }
            return;
        }
        long left = cooldownLeftSeconds(p);
        if (left > 0) {
            p.sendActionBar(Component.text("Blade ready in " + left + "s", NamedTextColor.RED));
            return;
        }
        activate(p, level);
    }

    private void activate(Player p, int level) {
        int cloakSeconds = plugin.getConfig().getInt("ability.cloak-seconds", 5);
        int cooldown = plugin.getConfig().getInt("ability.cooldown-seconds." + level, 30);
        long now = System.currentTimeMillis();
        cooldownUntil.put(p.getUniqueId(), now + cooldown * 1000L);

        Session s = new Session();
        s.end = now + cloakSeconds * 1000L;
        s.blades = level >= 2 ? plugin.getConfig().getInt("ability.miniblade-count", 3) : 0;
        sessions.put(p.getUniqueId(), s);

        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, cloakSeconds * 20, 0, false, false, false));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);

        // Potion invisibility still shows armor and held items, so everyone else is sent empty slots.
        s.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || System.currentTimeMillis() >= s.end) {
                    cancel();
                    sessions.remove(p.getUniqueId());
                    if (p.isOnline()) showGear(p);
                    return;
                }
                hideGear(p);
            }
        }.runTaskTimer(plugin, 0L, 4L);

        if (level >= 3) blackHole(p);
    }

    private void sendGear(Player owner, boolean hidden) {
        Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack real = owner.getInventory().getItem(slot);
            map.put(slot, hidden || real == null ? new ItemStack(Material.AIR) : real);
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(owner) || !viewer.getWorld().equals(owner.getWorld())) continue;
            if (viewer.getLocation().distanceSquared(owner.getLocation()) > 96 * 96) continue;
            viewer.sendEquipmentChange(owner, map);
        }
    }

    private void hideGear(Player p) {
        sendGear(p, true);
    }

    private void showGear(Player p) {
        sendGear(p, false);
    }

    // ---------- mini blades ----------

    private void throwMiniBlade(Player p) {
        double damage = plugin.getConfig().getDouble("ability.miniblade-damage", 1.0);
        World world = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location start = eye.clone().add(dir.clone().multiply(0.8));

        ItemDisplay display = world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(items.create(0, 0));
            d.setTransformation(new org.bukkit.util.Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f), new AxisAngle4f()));
            d.setTeleportDuration(1);
            d.setPersistent(false);
        });
        world.playSound(start, Sound.ITEM_TRIDENT_THROW, 1f, 1.5f);

        new BukkitRunnable() {
            final Location pos = start.clone();
            int ticks = 0;

            @Override
            public void run() {
                double speed = 1.2;
                RayTraceResult hit = world.rayTrace(pos, dir, speed, FluidCollisionMode.NEVER, true, 0.3,
                        e -> e instanceof LivingEntity && !e.equals(p) && !(e instanceof ArmorStand));
                if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                    target.damage(damage, p);
                    world.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(0xB0, 0, 0), 1.2f));
                    finish();
                    return;
                }
                if (hit != null && hit.getHitBlock() != null) {
                    finish();
                    return;
                }
                pos.add(dir.clone().multiply(speed));
                pos.setDirection(dir);
                display.teleport(pos);
                world.spawnParticle(Particle.DUST, pos, 2, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(0x20, 0, 0), 1f));
                if (++ticks > 28) finish();
            }

            private void finish() {
                cancel();
                display.remove();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---------- black hole ----------

    private Block ground(World w, int x, int y, int z) {
        for (int yy = y + 1; yy >= y - 5; yy--) {
            Block b = w.getBlockAt(x, yy, z);
            Block above = b.getRelative(BlockFace.UP);
            if (b.getType().isSolid() && above.isPassable() && !above.isLiquid()) return b;
        }
        return null;
    }

    private void sendBlocks(List<Player> viewers, List<Block> blocks, boolean black) {
        List<BlockState> states = new ArrayList<>(blocks.size());
        for (Block b : blocks) {
            BlockState state = b.getState();
            if (black) state.setBlockData(Material.BLACK_CONCRETE.createBlockData());
            states.add(state);
        }
        for (Player viewer : viewers) viewer.sendBlockChanges(states);
    }

    private void blackHole(Player caster) {
        int radius = plugin.getConfig().getInt("ability.blackhole.radius", 10);
        int stunSeconds = plugin.getConfig().getInt("ability.blackhole.stun-seconds", 10);
        int zoneSeconds = plugin.getConfig().getInt("ability.blackhole.zone-seconds", 10);
        Location center = caster.getLocation().clone();
        World world = center.getWorld();

        List<Block> ground = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                Block b = ground(world, center.getBlockX() + dx, center.getBlockY(), center.getBlockZ() + dz);
                if (b != null) ground.add(b);
            }
        }
        List<Player> viewers = new ArrayList<>();
        for (Player pl : world.getPlayers()) {
            if (pl.getLocation().distanceSquared(center) <= (radius + 48) * (radius + 48)) viewers.add(pl);
        }
        // Only the picture of the ground changes, so nothing is ever griefed.
        sendBlocks(viewers, ground, true);
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.5f);

        final long zoneEnd = System.currentTimeMillis() + zoneSeconds * 1000L;
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                if (now >= zoneEnd) {
                    cancel();
                    sendBlocks(viewers, ground, false);
                    return;
                }
                for (int i = 0; i < 24; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = Math.sqrt(random.nextDouble()) * radius;
                    Location at = center.clone().add(Math.cos(angle) * dist, 1.0, Math.sin(angle) * dist);
                    world.spawnParticle(Particle.SQUID_INK, at, 1, 0.1, 0.4, 0.1, 0.01);
                }
                for (Player target : world.getPlayers()) {
                    if (target.equals(caster) || isStunned(target)) continue;
                    GameMode mode = target.getGameMode();
                    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) continue;
                    Location tl = target.getLocation();
                    double dx = tl.getX() - center.getX();
                    double dz = tl.getZ() - center.getZ();
                    if (dx * dx + dz * dz > radius * radius || Math.abs(tl.getY() - center.getY()) > 8) continue;
                    long remaining = Math.min(stunSeconds * 1000L, zoneEnd - now);
                    stun(target, Math.max(remaining, 1000L));
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void stun(Player p, long millis) {
        stunnedUntil.put(p.getUniqueId(), System.currentTimeMillis() + millis);
        int ticks = (int) (millis / 50);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128, false, false, false));
        p.showTitle(Title.title(
                Component.text("STUNNED", NamedTextColor.DARK_RED),
                Component.text("Caught in the black hole", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(300))));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 1.4f);
    }
}
