package com.nexusuniverse.spawnshield;

/** The fighting rules inside the zone, with no server code in them so they can be tested on their own. */
public final class AttackRules {

    private AttackRules() {
    }

    /** What the thing being hit is, as far as the rules care. */
    public enum Target {
        /** Another player. */
        PLAYER,
        /** A mob that is hostile (zombie, skeleton, creeper, slime, ghast, phantom, boss...). */
        HOSTILE_MOB,
        /** Any other mob: animals, villagers, golems, pets... */
        OTHER_MOB,
        /** Not a mob and not a player: armor stands, boats, minecarts, end crystals... */
        OTHER
    }

    /** What to do with a player's attack. */
    public enum Verdict {
        /** Not our business: whatever the server and other plugins decided stands. */
        LEAVE,
        /** Force it to go through, even if another plugin said no. */
        ALLOW,
        /** Cancel it. */
        DENY
    }

    /**
     * A player hits something inside the zone.
     *
     * @param blockKilling       the {@code settings.block-killing} switch (off = this plugin never interferes)
     * @param mobsKillable       the {@code settings.mobs-killable} switch
     * @param attackerPrivileged on the exempt list, or has bypass switched on
     */
    public static Verdict playerAttack(boolean blockKilling, boolean mobsKillable, Target target, boolean attackerPrivileged) {
        if (!blockKilling) {
            return Verdict.LEAVE;
        }
        if (attackerPrivileged) {
            return Verdict.ALLOW;
        }
        if (mobsKillable) {
            if (target == Target.HOSTILE_MOB) {
                return Verdict.ALLOW;
            }
            if (target == Target.OTHER_MOB) {
                return Verdict.LEAVE;
            }
        }
        return Verdict.DENY;
    }

    /**
     * A mob (or something a mob fired) is about to hurt a player inside the zone: should it be stopped?
     *
     * @param enabled          the {@code settings.players-safe-from-mobs} switch
     * @param victimBypassing  the player has switched their personal bypass on (staff testing)
     */
    public static boolean shieldsPlayerFromMob(boolean enabled, boolean victimBypassing) {
        return enabled && !victimBypassing;
    }
}
