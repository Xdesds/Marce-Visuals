package dev.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HitEffects {
    private static final int CONFIRMATION_TICKS = 8;
    private static final int RETENTION_TICKS = 25;
    private static ClientSettings settings;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final HitChain CHAIN = new HitChain();

    private HitEffects() {}

    static void initialize(ClientSettings config) { settings = config; }

    static void attack(Player player, LivingEntity target) {
        if (!settings.hitColor || player.isSpectator() || !target.isAlive()) return;
        long tick = player.level().getGameTime();
        PENDING.put(target.getUUID(), new Pending(target.getId(), tick, target.hurtTime, target.getHealth(), isCritical(player)));
    }

    private static boolean isCritical(Player player) {
        return player.getAttackStrengthScale(0.5f) > 0.9f && player.fallDistance > 0
            && !player.onGround() && !player.onClimbable() && !player.isInWater()
            && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger() && !player.isSprinting();
    }

    static void tick(Minecraft client) {
        if (client.level == null || client.player == null || !settings.hitColor) {
            reset();
            return;
        }
        long tick = client.level.getGameTime();
        PENDING.entrySet().removeIf(entry -> confirmHit(client, entry.getKey(), entry.getValue(), tick));
        ACTIVE.entrySet().removeIf(entry -> tick - entry.getValue().tick > RETENTION_TICKS || tick < entry.getValue().tick);
    }

    private static boolean confirmHit(Minecraft client, UUID id, Pending pending, long tick) {
        var entity = client.level.getEntity(pending.id);
        if (!(entity instanceof LivingEntity target) || !entity.getUUID().equals(id)
            || tick - pending.tick > CONFIRMATION_TICKS || tick < pending.tick) return true;

        // Сам взмах не считается попаданием: ждём обновления урона у цели.
        int expectedHurt = Math.max(0, pending.hurtTime - (int) (tick - pending.tick));
        if (target.hurtTime <= expectedHurt && target.getHealth() >= pending.health) return false;

        HitChain.Kind kind = CHAIN.record(id, tick, pending.critical, settings.comboHits);
        var shader = switch (kind) {
            case CRITICAL -> settings.criticalShader;
            case COMBO -> settings.comboShader;
            case NORMAL -> settings.normalShader;
        };
        var color = switch (kind) {
            case CRITICAL -> settings.criticalColor;
            case COMBO -> settings.comboColor;
            case NORMAL -> settings.normalColor;
        };
        ACTIVE.put(id, new Active(tick, shader, color.resolve(settings)));
        return true;
    }

    public static Snapshot snapshot(LivingEntity entity, float partialTick) {
        if (settings == null || !settings.hitColor) return null;
        Active hit = ACTIVE.get(entity.getUUID());
        if (hit == null) return null;
        float age = (entity.level().getGameTime() - hit.tick + partialTick) * 50f / settings.hitDuration;
        if (age < 0 || age >= 1) return null;
        float fade = (1 - age) * (1 - age);
        int alpha = Math.round(255 * settings.hitIntensity / 100f * fade);
        return new Snapshot(hit.shader, (alpha << 24) | (hit.color & 0xFFFFFF));
    }

    public static boolean replacesRedOverlay(LivingEntity entity) {
        // После короткого эффекта не должна возвращаться обычная красная вспышка.
        return settings != null && settings.hitColor && ACTIVE.containsKey(entity.getUUID());
    }

    static void reset() {
        PENDING.clear();
        ACTIVE.clear();
        CHAIN.reset();
    }

    public record Snapshot(ClientSettings.HitShader shader, int color) {}
    private record Pending(int id, long tick, int hurtTime, float health, boolean critical) {}
    private record Active(long tick, ClientSettings.HitShader shader, int color) {}
}
