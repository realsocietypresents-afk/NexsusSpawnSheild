package com.nexusuniverse.spawnshield;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SpawnShieldCommandExecutor implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ZoneSettings settings;
    private final ExemptManager exemptManager;
    private final AdminBypassManager bypassManager;
    private final BlockedAttemptLog log;
    private final ShieldSummaryTask summaryTask;

    public SpawnShieldCommandExecutor(JavaPlugin plugin, ZoneSettings settings, ExemptManager exemptManager,
                                       AdminBypassManager bypassManager, BlockedAttemptLog log,
                                       ShieldSummaryTask summaryTask) {
        this.plugin = plugin;
        this.settings = settings;
        this.exemptManager = exemptManager;
        this.bypassManager = bypassManager;
        this.log = log;
        this.summaryTask = summaryTask;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status": {
                showStatus(sender);
                return true;
            }
            case "toggle": {
                settings.setEnabled(!settings.isEnabled());
                sender.sendMessage("§aSpawn zone protection is now " + (settings.isEnabled() ? "§aON" : "§cOFF") + "§a.");
                return true;
            }
            case "bypass": {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly a player can toggle their own bypass.");
                    return true;
                }
                boolean nowBypassing = bypassManager.toggle(player);
                sender.sendMessage(nowBypassing
                        ? "§eYou can now kill/break/place inside the spawn zone."
                        : "§7You're back to normal spawn-zone restrictions.");
                return true;
            }
            case "exempt": {
                return handleExempt(sender, args);
            }
            case "log": {
                int n = 15;
                if (args.length >= 2) {
                    try {
                        n = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        // keep default
                    }
                }
                List<BlockedAttemptLog.Entry> entries = log.recent(n);
                if (entries.isEmpty()) {
                    sender.sendMessage("§7Nothing blocked yet.");
                    return true;
                }
                sender.sendMessage("§6Last " + entries.size() + " blocked attempt(s) (of "
                        + log.totalBlocked() + " total since startup):");
                for (BlockedAttemptLog.Entry e : entries) {
                    sender.sendMessage("§7- §f" + e.format());
                }
                return true;
            }
            case "reload": {
                settings.load();
                exemptManager.load();
                summaryTask.restart();
                sender.sendMessage("§aNexusSpawnShield config reloaded.");
                return true;
            }
            default: {
                showStatus(sender);
                return true;
            }
        }
    }

    private boolean handleExempt(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexusspawnshield exempt <add|remove|list> [name]");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "add": {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /nexusspawnshield exempt add <name>");
                    return true;
                }
                String name = args[2];
                if (exemptManager.add(name)) {
                    sender.sendMessage("§a" + name + " can now kill/break/place inside the spawn zone.");
                    plugin.getLogger().info("[NexusSpawnShield] " + sender.getName() + " added " + name + " to the spawn-zone exempt list.");
                } else {
                    sender.sendMessage("§7" + name + " is already on the exempt list.");
                }
                return true;
            }
            case "remove": {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /nexusspawnshield exempt remove <name>");
                    return true;
                }
                String name = args[2];
                if (exemptManager.remove(name)) {
                    sender.sendMessage("§a" + name + " is no longer exempt in the spawn zone.");
                    plugin.getLogger().info("[NexusSpawnShield] " + sender.getName() + " removed " + name + " from the spawn-zone exempt list.");
                } else {
                    sender.sendMessage("§7" + name + " wasn't on the exempt list.");
                }
                return true;
            }
            case "list": {
                List<String> names = exemptManager.list();
                if (names.isEmpty()) {
                    sender.sendMessage("§7No one is on the spawn-zone exempt list.");
                } else {
                    sender.sendMessage("§6Exempt in the spawn zone: §f" + String.join(", ", names));
                }
                return true;
            }
            default: {
                sender.sendMessage("§cUsage: /nexusspawnshield exempt <add|remove|list> [name]");
                return true;
            }
        }
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("§6NexusSpawnShield status:");
        sender.sendMessage("§7Protection: " + (settings.isEnabled() ? "§aON" : "§cOFF"));
        sender.sendMessage("§7Zone: §fworld \"" + settings.worldName() + "\", center " + settings.centerX()
                + "," + settings.centerZ() + ", radius " + settings.radiusChunks() + " chunks");
        sender.sendMessage("§7Functional-block interactions: §aalways allowed in the zone");
        sender.sendMessage("§7Breaking/placing in the zone: §crestricted§7 (exceptions below)");
        sender.sendMessage("§7Mobs: " + (settings.mobsKillable()
                ? "§aplayers can kill them§7 (hostile mobs are forced killable)"
                : "§crestricted§7 (players cannot hurt them)"));
        sender.sendMessage("§7Players hurting other players / armor stands / vehicles: "
                + (settings.blockKilling() ? "§crestricted" : "§aallowed"));
        sender.sendMessage("§7Mobs hurting players: " + (settings.playersSafeFromMobs() ? "§aprevented" : "§cnot prevented"));
        sender.sendMessage("§7Exempt players: §f" + String.join(", ", exemptManager.list()));
        if (sender instanceof Player player) {
            if (bypassManager.isBypassing(player)) {
                sender.sendMessage("§eYour personal bypass is currently ON.");
            }
            var loc = player.getLocation();
            String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
            boolean sameWorld = world.equalsIgnoreCase(settings.worldName());
            int distance = settings.chebyshevChunkDistance(loc);
            boolean inZone = sameWorld && distance <= settings.radiusChunks();
            sender.sendMessage("§7You are at: §f" + world + " " + loc.getBlockX() + "," + loc.getBlockY()
                    + "," + loc.getBlockZ() + " §7-- " + (sameWorld ? distance + " chunk(s) from center" : "DIFFERENT WORLD than the zone"));
            sender.sendMessage(inZone
                    ? "§aYou are currently INSIDE the protected zone."
                    : "§cYou are currently OUTSIDE the protected zone" + (sameWorld ? "" : " (world name mismatch -- check zone.world in config.yml)") + ".");
        }
        sender.sendMessage("§7Blocked total since startup: §f" + log.totalBlocked());
    }
}
