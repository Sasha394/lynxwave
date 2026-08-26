package dev.client.api.nullcry.uiClient.clickGui.components.config;

import dev.client.LynxWave;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.cmdHelper.managers.configuration.ConfigurationManager;
import dev.client.api.nullcry.cmdHelper.managers.configuration.Configuration;
import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class ConfigManagerComponent extends Component {

    private float revealProgress = 1f;
    private final StringBuilder nameInput = new StringBuilder();
    private boolean nameFieldFocused = false;
    private float scroll = 0f;
    private float animScroll = 0f;

    private boolean createInlineOpen = false;
    private boolean sortNewestFirst = true;

    private static final float HEADER_H = 52f;
    private static final float INLINE_INPUT_H = 20f;
    private static final float ENTRY_H = 32f;
    private static final float PAD = 14f;

    public void setRevealProgress(float progress) {
        this.revealProgress = MathHelper.clamp(progress, 0f, 1f);
    }

    @Override
    public ConfigManagerComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (revealProgress < 0.01f) return this;

        animScroll = MathHelper.lerp(0.2f, animScroll, scroll);
        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        float panelX = getX();
        float panelY = getY();
        float panelW = getWidth();
        float panelH = getHeight();

        HelperElements.rectGui(context, panelX, panelY, panelW, panelH, revealProgress);

        int mainColor = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : -1;

        // ─── Top-left pill tag "CONFIGURATIONS" ──────────────────────────────
        String tag = "CONFIGURATIONS";
        float tagSize = 5.5f;
        float tagTextW = Builder.INTER.get().getWidth(tag, tagSize);
        float tagW = tagTextW + 12f;
        float tagH = 13f;
        float tagX = panelX + PAD;
        float tagY = panelY + PAD;

        int tagBg = ColorUtils.setAlpha(mainColor, (int) (38 * revealProgress));
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(tagW, tagH))
                .color(new QuadColorState(tagBg))
                .radius(new QuadRadiusState(3f))
                .build()
                .render(matrix, tagX, tagY);

        Builder.TEXT_BUILDER
                .size(tagSize)
                .font(Builder.INTER.get())
                .color(ColorUtils.setAlpha(mainColor, (int) (255 * revealProgress)))
                .text(tag)
                .build()
                .render(matrix, tagX + 6f, tagY + (tagH - Builder.INTER.get().getHeight(tag, tagSize)) / 2f - 0.5f);

        // ─── Title "Available configurations" ────────────────────────────────
        float titleSize = 10.5f;
        float titleY = tagY + tagH + 6f;
        Builder.TEXT_BUILDER
                .size(titleSize)
                .font(Builder.INTER.get())
                .color(HelperElements.themedText((int) (255 * revealProgress)))
                .text("Available configurations")
                .build()
                .render(matrix, panelX + PAD, titleY);

        // ─── Right-side action buttons: Create / Import / Newest first ──────
        float btnY = titleY - 1f;
        float btnH = 16f;
        float btnGap = 12f;
        float sortW = drawToolbarButton(matrix, mouseX, mouseY, panelX + panelW - PAD, btnY, btnH,
                (sortNewestFirst ? "Newest first" : "Oldest first"), "▾", true);
        float importX = panelX + panelW - PAD - sortW - btnGap;
        float importW = drawToolbarButton(matrix, mouseX, mouseY, importX, btnY, btnH, "Import", "↓", false);
        float createX = importX - importW - btnGap;
        drawToolbarButton(matrix, mouseX, mouseY, createX, btnY, btnH, "Create", "✦", false);

        // ─── Inline name input (shown when Create clicked) ──────────────────
        float listY = titleY + titleSize + 14f;

        if (createInlineOpen) {
            float inputY = listY;
            float saveBtnW = 48f;
            float inputW = panelW - PAD * 2f - saveBtnW - 6f;
            float inputX = panelX + PAD;

            int inputBg = HelperElements.themedPanel(22, 24, 28, (int) (200 * revealProgress));
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(inputW, INLINE_INPUT_H))
                    .color(new QuadColorState(inputBg))
                    .radius(new QuadRadiusState(4f))
                    .build()
                    .render(matrix, inputX, inputY);

            int inputBorderA = (int) ((nameFieldFocused ? 90 : 30) * revealProgress);
            Builder.BORDER_BUILDER
                    .size(new SizeState(inputW, INLINE_INPUT_H))
                    .radius(new QuadRadiusState(4f))
                    .color(new QuadColorState(ColorUtils.setAlpha(mainColor, inputBorderA)))
                    .thickness(-0.1f)
                    .build()
                    .render(matrix, inputX, inputY);

            String displayInput = nameInput.isEmpty() ? "Config name..." : nameInput.toString();
            int inputTextCol = nameInput.isEmpty()
                    ? ColorUtils.setAlpha(ColorUtils.rgb(120, 120, 125), (int) (200 * revealProgress))
                    : HelperElements.themedText((int) (230 * revealProgress));

            ScissorUtil.enable(inputX + 2f, inputY, inputW - 4f, INLINE_INPUT_H);
            Builder.TEXT_BUILDER
                    .size(7f)
                    .font(Builder.INTER.get())
                    .color(inputTextCol)
                    .text(nameFieldFocused && !nameInput.isEmpty() ? displayInput + "_" : displayInput)
                    .build()
                    .render(matrix, inputX + 7f, inputY + (INLINE_INPUT_H - Builder.INTER.get().getHeight(displayInput, 7f)) / 2f - 0.5f);
            ScissorUtil.disable();

            float saveX = inputX + inputW + 6f;
            boolean saveHovered = MouseClick.isClick(mouseX, mouseY, saveX, inputY, saveBtnW, INLINE_INPUT_H);
            if (saveHovered) CursorsUtil.setCursor(CursorsUtil.HAND);

            int saveBg = ColorUtils.setAlpha(mainColor, (int) ((saveHovered ? 200 : 140) * revealProgress));
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(saveBtnW, INLINE_INPUT_H))
                    .color(new QuadColorState(saveBg))
                    .radius(new QuadRadiusState(4f))
                    .build()
                    .render(matrix, saveX, inputY);

            String saveLabel = "Save";
            float saveTextW = Builder.INTER.get().getWidth(saveLabel, 7f);
            Builder.TEXT_BUILDER
                    .size(7f)
                    .font(Builder.INTER.get())
                    .color(HelperElements.themedText((int) (255 * revealProgress)))
                    .text(saveLabel)
                    .build()
                    .render(matrix, saveX + (saveBtnW - saveTextW) / 2f, inputY + (INLINE_INPUT_H - Builder.INTER.get().getHeight(saveLabel, 7f)) / 2f - 0.5f);

            listY += INLINE_INPUT_H + 10f;
        }

        // ─── Divider line under the header ──────────────────────────────────
        float divY = listY - 6f;
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(panelW - PAD * 2f, 0.6f))
                .color(new QuadColorState(ColorUtils.setAlpha(ColorUtils.rgb(50, 52, 58), (int) (140 * revealProgress))))
                .build()
                .render(matrix, panelX + PAD, divY);

        // ─── Config list ────────────────────────────────────────────────────
        float listH = panelH - (listY - panelY) - 6f;
        ScissorUtil.enable(panelX + 2f, listY, panelW - 4f, listH);

        ConfigurationManager configManager = LynxWave.getInstance().getConfigurationManager();
        List<Configuration> configs = new ArrayList<>(configManager.getConfigs());
        configs.sort(Comparator.comparingLong((Configuration c) -> c.getFile().lastModified())
                .reversed());
        if (!sortNewestFirst) {
            java.util.Collections.reverse(configs);
        }
        String currentName = configManager.getCurrentConfigurationName();

        if (configs.isEmpty()) {
            String emptyText = "Пока нет конфигов";
            float emptySize = 8f;
            float etW = Builder.INTER.get().getWidth(emptyText, emptySize);
            float etH = Builder.INTER.get().getHeight(emptyText, emptySize);
            Builder.TEXT_BUILDER
                    .size(emptySize)
                    .font(Builder.INTER.get())
                    .color(ColorUtils.setAlpha(ColorUtils.rgb(110, 112, 120), (int) (200 * revealProgress)))
                    .text(emptyText)
                    .build()
                    .render(matrix, panelX + (panelW - etW) / 2f, listY + (listH - etH) / 2f);
        } else {
            float yOff = listY + animScroll;
            for (int i = 0; i < configs.size(); i++) {
                Configuration config = configs.get(i);
                float entryY = yOff + i * (ENTRY_H + 4f);

                if (entryY + ENTRY_H < listY || entryY > listY + listH) continue;

                boolean isActive = config.getName().equalsIgnoreCase(currentName);
                float entryX = panelX + PAD;
                float entryW = panelW - PAD * 2f;
                boolean hovered = MouseClick.isClick(mouseX, mouseY, entryX, entryY, entryW, ENTRY_H);

                int entryBg = isActive
                        ? ColorUtils.setAlpha(mainColor, (int) (42 * revealProgress))
                        : HelperElements.themedPanel(22, 24, 28, (int) ((hovered ? 200 : 160) * revealProgress));

                if (hovered) CursorsUtil.setCursor(CursorsUtil.HAND);

                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(entryW, ENTRY_H))
                        .color(new QuadColorState(entryBg))
                        .radius(new QuadRadiusState(4f))
                        .build()
                        .render(matrix, entryX, entryY);

                if (isActive) {
                    Builder.RECTANGLE_BUILDER
                            .size(new SizeState(2.5f, ENTRY_H - 10f))
                            .color(new QuadColorState(ColorUtils.setAlpha(mainColor, (int) (255 * revealProgress))))
                            .radius(new QuadRadiusState(1.5f))
                            .build()
                            .render(matrix, entryX + 5f, entryY + 5f);
                }

                int nameCol = isActive
                        ? ColorUtils.setAlpha(mainColor, (int) (255 * revealProgress))
                        : HelperElements.themedText((int) (225 * revealProgress));

                Builder.TEXT_BUILDER
                        .size(7.8f)
                        .font(Builder.INTER.get())
                        .color(nameCol)
                        .text(config.getName())
                        .build()
                        .render(matrix, entryX + 14f, entryY + 6f);

                long lastModified = config.getFile().lastModified();
                String dateStr = new SimpleDateFormat("dd.MM.yyyy  HH:mm").format(new Date(lastModified));
                Builder.TEXT_BUILDER
                        .size(6f)
                        .font(Builder.INTER.get())
                        .color(ColorUtils.setAlpha(ColorUtils.rgb(115, 117, 125), (int) (200 * revealProgress)))
                        .text(dateStr)
                        .build()
                        .render(matrix, entryX + 14f, entryY + 18f);

                if (isActive) {
                    String badge = "ACTIVE";
                    float badgeSize = 5f;
                    float bw = Builder.INTER.get().getWidth(badge, badgeSize) + 10f;
                    float bh = 10f;
                    float bx = entryX + entryW - bw - 10f;
                    float by = entryY + (ENTRY_H - bh) / 2f;
                    Builder.RECTANGLE_BUILDER
                            .size(new SizeState(bw, bh))
                            .color(new QuadColorState(ColorUtils.setAlpha(mainColor, (int) (50 * revealProgress))))
                            .radius(new QuadRadiusState(2f))
                            .build()
                            .render(matrix, bx, by);
                    Builder.TEXT_BUILDER
                            .size(badgeSize)
                            .font(Builder.INTER.get())
                            .color(ColorUtils.setAlpha(mainColor, (int) (255 * revealProgress)))
                            .text(badge)
                            .build()
                            .render(matrix, bx + 5f, by + (bh - Builder.INTER.get().getHeight(badge, badgeSize)) / 2f - 0.5f);
                }
            }
        }

        ScissorUtil.disable();

        float totalContentH = configs.size() * (ENTRY_H + 4f);
        float maxScroll = Math.max(0f, totalContentH - listH);
        scroll = MathHelper.clamp(scroll, -maxScroll, 0f);

        return this;
    }

    private float drawToolbarButton(Matrix4f matrix, int mouseX, int mouseY, float rightX, float y, float h, String label, String icon, boolean rightAligned) {
        float textSize = 6.5f;
        float iconSize = 6f;
        float textW = Builder.INTER.get().getWidth(label, textSize);
        float iconW = icon == null ? 0f : Builder.INTER.get().getWidth(icon, iconSize) + 4f;
        float btnW = textW + iconW + 14f;
        float btnX = rightAligned ? rightX - btnW : rightX;

        boolean hovered = MouseClick.isClick(mouseX, mouseY, btnX, y, btnW, h);
        if (hovered) CursorsUtil.setCursor(CursorsUtil.HAND);

        int bg = HelperElements.themedPanel(22, 24, 28, (int) ((hovered ? 220 : 160) * revealProgress));
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(btnW, h))
                .color(new QuadColorState(bg))
                .radius(new QuadRadiusState(3f))
                .build()
                .render(matrix, btnX, y);

        int mainColor = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : -1;
        Builder.BORDER_BUILDER
                .size(new SizeState(btnW, h))
                .radius(new QuadRadiusState(3f))
                .color(new QuadColorState(ColorUtils.setAlpha(mainColor, (int) ((hovered ? 70 : 30) * revealProgress))))
                .thickness(-0.1f)
                .build()
                .render(matrix, btnX, y);

        float cx = btnX + 7f;
        if (icon != null && !icon.isBlank()) {
            Builder.TEXT_BUILDER
                    .size(iconSize)
                    .font(Builder.INTER.get())
                    .color(ColorUtils.setAlpha(mainColor, (int) (230 * revealProgress)))
                    .text(icon)
                    .build()
                    .render(matrix, cx, y + (h - Builder.INTER.get().getHeight(icon, iconSize)) / 2f - 0.5f);
            cx += Builder.INTER.get().getWidth(icon, iconSize) + 4f;
        }

        Builder.TEXT_BUILDER
                .size(textSize)
                .font(Builder.INTER.get())
                .color(HelperElements.themedText((int) (230 * revealProgress)))
                .text(label)
                .build()
                .render(matrix, cx, y + (h - Builder.INTER.get().getHeight(label, textSize)) / 2f - 0.5f);

        return btnW;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (!MouseClick.isClick(mouseX, mouseY, getX(), getY(), getWidth(), getHeight())) {
            nameFieldFocused = false;
            LynxWave.getInstance().setFocusedRender(false);
            return false;
        }

        float panelX = getX();
        float panelY = getY();
        float panelW = getWidth();

        float tagY = panelY + PAD;
        float tagH = 13f;
        float titleY = tagY + tagH + 6f;
        float btnY = titleY - 1f;
        float btnH = 16f;

        // ── Toolbar buttons (measured right-to-left like in render) ─────────
        float sortW = measureButton(sortNewestFirst ? "Newest first" : "Oldest first", "▾");
        float sortX = panelX + panelW - PAD - sortW;
        if (MouseClick.isClick(mouseX, mouseY, sortX, btnY, sortW, btnH)) {
            sortNewestFirst = !sortNewestFirst;
            return true;
        }

        float importW = measureButton("Import", "↓");
        float importX = sortX - 12f - importW;
        if (MouseClick.isClick(mouseX, mouseY, importX, btnY, importW, btnH)) {
            notify("Import not available yet");
            return true;
        }

        float createW = measureButton("Create", "✦");
        float createX = importX - 12f - createW;
        if (MouseClick.isClick(mouseX, mouseY, createX, btnY, createW, btnH)) {
            createInlineOpen = !createInlineOpen;
            if (!createInlineOpen) {
                nameInput.setLength(0);
                nameFieldFocused = false;
                LynxWave.getInstance().setFocusedRender(false);
            }
            return true;
        }

        // ── Inline input ────────────────────────────────────────────────────
        float listY = titleY + 10.5f + 14f;
        if (createInlineOpen) {
            float inputY = listY;
            float saveBtnW = 48f;
            float inputW = panelW - PAD * 2f - saveBtnW - 6f;
            float inputX = panelX + PAD;
            float saveX = inputX + inputW + 6f;

            if (MouseClick.isClick(mouseX, mouseY, inputX, inputY, inputW, INLINE_INPUT_H)) {
                nameFieldFocused = true;
                LynxWave.getInstance().setFocusedRender(true);
                return true;
            }
            if (MouseClick.isClick(mouseX, mouseY, saveX, inputY, saveBtnW, INLINE_INPUT_H)) {
                createConfig();
                return true;
            }
            nameFieldFocused = false;
            LynxWave.getInstance().setFocusedRender(false);
            listY += INLINE_INPUT_H + 10f;
        }

        // ── Config entries ──────────────────────────────────────────────────
        ConfigurationManager configManager = LynxWave.getInstance().getConfigurationManager();
        List<Configuration> configs = new ArrayList<>(configManager.getConfigs());
        configs.sort(Comparator.comparingLong((Configuration c) -> c.getFile().lastModified()).reversed());
        if (!sortNewestFirst) java.util.Collections.reverse(configs);
        String currentName = configManager.getCurrentConfigurationName();

        float yOff = listY + animScroll;
        for (int i = 0; i < configs.size(); i++) {
            Configuration config = configs.get(i);
            float entryY = yOff + i * (ENTRY_H + 4f);
            float entryX = panelX + PAD;
            float entryW = panelW - PAD * 2f;
            boolean isActive = config.getName().equalsIgnoreCase(currentName);

            if (MouseClick.isClick(mouseX, mouseY, entryX, entryY, entryW, ENTRY_H)) {
                if (!isActive) {
                    configManager.saveConfiguration(currentName, false);
                    configManager.loadConfiguration(config.getName());
                    notify("Loaded: " + config.getName());
                }
                return true;
            }
        }

        return true;
    }

    private float measureButton(String label, String icon) {
        float textW = Builder.INTER.get().getWidth(label, 6.5f);
        float iconW = icon == null ? 0f : Builder.INTER.get().getWidth(icon, 6f) + 4f;
        return textW + iconW + 14f;
    }

    private void createConfig() {
        String name = nameInput.toString().trim();
        if (name.isEmpty()) return;

        ConfigurationManager configManager = LynxWave.getInstance().getConfigurationManager();
        configManager.saveConfiguration(name, true);
        notify("Created: " + name);
        nameInput.setLength(0);
        nameFieldFocused = false;
        createInlineOpen = false;
        LynxWave.getInstance().setFocusedRender(false);
    }

    private void notify(String message) {
        if (Interface.INSTANCE != null && Interface.INSTANCE.isEnabled()) {
            LynxWave.getInstance().getModuleManager().get(Interface.class)
                    .handlerClient().getNotificationManager().add(
                            message, 2, NotificationType.SUCCESS, RenderType.WORLD
                    );
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!nameFieldFocused) return false;
        if (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-') {
            if (nameInput.length() < 20) nameInput.append(chr);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!nameFieldFocused) return false;

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !nameInput.isEmpty()) {
            nameInput.deleteCharAt(nameInput.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            createConfig();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            nameFieldFocused = false;
            LynxWave.getInstance().setFocusedRender(false);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (MouseClick.isClick(mouseX, mouseY, getX(), getY(), getWidth(), getHeight())) {
            scroll += (float) (verticalAmount * 15f);
            ConfigurationManager configManager = LynxWave.getInstance().getConfigurationManager();
            float listH = getHeight() - HEADER_H - (createInlineOpen ? INLINE_INPUT_H + 10f : 0f) - 10f;
            float totalH = configManager.getConfigs().size() * (ENTRY_H + 4f);
            float maxScroll = Math.max(0f, totalH - listH);
            scroll = MathHelper.clamp(scroll, -maxScroll, 0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
