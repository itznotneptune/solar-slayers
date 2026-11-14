package dev.solarslayers.slayer;

import dev.solarslayers.config.SlayersConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/** Title + ding when the player hits slayer mobs without an active quest. Never changes the attack. */
public final class QuestWarning {
    private static long lastShown;

    private QuestWarning() {}

    public static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, EntityHitResult hit) {
        if (level.isClientSide()) check(target);
        return InteractionResult.PASS;
    }

    private static void check(Entity target) {
        SlayersConfig cfg = SlayersConfig.get();
        SidebarState sb = SidebarState.INSTANCE;
        if (!cfg.questWarning || !sb.inSlayer || !sb.noQuest) return;
        if (!(target instanceof LivingEntity le) || !le.hasCustomName() || le.getCustomName() == null) return;
        if (!le.getCustomName().getString().contains("❤")) return; // slayer mobs carry an HP nametag
        long now = System.currentTimeMillis();
        if (now - lastShown < cfg.questWarningCooldown * 1000L) return;
        lastShown = now;
        show();
    }

    public static void show() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(5, 40, 10);
        mc.gui.setTitle(Component.literal("You don't have an active slayer quest!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        mc.gui.setSubtitle(Component.literal("Start one with ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("/slayer").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        if (SlayersConfig.get().questWarningSound) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 1.0f));
        }
    }
}
