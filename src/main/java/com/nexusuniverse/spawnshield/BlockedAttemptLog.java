package com.nexusuniverse.spawnshield;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory-only record of blocked kill/break/place attempts inside the zone -- same convention
 * as NexusMobShield's BlockedKillLog: a capped recent-attempts ring buffer plus a periodic
 * aggregated counter, no per-attempt disk/console logging (this zone can be large and busy).
 */
public final class BlockedAttemptLog {

    private static final int MAX_RECENT = 200;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static final class Entry {
        final long atMillis;
        final String kind;
        final String playerName;
        final String world;
        final int x;
        final int y;
        final int z;

        Entry(String kind, String playerName, String world, int x, int y, int z) {
            this.atMillis = System.currentTimeMillis();
            this.kind = kind;
            this.playerName = playerName;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String format() {
            String time = TIME_FORMAT.format(Instant.ofEpochMilli(atMillis));
            return "[" + time + "] " + kind + " blocked -- " + playerName + " at " + world + " "
                    + x + "," + y + "," + z;
        }
    }

    private final Deque<Entry> recent = new ArrayDeque<>();
    private final AtomicLong totalBlocked = new AtomicLong();
    private final AtomicLong sinceLastSummary = new AtomicLong();

    public synchronized void record(String kind, String playerName, String world, int x, int y, int z) {
        recent.addLast(new Entry(kind, playerName, world, x, y, z));
        while (recent.size() > MAX_RECENT) {
            recent.removeFirst();
        }
        totalBlocked.incrementAndGet();
        sinceLastSummary.incrementAndGet();
    }

    public synchronized List<Entry> recent(int n) {
        List<Entry> all = new ArrayList<>(recent);
        int from = Math.max(0, all.size() - n);
        return all.subList(from, all.size());
    }

    public long totalBlocked() {
        return totalBlocked.get();
    }

    public long drainSinceLastSummary() {
        return sinceLastSummary.getAndSet(0);
    }
}
