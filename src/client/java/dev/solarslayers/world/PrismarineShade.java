package dev.solarslayers.world;

import dev.solarslayers.config.SlayersConfig;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Darkens prismarine blocks by {@link SlayersConfig#prismarineShade} percent: 0 leaves them as they are, 100 makes them
 * black. The mod's {@code minecraft:block/*prismarine*} model overrides give every face tint index 0 (vanilla prismarine
 * has none), and this colour provider supplies the darkening for it. Placed blocks only; items are unchanged.
 */
public final class PrismarineShade {
    public static final Block[] BLOCKS = {
            Blocks.PRISMARINE, Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_SLAB, Blocks.PRISMARINE_WALL,
            Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICK_SLAB,
            Blocks.DARK_PRISMARINE, Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB,
    };

    /** Ticks the setting must stay unchanged before chunks are rebuilt, so dragging the slider rebuilds once. */
    private static final int SETTLE_TICKS = 5;

    private static int applied = -1;
    private static int pending = -1;
    private static int stableFor;

    private PrismarineShade() {}

    public static void register() {
        // 26.3: one tint source per tint index; the overrides only use index 0
        BlockColorRegistry.register(List.of(state -> color(SlayersConfig.get().prismarineShade)), BLOCKS);
    }

    /** Opaque grey for a shade percentage: 0 → white (no change), 100 → black. */
    public static int color(int shadePercent) {
        int p = Math.max(0, Math.min(100, shadePercent));
        int v = Math.round(255 * (100 - p) / 100f);
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** Rebuilds the loaded chunks once the shade setting has changed and settled, so placed blocks pick it up. */
    public static void tick(Minecraft mc) {
        int now = SlayersConfig.get().prismarineShade;
        if (applied < 0) applied = now; // first tick: chunks are built with the current value already
        if (now == applied) {
            pending = -1;
            return;
        }
        if (now != pending) {
            pending = now;
            stableFor = 0;
            return;
        }
        if (++stableFor < SETTLE_TICKS || mc.level == null) return;
        applied = now;
        pending = -1;
        mc.levelExtractor.allChanged();
    }
}
