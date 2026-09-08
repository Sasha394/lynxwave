package dev.client.api.nullcry.uiClient.clickGui.components.search;

import dev.client.LynxWave;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.GLUtils;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.modules.core.render.Interface;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Строка поиска с выпадающей выдачей: модули и их настройки, отсортированные по
 * точности совпадения. Тайминги и раскладка списка перенесены из исходной панели
 * (18 px на строку, 6 px отступы, потолок 150 px, задержка 140 мс перед показом,
 * раскрытие 180 мс / сворачивание 260 мс), отрисовка — на билдерах клиента.
 */
public class SearchBarComponent extends Component {

    private static final float ROW_HEIGHT = 18f;
    private static final float LIST_PADDING = 6f;
    private static final float LIST_MAX_HEIGHT = 150f;
    private static final float LIST_GAP = 4f;

    private static final long LIST_DELAY_MS = 140L;
    private static final long LIST_OPEN_MS = 180L;
    private static final long LIST_CLOSE_MS = 260L;
    private static final long SELECT_MS = 160L;

    /** Пружина, по которой подсказка «TAB — Открыть» переезжает между строками. */
    private static final float HINT_STIFFNESS = 300f;
    private static final float HINT_DAMPING = 28f;

    private static final String HINT_TEXT = "TAB - Открыть";
    private static final float TEXT_SIZE = 8f;
    private static final float HINT_SIZE = 7f;

    private final StringBuilder searchText = new StringBuilder();

    int cursorPosition = 0;
    boolean isFocused = false;
    boolean isSelectedAll = false;
    long lastBlink = System.currentTimeMillis();
    boolean showCursor = true;

    long lastDotTick = System.currentTimeMillis();
    int dotsCount = 0;

    @Setter
    private SearchUpdateListener listener;
    private float revealProgress = 1f;

    // ---- выдача ----
    private final List<SearchEntry> results = new ArrayList<>();
    private int selected = 0;
    private float scroll = 0f;

    // ---- анимации ----
    private long openTime = 0L;
    private long lastFrame = 0L;
    private float listProgress = 0f;
    private float listFrom = 0f;
    private float listTarget = 0f;
    private long listStart = 0L;
    private int animatedSelection = -1;
    private long selectionStart = 0L;
    private float hintY = 0f;
    private float hintVelocity = 0f;
    private boolean hintPlaced = false;

    public void setRevealProgress(float revealProgress) {
        this.revealProgress = MathHelper.clamp(revealProgress, 0f, 1f);
    }

    @Override
    public SearchBarComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0L ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        if (isFocused && now - lastBlink > 500) {
            showCursor = !showCursor;
            lastBlink = now;
        }

        if (!isFocused && searchText.isEmpty()) {
            if (now - lastDotTick >= 1000) {
                dotsCount = (dotsCount % 3) + 1;
                lastDotTick = now;
            }
        } else {
            dotsCount = Math.min(dotsCount, 3);
        }

        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        renderBar(context, matrix);
        renderResults(context, matrix, mouseX, mouseY, now, dt);

