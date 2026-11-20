package dev.visual.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CustomHitboxes {
    private static final int RANGE = 24;
    private static final int MAX_ENTITIES = 16;
    private static final int WAVE_SEGMENTS = 16;
    private static final int[][] EDGES = {
        {0, 1}, {0, 2}, {1, 3}, {2, 3}, {4, 5}, {4, 6}, {5, 7}, {6, 7},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private final ClientSettings settings;

    CustomHitboxes(ClientSettings settings) { this.settings = settings; }

    void extract(WorldExtractionContext context) {
        if (!settings.customHitbox) return;
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        double time = context.world().getGameTime() + partial;
        List<LivingEntity> visible = visibleEntities(context);
        try (var ignored = context.worldRenderer().collectPerFrameGizmos()) {
            for (LivingEntity entity : visible) {
                Vec3 position = entity.getPosition(partial);
                AABB box = entity.getBoundingBox().move(position.subtract(entity.position()));
                if (settings.hitboxStyle == ClientSettings.HitboxStyle.VANILLA) {
                    vanillaBox(entity, box, position, partial);
                } else {
                    waveBox(box, time, entity.getId());
                }
            }
        }
    }

    private List<LivingEntity> visibleEntities(WorldExtractionContext context) {
        Vec3 camera = context.camera().position();
        var visible = new ArrayList<LivingEntity>();
        for (var entity : context.world().entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()
                || (entity == context.camera().entity() && !context.camera().isDetached())
                || (settings.hitboxPlayersOnly && !(entity instanceof Player))
                || entity.distanceToSqr(camera) > RANGE * RANGE
                || !context.frustum().isVisible(entity.getBoundingBox().inflate(0.1))) continue;
            visible.add(living);
        }
        visible.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(camera)));
        return visible.subList(0, Math.min(MAX_ENTITIES, visible.size()));
    }

    private void vanillaBox(LivingEntity entity, AABB box, Vec3 position, float partial) {
        Gizmos.cuboid(box, GizmoStyle.stroke(0xFFFFFFFF, settings.hitboxWidth));
        Vec3 eyes = position.add(0, entity.getEyeHeight(), 0);
        AABB eyeLine = new AABB(box.minX, eyes.y - 0.01, box.minZ, box.maxX, eyes.y + 0.01, box.maxZ);
        Gizmos.cuboid(eyeLine, GizmoStyle.stroke(0xFFFF5555, settings.hitboxWidth));
        Gizmos.line(eyes, eyes.add(entity.getViewVector(partial).scale(2)), 0xFF5588FF, settings.hitboxWidth);
    }

    private void waveBox(AABB box, double time, int seed) {
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vec3((i & 1) == 0 ? box.minX : box.maxX,
                (i & 4) == 0 ? box.minY : box.maxY, (i & 2) == 0 ? box.minZ : box.maxZ);
        }
        int color = settings.hitboxColor.resolve(settings);
        double amplitude = settings.hitboxWave / 100.0 * 0.065;
        for (int[] edge : EDGES) {
            waveEdge(corners[edge[0]], corners[edge[1]], time, seed, amplitude, color);
        }
    }

    private void waveEdge(Vec3 from, Vec3 to, double time, int seed, double amplitude, int color) {
        Vec3 direction = to.subtract(from);
        Vec3 side = direction.cross(Math.abs(direction.y) > 0.01 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 other = direction.normalize().cross(side);
        Vec3 previous = from;
        for (int step = 1; step <= WAVE_SEGMENTS; step++) {
            double t = (double) step / WAVE_SEGMENTS;
            double envelope = Math.sin(t * Math.PI);
            double phase = t * Math.PI * 4 - time * 0.22 + seed * 0.7;
            Vec3 point = from.add(direction.scale(t))
                .add(side.scale(Math.sin(phase) * amplitude * envelope))
                .add(other.scale(Math.cos(phase * 0.8) * amplitude * envelope * 0.55));
            waveLine(previous, point, color);
            previous = point;
        }
    }

    private void waveLine(Vec3 from, Vec3 to, int color) {
        if (settings.hitboxGlow) {
            Gizmos.line(from, to, alpha(color, 22), settings.hitboxWidth + 5);
            Gizmos.line(from, to, alpha(color, 48), settings.hitboxWidth + 2.5f);
        }
        Gizmos.line(from, to, alpha(color, 235), settings.hitboxWidth);
    }

    private static int alpha(int color, int opacity) { return (opacity << 24) | (color & 0xFFFFFF); }
}
