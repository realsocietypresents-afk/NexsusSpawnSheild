package com.nexusuniverse.spawnshield;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Personal admin bypass, same pattern used elsewhere in this ecosystem (NexusSpawn, NexusRealms,
 * NexusDenyBlocks): players with nexusspawnshield.bypass are exempt from the zone's kill/break/
 * place restrictions by default, and can flip that off/on for themselves with /nexusspawnshield
 * bypass. The flip is in-memory only and resets to the permission default on every restart --
 * this is a convenience toggle for admins who are already trusted, not a substitute for the
 * exempt-players list (which is who gets to bypass even without the permission).
 */
public final class AdminBypassManager {

    private static final String PERMISSION = "nexusspawnshield.bypass";

    /** Players whose personal toggle differs from their permission-based default right now. */
    private final Set<String> flipped = Collections.synchronizedSet(new HashSet<>());

    public boolean isBypassing(Player player) {
        if (player == null) {
            return false;
        }
        boolean permissionDefault = player.hasPermission(PERMISSION);
        boolean isFlipped = flipped.contains(player.getUniqueId().toString());
        return isFlipped != permissionDefault;
    }

    /** @return the resulting bypass state after the toggle */
    public boolean toggle(Player player) {
        String key = player.getUniqueId().toString();
        if (!flipped.remove(key)) {
            flipped.add(key);
        }
        return isBypassing(player);
    }
}
