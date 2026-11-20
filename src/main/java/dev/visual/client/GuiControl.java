package dev.visual.client;

import dev.visual.client.ClientSettings.Accent;

import java.util.Locale;

enum GuiControl {
    ISLAND("Dynamic Island", "hud", "Островок у верхнего края экрана"),
    FPS("FPS counter", "hud frame rate"),
    PING("Ping", "hud latency"),
    FEEDBACK("Status messages", "hud notification"),
    MUSIC("Now playing", "music spotify track media", "Трек из музыкального приложения"),
    LAYOUT("Track layout", "music separate inline", "Трек внутри островка или под ним"),
    WAVE("Wave intensity", "music audio bass beat", "Насколько сильно края реагируют на звук"),
    ACCENT("Accent color", "theme appearance palette custom"),
    GRADIENT("Gradient", "gui background grainient", "Фон в цветах выбранной темы"),
    ANIMATION("Animation", "gui gradient motion flow", "Движение градиента за курсором"),
    JELLY("Jelly opening", "gui animation spring menu", "Упругое появление окон"),
    DISCORD_RPC("Discord RPC", "gui discord rich presence activity дискорд статус", "Статус игры в настольном Discord"),
    GRAIN("Grain", "gui gradient texture noise", "Лёгкая зернистость фона"),
    HIT_COLOR("Shader Hit Color", "function combat damage", "Эффект после попадания по цели"),
    NORMAL_SHADER("Normal shader", "function hit pulse aurora", "Нажми, чтобы сменить эффект"),
    CRITICAL_SHADER("Critical shader", "function hit crit crystal", "Крит важнее комбо"),
    COMBO_SHADER("Combo shader", "function hit combo shock", "Удары по одной цели с паузой до 1,2 с"),
    NORMAL_COLOR("Normal color", "function hit color"),
    CRITICAL_COLOR("Critical color", "function crit color"),
    COMBO_COLOR("Combo color", "function combo color"),
    HIT_DURATION("Duration", "function hit time", "Время затухания после попадания"),
    HIT_INTENSITY("Intensity", "function hit opacity", "Яркость эффекта поверх модели"),
    COMBO_HITS("Combo starts at", "function hit streak", "Сколько попаданий нужно для комбо"),
    HITBOX("Custom Hitbox", "function entity outline", "Только отрисовка границ существа"),
    BOX_STYLE("Line style", "function hitbox vanilla wave", "Обычная рамка или волнистые линии"),
    BOX_COLOR("Line color", "function hitbox color", "Theme берёт акцент из GUI Settings"),
    BOX_WIDTH("Line width", "function hitbox thickness"),
    BOX_WAVE("Wave amount", "function hitbox amplitude", "При 0% линии будут прямыми"),
    BOX_GLOW("Glow", "function hitbox bloom halo", "Мягкое свечение вокруг линий"),
    BOX_PLAYERS("Players only", "function hitbox filter", "Скрыть рамки мобов и животных"),
    VIOLET(Accent.VIOLET), MINT(Accent.MINT), SKY(Accent.SKY);

    final String label;
    final String searchTerms;
    final String detail;
    final Accent preset;

    GuiControl(String label, String keywords) {
        this(label, keywords, "");
    }

    GuiControl(String label, String keywords, String detail) {
        this.label = label;
        this.searchTerms = label.toLowerCase(Locale.ROOT) + " " + keywords + " " + detail.toLowerCase(Locale.ROOT);
        this.detail = detail;
        this.preset = null;
    }

    GuiControl(Accent preset) {
        this.label = preset.label;
        this.searchTerms = label.toLowerCase(Locale.ROOT) + " theme appearance preset";
        this.detail = String.format(Locale.ROOT, "#%06X", preset.color & 0xFFFFFF);
        this.preset = preset;
    }

    boolean toggle() {
        return switch (this) {
            case ISLAND, FPS, PING, FEEDBACK, MUSIC, GRADIENT, ANIMATION, GRAIN, JELLY,
                HIT_COLOR, HITBOX, BOX_GLOW, BOX_PLAYERS, DISCORD_RPC -> true;
            default -> false;
        };
    }

