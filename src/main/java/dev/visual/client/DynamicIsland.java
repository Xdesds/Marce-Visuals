package dev.visual.client;

import dev.visual.client.MarceLogo.Layer;
import dev.visual.client.MsdfDraw.Icon;
import dev.visual.client.ClientSettings.MusicLayout;
import dev.visual.client.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.awt.image.BufferedImage;
import java.util.Objects;

final class DynamicIsland {
    static final float COMPACT_HEIGHT = 22;
    static final float LOGO_WIDTH = 15;

    private static final int MUTED = 0xFFADA9BA;
    private static final float EXPANDED_HEIGHT = 37;
    private static final float MIN_WIDTH = 106;
    private static final float METRIC_SIZE = 9;
    private static final float LOGO_GAP = 6;
    private static final float METRIC_ICON_SIZE = 10;
    private static final float ICON_AND_GAP = 13;
    private static final float SIDE_PADDING = 8;
    private static final float TRACK_CARD_HEIGHT = 20;
    private static final MsdfDraw TYPE = MsdfDraw.INSTANCE;

    private final ClientSettings settings;
    private final WindowsMusic music;
    private final IslandWelcome welcome = new IslandWelcome();
    private final IslandDock dock = new IslandDock();
    private float width;
    private float height = COMPACT_HEIGHT;
    private long lastFrame;
    private long introEnded;
    private long messageUntil;
    private String message = "";
    private Row row;
    private float rowOpacity;
    private float musicBlend;
    private MusicLayout trackLayout;
    private float musicMotion;
    private float musicBeat;
    private float wavePhase;

    DynamicIsland(ClientSettings settings, WindowsMusic music) {
        this.settings = settings;
        this.music = music;
    }

    void joined() {
        reset();
        if (settings.island) {
            welcome.queue();
        }
    }

    void reset() {
        lastFrame = 0;
        introEnded = 0;
        messageUntil = 0;
        message = "";
        row = null;
        rowOpacity = 0;
        musicBlend = 0;
        musicMotion = 0;
        musicBeat = 0;
        wavePhase = 0;
        welcome.reset();
        dock.reset();
    }

    void showMessage(String text) {
        if (settings.feedback && settings.island) {
            message = text;
            messageUntil = System.nanoTime() + 2_400_000_000L;
        }
    }

