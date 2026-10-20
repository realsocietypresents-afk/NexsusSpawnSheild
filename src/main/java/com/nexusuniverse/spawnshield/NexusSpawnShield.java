package com.nexusuniverse.spawnshield;

import org.bukkit.plugin.java.JavaPlugin;

public final class NexusSpawnShield extends JavaPlugin {

    private ZoneSettings settings;
    private ExemptManager exemptManager;
    private AdminBypassManager bypassManager;
    private BlockedAttemptLog log;
    private ShieldSummaryTask summaryTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.settings = new ZoneSettings(this);
        settings.load();

        this.exemptManager = new ExemptManager(this);
        exemptManager.load();

        this.bypassManager = new AdminBypassManager();
        this.log = new BlockedAttemptLog();
        this.summaryTask = new ShieldSummaryTask(this, settings, log);

        getServer().getPluginManager().registerEvents(
                new SpawnShieldListener(settings, exemptManager, bypassManager, log), this);

        SpawnShieldCommandExecutor executor = new SpawnShieldCommandExecutor(
                this, settings, exemptManager, bypassManager, log, summaryTask);
        getCommand("nexusspawnshield").setExecutor(executor);

        summaryTask.restart();

        getLogger().warning("NexusSpawnShield enabled -- overriding a " + settings.radiusChunks()
                + "-chunk zone centered on " + settings.centerX() + "," + settings.centerZ()
                + " in world \"" + settings.worldName() + "\". Functional blocks always work there;"
                + " killing/breaking/placing is restricted to: " + String.join(", ", exemptManager.list()) + ".");
    }

    @Override
    public void onDisable() {
        if (summaryTask != null) {
            summaryTask.stop();
        }
        getLogger().info("NexusSpawnShield disabled.");
    }
}
