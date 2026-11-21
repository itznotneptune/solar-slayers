package dev.solarslayers.world;

import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.slayer.SidebarState;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Darkens the bright, pale blocks of world 1 (the Grave Tyrant's slayer world) by {@link SlayersConfig#graveShade}
 * percent: sand and the sandstone family, birch wood, quartz, calcite and white wool, picked from a 2026-11-21 block
 * scan of that world. The same blocks elsewhere (the hub has sand and sandstone slabs) are left as they are.
 *
 * <p>World 1 is the {@code minecraft:slayer} dimension with a sidebar line naming the Grave Tyrant. The models come
 * from {@code tools/gen_shade.py}, which gives these blocks' faces tint index 0 for this colour provider.
 */
public final class GraveShade {
    public static final Block[] BLOCKS = {
            Blocks.SAND, Blocks.RED_SAND,
            Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL,
            Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE_SLAB, Blocks.CHISELED_SANDSTONE,
            Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB,
            Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_WALL,
            Blocks.CUT_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE_SLAB, Blocks.CHISELED_RED_SANDSTONE,
            Blocks.SMOOTH_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.SMOOTH_RED_SANDSTONE_SLAB,
            Blocks.BIRCH_PLANKS, Blocks.BIRCH_SLAB, Blocks.BIRCH_STAIRS, Blocks.BIRCH_FENCE, Blocks.BIRCH_TRAPDOOR,
            Blocks.SMOOTH_QUARTZ, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.CALCITE, Blocks.WHITE_WOOL,
    };

    /** Ticks the shade must stay unchanged before chunks are rebuilt, so dragging the slider rebuilds once. */
    private static final int SETTLE_TICKS = 5;

    /** The shade the colour provider uses right now: the setting in world 1, 0 anywhere else. */
    private static volatile int shade;
    private static boolean inGrave;
    private static ClientLevel seenLevel;
    private static int pending = -1;
    private static int stableFor;

    private GraveShade() {}

    public static void register() {
        ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> PrismarineShade.color(shade), BLOCKS);
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != seenLevel) {
            seenLevel = level;
            inGrave = false;
        }
        if (level == null || !level.dimension().toString().endsWith("minecraft:slayer]")) {
            inGrave = false;
        } else {
            SidebarState s = SidebarState.INSTANCE;
            // keep the last answer while the sidebar is missing (loading, menus); change it once it says otherwise
            if (s.inSlayer) inGrave = s.graveTyrant;
        }

        int want = inGrave ? SlayersConfig.get().graveShade : 0;
        if (want == shade) {
            pending = -1;
            return;
        }
        if (want != pending) {
            pending = want;
            stableFor = 0;
            return;
        }
        if (++stableFor < SETTLE_TICKS || level == null) return;
        shade = want;
        pending = -1;
        mc.levelRenderer.allChanged();
    }
}
