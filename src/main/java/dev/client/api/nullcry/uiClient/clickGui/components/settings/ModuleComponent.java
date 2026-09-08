package dev.client.api.nullcry.uiClient.clickGui.components.settings;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.helper.client.keyboard.KeyboardStorage;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.Helper;
import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import dev.client.api.nullcry.uiClient.clickGui.api.component.ComponentLayer;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.search.SearchSpotlight;
import dev.client.modules.core.render.Interface;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModuleComponent extends Component {
    Module module;
    List<SettingComponent> components = new ArrayList<>();
    @NonFinal
    private float globalAlpha = 1f;

    @NonFinal
    private float bindVisibility = 0f;
    @NonFinal
    private float bindVisibilityTarget = 0f;
    @NonFinal
    private boolean bindHovered = false;
    @NonFinal
    private boolean nameHovered = false;
    @NonFinal
    private float bindX = 0f;
    @NonFinal
    private float bindY = 0f;
    @NonFinal
    private float bindWidth = 0f;
    @NonFinal
    private float bindHeight = 0f;
    @NonFinal
    private float nameClipX = 0f;
    @NonFinal
    private float nameClipWidth = 0f;

    public void setGlobalAlpha(float alpha) {
        this.globalAlpha = MathHelper.clamp(alpha, 0f, 1f);
    }

    @NonFinal
    @Getter
    @Setter
    private boolean settingsOpened = false;
    @NonFinal
    private float settingsVisibility = 0f;

    public ModuleComponent(Module module) {
        this.module = module;
        this.components.addAll(Helper.settingComponents(module));
    }

    @Override
    public ComponentLayer render(DrawContext context, int mouseX, int mouseY, float delta) {
        final float headerHeight = 22f;
        float animation = module.getAnimation().getOutput().floatValue();
        boolean hovered = MouseClick.isClick(mouseX, mouseY, getX() + 2f, getY(), getWidth() - 4f, headerHeight);
        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
        float panelX = getX() + 3;
        float panelW = getWidth() - 6;
        int c1 = Interface.INSTANCE.getFirstColor();
        int c2 = Interface.INSTANCE.getSecondColor();
        boolean excellentStyle = Interface.INSTANCE.isExcellentMode();

        if (excellentStyle) {
            HelperElements.rect(matrix, panelX, getY(), panelW, Math.max(headerHeight, getHeight()), globalAlpha);
        }

        // Glow behind enabled modules
        if (!excellentStyle && animation > 0.01f) {
            Builder.GLOW_BUILDER
                    .size(new SizeState(panelW, headerHeight))
                    .radius(new QuadRadiusState(4f))
                    .color(new QuadColorState(
                            ColorUtils.setAlpha(c1, (int) (22 * globalAlpha * animation)),
                            ColorUtils.setAlpha(c2, (int) (14 * globalAlpha * animation)),
                            ColorUtils.setAlpha(c2, (int) (6 * globalAlpha * animation)),
                            ColorUtils.setAlpha(c1, (int) (10 * globalAlpha * animation))
                    ))
                    .softness(0.8f)
                    .glowRadius(3f)
                    .build()
                    .render(matrix, panelX, getY());
        }

        // Background fill
        if (!excellentStyle && animation > 0.01f) {
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(panelW, headerHeight))
                    .radius(new QuadRadiusState(4f))
                    .color(new QuadColorState(
                            ColorUtils.setAlpha(c1, (int) (20 * globalAlpha * animation)),
                            ColorUtils.setAlpha(c2, (int) (12 * globalAlpha * animation)),
                            ColorUtils.setAlpha(c2, (int) (8 * globalAlpha * animation)),
                            ColorUtils.setAlpha(c1, (int) (14 * globalAlpha * animation))
                    ))
                    .build()
                    .render(matrix, panelX, getY());
        }

        // Hover highlight
        if (hovered && globalAlpha > 0.01f) {
            float hoverAlpha = module.isEnabled() ? 12f : 10f;
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(panelW, headerHeight))
                    .radius(new QuadRadiusState(4f))
                    .color(new QuadColorState(HelperElements.themedWhite((int) (hoverAlpha * globalAlpha))))
                    .build()
                    .render(matrix, panelX, getY());
        }

        // Gradient border
        int borderAlpha = module.isEnabled() ? (int) (70 * globalAlpha) : (int) (20 * globalAlpha);
        if (hovered) borderAlpha = Math.min(255, borderAlpha + (int) (25 * globalAlpha));
        if (!excellentStyle) {
            Builder.BORDER_BUILDER
                    .size(new SizeState(panelW, getHeight()))
                    .radius(new QuadRadiusState(4f))
                    .color(new QuadColorState(
                            ColorUtils.setAlpha(c1, borderAlpha),
                            ColorUtils.setAlpha(c2, borderAlpha),
                            ColorUtils.setAlpha(c2, borderAlpha / 2),
                            ColorUtils.setAlpha(c1, borderAlpha / 2)
                    ))
                    .thickness(-0.1f)
                    .build()
                    .render(matrix, panelX, getY());
        }

        // Left accent indicator
        if (module.isEnabled() && animation > 0.01f) {
            float indicatorH = Math.min(14f, headerHeight - 6f) * animation;
            float indicatorY = getY() + (headerHeight - indicatorH) / 2f;
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(2f, indicatorH))
                    .color(new QuadColorState(
                            ColorUtils.setAlpha(c1, (int) (255 * globalAlpha * animation)),
                            ColorUtils.setAlpha(c1, (int) (255 * globalAlpha * animation)),
                            ColorUtils.setAlpha(c2, (int) (200 * globalAlpha * animation)),
                            ColorUtils.setAlpha(c2, (int) (200 * globalAlpha * animation))
                    ))
                    .radius(new QuadRadiusState(1f))
                    .build()
                    .render(matrix, getX() + 5f, indicatorY);
        }

        String moduleName = module.getName();
        int keyCode = module.getKey();
        String keyText = KeyboardStorage.getKey(keyCode);
        String resolvedBindText = (keyCode != -1 && keyText != null && !keyText.isEmpty()) ? keyText : "N/A";
        String displayBindText = module.isBinding() ? "Press" : resolvedBindText;

        // Module name text
        int textColor;
        if (module.isEnabled()) {
            textColor = HelperElements.themedText((int) (globalAlpha * (255 * (65 + 35 * animation) / 100f)));
        } else {
            int dimColor = hovered ? ColorUtils.rgb(175, 180, 190) : ColorUtils.rgb(130, 135, 145);
            textColor = ColorUtils.setAlpha(dimColor, (int) (globalAlpha * (255 * (50 + 50 * (1 - animation)) / 100f)));
        }

        float textSize = 8f;
        float textHeight = Builder.INTER.get().getHeight(moduleName, textSize);
        float textX = getX() + 12f;
        float textY = getY() + (headerHeight - textHeight) / 2f;

        float iconSize = 9f;
        boolean hasSettings = !components.isEmpty();
        float iconX = getX() + getWidth() - iconSize - 10f;
        float iconY = getY() + (headerHeight - iconSize) / 2f + 0.5f;

        boolean shouldShowBind = dev.client.api.compat.InputCompat.isAltDown() || settingsOpened || module.isBinding();
        bindVisibilityTarget = shouldShowBind ? 1f : 0f;
        bindVisibility = MathHelper.lerp(0.25f, bindVisibility, bindVisibilityTarget);
        if (Math.abs(bindVisibility - bindVisibilityTarget) <= 0.01f) {
            bindVisibility = bindVisibilityTarget;
        }

        bindWidth = Builder.INTER.get().getWidth(displayBindText, 6f) + 7f;
        bindHeight = Builder.INTER.get().getHeight(displayBindText, 6f) + 6f;
        float bindAnchor = hasSettings ? iconX : getX() + getWidth() - 10f;
        float bindGap = hasSettings ? 6f : -1f;
        bindX = bindAnchor - bindWidth - bindGap;
        bindY = getY() + (headerHeight - bindHeight) / 2f + 0.5f;

        float rightLimit = getX() + getWidth() - 8f;
        if (hasSettings) {
            rightLimit = Math.min(rightLimit, iconX - 4f);
        }
        if (bindVisibility > 0.001f) {
            rightLimit = Math.min(rightLimit, bindX - 4f);
        }
        float clipWidth = Math.max(0f, rightLimit - textX);

        nameClipX = textX;
        nameClipWidth = clipWidth;
        nameHovered = clipWidth > 0f && MouseClick.isClick(mouseX, mouseY, textX, getY(), clipWidth, headerHeight);

        if (clipWidth > 0f) {
            ScissorUtil.enable(textX, getY(), clipWidth, headerHeight);
            Builder.TEXT_BUILDER
                    .size(textSize)
                    .font(Builder.INTER.get())
                    .color(textColor)
                    .text(moduleName)
                    .build()
                    .render(matrix, textX, textY);
            ScissorUtil.disable();
        }

        // Поиск попросил показать настройку этого модуля — раскрываем его сами.
        if (SearchSpotlight.shouldOpenSettings(module)) settingsOpened = true;

        settingsVisibility = MathHelper.lerp(0.22f, settingsVisibility, settingsOpened ? 1f : 0f);
        if (Math.abs(settingsVisibility - (settingsOpened ? 1f : 0f)) <= 0.01f) {
            settingsVisibility = settingsOpened ? 1f : 0f;
        }

        // Settings gear icon
        if (hasSettings) {
            float iconCenterX = iconX + iconSize / 2f;
            float iconCenterY = iconY + iconSize / 2f;
            Matrix4f iconMatrix = new Matrix4f(matrix);
            if (settingsVisibility > 0.01f) {
                iconMatrix.translate(iconCenterX, iconCenterY, 0f);
                iconMatrix.rotateZ((float) Math.toRadians(90f * settingsVisibility));
                iconMatrix.translate(-iconCenterX, -iconCenterY, 0f);
            }

            int gearAlpha = (int) ((80 + 120 * settingsVisibility) * globalAlpha);
            int gearColor = settingsOpened
                    ? ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), gearAlpha)
                    : HelperElements.themedWhite(gearAlpha);

            Builder.TEXT_BUILDER
                    .text("G")
                    .size(iconSize)
                    .font(Builder.OTHER_ICONS.get())
                    .color(gearColor)
                    .build()
                    .render(iconMatrix, iconX, iconY);
        }

        // Bind badge
        bindHovered = false;
        if (bindVisibility > 0.01f) {
            float alphaFactor = bindVisibility * globalAlpha;

            if (!excellentStyle && Interface.INSTANCE.blurStrength.getValue() > 0) {
                Builder.BLUR_BUILDER
                        .blurRadius(Math.min(10f, Math.max(0f, Interface.INSTANCE.blurStrength.getValue())))
                        .radius(new QuadRadiusState(3f))
                        .size(new SizeState(bindWidth, bindHeight))
                        .alpha(alphaFactor)
                        .build()
                        .render(matrix, bindX, bindY);
            }

            float bgAlpha = module.isBinding() ? 180f : 140f;
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(bindWidth, bindHeight))
                    .color(new QuadColorState(HelperElements.themedPanel(20, 22, 28, (int) (bgAlpha * alphaFactor))))
                    .radius(new QuadRadiusState(3f))
                    .build()
                    .render(matrix, bindX, bindY);

            // Bind badge accent border
            int bindBorderColor = module.isBinding()
                    ? ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), (int) (120 * alphaFactor))
                    : HelperElements.themedWhite((int) (30 * alphaFactor));
            Builder.BORDER_BUILDER
                    .size(new SizeState(bindWidth, bindHeight))
                    .radius(new QuadRadiusState(3f))
                    .color(new QuadColorState(bindBorderColor))
                    .thickness(-0.1f)
                    .build()
                    .render(matrix, bindX, bindY);

            int bindColor;
            if (module.isBinding()) {
                bindColor = ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), (int) (255 * alphaFactor));
            } else if (keyCode == -1) {
                bindColor = ColorUtils.setAlpha(ColorUtils.rgb(100, 105, 115), (int) (255 * alphaFactor));
            } else {
                bindColor = HelperElements.themedText((int) (220 * alphaFactor));
            }

            float bindTextWidth = Builder.INTER.get().getWidth(displayBindText, 6f);
            Builder.TEXT_BUILDER
                    .size(6f)
                    .font(Builder.INTER.get())
                    .color(bindColor)
                    .text(displayBindText)
                    .build()
                    .render(matrix, bindX + (bindWidth - bindTextWidth) / 2f, bindY + 2.5f);

            bindHovered = MouseClick.isClick(mouseX, mouseY, bindX, bindY, bindWidth, bindHeight);
            if (bindHovered) {
                CursorsUtil.setCursor(CursorsUtil.HAND);
            }
        }

        // Settings content area
        if (settingsVisibility > 0.01f) {
            float settingsAlpha = globalAlpha * settingsVisibility;

            // Divider line under header
            int divColor1 = ColorUtils.setAlpha(c1, (int) (40 * settingsAlpha));
            int divColor2 = ColorUtils.setAlpha(c2, (int) (40 * settingsAlpha));
            int divFade = ColorUtils.setAlpha(c1, 0);
            float divY = getY() + headerHeight + 0.5f;
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(panelW - 12f, 0.5f))
                    .color(new QuadColorState(divColor1, divColor2, divFade, divFade))
                    .radius(new QuadRadiusState(0f))
                    .build()
                    .render(matrix, getX() + 9f, divY);

            AtomicReference<Float> offset = new AtomicReference<>(0f);
            float settingsWidth = Math.max(0f, getWidth() - 18f);
            float settingsX = getX() + 11f;
            components.stream()
                    .filter(e -> e.getSetting().getShown().get())
                    .forEach(e -> {
                        e.setGlobalAlpha(settingsAlpha);
                        e.size(settingsWidth, e.getHeight());
                        if (SearchSpotlight.highlightsSetting(e.getSetting())) {
                            drawSpotlight(matrix, getX() + 4f, getY() + headerHeight + 2f + offset.get(), getWidth() - 8f, e.getHeight() + 4f);
                        }

                        e.position(settingsX, getY() + headerHeight + 4f + offset.get()).render(context, mouseX, mouseY, delta);
                        offset.set(offset.get() + e.getHeight() + 5f);
                    });
        }

        if (SearchSpotlight.highlightsModule(module)) {
            drawSpotlight(matrix, getX() + 4f, getY(), getWidth() - 8f, headerHeight);
        }

        boolean headerHovered = MouseClick.isClick(mouseX, mouseY, getX() + 2f, getY(), getWidth() - 4f, headerHeight);
        if (headerHovered) {
            CursorsUtil.setCursor(CursorsUtil.HAND);
        }

        return null;
    }

    public float getFullHeight() {
        float baseHeight = 22f;

        if (settingsOpened || settingsVisibility > 0.01f) {
            baseHeight += getSettingsHeight() * settingsVisibility;
        }

        return baseHeight;
    }

    private float getSettingsHeight() {
        float height = 0f;
        for (SettingComponent component : components) {
            if (component.getSetting().getShown().get()) {
                height += component.getHeight() + 5f;
            }
        }
        return height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean hasSettings = !components.isEmpty();
        float headerHeight = 22f;

        boolean headerHovered = MouseClick.isClick(mouseX, mouseY, getX(), getY(), getWidth(), headerHeight);
        boolean bindAreaHovered = bindVisibility > 0.01f && MouseClick.isClick(mouseX, mouseY, bindX, bindY, bindWidth, bindHeight);

        // --- если уже идёт биндинг ---
        if (module.isBinding()) {
            if (button == 0) {
                module.setBinding(false);
                return true;
            }

            module.setKey(-100 + button);
            module.setBinding(false);
            return true;
        }

        if (!settingsOpened && headerHovered && button == 2) {
            startBinding();
            bindVisibilityTarget = 1f;
            return true;
        }

        if (bindAreaHovered) {
            if (button == 0 || button == 2) {
                startBinding();
                return true;
            }
        }

        if (settingsOpened) {
            for (SettingComponent component : components) {
                if (component.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        if (headerHovered) {
            if (button == 0) {
                if (!bindAreaHovered) {
                    module.toggle();
                    return true;
                }
            }

            if (button == 1) {
                if (hasSettings) {
                    settingsOpened = !settingsOpened;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void startBinding() {
        LynxWave.getInstance().getModuleManager().getModules().forEach(other -> {
            if (other != module && other.isBinding()) {
                other.setBinding(false);
            }
        });
        module.setBinding(true);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        components.forEach(e -> e.mouseReleased(mouseX, mouseY, button));

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (module.isBinding()) {
            module.setKey(keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_ESCAPE ? -1 : keyCode);
            module.setBinding(false);

            return true;
        }

        components.forEach(e -> e.keyPressed(keyCode, scanCode, modifiers));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        components.forEach(e -> e.keyReleased(keyCode, scanCode, modifiers));
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        components.forEach(e -> e.charTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    /** Подсветка строки, на которую увёл поиск: держится 1.6 с и гаснет. */
    private void drawSpotlight(Matrix4f matrix, float x, float y, float width, float height) {
        float alpha = SearchSpotlight.alpha() * globalAlpha;
        if (alpha <= 0.01f) return;

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, height))
                .color(new QuadColorState(ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), (int) (46 * alpha))))
                .radius(new QuadRadiusState(4f))
                .build()
                .render(matrix, x, y);
    }
}
