package dev.visual.client;

import kotlin.Unit;
import sweetie.evaware.luma.Luma;
import sweetie.evaware.luma.minecraft.MinecraftRenderPlatform;
import sweetie.evaware.luma.shader.GlslLibrary;
import sweetie.evaware.renderutil.RenderUtil;

import java.util.ArrayList;
import java.util.List;

public final class VisualDraw {
    private static final List<Shape> SHAPES = new ArrayList<>();
    private static final List<Runnable> OVERLAYS = new ArrayList<>();

    private VisualDraw() {}

    static void initialize() {
        Luma.platform = MinecraftRenderPlatform.INSTANCE;
        GlslLibrary.INSTANCE.register("scissor", "assets/luma-renderer/shaders/include/scissor.glsl").attach();
    }

    static void rounded(float x, float y, float width, float height, float radius, int color) {
        SHAPES.add(new Shape(x, y, width, height, radius, color));
    }

    static void panel(float x, float y, float width, float height, float radius, int border, int fill) {
        rounded(x, y, width, height, radius, border);
        rounded(x + 1, y + 1, width - 2, height - 2, radius - 1, fill);
    }

    static void overlay(Runnable draw) {
        OVERLAYS.add(draw);
    }

    public static void flush() {
        if (SHAPES.isEmpty() && OVERLAYS.isEmpty() && !IslandBlur.INSTANCE.hasPending()
            && !MarceLogo.INSTANCE.hasPending() && !MsdfDraw.INSTANCE.hasPending()) {
            return;
        }
        try {
            RenderUtil.INSTANCE.renderFrame(() -> {
                flushLayer();
                for (Runnable overlay : OVERLAYS) {
                    overlay.run();
                    flushLayer();
                }
                return Unit.INSTANCE;
            });
        } finally {
            SHAPES.clear();
            OVERLAYS.clear();
        }
    }

    private static void flushLayer() {
        IslandBlur.INSTANCE.flush();
        MenuGradient.INSTANCE.flush();
        for (Shape shape : SHAPES) {
            RenderUtil.INSTANCE.getROUNDED_RECT()
                .color(shape.color)
                .radius(shape.radius)
                .draw(shape.x, shape.y, shape.width, shape.height);
        }
        SHAPES.clear();
        RenderUtil.INSTANCE.flushAll();
        MenuColorField.INSTANCE.flush();
        MarceLogo.INSTANCE.flush();
        AlbumArt.INSTANCE.flush();
        MsdfDraw.INSTANCE.flush();
    }

    public static void close() {
        SHAPES.clear();
        OVERLAYS.clear();
        IslandBlur.INSTANCE.close();
        MarceLogo.INSTANCE.close();
        AlbumArt.INSTANCE.close();
        MenuColorField.INSTANCE.close();
        MenuGradient.INSTANCE.close();
        MsdfDraw.INSTANCE.close();
        RenderUtil.INSTANCE.close();
    }

    private record Shape(float x, float y, float width, float height, float radius, int color) {}
}
