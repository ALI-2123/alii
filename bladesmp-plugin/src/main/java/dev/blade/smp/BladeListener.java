package dev.blade.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class BladeListener implements Listener {
    private final BladeSmp plugin;
    private final NamespacedKey givenKey;
    private final Map<UUID, Map<UUID, Long>> recentKills = new HashMap<>();

    BladeListener(BladeSmp plugin) {
        this.plugin = plugin;
        this.givenKey = new NamespacedKey(plugin, "blade_given");
    }

    private BladeItem items() {
        return plugin.items();
    }

    private Abilities abilities() {
        return plugin.abilities();
    }

    /** Replaces the player's blade (or gives one) at the level and kill count given. */
    static void setBlade(BladeItem items, Player p, int level, int kills) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (items.isBlade(inv.getItem(i))) {
                inv.setItem(i, items.create(level, kills));
                return;
            }
        }
        inv.addItem(items.create(level, kills));
    }

    // ---------- everyone starts with a black blade ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.getPersistentDataContainer().has(givenKey, PersistentDataType.BYTE)) {
            p.getPersistentDataContainer().set(givenKey, PersistentDataType.BYTE, (byte) 1);
            p.getInventory().addItem(items().create(0, 0));
            p.sendMessage(Component.text("You were given the Blade. Win fights to awaken it.", NamedTextColor.DARK_RED));
        }
        sendResourcePack(p);
    }

    private void sendResourcePack(Player p) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        byte[] hash = null;
        if (sha1 != null && sha1.matches("[0-9a-fA-F]{40}")) {
            hash = new byte[20];
            for (int i = 0; i < 20; i++) hash[i] = (byte) Integer.parseInt(sha1.substring(i * 2, i * 2 + 2), 16);
        }
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
        p.setResourcePack(UUID.nameUUIDFromBytes("bladesmp-pack".getBytes()), url, hash,
                Component.text("Blade textures: black, blood, and the black and red fade"), required);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        abilities().forget(e.getPlayer());
    }

    // ---------- right click: abilities ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (abilities().isStunned(p)) {
            e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!items().isBlade(held)) return;
        abilities().use(p, items().level(held));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && abilities().isStunned(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!abilities().isStunned(p)) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location stay = from.clone();
            stay.setYaw(to.getYaw());
            stay.setPitch(to.getPitch());
            e.setTo(stay);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!abilities().isStunned(e.getPlayer())) return;
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            e.setCancelled(true);
        }
    }

    // ---------- kills upgrade the blade ----------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();

        // The blade never drops: it comes back with you
        Iterator<ItemStack> drops = e.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            if (items().isBlade(drop)) {
                drops.remove();
                e.getItemsToKeep().add(drop);
            }
        }

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        int cooldown = plugin.getConfig().getInt("same-victim-cooldown-seconds", 600);
        Map<UUID, Long> seen = recentKills.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long last = seen.get(victim.getUniqueId());
        if (last != null && now - last < cooldown * 1000L) {
            killer.sendMessage(Component.text("That kill doesn't count yet. Same player again too soon.", NamedTextColor.GRAY));
            return;
        }
        seen.put(victim.getUniqueId(), now);

        PlayerInventory inv = killer.getInventory();
        int slot = -1;
        if (items().isBlade(inv.getItemInMainHand())) slot = inv.getHeldItemSlot();
        else for (int i = 0; i < inv.getSize(); i++) if (items().isBlade(inv.getItem(i))) { slot = i; break; }
        if (slot < 0) return;

        ItemStack blade = inv.getItem(slot);
        int level = items().level(blade);
        if (level >= BladeItem.MAX_LEVEL) return;
        int kills = items().kills(blade) + 1;
        if (kills >= items().killsPerUpgrade()) {
            level++;
            kills = 0;
            killer.getWorld().playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            killer.showTitle(Title.title(
                    Component.text("BLADE UPGRADED", NamedTextColor.DARK_RED),
                    Component.text("Level " + level + (level == BladeItem.MAX_LEVEL ? " (MAX)" : ""), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        inv.setItem(slot, items().create(level, kills));
    }

    // ---------- the blade is soulbound ----------

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (items().isBlade(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Component.text("The Blade can't be dropped.", NamedTextColor.GRAY));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) return;
        boolean clickedTop = e.getClickedInventory() == top;
        boolean cursorBlade = items().isBlade(e.getCursor());
        boolean currentBlade = items().isBlade(e.getCurrentItem());
        boolean hotbarBlade = false;
        if (e.getClick() == ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p) {
            hotbarBlade = items().isBlade(p.getInventory().getItem(e.getHotbarButton()));
        }
        if ((clickedTop && (cursorBlade || hotbarBlade)) || (!clickedTop && e.isShiftClick() && currentBlade)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!items().isBlade(e.getOldCursor())) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < topSize && e.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
