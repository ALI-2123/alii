package dev.blade.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class BladeSmp extends JavaPlugin {
    private BladeItem items;
    private Abilities abilities;

    BladeItem items() {
        return items;
    }

    Abilities abilities() {
        return abilities;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setup();
        getServer().getPluginManager().registerEvents(new BladeListener(this), this);

        // Shows kills and ability cooldown while you hold the blade
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack held = p.getInventory().getItemInMainHand();
                if (!items.isBlade(held)) continue;
                int level = items.level(held);
                String text = "Blade Lv " + level;
                if (level < BladeItem.MAX_LEVEL) text += "  |  Kills " + items.kills(held) + "/" + items.killsPerUpgrade();
                if (abilities.isStunned(p)) text += "  |  STUNNED";
                else if (level > 0) {
                    long left = abilities.cooldownLeftSeconds(p);
                    text += abilities.isCloaked(p) ? "  |  CLOAKED" : left > 0 ? "  |  Ability in " + left + "s" : "  |  Ability ready";
                }
                p.sendActionBar(Component.text(text, level == 0 ? NamedTextColor.GRAY : NamedTextColor.RED));
            }
        }, 20L, 10L);
        getLogger().info("BladeSMP enabled");
    }

    private void setup() {
        items = new BladeItem(this, Math.max(1, getConfig().getInt("kills-per-upgrade", 5)));
        abilities = new Abilities(this, items);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("/blade give [player] | level <0-3> [player] | reload", NamedTextColor.RED));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                setup();
                sender.sendMessage(Component.text("BladeSMP reloaded", NamedTextColor.GREEN));
            }
            case "give" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(items.create(0, 0));
                sender.sendMessage(Component.text("Gave a Blade to " + target.getName(), NamedTextColor.GREEN));
            }
            case "level" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/blade level <0-3> [player]", NamedTextColor.RED));
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Level must be a number from 0 to 3", NamedTextColor.RED));
                    return true;
                }
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found", NamedTextColor.RED));
                    return true;
                }
                BladeListener.setBlade(items, target, Math.max(0, Math.min(BladeItem.MAX_LEVEL, level)), 0);
                sender.sendMessage(Component.text(target.getName() + "'s blade is now level " + level, NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("give", "level", "reload")) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("level")) {
            out.addAll(List.of("0", "1", "2", "3"));
        } else if ((args.length == 2 && args[0].equalsIgnoreCase("give")) || args.length == 3) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        return out;
    }
}
