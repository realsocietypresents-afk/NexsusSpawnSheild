package com.nexusuniverse.spawnshield;

import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * The override itself.
 *
 * v1.0.2 change: everything here now runs at MONITOR, not HIGHEST, and every handler forces the
 * FINAL state both ways (ALLOW for what should succeed, DENY for what shouldn't) instead of only
 * acting on one side. Why: HIGHEST wasn't enough in practice -- if another plugin's own
 * protection listener (NexusRealms' included) is ALSO registered at HIGHEST, Bukkit does not
 * guarantee which of two same-priority listeners runs last, so this plugin could lose that race
 * depending on plugin load order. MONITOR is the one priority tier that is guaranteed to run
 * after every HIGHEST (and lower) listener, full stop -- so as long as nothing else on the server
 * is also gaming MONITOR to fight back, this plugin's decision is the one that actually sticks.
 * (Yes, this breaks the polite Bukkit convention that MONITOR shouldn't mutate events -- that's
 * the whole point of an "override": it needs to genuinely be the last word.)
 */
public final class SpawnShieldListener implements Listener {

    private final ZoneSettings settings;
    private final ExemptManager exemptManager;
    private final AdminBypassManager bypassManager;
    private final BlockedAttemptLog log;

    public SpawnShieldListener(ZoneSettings settings, ExemptManager exemptManager,
                                AdminBypassManager bypassManager, BlockedAttemptLog log) {
        this.settings = settings;
        this.exemptManager = exemptManager;
        this.bypassManager = bypassManager;
        this.log = log;
    }

    private boolean isPrivileged(Player player) {
        return player != null && (exemptManager.isExempt(player.getName()) || bypassManager.isBypassing(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (!settings.forceAllowInteractions()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.hasBlock()) {
            return;
        }
        if (!settings.isInZone(event.getClickedBlock().getLocation())) {
            return;
        }
        // Force-allow, for everyone -- doors, trapdoors, fence gates, buttons, levers, pressure
        // plates, chests, trapped chests, barrels, ender chests, shulker boxes, furnaces,
        // crafting tables, anvils, every other right-click-to-use block. This overrides any
        // earlier DENY from another plugin on this same event, no matter what priority it used.
        event.setUseInteractedBlock(Event.Result.ALLOW);
        event.setUseItemInHand(Event.Result.ALLOW);
        event.setCancelled(false);
    }

    /**
     * Defense in depth: if something else cancels the actual inventory open rather than (or in
     * addition to) the interact event, this un-cancels it too, as long as the container's block
     * is inside the zone. A player-inventory or crafting-table-style GUI has no block location,
     * so this only ever touches real placed containers.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!settings.forceAllowInteractions() || event.getInventory() == null) {
            return;
        }
        Location location = event.getInventory().getLocation();
        if (location == null || !settings.isInZone(location)) {
            return;
        }
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        if (!settings.blockBreaking() || event.getBlock() == null) {
            return;
        }
        if (!settings.isInZone(event.getBlock().getLocation())) {
            return;
        }
        Player player = event.getPlayer();
        if (isPrivileged(player)) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        recordAndNotify("break", player, event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        if (!settings.blockPlacing() || event.getBlock() == null) {
            return;
        }
        if (!settings.isInZone(event.getBlock().getLocation())) {
            return;
        }
        Player player = event.getPlayer();
        if (isPrivileged(player)) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        recordAndNotify("place", player, event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!settings.blockBuckets() || event.getBlockClicked() == null) {
            return;
        }
        if (!settings.isInZone(event.getBlockClicked().getLocation())) {
            return;
        }
        Player player = event.getPlayer();
        if (isPrivileged(player)) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        recordAndNotify("place", player, event.getBlockClicked().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!settings.blockBuckets() || event.getBlockClicked() == null) {
            return;
        }
        if (!settings.isInZone(event.getBlockClicked().getLocation())) {
            return;
        }
        Player player = event.getPlayer();
        if (isPrivileged(player)) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        recordAndNotify("break", player, event.getBlockClicked().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        if (damaged == null) {
            return;
        }
        Location location = damaged.getLocation();
        if (!settings.isInZone(location)) {
            return;
        }
        Player attacker = resolveAttackingPlayer(event.getDamager());
        if (attacker == null) {
            // not a player doing the hitting: is a mob about to hurt a player?
            if (damaged instanceof Player victim && fromMob(event.getDamager())
                    && AttackRules.shieldsPlayerFromMob(settings.playersSafeFromMobs(), bypassManager.isBypassing(victim))) {
                event.setCancelled(true);
            }
            return;
        }
        switch (AttackRules.playerAttack(settings.blockKilling(), settings.mobsKillable(), classify(damaged), isPrivileged(attacker))) {
            case ALLOW:
                event.setCancelled(false);
                break;
            case DENY:
                event.setCancelled(true);
                recordAndNotify("kill", attacker, location);
                break;
            default:
                break;
        }
    }

    static AttackRules.Target classify(Entity damaged) {
        if (damaged instanceof Player) {
            return AttackRules.Target.PLAYER;
        }
        if (damaged instanceof Mob) {
            return damaged instanceof Enemy ? AttackRules.Target.HOSTILE_MOB : AttackRules.Target.OTHER_MOB;
        }
        return AttackRules.Target.OTHER;
    }

    /** A mob hitting directly, or something a mob fired or summoned (arrow, fireball, wither skull, evoker fangs). */
    static boolean fromMob(Entity damager) {
        if (damager instanceof Mob) {
            return true;
        }
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Mob;
        }
        if (damager instanceof EvokerFangs fangs) {
            return fangs.getOwner() instanceof Mob;
        }
        return false;
    }

    /** A player attacking directly, or a projectile (arrow, trident, snowball, ...) they fired. */
    private Player resolveAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void recordAndNotify(String kind, Player player, Location location) {
        String name = player != null ? player.getName() : "unknown";
        String world = location.getWorld() != null ? location.getWorld().getName() : "?";
        log.record(kind, name, world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (player != null) {
            player.sendMessage("You can't " + kind + " here -- this area is protected.");
        }
    }
}