    void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!settings.island || client.player == null || client.options.hideGui) {
            lastFrame = 0;
            dock.reset();
            if (!settings.island) {
                welcome.reset();
            }
            return;
        }

        int accent = settings.accentColor();
        String fps = settings.fps ? client.getFps() + " FPS" : "";
        PlayerInfo info = client.getConnection() == null ? null
            : client.getConnection().getPlayerInfo(client.player.getUUID());
        String ping = "";
        if (settings.ping) {
            if (info != null) {
                ping = Math.max(0, info.getLatency()) + " ms";
            } else if (client.hasSingleplayerServer()) {
                ping = "0 ms";
            }
        }
        float fpsWidth = TYPE.width(fps, METRIC_SIZE);
        float pingWidth = TYPE.width(ping, METRIC_SIZE);
        float metricWidth = Math.max(fpsWidth, pingWidth) + ICON_AND_GAP;
        float metricOffset = LOGO_WIDTH * 0.5f + LOGO_GAP + metricWidth * 0.5f;
        float compactWidth = Math.max(MIN_WIDTH, 2 * (metricOffset + metricWidth * 0.5f + SIDE_PADDING));

        long now = System.nanoTime();
        double elapsed = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000.0, 0.05);
        boolean firstFrame = lastFrame == 0;
        lastFrame = now;

        // The settings screen hides the vanilla HUD, including the boss bars.
        boolean inSettings = client.screen instanceof VisualScreen;
        if (inSettings) {
            dock.reset();
        }
        float targetTop = inSettings ? 0 : bossClearance(client, graphics.guiHeight());
        float top = dock.update(targetTop, elapsed);
        float stretch = dock.stretch();
        float contentOpacity = targetTop == 0 ? 1 : Math.clamp((top - targetTop + 8) / 7, 0, 1);

        Row desiredTrack = musicRow();
        if (desiredTrack != null && settings.musicLayout == MusicLayout.INLINE) {
            welcome.reset();
        }
        var intro = welcome.render(graphics.guiWidth(), top, now, client.player.getScoreboardName(),
            client.screen == null && client.getOverlay() == null, compactWidth, accent, contentOpacity, stretch);
        if (intro != null) {
            width = intro.width();
            height = intro.height();
            introEnded = now;
            return;
        }

        if (desiredTrack != null) {
            updateRow(desiredTrack, (float) elapsed);
        }
        if (musicBlend == 0) {
            trackLayout = settings.musicLayout;
        }
        float blendStep = (float) elapsed * 3.7f;
        boolean showTrack = desiredTrack != null && trackLayout == settings.musicLayout;
        musicBlend = Math.clamp(musicBlend + (showTrack ? blendStep : -blendStep), 0, 1);
        if (musicBlend == 0 && desiredTrack == null) {
            row = null;
            rowOpacity = 0;
        }

        float inline = trackLayout == MusicLayout.INLINE ? musicBlend : 0;
        float separate = trackLayout == MusicLayout.SEPARATE ? musicBlend : 0;
        float maximumRowWidth = Math.max(36, Math.min(144, graphics.guiWidth() - 58));
        String rowText = row == null ? "" : TYPE.ellipsize(row.text(), METRIC_SIZE, maximumRowWidth);
        float rowWidth = TYPE.width(rowText, METRIC_SIZE);
        float mediaGap = row != null && row.cover() != null ? 20 : ICON_AND_GAP;
        float mediaWidth = Math.max(88, rowWidth + mediaGap + 24);
        boolean statusVisible = settings.feedback && now < messageUntil && separate == 0
            && !(desiredTrack != null && settings.musicLayout == MusicLayout.SEPARATE);
        float statusWidth = statusVisible ? TYPE.width(message, METRIC_SIZE) : 0;
        float normalWidth = statusVisible ? Math.max(compactWidth + 10, statusWidth + 46) : compactWidth;
        float normalHeight = statusVisible ? EXPANDED_HEIGHT : COMPACT_HEIGHT;
        float layoutBlend = smooth(inline);
        float targetWidth = normalWidth + (mediaWidth - normalWidth) * layoutBlend;
        float targetHeight = normalHeight + (COMPACT_HEIGHT - normalHeight) * layoutBlend;
        if (firstFrame) {
            width = compactWidth;
            height = COMPACT_HEIGHT;
        }
        float amount = (float) (1 - Math.exp(-18 * elapsed));
        width += (targetWidth - width) * amount;
        height += (targetHeight - height) * amount;

        float centerX = graphics.guiWidth() * 0.5f;
        WindowsMusic.Signal signal = desiredTrack == null ? WindowsMusic.Signal.SILENT : music.signal(now);
        float intensity = settings.waveIntensity / 100f;
        musicMotion = followAudio(musicMotion, Math.min(2.1f, signal.bass() * intensity), elapsed, 105, 19);
        musicBeat = followAudio(musicBeat, Math.min(1.55f, signal.beat() * intensity), elapsed, 165, 33);
        wavePhase = (float) ((wavePhase + elapsed * (4 + 5 * musicMotion + 7 * musicBeat)) % (2 * Math.PI));
        drawShell(centerX - width * 0.5f, top, width, height, stretch,
            musicMotion * inline, musicBeat * inline, wavePhase);
        float centerY = top + COMPACT_HEIGHT * 0.5f;
        float normalOpacity = contentOpacity * (1 - smooth(Math.min(1, inline * 2)));
        MarceLogo.INSTANCE.layer(Layer.FULL, centerX, centerY, LOGO_WIDTH, 0, normalOpacity, accent);
        float introFade = introEnded == 0 ? 1 : Math.clamp((now - introEnded) / 150_000_000f, 0, 1);
        float metricsOpacity = normalOpacity * introFade * Math.clamp((width - compactWidth + 10) / 8, 0, 1);
        if (metricsOpacity > 0) {
            if (!fps.isEmpty()) {
                float right = centerX + metricOffset - (fpsWidth + ICON_AND_GAP) * 0.5f;
                TYPE.text(fps, right, centerY, METRIC_SIZE, withOpacity(MUTED, metricsOpacity));
                TYPE.icon(Icon.SPEED, right + fpsWidth + ICON_AND_GAP - METRIC_ICON_SIZE * 0.5f,
                    centerY, METRIC_ICON_SIZE, withOpacity(accent, metricsOpacity));
            }
            if (!ping.isEmpty()) {
                float left = centerX - metricOffset - (pingWidth + ICON_AND_GAP) * 0.5f;
                TYPE.icon(Icon.WIFI, left + METRIC_ICON_SIZE * 0.5f, centerY,
                    METRIC_ICON_SIZE, withOpacity(accent, metricsOpacity));
                TYPE.text(ping, left + ICON_AND_GAP, centerY, METRIC_SIZE, withOpacity(MUTED, metricsOpacity));
            }
        }

        if (statusVisible && normalOpacity > 0 && width > statusWidth + 32) {
            float opacity = normalOpacity * Math.clamp((height - 30) / 6, 0, 1)
                * Math.clamp((messageUntil - now) / 150_000_000f, 0, 1);
            float left = centerX - (statusWidth + ICON_AND_GAP) * 0.5f;
            TYPE.icon(Icon.NOTIFICATIONS, left + 4.5f, top + 28.5f, 10, withOpacity(accent, opacity));
            TYPE.text(message, left + ICON_AND_GAP, top + 28.5f, METRIC_SIZE, withOpacity(MUTED, opacity));
        }

        if (row == null) {
            return;
        }
        float inlineOpacity = contentOpacity * rowOpacity * smooth(Math.max(0, inline * 2 - 1));
        if (inlineOpacity > 0 && width > rowWidth + mediaGap + 16) {
            drawTrack(rowText, centerX, centerY, rowWidth, mediaGap, accent, inlineOpacity);
        }
        float separateOpacity = contentOpacity * rowOpacity * smooth(separate);
        if (separateOpacity > 0) {
            float cardTop = top + height + 6 - 2 * (1 - separate);
            float radius = TRACK_CARD_HEIGHT * 0.5f;
            float waveScale = inSettings ? 0.35f : 0.63f;
            IslandBlur.INSTANCE.queue(centerX - mediaWidth * 0.5f, cardTop, mediaWidth, TRACK_CARD_HEIGHT,
                radius, radius, waveScale * musicMotion, waveScale * musicBeat, wavePhase, separateOpacity);
            drawTrack(rowText, centerX, cardTop + radius, rowWidth, mediaGap, accent, separateOpacity);
        }
    }

    private void drawTrack(String text, float centerX, float centerY, float textWidth, float gap, int accent, float opacity) {
        float left = centerX - (textWidth + gap) * 0.5f;
        if (row.cover() == null) {
            TYPE.icon(Icon.MUSIC, left + 4.5f, centerY, 10, withOpacity(accent, opacity));
        } else {
            AlbumArt.INSTANCE.draw(row.cover(), left, centerY - 7.5f, 15, opacity);
        }
        TYPE.text(text, left + gap, centerY, METRIC_SIZE, withOpacity(MUTED, opacity));
    }

    private Row musicRow() {
        WindowsMusic.Track track = settings.music ? music.playing() : null;
        if (track == null) {
            return null;
        }
        String text = track.artist().isBlank() ? track.title() : track.title() + " · " + track.artist();
        return new Row(text, track.cover());
    }

    private void updateRow(Row desired, float seconds) {
        if (Objects.equals(row, desired)) {
            rowOpacity = Math.min(1, rowOpacity + seconds * 6);
            return;
        }
        rowOpacity = Math.max(0, rowOpacity - seconds * 10);
        if (row == null || rowOpacity == 0) {
            row = desired;
            rowOpacity = 0;
        }
    }

    private static float bossClearance(Minecraft client, int guiHeight) {
        int count = ((BossHealthOverlayAccessor) client.gui.getBossOverlay()).marce$getEvents().size();
        if (count == 0) {
            return 0;
        }

        // Vanilla starts at y=12, uses a 5 px bar, and advances 19 px until one third of the screen.
        int visible = Math.min(count, Math.max(1, Math.ceilDiv(guiHeight / 3 - 12, 19)));
        return 24 + (visible - 1) * 19;
    }

    static void drawShell(float x, float y, float width, float height, float stretch, float motion, float beat, float phase) {
        float attachment = Math.clamp(y / 16, 0, 1);
        attachment = attachment * attachment * (3 - 2 * attachment);
        float bleed = 2 * (1 - attachment);
        float rim = Math.min(14, height * 0.5f) * attachment;
        float pinch = 2 * stretch;
        IslandBlur.INSTANCE.queue(x + pinch, y - bleed, width - 2 * pinch,
            height + bleed + 2.5f * stretch, rim, height * 0.5f, motion, beat, phase, 1);
    }

    static int withOpacity(int color, float opacity) {
        return (Math.round(255 * opacity) << 24) | (color & 0xFFFFFF);
    }

    private static float smooth(float progress) {
        return progress * progress * (3 - 2 * progress);
    }

    private static float followAudio(float value, float target, double elapsed, float attack, float release) {
        float rate = target > value ? attack : release;
        return target + (value - target) * (float) Math.exp(-rate * elapsed);
    }

    private record Row(String text, BufferedImage cover) {}
}
