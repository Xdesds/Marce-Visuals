package dev.visual.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ClientSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger("Marce Visual");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("visual-client.json");

    boolean island = true;
    boolean fps = true;
    boolean ping = true;
    boolean music = true;
    boolean feedback = true;
    Accent accent = Accent.VIOLET;
    Integer customAccent;
    MusicLayout musicLayout = MusicLayout.INLINE;
    int waveIntensity = 100;
    boolean guiGradient = true;
    boolean guiAnimation = true;
    boolean guiGrain = true;
    boolean guiJelly = true;
    boolean discordRpc = true;
    boolean hitColor = false;
    HitShader normalShader = HitShader.PULSE;
    HitShader criticalShader = HitShader.CRYSTAL;
    HitShader comboShader = HitShader.SHOCK;
    EffectColor normalColor = EffectColor.THEME;
    EffectColor criticalColor = EffectColor.SKY;
    EffectColor comboColor = EffectColor.ROSE;
    int hitDuration = 400;
    int hitIntensity = 85;
    int comboHits = 3;
    boolean customHitbox = false;
    HitboxStyle hitboxStyle = HitboxStyle.WAVE;
    EffectColor hitboxColor = EffectColor.THEME;
    int hitboxWidth = 2;
    int hitboxWave = 50;
    boolean hitboxGlow = true;
    boolean hitboxPlayersOnly = false;

    enum HitShader {
        PULSE("Pulse"), AURORA("Aurora"), SHOCK("Shock"), CRYSTAL("Crystal");
        final String label;
        HitShader(String label) { this.label = label; }
    }

    enum HitboxStyle {
        VANILLA("Vanilla"), WAVE("Wave");
        final String label;
        HitboxStyle(String label) { this.label = label; }
    }

    enum EffectColor {
        THEME("Theme", 0), SKY("Sky", 0xFF88C8FF), MINT("Mint", 0xFF83E9C0),
        EMBER("Ember", 0xFFFF925D), ROSE("Rose", 0xFFFF79B0);
        final String label;
        final int color;
        EffectColor(String label, int color) { this.label = label; this.color = color; }
        int resolve(ClientSettings settings) { return this == THEME ? settings.accentColor() : color; }
    }

    int accentColor() {
        return customAccent == null ? accent.color : 0xFF000000 | (customAccent & 0xFFFFFF);
    }

    void selectAccent(Accent preset) {
        accent = preset;
        customAccent = null;
    }

    enum MusicLayout {
        INLINE("Inline"), SEPARATE("Separate");

        final String label;

        MusicLayout(String label) {
            this.label = label;
        }
    }

    enum Accent {
        VIOLET("Violet", 0xFFB5A3FF),
        MINT("Mint", 0xFF83E9C0),
        SKY("Sky", 0xFF88C8FF);

        final String label;
        final int color;

        Accent(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    static ClientSettings load() {
        if (Files.isRegularFile(FILE)) {
            try (var reader = Files.newBufferedReader(FILE)) {
                var settings = GSON.fromJson(reader, ClientSettings.class);
                if (settings != null) {
                    if (settings.accent == null) {
                        settings.accent = Accent.VIOLET;
                    }
                    if (settings.musicLayout == null) {
                        settings.musicLayout = MusicLayout.INLINE;
                    }
                    settings.waveIntensity = Math.clamp(settings.waveIntensity, 0, 150);
                    if (settings.normalShader == null) settings.normalShader = HitShader.PULSE;
                    if (settings.criticalShader == null) settings.criticalShader = HitShader.CRYSTAL;
                    if (settings.comboShader == null) settings.comboShader = HitShader.SHOCK;
                    if (settings.normalColor == null) settings.normalColor = EffectColor.THEME;
                    if (settings.criticalColor == null) settings.criticalColor = EffectColor.SKY;
                    if (settings.comboColor == null) settings.comboColor = EffectColor.ROSE;
                    if (settings.hitboxStyle == null) settings.hitboxStyle = HitboxStyle.WAVE;
                    if (settings.hitboxColor == null) settings.hitboxColor = EffectColor.THEME;
                    settings.hitDuration = Math.clamp(settings.hitDuration, 150, 1200);
                    settings.hitIntensity = Math.clamp(settings.hitIntensity, 10, 100);
                    settings.comboHits = Math.clamp(settings.comboHits, 2, 5);
                    settings.hitboxWidth = Math.clamp(settings.hitboxWidth, 1, 4);
                    settings.hitboxWave = Math.clamp(settings.hitboxWave, 0, 100);
                    settings.hitboxWidth = Math.clamp(settings.hitboxWidth, 1, 4);
                    settings.hitboxWave = Math.clamp(settings.hitboxWave, 0, 100);
                    return settings;
                }
            } catch (IOException | JsonParseException error) {
                LOGGER.warn("Could not read {}", FILE, error);
            }
        }
        return new ClientSettings();
    }

    void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = Files.createTempFile(FILE.getParent(), "visual-client-", ".json.tmp");
            try {
                try (var writer = Files.newBufferedWriter(temporary)) {
                    GSON.toJson(this, writer);
                }
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            LOGGER.warn("Could not save {}", FILE, error);
        }
    }
}