        cursorPosition = Math.max(0, Math.min(cursorPosition, searchText.length()));
        return this;
    }

    // ------------------------------------------------------------------ строка

    private void renderBar(DrawContext context, Matrix4f matrix) {
        float padding = 5f;

        HelperElements.rectGui(context, getX(), getY(), getWidth(), getHeight(), revealProgress);

        String baseText = searchText.toString();
        int safeCursor = Math.max(0, Math.min(cursorPosition, baseText.length()));
        String leftText = baseText.substring(0, safeCursor);
        String rightText = baseText.substring(safeCursor);

        String displayText;
        if (isFocused) {
            displayText = leftText + (showCursor ? "_" : "") + rightText;
            if (displayText.isEmpty()) displayText = " ";
        } else if (baseText.isEmpty()) {
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < dotsCount; i++) dots.append('.');
            displayText = "Search" + dots;
        } else {
            displayText = baseText;
        }

        float iconSize = 10f;
        float iconWidth = Builder.OTHER_ICONS.get().getWidth("F", iconSize);
        float iconX = getX() + padding + 2f;
        float textY = getY() + (getHeight() - TEXT_SIZE) / 2f - 0.5f;
        int accent = Interface.INSTANCE.getMainColor();

        Builder.TEXT_BUILDER
                .text("F")
                .size(iconSize)
                .font(Builder.OTHER_ICONS.get())
                .color(ColorUtils.setAlpha(accent, (int) ((isFocused ? 210 : 150) * revealProgress)))
                .build()
                .render(matrix, iconX, textY);

        float textStartX = iconX + iconWidth + 6f;
        float textEndX = getX() + getWidth() - padding - (isFocused ? 12f : 2f);

        ScissorUtil.enable(textStartX - 1f, getY() + 1f, textEndX - textStartX + 2f, getHeight() - 2f);

        if (isFocused && isSelectedAll && !baseText.isEmpty()) {
            float selectionWidth = Builder.INTER.get().getWidth(baseText, TEXT_SIZE);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(selectionWidth + 3f, getHeight() - 9f))
                    .color(new QuadColorState(ColorUtils.rgba(50, 100, 200, (int) (100 * revealProgress))))
                    .radius(new QuadRadiusState(2f))
                    .build()
                    .render(matrix, textStartX - 1.5f, getY() + 4.5f);
        }

        int textColor = isFocused
                ? HelperElements.themedText((int) (215 * revealProgress))
                : ColorUtils.setAlpha(accent, (int) (150 * revealProgress));

        Builder.TEXT_BUILDER
                .text(displayText)
                .size(TEXT_SIZE)
                .font(Builder.INTER.get())
                .color(textColor)
                .build()
                .render(matrix, textStartX, textY);

        ScissorUtil.disable();

        if (isFocused) {
            drawCross(matrix, closeCenterX(), getY() + getHeight() / 2f, 7f,
                    HelperElements.themedText((int) (170 * revealProgress)));
        }
    }

    /** Крестик собран из двух повёрнутых полосок — в иконочном шрифте клиента его нет. */
    private void drawCross(Matrix4f matrix, float centerX, float centerY, float size, int color) {
        for (float degrees : new float[]{45f, -45f}) {
            Matrix4f rotated = new Matrix4f(matrix)
                    .translate(centerX, centerY, 0f)
                    .rotateZ((float) Math.toRadians(degrees))
                    .translate(-centerX, -centerY, 0f);

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(size, 1f))
                    .color(new QuadColorState(color))
                    .radius(new QuadRadiusState(0.5f))
                    .build()
                    .render(rotated, centerX - size / 2f, centerY - 0.5f);
        }
    }

    private float closeCenterX() {
        return getX() + getWidth() - 9f;
    }

    // ------------------------------------------------------------------ выдача

    private void renderResults(DrawContext context, Matrix4f matrix, int mouseX, int mouseY, long now, float dt) {
        boolean wantList = isFocused && now - openTime > LIST_DELAY_MS && !results.isEmpty();
        float target = wantList ? 1f : 0f;

        if (target != listTarget) {
            listTarget = target;
            listFrom = listProgress;
            listStart = now;
        }

        long duration = target == 1f ? LIST_OPEN_MS : LIST_CLOSE_MS;
        float phase = MathHelper.clamp((now - listStart) / (float) duration, 0f, 1f);
        listProgress = listFrom + (listTarget - listFrom) * easeOutQuart(phase);

        if (listProgress <= 0.002f || results.isEmpty()) return;

        float contentHeight = Math.min(LIST_MAX_HEIGHT, results.size() * ROW_HEIGHT + LIST_PADDING * 2f);
        float listHeight = contentHeight * listProgress;
        float listY = listTop(contentHeight, listHeight);
        float alpha = revealProgress * listProgress;

        HelperElements.rectGui(context, getX(), listY, getWidth(), listHeight, alpha);

        scroll = MathHelper.clamp(scroll, 0f, maxScroll(contentHeight));

        ScissorUtil.enable(getX(), listY, getWidth(), listHeight);

        boolean mouseInside = MouseClick.isClick(mouseX, mouseY, getX(), listY, getWidth(), listHeight);
        float hintTarget = listY + LIST_PADDING;

        for (int i = 0; i < results.size(); i++) {
            float rowY = listY + LIST_PADDING + i * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT < listY || rowY > listY + listHeight) continue;

            if (mouseInside && MouseClick.isClick(mouseX, mouseY, getX(), rowY, getWidth(), ROW_HEIGHT)) {
                select(i, now);
            }

            if (i == selected) hintTarget = rowY;

            renderRow(matrix, results.get(i), i, rowY, alpha, now);
        }

        tickHint(hintTarget, dt);
        if (selected >= 0 && selected < results.size()) {
            renderHint(matrix, alpha * selectionProgress(now));
        }

        ScissorUtil.disable();
    }

    private void renderRow(Matrix4f matrix, SearchEntry entry, int index, float rowY, float alpha, long now) {
        float selectionAlpha = index == selected ? selectionProgress(now) : 0f;

        if (selectionAlpha > 0.01f) {
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(getWidth() - 8f, ROW_HEIGHT - 2f))
                    .color(new QuadColorState(ColorUtils.setAlpha(
                            Interface.INSTANCE.getMainColor(), (int) (38 * selectionAlpha * alpha))))
                    .radius(new QuadRadiusState(3f))
                    .build()
                    .render(matrix, getX() + 4f, rowY + 1f);
        }

        float textY = rowY + (ROW_HEIGHT - TEXT_SIZE) / 2f - 0.5f;
        float textX = getX() + 10f;

        // Настройка показывается с префиксом модуля, чтобы было видно, откуда она.
        if (entry.isSetting()) {
            String owner = entry.module().getName();
            Builder.TEXT_BUILDER
                    .text(owner)
                    .size(TEXT_SIZE)
                    .font(Builder.INTER.get())
                    .color(HelperElements.themedText((int) (110 * alpha)))
                    .build()
                    .render(matrix, textX, textY);

            float ownerWidth = Builder.INTER.get().getWidth(owner, TEXT_SIZE);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(2f, 2f))
                    .color(new QuadColorState(HelperElements.themedText((int) (110 * alpha))))
                    .radius(new QuadRadiusState(1f))
                    .build()
                    .render(matrix, textX + ownerWidth + 2f, rowY + ROW_HEIGHT / 2f - 1f);

            textX += ownerWidth + 8f;
        }

        float labelLimit = (index == selected ? hintLeftX() - 6f : getX() + getWidth() - 10f) - textX;
        ScissorUtil.enable(textX, rowY, Math.max(0f, labelLimit), ROW_HEIGHT);
        Builder.TEXT_BUILDER
                .text(entry.label())
                .size(TEXT_SIZE)
                .font(Builder.INTER.get())
                .color(HelperElements.themedText((int) (225 * alpha)))
                .build()
                .render(matrix, textX, textY);
        ScissorUtil.disable();
    }

    /** Подсказка едет за выделением по пружине, а не прыгает между строками. */
    private void renderHint(Matrix4f matrix, float alpha) {
        int color = HelperElements.themedText((int) (130 * alpha));
        float textY = hintY + (ROW_HEIGHT - HINT_SIZE) / 2f - 0.5f;

        Builder.TEXT_BUILDER
                .text(HINT_TEXT)
                .size(HINT_SIZE)
                .font(Builder.INTER.get())
                .color(color)
                .build()
                .render(matrix, hintLeftX(), textY);

        float chevronSize = 5f;
        float chevronX = getX() + getWidth() - 10f - Builder.OTHER_ICONS.get().getWidth("G", chevronSize);
        Builder.TEXT_BUILDER
                .text("H")
                .size(chevronSize)
                .font(Builder.OTHER_ICONS.get())
                .color(color)
                .build()
                .render(matrix, chevronX, hintY + ROW_HEIGHT / 2f - chevronSize - 0.5f);
        Builder.TEXT_BUILDER
                .text("G")
                .size(chevronSize)
                .font(Builder.OTHER_ICONS.get())
                .color(color)
                .build()
                .render(matrix, chevronX, hintY + ROW_HEIGHT / 2f + 0.5f);
    }

    private float hintLeftX() {
        float chevronWidth = Builder.OTHER_ICONS.get().getWidth("G", 5f);
        return getX() + getWidth() - 10f - chevronWidth - 3f - Builder.INTER.get().getWidth(HINT_TEXT, HINT_SIZE);
    }

    private void tickHint(float targetY, float dt) {
        if (!hintPlaced || dt <= 0f) {
            hintY = targetY;
            hintVelocity = 0f;
            hintPlaced = true;
            return;
        }

        float force = (targetY - hintY) * HINT_STIFFNESS - hintVelocity * HINT_DAMPING;
        hintVelocity += force * dt;
        hintY += hintVelocity * dt;
    }

    private float selectionProgress(long now) {
        return easeOutQuart(MathHelper.clamp((now - selectionStart) / (float) SELECT_MS, 0f, 1f));
    }

    private static float easeOutQuart(float x) {
        float inverted = 1f - x;
        return 1f - inverted * inverted * inverted * inverted;
    }

    /** Список раскрывается вниз, а если снизу не хватает места — вверх. */
    private float listTop(float contentHeight, float listHeight) {
        float below = getY() + getHeight() + LIST_GAP;
        boolean fitsBelow = below + contentHeight <= GLUtils.getScaledHeightFor(2) - 10f;
        return fitsBelow ? below : getY() - LIST_GAP - listHeight;
    }

    private float maxScroll(float contentHeight) {
        return Math.max(0f, results.size() * ROW_HEIGHT + LIST_PADDING * 2f - contentHeight);
    }

    private float visibleHeight() {
        return Math.min(LIST_MAX_HEIGHT, results.size() * ROW_HEIGHT + LIST_PADDING * 2f);
    }

    // ------------------------------------------------------------------ данные

    private void rebuild() {
        results.clear();
        results.addAll(SearchResults.collect(searchText.toString()));
        select(Math.min(selected, Math.max(0, results.size() - 1)), System.currentTimeMillis());
        scroll = MathHelper.clamp(scroll, 0f, maxScroll(visibleHeight()));
    }

    private void select(int index, long now) {
        if (results.isEmpty()) {
            selected = 0;
            return;
        }

        int clamped = MathHelper.clamp(index, 0, results.size() - 1);
        if (clamped != animatedSelection) {
            animatedSelection = clamped;
            selectionStart = now;
        }
        selected = clamped;
    }

    private void moveSelection(int step) {
        if (results.isEmpty()) return;

        select(selected + step, System.currentTimeMillis());
        scrollIntoView();
    }

    private void scrollIntoView() {
        float visible = visibleHeight();
        float top = selected * ROW_HEIGHT;
        float bottom = top + ROW_HEIGHT;

        if (top < scroll) {
            scroll = top;
        } else if (bottom > scroll + visible - LIST_PADDING * 2f) {
            scroll = bottom - (visible - LIST_PADDING * 2f);
        }

        scroll = MathHelper.clamp(scroll, 0f, maxScroll(visible));
    }

    /**
     * TAB — оставить в панелях только этот модуль, раскрыть его настройки,
     * если искали настройку, и подсветить найденное.
     */
    private void openSelected() {
        if (results.isEmpty()) return;

        SearchEntry entry = results.get(MathHelper.clamp(selected, 0, results.size() - 1));
        if (listener != null) listener.onSearchUpdate(entry.module().getName().toLowerCase());
        SearchSpotlight.focus(entry.module(), entry.setting());

        searchText.setLength(0);
        cursorPosition = 0;
        setListening(false);
    }

    /** Enter — переключить прямо из выдачи, не закрывая поиск. */
    private void toggleSelected() {
        if (results.isEmpty()) return;

        SearchEntry entry = results.get(MathHelper.clamp(selected, 0, results.size() - 1));
        if (entry.setting() == null) {
            entry.module().toggle();
        } else if (entry.setting() instanceof CheckBox checkBox) {
            checkBox.applyDefault(!Boolean.TRUE.equals(checkBox.getEnabled()));
        }
    }

    // ------------------------------------------------------------------ ввод

    public void setListening(boolean listening) {
        boolean wasFocused = this.isFocused;
        this.isFocused = listening;
        LynxWave.getInstance().setFocusedRender(listening);

        if (listening) {
            if (!wasFocused) {
                openTime = System.currentTimeMillis();
                scroll = 0f;
                selected = 0;
                animatedSelection = -1;
                hintPlaced = false;
            }
            rebuild();
        } else {
            this.isSelectedAll = false;
            this.cursorPosition = searchText.length();
            results.clear();
        }
    }

    public String getSearchText() {
        return searchText.toString().toLowerCase();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFocused) {
            if (MouseClick.isClick(mouseX, mouseY, closeCenterX() - 7f, getY(), 14f, getHeight())) {
                clearAndClose();
                return true;
            }

            float listHeight = visibleHeight() * listProgress;
            float listY = listTop(visibleHeight(), listHeight);

            if (listProgress > 0.5f && MouseClick.isClick(mouseX, mouseY, getX(), listY, getWidth(), listHeight)) {
                int row = (int) Math.floor((mouseY - (listY + LIST_PADDING) + scroll) / ROW_HEIGHT);
                if (row >= 0 && row < results.size()) {
                    select(row, System.currentTimeMillis());
                    openSelected();
                }
                return true;
            }
        }

        if (MouseClick.isClick(mouseX, mouseY, getX(), getY(), getWidth(), getHeight())) {
            setListening(true);
            isSelectedAll = false;
            cursorPosition = searchText.length();
            return true;
        }

        // Клик мимо просто закрывает поиск и уходит дальше, к панелям.
        if (isFocused) setListening(false);
        return false;
    }

    private void clearAndClose() {
        searchText.setLength(0);
        cursorPosition = 0;
        if (listener != null) listener.onSearchUpdate("");
        SearchSpotlight.clear();
        setListening(false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isFocused || results.isEmpty()) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);

        float contentHeight = visibleHeight();
        float listY = listTop(contentHeight, contentHeight * listProgress);
        if (!MouseClick.isClick(mouseX, mouseY, getX(), listY, getWidth(), contentHeight)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        scroll = MathHelper.clamp(scroll - (float) verticalAmount * ROW_HEIGHT, 0f, maxScroll(contentHeight));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused) return super.charTyped(chr, modifiers);

        if (Character.isLetterOrDigit(chr) || Character.isWhitespace(chr)) {
            if (isSelectedAll) {
                searchText.setLength(0);
                isSelectedAll = false;
                cursorPosition = 0;
            }

            int safeCursor = Math.max(0, Math.min(cursorPosition, searchText.length()));
            searchText.insert(safeCursor, chr);
            cursorPosition = safeCursor + 1;

            selected = 0;
            scroll = 0f;
            rebuild();
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            setListening(true);
            isSelectedAll = false;
            return true;
        }

        if (!isFocused) return super.keyPressed(keyCode, scanCode, modifiers);

        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            isSelectedAll = true;
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                clearAndClose();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveSelection(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                openSelected();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                toggleSelected();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursorPosition > 0) cursorPosition--;
                isSelectedAll = false;
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursorPosition < searchText.length()) cursorPosition++;
                isSelectedAll = false;
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (isSelectedAll) {
                    searchText.setLength(0);
                    cursorPosition = 0;
                    isSelectedAll = false;
                } else if (cursorPosition > 0 && !searchText.isEmpty()) {
                    searchText.deleteCharAt(cursorPosition - 1);
                    cursorPosition--;
                }
                selected = 0;
                scroll = 0f;
                rebuild();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursorPosition = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursorPosition = searchText.length();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }
}
