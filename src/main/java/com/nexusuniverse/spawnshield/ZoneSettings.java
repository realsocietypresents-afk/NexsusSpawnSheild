package com.nexusuniverse.spawnshield;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The protection zone and the on/off switches for what it enforces. Distance is Chebyshev
 * ("chessboard") distance in chunks from the center chunk -- the same convention this ecosystem's
 * other zone-based plugins (NexusSpawn, NexusSeasons) already use, so a 1000-chunk radius means a
 * 2001x2001 chunk square centered on the configured point.
 */
public final class ZoneSettings {

    private final JavaPlugin plugin;

    private volatile boolean enabled = true;
    private volatile String worldName = "world";
    private volatile int centerX = -16649;
    private volatile int centerZ = 9646;
    private volatile int radiusChunks = 1000;

    private volatile boolean blockKilling = true;
    private volatile boolean mobsKillable = true;
    private volatile boolean playersSafeFromMobs = true;
    private volatile boolean blockBreaking = true;
    private volatile boolean blockPlacing = true;
    private volatile boolean blockBuckets = true;
    private volatile boolean forceAllowInteractions = true;

    private volatile boolean heartbeatEnabled = true;
    private volatile int heartbeatSummaryIntervalSeconds = 60;

    public ZoneSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        var cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("settings.enabled", true);
        this.worldName = cfg.getString("zone.world", "world");
        this.centerX = cfg.getInt("zone.center-x", -16649);
        this.centerZ = cfg.getInt("zone.center-z", 9646);
        this.radiusChunks = Math.max(0, cfg.getInt("zone.radius-chunks", 1000));

        this.blockKilling = cfg.getBoolean("settings.block-killing", true);
        this.mobsKillable = cfg.getBoolean("settings.mobs-killable", true);
        this.playersSafeFromMobs = cfg.getBoolean("settings.players-safe-from-mobs", true);
        this.blockBreaking = cfg.getBoolean("settings.block-breaking", true);
        this.blockPlacing = cfg.getBoolean("settings.block-placing", true);
        this.blockBuckets = cfg.getBoolean("settings.block-buckets", true);
        this.forceAllowInteractions = cfg.getBoolean("settings.force-allow-interactions", true);

        this.heartbeatEnabled = cfg.getBoolean("heartbeat.enabled", true);
        this.heartbeatSummaryIntervalSeconds = Math.max(5, cfg.getInt("heartbeat.summary-interval-seconds", 60));
    }

    /** Chebyshev distance in chunks from the configured center, on the configured world. */
    public boolean isInZone(Location location) {
        if (!enabled || location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        return chebyshevChunkDistance(location) <= radiusChunks;
    }

    /**
     * Chebyshev chunk distance from the configured center, ignoring world/enabled entirely --
     * used for diagnostics (/nexusspawnshield status) so a wrong world name or a too-small radius
     * shows up as an obvious number instead of a silent "nothing happened."
     */
    public int chebyshevChunkDistance(Location location) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        int centerChunkX = centerX >> 4;
        int centerChunkZ = centerZ >> 4;
        return Math.max(Math.abs(chunkX - centerChunkX), Math.abs(chunkZ - centerChunkZ));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("settings.enabled", enabled);
        plugin.saveConfig();
    }

    public String worldName() {
        return worldName;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int radiusChunks() {
        return radiusChunks;
    }

    public boolean blockKilling() {
        return blockKilling;
    }

    /** Players may hurt and kill mobs in the zone (hostile ones are forced killable). */
    public boolean mobsKillable() {
        return mobsKillable;
    }

    /** Mobs (and what they fire) cannot damage players in the zone. */
    public boolean playersSafeFromMobs() {
        return playersSafeFromMobs;
    }

    public boolean blockBreaking() {
        return blockBreaking;
    }

    public boolean blockPlacing() {
        return blockPlacing;
    }

    public boolean blockBuckets() {
        return blockBuckets;
    }

    public boolean forceAllowInteractions() {
        return forceAllowInteractions;
    }

    public boolean isHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    public int heartbeatSummaryIntervalSeconds() {
        return heartbeatSummaryIntervalSeconds;
    }
}
