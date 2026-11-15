package dev.solarslayers.slayer;

/**
 * Slayer run timing from chat: {@code SLAYER QUEST STARTED!} → {@code SLAYER BOSS SPAWNED!} → {@code NICE! SLAYER BOSS
 * SLAIN!} (or {@code SLAYER QUEST FAILED!}). Times are wall-clock milliseconds passed in by the caller, so the logic can
 * be checked without a game.
 *
 * <p>Bosses per hour counts only active time: each gap between two slayer events (and the time since the last one)
 * counts up to {@link #IDLE_CAP_MS}, so a break away from slayers does not drag the rate down.
 */
public final class SlayerTimer {
    public static final SlayerTimer INSTANCE = new SlayerTimer();

    /** Longest gap between slayer events that still counts as slaying. */
    public static final long IDLE_CAP_MS = 10 * 60_000L;

    // current quest
    private long questStart = -1;
    private long spawnAt = -1;

    // last finished boss
    private long lastSpawnMs = -1, lastKillMs = -1;

    // session
    private int kills;
    private long totalBossMs;   // sum of quest-start → kill over timed kills
    private int timedKills;
    private long bestMs = -1;
    private long activeMs;
    private long lastEvent = -1;

    private SlayerTimer() {}

    /** A fresh timer, for checks. */
    static SlayerTimer create() {
        return new SlayerTimer();
    }

    private void touch(long now) {
        if (lastEvent >= 0) activeMs += Math.min(Math.max(0, now - lastEvent), IDLE_CAP_MS);
        lastEvent = now;
    }

    public void onQuestStart(long now) {
        touch(now);
        questStart = now;
        spawnAt = -1;
    }

    public void onBossSpawn(long now) {
        touch(now);
        spawnAt = now;
    }

    public void onBossKill(long now) {
        touch(now);
        kills++;
        if (questStart >= 0) {
            long total = now - questStart;
            totalBossMs += total;
            timedKills++;
            if (bestMs < 0 || total < bestMs) bestMs = total;
            lastSpawnMs = spawnAt >= 0 ? spawnAt - questStart : -1;
            lastKillMs = spawnAt >= 0 ? now - spawnAt : -1;
        } else {
            // quest started before the game was opened: only the fight is known, if even that
            lastSpawnMs = -1;
            lastKillMs = spawnAt >= 0 ? now - spawnAt : -1;
        }
        questStart = -1;
        spawnAt = -1;
    }

    public void onQuestFail(long now) {
        touch(now);
        questStart = -1;
        spawnAt = -1;
    }

    public void resetSession() {
        kills = 0;
        totalBossMs = 0;
        timedKills = 0;
        bestMs = -1;
        activeMs = 0;
        lastEvent = -1;
        lastSpawnMs = lastKillMs = -1;
    }

    // ---- read side ----

    public boolean inQuest() {
        return questStart >= 0 || spawnAt >= 0;
    }

    public boolean bossAlive() {
        return spawnAt >= 0;
    }

    /** Time since the quest started, or -1. */
    public long questElapsed(long now) {
        return questStart < 0 ? -1 : now - questStart;
    }

    /** Time from quest start to boss spawn for the current quest, or -1. */
    public long spawnTook() {
        return questStart < 0 || spawnAt < 0 ? -1 : spawnAt - questStart;
    }

    /** Time since the boss spawned, or -1. */
    public long fightElapsed(long now) {
        return spawnAt < 0 ? -1 : now - spawnAt;
    }

    public long lastSpawnMs() { return lastSpawnMs; }
    public long lastKillMs() { return lastKillMs; }
    public long lastTotalMs() { return lastSpawnMs < 0 || lastKillMs < 0 ? -1 : lastSpawnMs + lastKillMs; }
    public int kills() { return kills; }
    public long bestMs() { return bestMs; }

    /** Average quest-start → kill time, or -1 before the first timed kill. */
    public long averageMs() {
        return timedKills == 0 ? -1 : totalBossMs / timedKills;
    }

    /** Active slaying time this session, including the time since the last event (capped). */
    public long activeMs(long now) {
        if (lastEvent < 0) return 0;
        return activeMs + Math.min(Math.max(0, now - lastEvent), IDLE_CAP_MS);
    }

    /** Bosses killed per hour of active time, or NaN before the first kill. */
    public double perHour(long now) {
        long active = activeMs(now);
        if (kills == 0 || active <= 0) return Double.NaN;
        return kills * 3_600_000.0 / active;
    }

    /** {@code 75_000} → {@code 1:15}; {@code 3_725_000} → {@code 1:02:05}. */
    public static String clock(long ms) {
        if (ms < 0) return "–";
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