    boolean enabled(ClientSettings settings) {
        return switch (this) {
            case ISLAND -> settings.island;
            case FPS -> settings.fps;
            case PING -> settings.ping;
            case FEEDBACK -> settings.feedback;
            case MUSIC -> settings.music;
            case GRADIENT -> settings.guiGradient;
            case ANIMATION -> settings.guiAnimation;
            case GRAIN -> settings.guiGrain;
            case JELLY -> settings.guiJelly;
            case DISCORD_RPC -> settings.discordRpc;
            case HIT_COLOR -> settings.hitColor;
            case HITBOX -> settings.customHitbox;
            case BOX_GLOW -> settings.hitboxGlow;
            case BOX_PLAYERS -> settings.hitboxPlayersOnly;
            default -> false;
        };
    }

    void flip(ClientSettings settings) {
        switch (this) {
            case ISLAND -> settings.island = !settings.island;
            case FPS -> settings.fps = !settings.fps;
            case PING -> settings.ping = !settings.ping;
            case FEEDBACK -> settings.feedback = !settings.feedback;
            case MUSIC -> settings.music = !settings.music;
            case GRADIENT -> settings.guiGradient = !settings.guiGradient;
            case ANIMATION -> settings.guiAnimation = !settings.guiAnimation;
            case GRAIN -> settings.guiGrain = !settings.guiGrain;
            case JELLY -> settings.guiJelly = !settings.guiJelly;
            case DISCORD_RPC -> settings.discordRpc = !settings.discordRpc;
            case HIT_COLOR -> settings.hitColor = !settings.hitColor;
            case HITBOX -> settings.customHitbox = !settings.customHitbox;
            case BOX_GLOW -> settings.hitboxGlow = !settings.hitboxGlow;
            case BOX_PLAYERS -> settings.hitboxPlayersOnly = !settings.hitboxPlayersOnly;
            default -> throw new IllegalStateException("У настройки нет переключателя");
        }
    }

    String value(ClientSettings settings) {
        return switch (this) {
            case NORMAL_SHADER -> settings.normalShader.label;
            case CRITICAL_SHADER -> settings.criticalShader.label;
            case COMBO_SHADER -> settings.comboShader.label;
            case NORMAL_COLOR -> settings.normalColor.label;
            case CRITICAL_COLOR -> settings.criticalColor.label;
            case COMBO_COLOR -> settings.comboColor.label;
            case HIT_DURATION -> settings.hitDuration + " ms";
            case HIT_INTENSITY -> settings.hitIntensity + "%";
            case COMBO_HITS -> settings.comboHits + " hits";
            case BOX_STYLE -> settings.hitboxStyle.label;
            case BOX_COLOR -> settings.hitboxColor.label;
            case BOX_WIDTH -> settings.hitboxWidth + " px";
            case BOX_WAVE -> settings.hitboxWave + "%";
            default -> null;
        };
    }

    void cycle(ClientSettings settings) {
        switch (this) {
            case NORMAL_SHADER -> settings.normalShader = next(settings.normalShader);
            case CRITICAL_SHADER -> settings.criticalShader = next(settings.criticalShader);
            case COMBO_SHADER -> settings.comboShader = next(settings.comboShader);
            case NORMAL_COLOR -> settings.normalColor = next(settings.normalColor);
            case CRITICAL_COLOR -> settings.criticalColor = next(settings.criticalColor);
            case COMBO_COLOR -> settings.comboColor = next(settings.comboColor);
            case HIT_DURATION -> settings.hitDuration = nextNumber(settings.hitDuration, 250, 400, 650, 900);
            case HIT_INTENSITY -> settings.hitIntensity = nextNumber(settings.hitIntensity, 40, 65, 85, 100);
            case COMBO_HITS -> settings.comboHits = nextNumber(settings.comboHits, 2, 3, 4, 5);
            case BOX_STYLE -> settings.hitboxStyle = next(settings.hitboxStyle);
            case BOX_COLOR -> settings.hitboxColor = next(settings.hitboxColor);
            case BOX_WIDTH -> settings.hitboxWidth = nextNumber(settings.hitboxWidth, 1, 2, 3, 4);
            case BOX_WAVE -> settings.hitboxWave = nextNumber(settings.hitboxWave, 0, 25, 50, 75, 100);
            default -> throw new IllegalStateException("У настройки нет вариантов");
        }
    }

    private static <E extends Enum<E>> E next(E current) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        return values[(current.ordinal() + 1) % values.length];
    }

    private static int nextNumber(int current, int... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        return values[0];
    }
}
