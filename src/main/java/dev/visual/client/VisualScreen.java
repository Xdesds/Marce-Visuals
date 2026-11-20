package dev.visual.client;

import dev.visual.client.ClientSettings.Accent;
import dev.visual.client.ClientSettings.MusicLayout;
import dev.visual.client.MsdfDraw.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;

public final class VisualScreen extends Screen {
    private static final int MENU_WIDTH = 414;
    private static final int RAIL_WIDTH = 27;
    private static final int EXPANDED_RAIL_WIDTH = 92;
    private static final int ROW_HEIGHT = 15;
    private static final int TEXT = 0xFFF3F1F7;
    private static final int MUTED = 0xFF9A96A3;
    private static final int QUIET = 0xFF75717E;
    private static final int RAIL = 0xFF101013;
    private static final int LINE = 0xFF242328;
    private static final int HOVER = 0xFF1B1A21;
    private static final int POPUP_HEIGHT = 122;
    private static final float LABEL_SIZE = 5.8f;
    private static final MsdfDraw TYPE = MsdfDraw.INSTANCE;

    private final ClientSettings settings;
    private final DynamicIsland island;
    private Tab tab = Tab.HUD;
    private EditBox search;
    private Module popup;
    private int popupRow;
    private boolean popupFromGear;
    private long popupOpenedAt;
    private long pickerOpenedAt;
    private JellyAppearance drawMotion;
    private JellyAppearance segmentMotion;
    private JellyAppearance waveMotion;
    private Rect waveDrag;
    private float[] hsv;
    private PickerDrag pickerDrag;
    private boolean pickerChanged;
    private long copiedUntil;
    private final RubberSegment inlineSegment = new RubberSegment();
    private final RubberSegment popupSegment = new RubberSegment();
    private RubberSegment draggingSegment;
    private Rect segmentDragBounds;
    private long gradientClick;
    private float gradientClickX;
    private float gradientClickY;
    private float pointerX;
    private float pointerY;
    private boolean railExpanded;
    private float railWidth = RAIL_WIDTH;
    private float railVelocity;
    private long railUpdatedAt = System.nanoTime();
    private final long openedAt = System.nanoTime();

    VisualScreen(ClientSettings settings, DynamicIsland island) {
        super(Component.literal("Marce Visual"));
        this.settings = settings;
        this.island = island;
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        boolean focused = search != null && search.isFocused();
        Rect box = searchBox(menu());
        search = new EditBox(font, box.x, box.y, box.w, box.h, Component.literal("Search settings"));
        search.setMaxLength(32);
        search.setValue(query);
        search.setFocused(focused);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        island.render(graphics);
        updateRail();
        drawMotion = menuMotion();
        drawMenu((int) drawMotion.inverseX(mouseX / menuScale()), (int) drawMotion.inverseY(mouseY / menuScale()));
    }

