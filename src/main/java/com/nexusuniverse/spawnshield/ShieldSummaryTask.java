package com.nexusuniverse.spawnshield;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodic aggregated summary of blocked kill/break/place attempts -- one console line every
 * interval instead of one per attempt, since this zone can be large and busy.
 */
public final class ShieldSummaryTask {

    private final JavaPlugin plugin;
    private final ZoneSettings settings;
    private final BlockedAttemptLog log;

    private BukkitTask task;

    public ShieldSummaryTask(JavaPlugin plugin, ZoneSettings settings, BlockedAttemptLog log) {
        this.plugin = plugin;
        this.settings = settings;
        this.log = log;
    }

    public void restart() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        long periodTicks = settings.heartbeatSummaryIntervalSeconds() * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runOnce, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void runOnce() {
        long blocked = log.drainSinceLastSummary();
        if (blocked <= 0) {
            return;
        }
        plugin.getLogger().info("[NexusSpawnShield] Blocked " + blocked
                + " kill/break/place attempt(s) inside the spawn zone in the last "
                + settings.heartbeatSummaryIntervalSeconds() + "s (total since startup: "
                + log.totalBlocked() + "). See /nexusspawnshield log for detail.");
        if (settings.isHeartbeatEnabled()) {
            HeartbeatBridge.report(plugin, "SECURITY", -1, "Blocked " + blocked
                    + " kill/break/place attempt(s) inside the spawn zone in the last "
                    + settings.heartbeatSummaryIntervalSeconds() + " seconds.");
        }
    }
}
