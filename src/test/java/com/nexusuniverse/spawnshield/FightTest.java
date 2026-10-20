package com.nexusuniverse.spawnshield;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FightTest {

    static int checks;
    static int failures;

    static void expect(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    static final class TestPlugin extends JavaPlugin {
    }

    static final class Guy extends Player {
        final String name;
        final Set<String> perms = new HashSet<>();
        final List<String> messages = new ArrayList<>();
        final Location where;

        Guy(String name, Location at) {
            this.name = name;
            this.where = at;
        }

        @Override public Location getLocation() { return where; }

        @Override public String getName() { return name; }
        @Override public boolean hasPermission(String node) { return perms.contains(node); }
        @Override public void sendMessage(String m) { messages.add(m); }
    }

    static final World WORLD = new World("world");
    static final World NETHER = new World("world_nether");
    static final Location IN = new Location(WORLD, 40, 64, 40);
    static final Location FAR = new Location(WORLD, 50_000, 64, 50_000);

    static <T extends Entity> T at(T e, Location l) {
        e.stubSetLocation(l);
        return e;
    }

    static final class Env {
        final TestPlugin plugin = new TestPlugin();
        final ZoneSettings settings = new ZoneSettings(plugin);
        final ExemptManager exempt = new ExemptManager(plugin);
        final AdminBypassManager bypass = new AdminBypassManager();
        final BlockedAttemptLog log = new BlockedAttemptLog();
        SpawnShieldListener l;

        Env() {
            this(new Object[0]);
        }

        /** pairs: key, value (Boolean / Integer / String) */
        Env(Object... pairs) {
            plugin.getConfig().set("zone.world", "world");
            plugin.getConfig().set("zone.center-x", 0);
            plugin.getConfig().set("zone.center-z", 0);
            plugin.getConfig().set("zone.radius-chunks", 10);
            plugin.getConfig().set("exempt-players", new ArrayList<>(List.of("Boss")));
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                plugin.getConfig().set((String) pairs[i], pairs[i + 1]);
            }
            settings.load();
            exempt.load();
            l = new SpawnShieldListener(settings, exempt, bypass, log);
        }

        /** Fires the hit and returns whether it ended up cancelled. */
        boolean hit(Entity attacker, Entity victim, boolean alreadyCancelled) {
            EntityDamageByEntityEvent ev = new EntityDamageByEntityEvent(attacker, victim, DamageCause.ENTITY_ATTACK);
            ev.setCancelled(alreadyCancelled);
            l.onDamage(ev);
            return ev.isCancelled();
        }

        boolean hit(Entity attacker, Entity victim) {
            return hit(attacker, victim, false);
        }
    }

    public static void main(String[] args) throws Exception {
        rules();
        playersHitting();
        mobsHitting();
        switches();
        settingsAndConfig();
        status();
        shipped();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ the pure rules

    static void rules() {
        // every combination, spelled out: blockKilling, mobsKillable, privileged
        AttackRules.Target[] all = AttackRules.Target.values();
        for (AttackRules.Target t : all) {
            for (boolean priv : new boolean[]{false, true}) {
                expect(AttackRules.playerAttack(false, true, t, priv) == AttackRules.Verdict.LEAVE, "rules: block-killing off -> leave: " + t + " priv=" + priv);
                expect(AttackRules.playerAttack(false, false, t, priv) == AttackRules.Verdict.LEAVE, "rules: block-killing off, mobs off -> leave: " + t);
                expect(AttackRules.playerAttack(true, true, t, true) == AttackRules.Verdict.ALLOW, "rules: privileged always allowed (mobs killable) " + t);
                expect(AttackRules.playerAttack(true, false, t, true) == AttackRules.Verdict.ALLOW, "rules: privileged always allowed (mobs locked) " + t);
            }
        }
        expect(AttackRules.playerAttack(true, true, AttackRules.Target.HOSTILE_MOB, false) == AttackRules.Verdict.ALLOW, "rules: hostile mob is forced killable");
        expect(AttackRules.playerAttack(true, true, AttackRules.Target.OTHER_MOB, false) == AttackRules.Verdict.LEAVE, "rules: other mobs are left alone");
        expect(AttackRules.playerAttack(true, true, AttackRules.Target.PLAYER, false) == AttackRules.Verdict.DENY, "rules: players are protected from players");
        expect(AttackRules.playerAttack(true, true, AttackRules.Target.OTHER, false) == AttackRules.Verdict.DENY, "rules: armor stands and vehicles are protected");
        expect(AttackRules.playerAttack(true, false, AttackRules.Target.HOSTILE_MOB, false) == AttackRules.Verdict.DENY, "rules: mobs locked -> hostile denied");
        expect(AttackRules.playerAttack(true, false, AttackRules.Target.OTHER_MOB, false) == AttackRules.Verdict.DENY, "rules: mobs locked -> other mob denied");
        expect(AttackRules.playerAttack(true, false, AttackRules.Target.PLAYER, false) == AttackRules.Verdict.DENY, "rules: mobs locked -> player denied");
        expect(AttackRules.playerAttack(true, false, AttackRules.Target.OTHER, false) == AttackRules.Verdict.DENY, "rules: mobs locked -> other denied");

        expect(AttackRules.shieldsPlayerFromMob(true, false), "shield: on, not bypassing -> protected");
        expect(!AttackRules.shieldsPlayerFromMob(true, true), "shield: on, bypassing -> not protected");
        expect(!AttackRules.shieldsPlayerFromMob(false, false), "shield: off -> not protected");
        expect(!AttackRules.shieldsPlayerFromMob(false, true), "shield: off and bypassing -> not protected");
    }

    // ------------------------------------------------------------------ players hitting things

    static void playersHitting() {
        Env e = new Env();
        Guy ann = new Guy("Ann", IN);

        Monster zombie = at(new Monster(), IN);
        expect(!e.hit(ann, zombie), "player: hostile mob in the zone can be hit");
        expect(!e.hit(ann, zombie, true), "player: ...even if another plugin cancelled it (forced killable)");
        expect(ann.messages.isEmpty() && e.log.totalBlocked() == 0, "player: no 'protected' message or log entry for a mob hit");

        Cow cow = at(new Cow(), IN);
        expect(!e.hit(ann, cow, false), "player: a cow can be hit");
        expect(e.hit(ann, cow, true), "player: a cow that another plugin protects stays protected (we only stop blocking it)");
        Villager villager = at(new Villager(), IN);
        expect(!e.hit(ann, villager, false) && e.hit(ann, villager, true), "player: villagers are left to whatever else decides");
        Wolf wolf = at(new Wolf(), IN);
        expect(!e.hit(ann, wolf, false), "player: pets are mobs too");

        Guy bob = new Guy("Bob", IN);
        expect(e.hit(ann, bob), "player: another player in the zone is protected from players");
        expect(ann.messages.size() == 1 && ann.messages.get(0).contains("can't kill"), "player: told why: " + ann.messages);
        expect(e.log.totalBlocked() == 1, "player: counted in the log");

        ArmorStand stand = at(new ArmorStand(), IN);
        expect(e.hit(ann, stand), "player: armor stands are protected");
        Minecart cart = at(new Minecart(), IN);
        expect(e.hit(ann, cart), "player: minecarts are protected");
        expect(e.log.totalBlocked() == 3, "player: each protected hit is logged: " + e.log.totalBlocked());

        // arrows count as the player who fired them
        Projectile arrowAtMob = at(new Projectile(ann), IN);
        expect(!e.hit(arrowAtMob, zombie), "player: own arrow at a hostile mob goes through");
        expect(!e.hit(arrowAtMob, zombie, true), "player: ...forced");
        Projectile arrowAtPlayer = at(new Projectile(ann), IN);
        expect(e.hit(arrowAtPlayer, bob), "player: own arrow at another player is stopped");
        expect(e.hit(arrowAtPlayer, stand), "player: own arrow at an armor stand is stopped");

        // privileged: exempt list and bypass permission
        Guy boss = new Guy("boss", IN);
        expect(!e.hit(boss, bob, true), "player: exempt player can hit a player (forced), name matched ignoring case");
        expect(!e.hit(boss, stand, true), "player: exempt player can hit an armor stand (forced)");
        Guy op = new Guy("Op", IN);
        op.perms.add("nexusspawnshield.bypass");
        expect(!e.hit(op, bob, true), "player: bypass permission can hit a player");
        e.bypass.toggle(op);
        expect(e.hit(op, bob), "player: ...until they switch their own bypass off");
        expect(!e.hit(op, zombie, true), "player: ...but mobs are killable for them anyway");

        // outside the zone, other worlds, switched off
        Monster farZombie = at(new Monster(), FAR);
        Guy farAnn = new Guy("FarAnn", FAR);
        Guy farBob = new Guy("FarBob", FAR);
        expect(!e.hit(farAnn, farBob), "outside: nothing is stopped");
        expect(e.hit(farAnn, farBob, true), "outside: and a cancel by someone else stays cancelled");
        expect(e.hit(farAnn, farZombie, true), "outside: a protected mob outside the zone stays protected (not our zone)");
        Guy nether = new Guy("Nether", new Location(NETHER, 40, 64, 40));
        Guy nether2 = new Guy("Nether2", new Location(NETHER, 41, 64, 41));
        expect(!e.hit(nether, nether2), "other world: not our zone");
        // a victim outside, attacker inside: the VICTIM's position decides
        expect(!e.hit(ann, farBob), "position: victim outside the zone -> fine even if the attacker is inside");
        // a victim inside, attacker outside
        expect(e.hit(farAnn, bob), "position: victim inside the zone -> protected even if the attacker is outside");
        // edge of the zone: radius 10 chunks around chunk 0 -> block x 175 is chunk 10, x 176 is chunk 11
        Guy edgeIn = new Guy("EdgeIn", new Location(WORLD, 175, 64, 0));
        Guy edgeOut = new Guy("EdgeOut", new Location(WORLD, 176, 64, 0));
        expect(e.hit(ann, edgeIn), "edge: last block inside is protected");
        expect(!e.hit(ann, edgeOut), "edge: first block outside is not");
        Guy negIn = new Guy("NegIn", new Location(WORLD, -160, 64, 0));
        Guy negOut = new Guy("NegOut", new Location(WORLD, -161, 64, 0));
        expect(e.hit(ann, negIn), "edge: negative side, last block inside (chunk -10)");
        expect(!e.hit(ann, negOut), "edge: negative side, first block outside (chunk -11)");
    }

    // ------------------------------------------------------------------ mobs hitting players

    static void mobsHitting() {
        Env e = new Env();
        Guy ann = new Guy("Ann", IN);
        Monster zombie = at(new Monster(), IN);
        Monster skeleton = at(new Monster(), IN);

        expect(e.hit(zombie, ann), "mob: a hostile mob cannot hurt a player in the zone");
        expect(e.hit(zombie, ann, true), "mob: (already cancelled stays cancelled)");
        Cow cow = at(new Cow(), IN);
        expect(e.hit(cow, ann), "mob: any mob, even a cow or wolf");
        expect(e.hit(at(new Wolf(), IN), ann), "mob: a wolf");
        expect(ann.messages.isEmpty() && e.log.totalBlocked() == 0, "mob: silent, not logged as a blocked player");

        Projectile arrow = at(new Projectile(skeleton), IN);
        expect(e.hit(arrow, ann), "mob: an arrow fired by a mob");
        Projectile fireball = at(new Projectile(zombie), IN);
        expect(e.hit(fireball, ann), "mob: a fireball or skull fired by a mob");
        EvokerFangs fangs = at(new EvokerFangs(zombie), IN);
        expect(e.hit(fangs, ann), "mob: evoker fangs summoned by a mob");

        expect(!e.hit(at(new Projectile(null), IN), ann), "mob: a dispenser arrow (no shooter) is not a mob");
        expect(!e.hit(at(new EvokerFangs(null), IN), ann), "mob: fangs with no owner are not a mob");
        expect(!e.hit(at(new EvokerFangs(new ArmorStand()), IN), ann), "mob: fangs owned by a non-mob");
        expect(!e.hit(at(new Projectile(new ArmorStand()), IN), ann), "mob: a projectile from a non-mob shooter");
        expect(!e.hit(at(new Entity(), IN), ann), "mob: some other thing (TNT, lightning) is not a mob");
        expect(!e.hit(at(new Minecart(), IN), ann), "mob: a minecart is not a mob");
        expect(e.hit(at(new Entity(), IN), ann, true), "mob: ...and a cancel by someone else stays");

        // other targets are not the shield's business
        Villager villager = at(new Villager(), IN);
        expect(!e.hit(zombie, villager), "mob: mob hitting a mob is untouched");
        expect(!e.hit(zombie, at(new ArmorStand(), IN)), "mob: mob hitting an armor stand is untouched");

        // outside the zone
        Guy farAnn = new Guy("FarAnn", FAR);
        expect(!e.hit(zombie, farAnn), "mob: outside the zone a player is not shielded");
        expect(e.hit(zombie, farAnn, true), "mob: ...and someone else's cancel stays");
        Guy nether = new Guy("N", new Location(NETHER, 40, 64, 40));
        expect(!e.hit(zombie, nether), "mob: other world not shielded");

        // exempt list is not the same as bypass
        Guy boss = new Guy("Boss", IN);
        expect(e.hit(zombie, boss), "mob: an exempt player is still shielded from mobs");
        Guy op = new Guy("Op", IN);
        op.perms.add("nexusspawnshield.bypass");
        expect(!e.hit(zombie, op), "mob: a player with bypass on is not shielded (staff can test mobs)");
        e.bypass.toggle(op);
        expect(e.hit(zombie, op), "mob: ...but is once they switch bypass off");
        e.bypass.toggle(op);
        expect(!e.hit(zombie, op), "mob: ...and not again after switching back on");
        Guy bob = new Guy("Bob", IN);
        e.bypass.toggle(bob);
        expect(!e.hit(zombie, bob), "mob: a normal player who turns bypass on for themselves is not shielded");

        // a mob hitting a player OUTSIDE who stands inside? the victim's position decides
        expect(e.hit(at(new Monster(), FAR), ann), "mob: attacker outside, victim inside -> shielded");
    }

    // ------------------------------------------------------------------ the switches

    static void switches() {
        // zone off
        Env off = new Env("settings.enabled", false);
        Guy ann = new Guy("Ann", IN);
        Guy bob = new Guy("Bob", IN);
        Monster zombie = at(new Monster(), IN);
        expect(!off.hit(ann, bob) && !off.hit(zombie, ann), "switch: zone disabled -> nothing is stopped");
        expect(off.hit(ann, bob, true) && off.hit(zombie, ann, true), "switch: zone disabled -> others' cancels stay");

        // block-killing off: no player hits are touched (mob safety is separate)
        Env nk = new Env("settings.block-killing", false);
        expect(!nk.hit(ann, bob), "switch: block-killing off -> players can hit players");
        expect(nk.hit(ann, bob, true), "switch: ...and nothing is forced");
        expect(nk.hit(ann, zombie, true), "switch: ...hostile mobs are not forced either");
        expect(nk.hit(zombie, ann), "switch: ...mobs still cannot hurt players (its own switch)");

        // mobs-killable off: the old behaviour for mobs
        Env mk = new Env("settings.mobs-killable", false);
        expect(mk.hit(ann, zombie), "switch: mobs-killable off -> hostile mob is protected");
        expect(mk.hit(ann, at(new Cow(), IN)), "switch: mobs-killable off -> cow is protected");
        expect(mk.hit(zombie, ann), "switch: ...and mobs still cannot hurt players");

        // players-safe-from-mobs off
        Env ps = new Env("settings.players-safe-from-mobs", false);
        expect(!ps.hit(zombie, ann), "switch: players-safe-from-mobs off -> mobs can hurt players");
        expect(!ps.hit(ann, zombie, true), "switch: ...players can still kill mobs");
        expect(ps.hit(ann, bob), "switch: ...players still cannot hit players");

        // both new switches off: exactly v1.0.2 for players hitting things, nothing for mobs
        Env old = new Env("settings.mobs-killable", false, "settings.players-safe-from-mobs", false);
        expect(old.hit(ann, zombie) && old.hit(ann, bob) && old.hit(ann, at(new Cow(), IN)), "switch: both off -> everything a player hits is protected (v1.0.2)");
        expect(!old.hit(zombie, ann), "switch: both off -> mobs hurt players");
    }

    // ------------------------------------------------------------------ settings

    static void settingsAndConfig() {
        // a config written by v1.0.2 (no new keys): the fix applies without editing it
        Env legacy = new Env("settings.block-killing", true);
        expect(legacy.settings.mobsKillable() && legacy.settings.playersSafeFromMobs(), "config: a v1.0.2 config gets both new behaviours by default");
        expect(legacy.settings.blockKilling(), "config: block-killing kept");
        Env explicit = new Env("settings.mobs-killable", false, "settings.players-safe-from-mobs", false);
        expect(!explicit.settings.mobsKillable() && !explicit.settings.playersSafeFromMobs(), "config: both new switches can be turned off");
        // reload picks up changes
        explicit.plugin.getConfig().set("settings.mobs-killable", true);
        explicit.settings.load();
        expect(explicit.settings.mobsKillable() && !explicit.settings.playersSafeFromMobs(), "config: reload re-reads each switch independently");
    }

    // ------------------------------------------------------------------ /nexusspawnshield status

    static final class Console implements org.bukkit.command.CommandSender {
        final List<String> out = new ArrayList<>();

        @Override public boolean hasPermission(String node) { return true; }
        @Override public void sendMessage(String message) { out.add(message); }
        @Override public void sendMessage(net.kyori.adventure.text.Component component) { out.add(component.toString()); }
        @Override public String getName() { return "Console"; }

        String all() {
            return String.join("\n", out).replaceAll("§.", "");
        }
    }

    static String statusFor(Object... pairs) {
        Env e = new Env(pairs);
        SpawnShieldCommandExecutor ex = new SpawnShieldCommandExecutor(e.plugin, e.settings, e.exempt, e.bypass, e.log,
                new ShieldSummaryTask(e.plugin, e.settings, e.log));
        Console c = new Console();
        ex.onCommand(c, null, "nss", new String[]{"status"});
        return c.all();
    }

    static void status() {
        String on = statusFor();
        expect(on.contains("Mobs: players can kill them"), "status: says mobs are killable: " + on);
        expect(on.contains("Mobs hurting players: prevented"), "status: says mobs are kept off players");
        expect(on.contains("Players hurting other players / armor stands / vehicles: restricted"), "status: says what is still protected");
        expect(on.contains("Breaking/placing in the zone: restricted"), "status: breaking and placing line");
        expect(!on.contains("Killing/breaking/placing"), "status: the old combined line is gone");
        String off = statusFor("settings.mobs-killable", false, "settings.players-safe-from-mobs", false, "settings.block-killing", false);
        expect(off.contains("Mobs: restricted (players cannot hurt them)"), "status: mobs locked: " + off);
        expect(off.contains("Mobs hurting players: not prevented"), "status: mobs allowed to hurt players");
        expect(off.contains("Players hurting other players / armor stands / vehicles: allowed"), "status: player hits allowed");
    }

    // ------------------------------------------------------------------ the config.yml we ship

    static void shipped() throws Exception {
        String path = System.getProperty("shipped.config");
        if (path == null) {
            return;
        }
        TestPlugin plugin = new TestPlugin();
        File f = new File(path);
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        for (String line : Files.readAllLines(f.toPath())) {
            String[] p = line.split("\t", 3);
            if (p.length < 3) {
                continue;
            }
            switch (p[1]) {
                case "b" -> plugin.getConfig().set(p[0], Boolean.parseBoolean(p[2]));
                case "i" -> plugin.getConfig().set(p[0], Integer.parseInt(p[2]));
                case "l" -> plugin.getConfig().set(p[0], new ArrayList<>(p[2].isEmpty() ? List.of() : List.of(p[2].split("\u0001", -1))));
                default -> plugin.getConfig().set(p[0], p[2]);
            }
        }
        expect(y != null, "shipped: parsed");
        ZoneSettings s = new ZoneSettings(plugin);
        s.load();
        expect(s.isEnabled() && s.worldName().equals("world") && s.centerX() == -16649 && s.centerZ() == 9646 && s.radiusChunks() == 1000, "shipped: zone unchanged");
        expect(s.blockKilling() && s.blockBreaking() && s.blockPlacing() && s.blockBuckets() && s.forceAllowInteractions(), "shipped: old switches unchanged");
        expect(s.mobsKillable() && s.playersSafeFromMobs(), "shipped: mobs killable and players safe from mobs");
        ExemptManager ex = new ExemptManager(plugin);
        ex.load();
        expect(ex.isExempt("RealSociety5107"), "shipped: exempt list unchanged");
        // a real fight with the shipped settings, at the real spawn
        Location spawn = new Location(WORLD, -16649, 70, 9646);
        SpawnShieldListener l = new SpawnShieldListener(s, ex, new AdminBypassManager(), new BlockedAttemptLog());
        Guy ann = new Guy("Ann", spawn);
        Monster zombie = at(new Monster(), spawn);
        EntityDamageByEntityEvent kill = new EntityDamageByEntityEvent(ann, zombie, DamageCause.ENTITY_ATTACK);
        kill.setCancelled(true);
        l.onDamage(kill);
        expect(!kill.isCancelled(), "shipped: at real spawn a player can hit a zombie");
        EntityDamageByEntityEvent bite = new EntityDamageByEntityEvent(zombie, ann, DamageCause.ENTITY_ATTACK);
        l.onDamage(bite);
        expect(bite.isCancelled(), "shipped: at real spawn a zombie cannot hurt a player");
    }
}
