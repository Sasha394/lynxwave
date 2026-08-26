package dev.client.api.nullcry.uiClient.title;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.GLUtils;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class UpdatesListScreen extends Screen implements ClientApi {

    private static final float PANEL_W = 400f;
    private static final float PANEL_H = 440f;
    private static final float ROW_H = 26f;
    private static final float BETWEEN = 4f;
    private static final float PAD = 20f;
    private static final float BTN_H = 26f;
    private static final float SCROLL_BAR_W = 3f;
    private static final float RADIUS = 6f;
    private static final float DIVIDER_H = 1f;
    private static final float HEADER_H = 48f;

    private final Screen parent;
    private final List<UpdateEntry> entries = new ArrayList<>();
    private float panelX, panelY;
    private float listScroll = 0f;
    private float listTarget = 0f;
    private float listY, listH;
    private boolean renderHandCursor = false;

    private final CompactAnimation alphaAnimation = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    private float renderAlpha = 1f;
    private boolean closing = false;
    private Runnable closeCallback = null;

    public UpdatesListScreen(Screen parent) {
        super(Text.literal(""));
        this.parent = parent;



        // v1.5
        entries.add(new UpdateEntry("=", "KillAura переписан"));
        entries.add(new UpdateEntry("+", "TotemSound добавлен"));
        entries.add(new UpdateEntry("+", "UseTracker добавлен"));
        entries.add(new UpdateEntry("=", "BlockHighLight — добавлена опция шейдера"));
        entries.add(new UpdateEntry("=", "ElytraHelper переписан"));
        entries.add(new UpdateEntry("=", "Speed -> AresMine"));
        entries.add(new UpdateEntry("+", "AutoWarden для FunTime 1.21"));
        entries.add(new UpdateEntry("=", "ClickGui — изменён дизайн ColorPicker"));
        entries.add(new UpdateEntry("=", "Interface переписан"));
        entries.add(new UpdateEntry("+", "Interface -> Белый (весь худ становится белым)"));
        entries.add(new UpdateEntry("+", "Interface -> Тема минимализма -> Кастом (свой цвет худа и текста)"));
        entries.add(new UpdateEntry("=", "Исправлен баг с Notification, ломавший функционал на некоторых ПК/телефонах"));
        entries.add(new UpdateEntry("-", "ElytraAura он посути нахуй не нужен"));

    }

    @Override
    protected void init() {
        if (client != null) {
            width = GLUtils.getScaledWidthFor(2);
            height = GLUtils.getScaledHeightFor(2);
        }
        panelX = (width - PANEL_W) / 2f;
        panelY = (height - PANEL_H) / 2f;
        listY = panelY + HEADER_H + 8f;
        listH = PANEL_H - HEADER_H - 8f - BTN_H - PAD - 10f;
        alphaAnimation.setValue(0.0);
        alphaAnimation.setDestinationValue(1.0);
        alphaAnimation.reset();
        renderAlpha = 0f;
        closing = false;
        closeCallback = null;
        CursorsUtil.resetState();
        clampScroll();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        GLUtils.setupOverlayRendering(2);
        boolean restored = false;
        try {
            CursorsUtil.beginFrame();
            renderHandCursor = false;

            alphaAnimation.run(closing ? 0.0 : 1.0);
            alphaAnimation.update();
            renderAlpha = clamp01((float) alphaAnimation.getValue());

            if (closing && renderAlpha <= 0.01f) {
                if (closeCallback != null) {
                    Runnable cb = closeCallback;
                    closeCallback = null;
                    closing = false;
                    CursorsUtil.applyFrameCursor();
                    CursorsUtil.resetState();
                    restored = true;
                    GLUtils.restoreRendering();
                    cb.run();
                    return;
                }
            }

            if (client != null) {
                width = GLUtils.getScaledWidthFor(2);
                height = GLUtils.getScaledHeightFor(2);
            }
            panelX = (width - PANEL_W) / 2f;
            panelY = (height - PANEL_H) / 2f;
            listY = panelY + HEADER_H + 8f;
            listH = PANEL_H - HEADER_H - 8f - BTN_H - PAD - 10f;

            int renderMouseX = (int) Math.round(GLUtils.scaleMouseX(mouseX, 2));
            int renderMouseY = (int) Math.round(GLUtils.scaleMouseY(mouseY, 2));

            Matrix4f m = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);

            String backgroundPath = TitleScreenSettings.getBackgroundPath();
            Builder.TEXTURE_BUILDER
                    .size(new SizeState(width, height))
                    .texture(0, 0, 1, 1, ClientTexture.of(backgroundPath))
                    .color(TitleBackgroundRenderer.resolveTextureColor(backgroundPath))
                    .build()
                    .render(m, 0, 0);

            int baseMain = Interface.INSTANCE.getMainColor();
            int baseText = -1;
            int main = applyAlpha(baseMain, renderAlpha);
            int text = applyAlpha(baseText, renderAlpha);
            int dimText = scaleAlpha(text, 0.55f);

            HelperElements.rect(m, panelX, panelY, PANEL_W, PANEL_H, renderAlpha);

            // === Header strip (inset so panel's rounded corners show through) ===
            int headerTint = scaleAlpha(main, 0.06f);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(PANEL_W - 2f, HEADER_H))
                    .color(new QuadColorState(headerTint))
                    .radius(new QuadRadiusState(3.5f))
                    .build().render(m, panelX + 1f, panelY + 1f);

            // Accent bar on left of header
            int accentColor = applyAlpha(baseMain, renderAlpha);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(3f, HEADER_H - 14f))
                    .color(new QuadColorState(accentColor))
                    .radius(new QuadRadiusState(1.5f))
                    .build().render(m, panelX + PAD, panelY + 7f);

            // Title + subtitle
            String title = "Список обновлений";
            String subtitle = "Что нового в LynxWave";
            float titleX = panelX + PAD + 10f;
            Builder.TEXT_BUILDER.text(title).size(10.5f).font(Builder.INTER.get()).color(text).build()
                    .render(m, titleX, panelY + 11f);
            Builder.TEXT_BUILDER.text(subtitle).size(6.5f).font(Builder.INTER.get()).color(scaleAlpha(text, 0.45f)).build()
                    .render(m, titleX, panelY + 25f);

            // Version pill on the right
            String versionStr = "v1.6";
            float verW = Builder.INTER.get().getWidth(versionStr, 7f);
            float verPillW = verW + 12f;
            float verPillH = 15f;
            float verPillX = panelX + PANEL_W - PAD - verPillW;
            float verPillY = panelY + (HEADER_H - verPillH) / 2f - 6f;
            int verPillBg = applyAlpha(baseMain, renderAlpha * 0.18f);
            Builder.RECTANGLE_BUILDER.size(new SizeState(verPillW, verPillH)).color(new QuadColorState(verPillBg))
                    .radius(new QuadRadiusState(verPillH / 2f)).build().render(m, verPillX, verPillY);
            Builder.BORDER_BUILDER.size(new SizeState(verPillW, verPillH)).color(new QuadColorState(scaleAlpha(main, 0.35f)))
                    .radius(new QuadRadiusState(verPillH / 2f)).thickness(-0.3f).build().render(m, verPillX, verPillY);
            Builder.TEXT_BUILDER.text(versionStr).size(7f).font(Builder.INTER.get()).color(accentColor).build()
                    .render(m, verPillX + (verPillW - verW) / 2f, verPillY + (verPillH - 7f) / 2f);

            // Entry count below version
            String countStr = entries.size() + " записей";
            float countW = Builder.INTER.get().getWidth(countStr, 6f);
            Builder.TEXT_BUILDER.text(countStr).size(6f).font(Builder.INTER.get()).color(scaleAlpha(text, 0.4f)).build()
                    .render(m, panelX + PANEL_W - PAD - countW, verPillY + verPillH + 5f);

            // Accent line under header
            int divColor = scaleAlpha(main, 0.2f);
            Builder.RECTANGLE_BUILDER.size(new SizeState(PANEL_W - PAD, DIVIDER_H))
                    .color(new QuadColorState(divColor)).radius(new QuadRadiusState(0.5f)).build()
                    .render(m, panelX + PAD / 2f, panelY + HEADER_H + 1f);

            // === List ===
            listScroll += (listTarget - listScroll) * 0.18f;
            float contentH = entries.size() * (ROW_H + BETWEEN) - BETWEEN;
            float listW = PANEL_W - PAD * 2f - SCROLL_BAR_W - 6f;

            ScissorUtil.enableContext(ctx, panelX + PAD, listY, listW, listH);
            float cy = listY - listScroll;
            for (int i = 0; i < entries.size(); i++) {
                UpdateEntry entry = entries.get(i);
                float cardY = cy + i * (ROW_H + BETWEEN);
                if (cardY > listY - ROW_H && cardY < listY + listH) {
                    boolean hover = MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, cardY, listW, ROW_H);

                    // Tag colors by type
                    int tagColor;
                    String tagText;
                    String tagLabel;
                    switch (entry.tag) {
                        case "+" -> { tagColor = ColorUtils.rgb(80, 220, 120); tagText = "+"; tagLabel = "NEW"; }
                        case "-" -> { tagColor = ColorUtils.rgb(255, 90, 90);  tagText = "-"; tagLabel = "DEL"; }
                        default  -> { tagColor = ColorUtils.rgb(120, 180, 255); tagText = "="; tagLabel = "UPD"; }
                    }
                    int tagAlpha = applyAlpha(tagColor, renderAlpha);

                    // Card background (always visible, brighter on hover)
                    int cardBg = scaleAlpha(main, hover ? 0.1f : 0.04f);
                    Builder.RECTANGLE_BUILDER.size(new SizeState(listW, ROW_H)).color(new QuadColorState(cardBg))
                            .radius(new QuadRadiusState(5f)).build().render(m, panelX + PAD, cardY);

                    // Left accent stripe (tag-colored)
                    int stripeColor = applyAlpha(tagColor, renderAlpha * (hover ? 1f : 0.85f));
                    Builder.RECTANGLE_BUILDER
                            .size(new SizeState(2.5f, ROW_H - 8f))
                            .color(new QuadColorState(stripeColor))
                            .radius(new QuadRadiusState(1.25f))
                            .build().render(m, panelX + PAD + 4f, cardY + 4f);

                    // Tag pill (label "NEW"/"UPD"/"DEL" + icon)
                    float pillW = 26f;
                    float pillH = 13f;
                    float pillX = panelX + PAD + 12f;
                    float pillY = cardY + (ROW_H - pillH) / 2f;
                    int pillBg = applyAlpha(tagColor, renderAlpha * 0.18f);
                    Builder.RECTANGLE_BUILDER.size(new SizeState(pillW, pillH)).color(new QuadColorState(pillBg))
                            .radius(new QuadRadiusState(pillH / 2f)).build().render(m, pillX, pillY);

                    float labelW = Builder.INTER.get().getWidth(tagLabel, 6f);
                    float labelH = Builder.INTER.get().getHeight(tagLabel, 6f);
                    Builder.TEXT_BUILDER.text(tagLabel).size(6f).font(Builder.INTER.get()).color(tagAlpha).build()
                            .render(m, pillX + (pillW - labelW) / 2f, pillY + (pillH - labelH) / 2f - 0.3f);

                    // Description
                    float descX = pillX + pillW + 10f;
                    float maxDescW = listW - (descX - panelX - PAD) - 8f;
                    String desc = entry.description;
                    if (Builder.INTER.get().getWidth(desc, 7.5f) > maxDescW) {
                        while (desc.length() > 4 && Builder.INTER.get().getWidth(desc + "...", 7.5f) > maxDescW)
                            desc = desc.substring(0, desc.length() - 1);
                        desc = desc + "...";
                    }
                    int descColor = scaleAlpha(text, hover ? 1f : 0.78f);
                    float descH = Builder.INTER.get().getHeight(desc, 7.5f);
                    Builder.TEXT_BUILDER.text(desc).size(7.5f).font(Builder.INTER.get()).color(descColor).build()
                            .render(m, descX, cardY + (ROW_H - descH) / 2f - 0.3f);
                }
            }
            ScissorUtil.disableContext(ctx);

            // === Scrollbar ===
            float barX = panelX + PANEL_W - PAD - SCROLL_BAR_W;
            float maxScroll = Math.max(0f, contentH - listH);
            if (maxScroll > 0) {
                // Track
                int trackColor = scaleAlpha(main, 0.08f);
                Builder.RECTANGLE_BUILDER.size(new SizeState(SCROLL_BAR_W, listH)).color(new QuadColorState(trackColor))
                        .radius(new QuadRadiusState(2f)).build().render(m, barX, listY);

                // Thumb
                float thumbH = Math.max(20f, listH * (listH / contentH));
                float thumbY = listY + (listScroll / maxScroll) * (listH - thumbH);
                int thumbColor = scaleAlpha(main, 0.35f);
                Builder.RECTANGLE_BUILDER.size(new SizeState(SCROLL_BAR_W, thumbH)).color(new QuadColorState(thumbColor))
                        .radius(new QuadRadiusState(2f)).build().render(m, barX, thumbY);
            }

            // === Divider before button ===
            float btnDivY = panelY + PANEL_H - PAD - BTN_H - 10f;
            Builder.RECTANGLE_BUILDER.size(new SizeState(PANEL_W - PAD * 2f, DIVIDER_H))
                    .color(new QuadColorState(scaleAlpha(main, 0.12f))).radius(new QuadRadiusState(0.5f)).build()
                    .render(m, panelX + PAD, btnDivY);

            // === Back button (accent filled) ===
            float backY = panelY + PANEL_H - PAD - BTN_H;
            float backW = PANEL_W - PAD * 2f;
            float backX = panelX + PAD;
            boolean hoverBack = MouseClick.isClick(renderMouseX, renderMouseY, backX, backY, backW, BTN_H);
            if (hoverBack) renderHandCursor = true;

            int backBgTop = applyAlpha(baseMain, renderAlpha * (hoverBack ? 0.9f : 0.7f));
            int backBgBot = applyAlpha(baseMain, renderAlpha * (hoverBack ? 0.7f : 0.5f));
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(backW, BTN_H))
                    .color(new QuadColorState(backBgTop, backBgTop, backBgBot, backBgBot))
                    .radius(new QuadRadiusState(RADIUS))
                    .build().render(m, backX, backY);

            String backLabel = "← Назад";
            float bw = Builder.INTER.get().getWidth(backLabel, 8.5f);
            float backLabelH = Builder.INTER.get().getHeight(backLabel, 8.5f);
            Builder.TEXT_BUILDER.text(backLabel).size(8.5f).font(Builder.INTER.get())
                    .color(applyAlpha(-1, renderAlpha)).build()
                    .render(m, backX + (backW - bw) / 2f, backY + (BTN_H - backLabelH) / 2f - 0.3f);

            if (renderHandCursor) CursorsUtil.setCursor(CursorsUtil.HAND);
            CursorsUtil.applyFrameCursor();
        } finally {
            if (!restored) GLUtils.restoreRendering();
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (closing) return true;
        if (button != 0) return super.mouseClicked(click, doubled);

        double renderMouseX = GLUtils.scaleMouseX(mouseX, 2);
        double renderMouseY = GLUtils.scaleMouseY(mouseY, 2);

        float backY = panelY + PANEL_H - PAD - BTN_H;
        float backW = PANEL_W - PAD * 2f;
        float backX = panelX + PAD;

        if (MouseClick.isClick(renderMouseX, renderMouseY, backX, backY, backW, BTN_H)) {
            startClose();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float contentH = entries.size() * (ROW_H + BETWEEN) - BETWEEN;
        float maxScroll = Math.max(0f, contentH - listH);
        listTarget = (float) Math.max(0, Math.min(maxScroll, listTarget - verticalAmount * 24));
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == 256) {
            startClose();
            return true;
        }
        return super.keyPressed(input);
    }

    private void startClose() {
        if (closing) return;
        closing = true;
        closeCallback = () -> {
            if (client != null) client.setScreen(parent);
        };
        double current = clamp01((float) alphaAnimation.getValue());
        alphaAnimation.setValue(current);
        alphaAnimation.setDestinationValue(0.0);
        alphaAnimation.reset();
    }

    private void clampScroll() {
        float contentH = entries.size() * (ROW_H + BETWEEN) - BETWEEN;
        float max = Math.max(0f, contentH - listH);
        listTarget = Math.min(Math.max(listTarget, 0f), max);
        listScroll = Math.min(Math.max(listScroll, 0f), max);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private int applyAlpha(int color, float factor) {
        int baseAlpha = ColorUtils.alpha(color);
        int newAlpha = Math.round(baseAlpha * clamp01(factor));
        return ColorUtils.setAlpha(color, newAlpha);
    }

    private int scaleAlpha(int color, float scale) {
        int base = ColorUtils.alpha(color);
        int newAlpha = Math.round(base * clamp01(scale));
        return ColorUtils.setAlpha(color, newAlpha);
    }

    private static class UpdateEntry {
        final String tag;
        final String description;

        UpdateEntry(String tag, String description) {
            this.tag = tag;
            this.description = description;
        }
    }
}
