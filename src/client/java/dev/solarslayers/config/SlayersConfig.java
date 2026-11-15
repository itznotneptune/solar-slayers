package dev.solarslayers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.solarslayers.SlayersClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything the player can change, saved to config/solarslayers.json. */
public final class SlayersConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("solarslayers.json");
    private static SlayersConfig instance = new SlayersConfig();

    // Alerts
    public boolean questWarning = true;
    public boolean questWarningSound = true;
    public int questWarningCooldown = 5;

    // Highlights
    public boolean bossHighlight = true;
    public int bossColor = 0xFFFF3B3B;
    public boolean onlyMyBoss = true;
    /** Opacity (percent) of the colour drawn over highlighted mobs. */
    public int highlightOpacity = 75;
    public boolean minibossHighlight = true;
    public int minibossColor = 0xFFFFA62B;
    public boolean minionHighlight = true;
    public int minionColor = 0xFFB86BFF;
    public boolean blazeMinionHighlight = true;
    public int blazeMinionColor = 0xFFB86BFF;

    // HUD
    public boolean hudOnlyInSlayer = true;
    public boolean progressHud = true;
    public boolean pityHud = true;
    public boolean trackerHud = true;
    public int xpPerKill = 5;
    public HudPos progressPos = new HudPos(6, 70, 1f);
    public HudPos pityPos = new HudPos(6, 135, 1f);
    public HudPos trackerPos = new HudPos(6, 180, 1f);

    // Chat
    public boolean slainChatLink = true;

    // World
    /** How dark prismarine blocks are drawn, percent: 0 = unchanged, 100 = black. */
    public int prismarineShade = 0;

    // Upgrade tracker
    public boolean shopTracker = true;
    public List<TrackedItem> tracked = new ArrayList<>();

    // Learned data
    public List<String> minibossNames = new ArrayList<>(List.of("Moonlit Hunter"));
    /** Base names (no HP) of boss minions, e.g. the Moonfang Alpha's pups. */
    public List<String> minionNames = new ArrayList<>(List.of("Moonfang"));
    /** Slayer name (normalised) → level, read from the sidebar while in that slayer's area. */
    public Map<String, Integer> slayerLevels = new HashMap<>();
    /** Exact essence last seen in the Upgrade Shop, kept up to date from kill messages. */
    public double essence = Double.NaN;

    public static SlayersConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                SlayersConfig c = GSON.fromJson(r, SlayersConfig.class);
                if (c != null) instance = c;
            } catch (Exception e) {
                SlayersClient.LOG.warn("Could not read {}; using defaults", FILE, e);
            }
        }
        instance.fix();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException | RuntimeException e) {
            // never let a settings problem take the mod (or the game) down
            SlayersClient.LOG.warn("Could not save {}", FILE, e);
        }
    }

    /** Fills anything an older or hand-edited file left out. */
    private void fix() {
        SlayersConfig d = new SlayersConfig();
        if (progressPos == null) progressPos = d.progressPos;
        if (pityPos == null) pityPos = d.pityPos;
        if (trackerPos == null) trackerPos = d.trackerPos;
        if (tracked == null) tracked = new ArrayList<>();
        if (minibossNames == null) minibossNames = d.minibossNames;
        if (minionNames == null) minionNames = d.minionNames;
        if (slayerLevels == null) slayerLevels = new HashMap<>();
        questWarningCooldown = Math.max(1, Math.min(60, questWarningCooldown));
        xpPerKill = Math.max(1, Math.min(100, xpPerKill));
        highlightOpacity = Math.max(20, Math.min(100, highlightOpacity));
        prismarineShade = Math.max(0, Math.min(100, prismarineShade));
    }

    public static final class HudPos {
        public int x;
        public int y;
        public float scale;

        public HudPos(int x, int y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }

    public static final class TrackedItem {
        public String name;
        public List<Requirement> requirements = new ArrayList<>();
    }

    public static final class Requirement {
        /** OWN: one specific item with no count, e.g. the previous armour tier or a boss drop. */
        public enum Kind { LEVEL, ESSENCE, ITEM, OWN, OTHER }

        public Kind kind;
        /** Slayer name for LEVEL, item name for ITEM, the full line for OTHER. */
        public String target;
        public long need;
        /** The ✔/✘ the shop showed when the item was tracked. */
        public boolean metInShop;
    }
}