    private void drawMenu(int mouseX, int mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
        Rect frame = menu();
        int accent = settings.accentColor();
        glass(frame, 8);
        rounded(frame.x + 4, frame.y + 4, railWidth - 6, frame.h - 8, 7, RAIL);
        rounded(frame.x + railWidth, frame.y + 8, 1, frame.h - 16, 0, LINE);

        float scale = menuScale();
        MarceLogo.INSTANCE.layer(MarceLogo.Layer.FULL, drawMotion.x(frame.x + 14) * scale,
            drawMotion.y(frame.y + 14) * scale, 14 * scale * drawMotion.contentScale(), 0, 1, accent);
        railLabel("Marce", frame, frame.y + 14, TEXT);
        for (Tab item : Tab.values()) {
            Rect button = tabButton(frame, item);
            boolean active = tab == item && search.getValue().isBlank();
            if (active || button.contains(mouseX, mouseY)) {
                rounded(button.x, button.y, button.w, button.h, 5, active ? accentTint(accent) : HOVER);
            }
            icon(item.icon, frame.x + 14, button.centerY(), 8.5f, active ? accent : MUTED);
            railLabel(item.label, frame, button.centerY(), active ? TEXT : MUTED);
        }
        Rect gear = gearButton(frame);
        boolean settingsOpen = popup == Module.THEME && popupFromGear;
        if (settingsOpen || gear.contains(mouseX, mouseY)) {
            rounded(gear.x, gear.y, gear.w, gear.h, 5, settingsOpen ? accentTint(accent) : HOVER);
        }
        icon(Icon.SETTINGS, frame.x + 14, gear.centerY(), 9, settingsOpen ? accent : MUTED);
        railLabel("GUI Settings", frame, gear.centerY(), settingsOpen ? TEXT : MUTED);

        int headerX = frame.x + Math.round(railWidth) + 10;
        text("Marce Visual", headerX, frame.y + 14, 6.5f, TEXT);
        if (frame.w >= 330) {
            float titleWidth = TYPE.width("Marce Visual", 6.5f);
            text(">", headerX + titleWidth + 6, frame.y + 14, 6, QUIET);
            text(search.getValue().isBlank() ? tab.label : "Search", headerX + titleWidth + 14,
                frame.y + 14, 5.8f, MUTED);
        }

        drawSearch(searchBox(frame), mouseX, mouseY);
        Rect close = closeButton(frame);
        if (close.contains(mouseX, mouseY)) {
            rounded(close.x, close.y, close.w, close.h, 5, HOVER);
        }
        icon(Icon.CANCEL, close.centerX(), close.centerY(), 8, MUTED);

        List<Group> groups = groups(frame);
        for (Group group : groups) {
            if (group.title != null) {
                text(group.title, group.x + 4, frame.y + group.offset - 9, 5.5f, QUIET);
                rounded(group.x + 4, frame.y + group.offset - 3, group.w - 8, 0.5f, 0, LINE);
            }
            for (int i = 0; i < group.controls.size(); i++) {
                drawControl(group.controls.get(i), row(frame, group, i), mouseX, mouseY, accent, true);
            }
        }
        if (groups.isEmpty()) {
            text("No matching settings", headerX + 4, frame.y + 59, LABEL_SIZE, MUTED);
        }
        if (popup != null) {
            Rect details = settingsBox(frame);
            JellyAppearance motion = popupMotion(details);
            float pointerScreenX = drawMotion.x(mouseX);
            float pointerScreenY = drawMotion.y(mouseY);
            int detailMouseX = (int) motion.inverseX(pointerScreenX);
            int detailMouseY = (int) motion.inverseY(pointerScreenY);
            VisualDraw.overlay(() -> {
                drawMotion = motion;
                drawSettings(details, detailMouseX, detailMouseY, accent);
            });
        }
        if (hsv != null) {
            Rect picker = pickerBox(frame);
            JellyAppearance motion = pickerMotion(picker);
            int pickerMouseX = (int) motion.inverseX(drawMotion.x(mouseX));
            int pickerMouseY = (int) motion.inverseY(drawMotion.y(mouseY));
            VisualDraw.overlay(() -> {
                drawMotion = motion;
                drawPicker(picker, pickerMouseX, pickerMouseY);
            });
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The Luma backdrop is drawn only inside the menu and picker.
    }

    private void drawSearch(Rect box, int mouseX, int mouseY) {
        boolean focused = search.isFocused();
        panel(box.x, box.y, box.w, box.h, 5, focused ? accentTint(settings.accentColor()) : LINE, 0xFF18171D);
        icon(Icon.MANAGE_SEARCH, box.x + 9, box.centerY(), 7, focused ? settings.accentColor() : QUIET);
        String value = search.getValue();
        int start = 0;
        int cursor = search.getCursorPosition();
        float available = box.w - 21;
        while (start < cursor && TYPE.width(value.substring(start, cursor), 5.8f) > available - 2) {
            start += Character.charCount(value.codePointAt(start));
        }
        String visible = value.isEmpty() ? "Search settings" : TYPE.ellipsize(value.substring(start), 5.8f, available);
        int textX = box.x + 16;
        text(visible, textX, box.centerY(), 5.8f, value.isEmpty() ? QUIET : TEXT);
        if (focused && (System.nanoTime() / 500_000_000L) % 2 == 0) {
            float caret = textX + TYPE.width(value.substring(start, cursor), 5.8f);
            rounded(caret, box.y + 4, 0.75f, box.h - 8, 0, settings.accentColor());
        }
    }

    private void drawControl(Control control, Rect bounds, int mouseX, int mouseY, int accent, boolean overview) {
        boolean hover = bounds.contains(mouseX, mouseY);
        if (hover) {
            rounded(bounds.x, bounds.y, bounds.w, bounds.h, 5, HOVER);
        }
        String label = control == Control.LAYOUT && bounds.w < 140 ? "Layout" : control.label;
        String option = control.value(settings);
        if (option != null) label = TYPE.ellipsize(label, LABEL_SIZE, bounds.w - TYPE.width(option, 5.3f) - 21);
        text(label, bounds.x + 5, bounds.centerY(), LABEL_SIZE, TEXT);
        Module module = overview ? module(control) : null;
        if (module != null) {
            Rect button = optionsButton(bounds, control);
            if (popup == module || button.contains(mouseX, mouseY)) {
                rounded(button.x, button.y, button.w, button.h, 3, accentTint(accent));
            }
            centered("...", button.centerX(), button.centerY() - 1, 7, popup == module ? accent : MUTED);
        }
        if (control.toggle()) {
            boolean enabled = control.enabled(settings);
            int at = bounds.right() - 15;
            rounded(at, bounds.centerY() - 3.25f, 11, 6.5f, 3.25f, enabled ? accent : 0xFF303036);
            rounded(at + (enabled ? 5.75f : 1.25f), bounds.centerY() - 2.1f, 4.2f, 4.2f, 2.1f,
                enabled ? accentTint(accent) : mixColor(0xFF77737F, accent, 0.35f));
        } else if (control == Control.LAYOUT) {
            drawSegment(bounds, overview, mouseX, mouseY);
        } else if (control == Control.WAVE) {
            Rect track = waveTrack(bounds);
            float filled = track.w * settings.waveIntensity / 150f;
            rounded(track.x, track.centerY() - 0.75f, track.w, 1.5f, 0.75f, 0xFF3B3843);
            if (filled >= 1) {
                rounded(track.x, track.centerY() - 0.75f, filled, 1.5f, 0.75f, accent);
            }
            rounded(track.x + filled - 1.8f, track.centerY() - 1.8f, 3.6f, 3.6f, 1.8f, 0xFFF3F0F8);
            String percent = settings.waveIntensity + "%";
            text(percent, bounds.right() - 4 - TYPE.width(percent, 5.2f), bounds.centerY(), 5.2f, MUTED);
        } else if (control == Control.ACCENT) {
            rounded(bounds.right() - 13, bounds.centerY() - 3.25f, 6.5f, 6.5f, 2, accent);
            if (bounds.w >= 110) {
                String hex = hex(accent);
                int inset = overview ? 32 : 17;
                text(hex, bounds.right() - inset - TYPE.width(hex, 5.3f), bounds.centerY(), 5.3f, MUTED);
            }
        } else if (option != null) {
            float valueWidth = TYPE.width(option, 5.3f);
            rounded(bounds.right() - valueWidth - 13, bounds.y + 2, valueWidth + 9, bounds.h - 4, 3, HOVER);
            text(option, bounds.right() - valueWidth - 8, bounds.centerY(), 5.3f, accent);
        } else if (control.preset != null) {
            int swatchX = bounds.right() - 16;
            rounded(swatchX, bounds.centerY() - 5, 10, 10, 3, control.preset.color);
            if (settings.customAccent == null && settings.accent == control.preset) {
                icon(Icon.CHECK_SMALL, swatchX + 5, bounds.centerY(), 8, 0xFF151017);
            }
        }
    }

    private void drawSegment(Rect row, boolean overview, int mouseX, int mouseY) {
        Rect bounds = segmentBox(row);
        RubberSegment segment = overview ? inlineSegment : popupSegment;
        segment.update(settings.musicLayout.ordinal(), System.nanoTime());
        float inset = 1;
        float slot = (bounds.w - 2 * inset) * 0.5f;
        float left = bounds.x + inset + segment.left() * slot;
        float right = bounds.x + inset + segment.right() * slot;
        float squash = Math.min(0.6f, Math.max(0, right - left - slot) * 0.025f);
        rounded(bounds.x, bounds.y, bounds.w, bounds.h, 3.5f, 0xFF27272A);
        rounded(left, bounds.y + inset + squash, right - left, bounds.h - 2 * inset - 2 * squash, 2.5f, 0xFFFAFAFA);
        for (MusicLayout layout : MusicLayout.values()) {
            float center = bounds.x + inset + (layout.ordinal() + 0.5f) * slot;
            float labelWidth = TYPE.width(layout.label, 5.1f);
            float overlap = Math.max(0, Math.min(right, center + labelWidth * 0.5f)
                - Math.max(left, center - labelWidth * 0.5f));
            float coverage = Math.clamp(overlap / labelWidth, 0, 1);
            int idle = bounds.contains(mouseX, mouseY) ? 0xFFE1E1E2 : 0xFFBBBBBE;
            centered(layout.label, center, bounds.centerY(), 5.1f, mixColor(idle, 0xFF18181B, coverage));
        }
    }

    private void drawSettings(Rect box, int mouseX, int mouseY, int accent) {
        glass(box, 8);
        Rect close = pickerClose(box);
        if (close.contains(mouseX, mouseY)) {
            rounded(close.x, close.y, close.w, close.h, 4, HOVER);
        }
        text(popup.label, box.x + 9, box.y + 12, 6.5f, TEXT);
        icon(Icon.CANCEL, close.centerX(), close.centerY(), 8, MUTED);
        rounded(box.x + 8, box.y + 21, box.w - 16, 0.5f, 0, LINE);
        for (int i = 0; i < popup.controls.size(); i++) {
            Control control = popup.controls.get(i);
            Rect row = detailRow(box, i);
            drawControl(control, row, mouseX, mouseY, accent, false);
            text(control.detail, row.x + 5, row.y + 17, 5.1f, QUIET);
            if (i + 1 < popup.controls.size()) {
                rounded(row.x + 5, row.y + 22, row.w - 10, 0.5f, 0, LINE);
            }
        }
    }

    private void drawPicker(Rect box, int mouseX, int mouseY) {
        glass(box, 7);
        Rect close = pickerClose(box);
        if (close.contains(mouseX, mouseY)) {
            rounded(close.x, close.y, close.w, close.h, 4, HOVER);
        }
        text("Accent color", box.x + 8, box.y + 12, LABEL_SIZE, TEXT);
        icon(Icon.CANCEL, close.centerX(), close.centerY(), 7, MUTED);
        Rect field = saturationField(box);
        Rect strip = hueField(box);
        colorField(field, false);
        colorField(strip, true);

        Rect copy = copyButton(box);
        rounded(copy.x, copy.y, copy.w, copy.h, 5, copy.contains(mouseX, mouseY) ? HOVER : 0xFF202026);
        int accent = settings.accentColor();
        rounded(copy.x + 4, copy.y + 3, 8, 8, 2, accent);
        text(hex(accent), copy.x + 16, copy.centerY(), 5.7f, TEXT);
        String action = System.nanoTime() < copiedUntil ? "Copied" : "Copy";
        text(action, copy.right() - TYPE.width(action, 5.4f) - 5, copy.centerY(), 5.4f, MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double rawX = event.x() / menuScale();
        double rawY = event.y() / menuScale();
        JellyAppearance mainMotion = menuMotion();
        double x = mainMotion.inverseX(rawX);
        double y = mainMotion.inverseY(rawY);
        Rect frame = menu();
        if (gearButton(frame).contains(x, y)) {
            boolean wasOpen = popup == Module.THEME && popupFromGear;
            closeSettings();
            search.setFocused(false);
            if (!wasOpen) {
                openSettings(Module.THEME, frame.h - 24, true);
            }
            return true;
        }
        if (hsv != null) {
            Rect picker = pickerBox(frame);
            JellyAppearance motion = pickerMotion(picker);
            double px = motion.inverseX(rawX);
            double py = motion.inverseY(rawY);
            if (pickerClose(picker).contains(px, py)) {
                closePicker();
                return true;
            }
            if (saturationField(picker).contains(px, py) || hueField(picker).contains(px, py)) {
                pickerDrag = saturationField(picker).contains(px, py) ? PickerDrag.COLOR : PickerDrag.HUE;
                movePicker(picker, px, py);
                return true;
            }
            if (copyButton(picker).contains(px, py) && minecraft != null) {
                minecraft.keyboardHandler.setClipboard(hex(settings.accentColor()));
                copiedUntil = System.nanoTime() + 1_200_000_000L;
                return true;
            }
            if (picker.contains(px, py)) {
                return true;
            }
            closePicker();
        }
        if (popup != null) {
            Rect details = settingsBox(frame);
            JellyAppearance motion = popupMotion(details);
            double px = motion.inverseX(rawX);
            double py = motion.inverseY(rawY);
            if (details.contains(px, py)) {
                if (pickerClose(details).contains(px, py)) {
                    closeSettings();
                    return true;
                }
                for (int i = 0; i < popup.controls.size(); i++) {
                    Rect bounds = detailRow(details, i);
                    if (bounds.contains(px, py)) {
                        activate(popup.controls.get(i), bounds, px, py, false);
                        break;
                    }
                }
                return true;
            }
            closeSettings();
        }
        if (closeButton(frame).contains(x, y)) {
            onClose();
            return true;
        }
        if (logoButton(frame).contains(x, y)) {
            railExpanded = !railExpanded;
            search.setFocused(false);
            return true;
        }
        if (searchBox(frame).contains(x, y)) {
            search.setFocused(true);
            search.moveCursorToEnd(false);
            return true;
        }
        search.setFocused(false);
        for (Tab item : Tab.values()) {
            if (tabButton(frame, item).contains(x, y)) {
                tab = item;
                search.setValue("");
                return true;
            }
        }
        for (Group group : groups(frame)) {
            for (int i = 0; i < group.controls.size(); i++) {
                Rect bounds = row(frame, group, i);
                if (bounds.contains(x, y)) {
                    activate(group.controls.get(i), bounds, x, y, true);
                    return true;
                }
            }
        }
        if (frame.contains(x, y)) {
            gradientClick = System.nanoTime();
            gradientClickX = (float) x;
            gradientClickY = (float) y;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void activate(Control control, Rect bounds, double x, double y, boolean overview) {
        Module module = overview ? module(control) : null;
        if (module != null && (!control.toggle() || x < bounds.right() - 18)) {
            openSettings(module, bounds.y - menu().y, false);
        } else if (control.toggle()) {
            control.flip(settings);
            settings.save();
            island.showMessage(control.label + (control.enabled(settings) ? " enabled" : " disabled"));
        } else if (control == Control.LAYOUT) {
            Rect track = segmentBox(bounds);
            if (track.contains(x, y)) {
                draggingSegment = overview ? inlineSegment : popupSegment;
                segmentDragBounds = track;
                segmentMotion = overview ? menuMotion() : popupMotion(settingsBox(menu()));
                draggingSegment.update(settings.musicLayout.ordinal(), System.nanoTime());
                draggingSegment.press(segmentX(x), System.nanoTime());
            }
        } else if (control == Control.WAVE) {
            Rect track = waveTrack(bounds);
            if (track.contains(x, y)) {
                waveDrag = track;
                waveMotion = overview ? menuMotion() : popupMotion(settingsBox(menu()));
                changeWave(x);
            }
        } else if (control == Control.ACCENT) {
            int rgb = settings.accentColor();
            hsv = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
            pickerOpenedAt = System.nanoTime();
            copiedUntil = 0;
        } else if (control.preset != null) {
            settings.selectAccent(control.preset);
            settings.save();
        } else if (control.value(settings) != null) {
            control.cycle(settings);
            settings.save();
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            if (draggingSegment != null) {
                draggingSegment.drag(segmentX(segmentMotion.inverseX(event.x() / menuScale())), System.nanoTime());
                return true;
            }
            if (pickerDrag != null) {
                Rect picker = pickerBox(menu());
                JellyAppearance motion = pickerMotion(picker);
                movePicker(picker, motion.inverseX(event.x() / menuScale()), motion.inverseY(event.y() / menuScale()));
                return true;
            }
            if (waveDrag != null) {
                changeWave(waveMotion.inverseX(event.x() / menuScale()));
                return true;
            }
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSegment != null && event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            int selected = draggingSegment.release(segmentX(segmentMotion.inverseX(event.x() / menuScale())), System.nanoTime());
            settings.musicLayout = MusicLayout.values()[selected];
            settings.save();
            draggingSegment = null;
            segmentDragBounds = null;
            segmentMotion = null;
            return true;
        }
        if (waveDrag != null && event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            finishWave();
            return true;
        }
        if (pickerDrag != null && event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            pickerDrag = null;
            savePicker();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW_KEY_ESCAPE) {
            if (hsv != null) {
                closePicker();
                return true;
            }
            if (popup != null) {
                closeSettings();
                return true;
            }
            if (search.isFocused() || !search.getValue().isBlank()) {
                search.setValue("");
                search.setFocused(false);
                return true;
            }
        }
        if (event.key() == GLFW_KEY_F && (event.modifiers() & (GLFW_MOD_CONTROL | GLFW_MOD_SUPER)) != 0) {
            closeSettings();
            search.setFocused(true);
            return true;
        }
        return (search.isFocused() && search.keyPressed(event)) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return (search.isFocused() && search.charTyped(event)) || super.charTyped(event);
    }

    @Override
    public void removed() {
        closeSettings();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void movePicker(Rect picker, double x, double y) {
        if (pickerDrag == PickerDrag.HUE) {
            Rect field = hueField(picker);
            hsv[0] = Math.clamp((float) ((x - field.x) / field.w), 0, 1);
        } else {
            Rect field = saturationField(picker);
            hsv[1] = Math.clamp((float) ((x - field.x) / field.w), 0, 1);
            hsv[2] = 1 - Math.clamp((float) ((y - field.y) / field.h), 0, 1);
        }
        settings.customAccent = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        pickerChanged = true;
    }

    private void closePicker() {
        savePicker();
        pickerDrag = null;
        hsv = null;
    }

    private void savePicker() {
        if (pickerChanged) {
            settings.save();
            pickerChanged = false;
        }
    }

    private void changeWave(double x) {
        settings.waveIntensity = Math.round(150 * Math.clamp((float) ((x - waveDrag.x) / waveDrag.w), 0, 1));
    }

    private void finishWave() {
        if (waveDrag != null) {
            waveDrag = null;
            waveMotion = null;
            settings.save();
        }
    }

    private void closeSettings() {
        if (draggingSegment != null) {
            draggingSegment.cancel();
            draggingSegment = null;
            segmentDragBounds = null;
            segmentMotion = null;
        }
        closePicker();
        finishWave();
        popup = null;
        popupFromGear = false;
    }

    private void openSettings(Module module, int row, boolean fromGear) {
        popup = module;
        popupRow = row;
        popupFromGear = fromGear;
        popupOpenedAt = System.nanoTime();
    }

    private JellyAppearance menuMotion() {
        Rect frame = menu();
        return JellyAppearance.at(frame.centerX(), frame.centerY(), openedAt, System.nanoTime(), settings.guiJelly);
    }

    private JellyAppearance popupMotion(Rect bounds) {
        return JellyAppearance.at(bounds.centerX(), bounds.centerY(), popupOpenedAt, System.nanoTime(), settings.guiJelly);
    }

    private JellyAppearance pickerMotion(Rect bounds) {
        return JellyAppearance.at(bounds.centerX(), bounds.centerY(), pickerOpenedAt, System.nanoTime(), settings.guiJelly);
    }

    private List<Group> groups(Rect frame) {
        Rect left = column(frame, false);
        Rect right = column(frame, true);
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            List<Control> matches = Arrays.stream(Control.values())
                .filter(control -> control.searchTerms.contains(query)).toList();
            if (matches.isEmpty()) {
                return List.of();
            }
            int split = (matches.size() + 1) / 2;
            return List.of(new Group("Results", left.x, left.w, 44, matches.subList(0, split)),
                new Group(null, right.x, right.w, 44, matches.subList(split, matches.size())));
        }
        if (tab == Tab.HUD) {
            return List.of(new Group("On-screen HUD", left.x, left.w, 44,
                    List.of(Control.ISLAND, Control.FPS, Control.PING)),
                new Group("Music", right.x, right.w, 44, List.of(Control.MUSIC, Control.LAYOUT, Control.WAVE)),
                new Group("Notifications", right.x, right.w, 117, List.of(Control.FEEDBACK)));
        }
        if (tab == Tab.FUNCTION) {
            return List.of(new Group("Combat visuals", left.x, left.w, 44,
                    List.of(Control.HIT_COLOR, Control.NORMAL_SHADER, Control.CRITICAL_SHADER, Control.COMBO_SHADER)),
                new Group("Entity outlines", right.x, right.w, 44,
                    List.of(Control.HITBOX, Control.BOX_STYLE, Control.BOX_COLOR, Control.BOX_GLOW)));
        }
        return List.of(new Group("Colors", left.x, left.w, 44, List.of(Control.ACCENT, Control.VIOLET, Control.MINT, Control.SKY)),
            new Group("Background", right.x, right.w, 44,
                List.of(Control.GRADIENT, Control.ANIMATION, Control.GRAIN, Control.JELLY)));
    }

    private Rect menu() {
        int viewWidth = viewportWidth();
        int viewHeight = viewportHeight();
        int h = Math.min(viewHeight - 16, Math.max(155, Math.round(viewHeight * 0.534f)));
        return new Rect((viewWidth - MENU_WIDTH) / 2, (viewHeight - h) / 2, MENU_WIDTH, h);
    }

    private float menuScale() {
        int pixels = minecraft == null ? width : Math.max(1, minecraft.getWindow().getWidth());
        return Math.max(width / 952f, Math.min(width / 610f, 1.25f * width / pixels));
    }

    private int viewportWidth() { return Math.round(width / menuScale()); }
    private int viewportHeight() { return Math.round(height / menuScale()); }

    private Rect column(Rect frame, boolean second) {
        int left = frame.x + Math.round(railWidth) + 5;
        int innerWidth = frame.right() - 10 - left;
        int gap = 10;
        int first = (innerWidth - gap) / 2;
        return second ? new Rect(left + first + gap, 0, innerWidth - first - gap, 0)
            : new Rect(left, 0, first, 0);
    }

    private static Rect row(Rect frame, Group group, int index) {
        return new Rect(group.x, frame.y + group.offset + index * ROW_HEIGHT, group.w, ROW_HEIGHT - 1);
    }

    private static Rect optionsButton(Rect bounds, Control control) {
        return new Rect(bounds.right() - (control.toggle() ? 29 : 27), bounds.y + 1, 11, bounds.h - 2);
    }

    private static Rect waveTrack(Rect bounds) {
        return new Rect(bounds.right() - 54, bounds.y, 32, bounds.h);
    }

    private Rect settingsBox(Rect frame) {
        int w = 128;
        int h = 46 + popup.controls.size() * 26;
        int x = Math.clamp(popupFromGear ? frame.x + Math.round(railWidth) + 5 : frame.right() - 31,
            6, viewportWidth() - w - 6);
        int anchorY = popupFromGear ? frame.y + frame.h - h - 7 : frame.y + Math.max(63, popupRow - 3);
        int y = Math.clamp(anchorY, 6, Math.max(6, viewportHeight() - h - 6));
        return new Rect(x, y, w, h);
    }

    private static Rect detailRow(Rect box, int index) {
        return new Rect(box.x + 5, box.y + 28 + index * 26, box.w - 10, ROW_HEIGHT - 1);
    }

    private Rect logoButton(Rect frame) {
        return new Rect(frame.x + 5, frame.y + 5, Math.round(railWidth) - 9, 18);
    }

    private Rect tabButton(Rect frame, Tab tab) {
        return new Rect(frame.x + 5, frame.y + 29 + tab.ordinal() * 23, Math.round(railWidth) - 9, 18);
    }

    private Rect gearButton(Rect frame) {
        return new Rect(frame.x + 5, frame.y + frame.h - 24, Math.round(railWidth) - 9, 18);
    }

    private void updateRail() {
        long now = System.nanoTime();
        float dt = Math.min((now - railUpdatedAt) / 1_000_000_000f, 0.05f);
        railUpdatedAt = now;
        float target = railExpanded ? EXPANDED_RAIL_WIDTH : RAIL_WIDTH;
        // Exact critically damped spring preserves velocity when the direction changes.
        float offset = railWidth - target;
        float impulse = railVelocity + 20 * offset;
        float decay = (float) Math.exp(-20 * dt);
        railWidth = target + (offset + impulse * dt) * decay;
        railVelocity = (railVelocity - 20 * impulse * dt) * decay;
        if (Math.abs(railWidth - target) < 0.01f && Math.abs(railVelocity) < 0.1f) {
            railWidth = target;
            railVelocity = 0;
        }
    }

    private void railLabel(String label, Rect frame, float y, int color) {
        float available = railWidth - 35;
        if (available < 5) return;
        float opacity = Math.clamp((railWidth - RAIL_WIDTH - 8) / 40, 0, 1);
        int faded = (Math.round(opacity * 255) << 24) | (color & 0xFFFFFF);
        text(TYPE.ellipsize(label, 6, available), frame.x + 27, y, 6, faded);
    }

    private static Rect closeButton(Rect frame) {
        return new Rect(frame.right() - 23, frame.y + 6, 16, 16);
    }

    private static Rect searchBox(Rect frame) {
        int w = 102;
        return new Rect(closeButton(frame).x - w - 5, frame.y + 6, w, 16);
    }

    private static Rect segmentBox(Rect row) {
        return new Rect(row.right() - 65, row.y + 1, 62, row.h - 2);
    }

    private float segmentX(double x) {
        return (float) ((x - segmentDragBounds.x - 1) / ((segmentDragBounds.w - 2) * 0.5));
    }

    private Rect pickerBox(Rect frame) {
        Rect anchor = settingsBox(frame);
        int w = 89;
        int x = anchor.right() - 11;
        if (x + w > viewportWidth() - 6) {
            x = anchor.x - w + 11;
        }
        int y = Math.clamp(anchor.y + 26, 6, Math.max(6, viewportHeight() - POPUP_HEIGHT - 6));
        return new Rect(Math.max(6, x), y, w, POPUP_HEIGHT);
    }

    private static Rect pickerClose(Rect box) {
        return new Rect(box.right() - 18, box.y + 5, 14, 14);
    }

    private static Rect saturationField(Rect box) {
        return new Rect(box.x + 7, box.y + 22, box.w - 14, 63);
    }

    private static Rect hueField(Rect box) {
        return new Rect(box.x + 7, box.y + 91, box.w - 14, 8);
    }

    private static Rect copyButton(Rect box) {
        return new Rect(box.x + 7, box.y + 104, box.w - 14, 14);
    }

    private static int accentTint(int accent) {
        int red = (int) (22 + ((accent >> 16) & 255) * 0.15);
        int green = (int) (18 + ((accent >> 8) & 255) * 0.15);
        int blue = (int) (27 + (accent & 255) * 0.15);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private void glass(Rect bounds, float radius) {
        float base = menuScale();
        float scale = base * drawMotion.contentScale();
        float x = drawMotion.x(bounds.x) * base;
        float y = drawMotion.y(bounds.y) * base;
        float w = bounds.w * drawMotion.scaleX() * base;
        float h = bounds.h * drawMotion.scaleY() * base;
        IslandBlur.INSTANCE.queue(x, y, w, h,
            radius * scale, radius * scale, 0, 0, 0, 1);
        float time = settings.guiAnimation ? (System.nanoTime() - openedAt) / 1_000_000_000f : 0;
        float clickAge = gradientClick == 0 || !bounds.equals(menu()) ? 5
            : (System.nanoTime() - gradientClick) / 1_000_000_000f;
        MenuGradient.INSTANCE.queue(x, y, w, h,
            radius * scale, settings.accentColor(), time, settings.guiGrain,
            (pointerX - bounds.x) / bounds.w, (pointerY - bounds.y) / bounds.h,
            (gradientClickX - bounds.x) / bounds.w, (gradientClickY - bounds.y) / bounds.h,
            settings.guiAnimation ? clickAge : 5, settings.guiGradient);
    }

    private void rounded(float x, float y, float w, float h, float radius, int color) {
        float base = menuScale();
        VisualDraw.rounded(drawMotion.x(x) * base, drawMotion.y(y) * base,
            w * base * drawMotion.scaleX(), h * base * drawMotion.scaleY(),
            radius * base * drawMotion.contentScale(), color);
    }

    private void panel(float x, float y, float w, float h, float radius, int border, int fill) {
        rounded(x, y, w, h, radius, border);
        rounded(x + 0.6f, y + 0.6f, w - 1.2f, h - 1.2f, radius - 0.6f, fill);
    }

    private void text(String value, float x, float y, float size, int color) {
        float scale = menuScale();
        TYPE.text(value, drawMotion.x(x) * scale, drawMotion.y(y) * scale,
            size * scale * drawMotion.contentScale(), color);
    }

    private void icon(Icon value, float x, float y, float size, int color) {
        float scale = menuScale();
        TYPE.icon(value, drawMotion.x(x) * scale, drawMotion.y(y) * scale,
            size * scale * drawMotion.contentScale(), color);
    }

    private void colorField(Rect bounds, boolean hueBar) {
        float scale = menuScale();
        MenuColorField.INSTANCE.queue(drawMotion.x(bounds.x) * scale, drawMotion.y(bounds.y) * scale,
            bounds.w * scale * drawMotion.scaleX(), bounds.h * scale * drawMotion.scaleY(),
            hsv[0], hsv[1], hsv[2], hueBar);
    }

    private static String hex(int accent) {
        return String.format(Locale.ROOT, "#%06X", accent & 0xFFFFFF);
    }

    private static int mixColor(int from, int to, float amount) {
        int red = Math.round(((from >> 16) & 255) * (1 - amount) + ((to >> 16) & 255) * amount);
        int green = Math.round(((from >> 8) & 255) * (1 - amount) + ((to >> 8) & 255) * amount);
        int blue = Math.round((from & 255) * (1 - amount) + (to & 255) * amount);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private void centered(String text, float x, float y, float size, int color) {
        text(text, x - TYPE.width(text, size) * 0.5f, y, size, color);
    }

    private record Group(String title, int x, int w, int offset, List<Control> controls) {}

    private record Rect(int x, int y, int w, int h) {
        int right() { return x + w; }
        float centerX() { return x + w * 0.5f; }
        float centerY() { return y + h * 0.5f; }
        boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < y + h; }
    }

    private enum PickerDrag { COLOR, HUE }

    private static Module module(Control control) {
        return switch (control) {
            case ISLAND -> Module.ISLAND;
            case MUSIC -> Module.MUSIC;
            case ACCENT -> Module.THEME;
            case HIT_COLOR -> Module.HIT_COLOR;
            case HITBOX -> Module.HITBOX;
            default -> null;
        };
    }

    private enum Module {
        ISLAND("Dynamic Island", List.of(Control.ISLAND, Control.FPS, Control.PING, Control.FEEDBACK)),
        MUSIC("Now playing", List.of(Control.MUSIC, Control.LAYOUT, Control.WAVE)),
        THEME("GUI Settings", List.of(Control.ACCENT, Control.GRADIENT, Control.ANIMATION, Control.GRAIN, Control.JELLY)),
        HIT_COLOR("Shader Hit Color", List.of(Control.HIT_COLOR, Control.NORMAL_SHADER, Control.NORMAL_COLOR,
            Control.CRITICAL_SHADER, Control.CRITICAL_COLOR, Control.COMBO_SHADER, Control.COMBO_COLOR,
            Control.HIT_DURATION, Control.HIT_INTENSITY, Control.COMBO_HITS)),
        HITBOX("Custom Hitbox", List.of(Control.HITBOX, Control.BOX_STYLE, Control.BOX_COLOR,
            Control.BOX_WIDTH, Control.BOX_WAVE, Control.BOX_GLOW, Control.BOX_PLAYERS));

        final String label;
        final List<Control> controls;

        Module(String label, List<Control> controls) {
            this.label = label;
            this.controls = controls;
        }
    }

    private enum Tab {
        HUD("HUD", Icon.TUNE), FUNCTION("Function", Icon.BOLT), APPEARANCE("GUI Settings", Icon.BRUSH);

        final String label;
        final Icon icon;

        Tab(String label, Icon icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private enum Control {
        ISLAND("Dynamic Island", "hud", "Keep the HUD at the top of the screen"),
        FPS("FPS counter", "hud frame rate", "Show the live frame rate"),
        PING("Ping", "hud latency", "Show the connection latency"),
        FEEDBACK("Status messages", "hud notification", "Show changes made in this menu"),
        MUSIC("Now playing", "music spotify track media", "Show the active track and cover"),
        LAYOUT("Track layout", "music separate inline", "Inside the island or below it"),
        WAVE("Wave intensity", "music audio bass beat", "Set how strongly the edges move"),
        ACCENT("Accent color", "theme appearance palette custom", "Open the color picker"),
        GRADIENT("Gradient", "gui background grainient", "Use theme colors in the background"),
        ANIMATION("Animation", "gui gradient motion flow", "Animate the gradient and pointer flow"),
        JELLY("Jelly opening", "gui animation spring menu", "Use a spring animation when opening"),
        GRAIN("Grain", "gui gradient texture noise", "Add a fine grain texture"),
        HIT_COLOR("Shader Hit Color", "function combat damage", "Shaders on confirmed local hits"),
        NORMAL_SHADER("Normal shader", "function hit pulse aurora", "Click to cycle the normal hit effect"),
        CRITICAL_SHADER("Critical shader", "function hit crit crystal", "Critical hits take priority over combos"),
        COMBO_SHADER("Combo shader", "function hit combo shock", "Repeated hits on one target within 1.2s"),
        NORMAL_COLOR("Normal color", "function hit color", "Independent normal-hit palette"),
        CRITICAL_COLOR("Critical color", "function crit color", "Independent critical-hit palette"),
        COMBO_COLOR("Combo color", "function combo color", "Independent combo-hit palette"),
        HIT_DURATION("Duration", "function hit time", "How long the shader fades after a hit"),
        HIT_INTENSITY("Intensity", "function hit opacity", "Opacity of the shader over the skin"),
        COMBO_HITS("Combo starts at", "function hit streak", "Hits required to activate combo shader"),
        HITBOX("Custom Hitbox", "function entity outline", "Visual bounds without changing collision"),
        BOX_STYLE("Line style", "function hitbox vanilla wave", "Vanilla debug bounds or animated waves"),
        BOX_COLOR("Line color", "function hitbox color", "Wave-line palette; Theme follows GUI accent"),
        BOX_WIDTH("Line width", "function hitbox thickness", "Core line thickness in pixels"),
        BOX_WAVE("Wave amount", "function hitbox amplitude", "Wave displacement; zero gives straight lines"),
        BOX_GLOW("Glow", "function hitbox bloom halo", "Soft layered halo around wave lines"),
        BOX_PLAYERS("Players only", "function hitbox filter", "Hide bounds of mobs and animals"),
        VIOLET(Accent.VIOLET), MINT(Accent.MINT), SKY(Accent.SKY);

        final String label;
        final String searchTerms;
        final String detail;
        final Accent preset;

        Control(String label, String keywords, String detail) {
            this.label = label;
            this.searchTerms = label.toLowerCase(Locale.ROOT) + " " + keywords;
            this.detail = detail;
            this.preset = null;
        }

        Control(Accent preset) {
            this.label = preset.label;
            this.searchTerms = label.toLowerCase(Locale.ROOT) + " theme appearance preset";
            this.detail = String.format(Locale.ROOT, "#%06X", preset.color & 0xFFFFFF);
            this.preset = preset;
        }

        boolean toggle() {
            return switch (this) {
                case ISLAND, FPS, PING, FEEDBACK, MUSIC, GRADIENT, ANIMATION, GRAIN, JELLY,
                    HIT_COLOR, HITBOX, BOX_GLOW, BOX_PLAYERS -> true;
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
                case HIT_COLOR -> settings.hitColor = !settings.hitColor;
                case HITBOX -> settings.customHitbox = !settings.customHitbox;
                case BOX_GLOW -> settings.hitboxGlow = !settings.hitboxGlow;
                case BOX_PLAYERS -> settings.hitboxPlayersOnly = !settings.hitboxPlayersOnly;
                default -> throw new IllegalStateException("Control is not a toggle");
            }
        }

        String value(ClientSettings s) {
            return switch (this) {
                case NORMAL_SHADER -> s.normalShader.label;
                case CRITICAL_SHADER -> s.criticalShader.label;
                case COMBO_SHADER -> s.comboShader.label;
                case NORMAL_COLOR -> s.normalColor.label;
                case CRITICAL_COLOR -> s.criticalColor.label;
                case COMBO_COLOR -> s.comboColor.label;
                case HIT_DURATION -> s.hitDuration + " ms";
                case HIT_INTENSITY -> s.hitIntensity + "%";
                case COMBO_HITS -> s.comboHits + " hits";
                case BOX_STYLE -> s.hitboxStyle.label;
                case BOX_COLOR -> s.hitboxColor.label;
                case BOX_WIDTH -> s.hitboxWidth + " px";
                case BOX_WAVE -> s.hitboxWave + "%";
                default -> null;
            };
        }

        void cycle(ClientSettings s) {
            switch (this) {
                case NORMAL_SHADER -> s.normalShader = next(s.normalShader);
                case CRITICAL_SHADER -> s.criticalShader = next(s.criticalShader);
                case COMBO_SHADER -> s.comboShader = next(s.comboShader);
                case NORMAL_COLOR -> s.normalColor = next(s.normalColor);
                case CRITICAL_COLOR -> s.criticalColor = next(s.criticalColor);
                case COMBO_COLOR -> s.comboColor = next(s.comboColor);
                case HIT_DURATION -> s.hitDuration = nextNumber(s.hitDuration, 250, 400, 650, 900);
                case HIT_INTENSITY -> s.hitIntensity = nextNumber(s.hitIntensity, 40, 65, 85, 100);
                case COMBO_HITS -> s.comboHits = nextNumber(s.comboHits, 2, 3, 4, 5);
                case BOX_STYLE -> s.hitboxStyle = next(s.hitboxStyle);
                case BOX_COLOR -> s.hitboxColor = next(s.hitboxColor);
                case BOX_WIDTH -> s.hitboxWidth = nextNumber(s.hitboxWidth, 1, 2, 3, 4);
                case BOX_WAVE -> s.hitboxWave = nextNumber(s.hitboxWave, 0, 25, 50, 75, 100);
                default -> throw new IllegalStateException("Control has no options");
            }
        }

        private static <E extends Enum<E>> E next(E current) {
            E[] values = current.getDeclaringClass().getEnumConstants();
            return values[(current.ordinal() + 1) % values.length];
        }

        private static int nextNumber(int current, int... values) {
            for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
            return values[0];
        }
    }
}
