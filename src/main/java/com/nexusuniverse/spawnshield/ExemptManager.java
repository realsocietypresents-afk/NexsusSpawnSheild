package com.nexusuniverse.spawnshield;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The player names allowed to kill/break/place inside the zone -- everyone else is restricted to
 * the functional-block interactions that this plugin force-allows for all players. Backed by
 * config.yml's "exempt-players" list and kept in sync with it (add/remove persist immediately).
 */
public final class ExemptManager {

    private final JavaPlugin plugin;
    private final Set<String> names = Collections.synchronizedSet(new LinkedHashSet<>());

    public ExemptManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        names.clear();
        for (String name : plugin.getConfig().getStringList("exempt-players")) {
            if (name != null && !name.isBlank()) {
                names.add(name.trim().toLowerCase());
            }
        }
    }

    public boolean isExempt(String playerName) {
        return playerName != null && names.contains(playerName.trim().toLowerCase());
    }

    /** @return true if the name was newly added (false if it was already on the list) */
    public boolean add(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        boolean added = names.add(playerName.trim().toLowerCase());
        if (added) {
            persist(playerName.trim());
        }
        return added;
    }

    /** @return true if the name was removed (false if it wasn't on the list) */
    public boolean remove(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        boolean removed = names.remove(playerName.trim().toLowerCase());
        if (removed) {
            persistRemoval(playerName.trim());
        }
        return removed;
    }

    public List<String> list() {
        synchronized (names) {
            return new ArrayList<>(names);
        }
    }

    private void persist(String displayName) {
        List<String> current = new ArrayList<>(plugin.getConfig().getStringList("exempt-players"));
        current.add(displayName);
        plugin.getConfig().set("exempt-players", current);
        plugin.saveConfig();
    }

    private void persistRemoval(String displayName) {
        List<String> current = new ArrayList<>(plugin.getConfig().getStringList("exempt-players"));
        current.removeIf(existing -> existing != null && existing.equalsIgnoreCase(displayName));
        plugin.getConfig().set("exempt-players", current);
        plugin.saveConfig();
    }
}
