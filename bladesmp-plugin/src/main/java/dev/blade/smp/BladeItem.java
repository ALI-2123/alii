package dev.blade.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Builds and reads the Blade. Level 0 is the starting black blade, level 3 is the max black and red fade blade. */
public final class BladeItem {
    public static final int MAX_LEVEL = 3;
    private static final int[] SHARPNESS = {0, 3, 4, 5};
    private static final TextColor[] COLORS = {
            NamedTextColor.DARK_GRAY, TextColor.color(0x8B0000), TextColor.color(0xB80000), TextColor.color(0xFF2A1A)};

    private final NamespacedKey levelKey;
    private final NamespacedKey killsKey;
    private final int killsPerUpgrade;

    public BladeItem(JavaPlugin plugin, int killsPerUpgrade) {
        this.levelKey = new NamespacedKey(plugin, "blade_level");
        this.killsKey = new NamespacedKey(plugin, "blade_kills");
        this.killsPerUpgrade = killsPerUpgrade;
    }

    public int killsPerUpgrade() {
        return killsPerUpgrade;
    }

    public boolean isBlade(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(levelKey, PersistentDataType.INTEGER);
    }

    public int level(ItemStack item) {
        if (!isBlade(item)) return 0;
        Integer value = item.getItemMeta().getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    public int kills(ItemStack item) {
        if (!isBlade(item)) return 0;
        Integer value = item.getItemMeta().getPersistentDataContainer().get(killsKey, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack create(int level, int kills) {
        level = Math.max(0, Math.min(MAX_LEVEL, level));
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        // Texture: black, then a little blood, then more, then the black and red fade (needs the resource pack)
        meta.setItemModel(NamespacedKey.fromString("blade:blade_" + level));
        meta.displayName(line("Blade", COLORS[level]).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        if (level > 0) meta.addEnchant(Enchantment.SHARPNESS, SHARPNESS[level], true);
        meta.setEnchantmentGlintOverride(level == MAX_LEVEL);

        List<Component> lore = new ArrayList<>();
        lore.add(line("Level " + level + (level == MAX_LEVEL ? " (MAX)" : ""), NamedTextColor.GRAY));
        if (level < MAX_LEVEL) lore.add(line("Kills: " + kills + "/" + killsPerUpgrade, NamedTextColor.DARK_RED));
        if (level > 0) lore.add(line("Sharpness " + SHARPNESS[level], NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(line(level == 0 ? "Get " + killsPerUpgrade + " kills to awaken it." : "Right click: Phantom Cloak", NamedTextColor.DARK_GRAY));
        if (level >= 2) lore.add(line("While cloaked: right click to throw mini blades", NamedTextColor.DARK_GRAY));
        if (level >= 3) lore.add(line("Black hole stuns everyone nearby", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, kills);
        item.setItemMeta(meta);
        return item;
    }
}
