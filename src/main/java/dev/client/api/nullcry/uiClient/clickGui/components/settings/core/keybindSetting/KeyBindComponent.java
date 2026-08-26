package dev.client.api.nullcry.uiClient.clickGui.components.settings.core.keybindSetting;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;

import com.google.common.base.Suppliers;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.helper.client.keyboard.KeyboardStorage;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

public class KeyBindComponent extends SettingComponent {
    private final Supplier<KeyBind> bindSetting = Suppliers.memoize(() -> (KeyBind) getSetting());
    private final Supplier<Float> valueWidth = () -> Builder.INTER.get().getWidth(getBindText(), 6) + 9;

    boolean isHovered;
    float textScroll = 0f;
    long scrollTimer = System.currentTimeMillis();

    public KeyBindComponent(Setting setting) {
        super(setting);
    }

    @Override
    public void init() {
        float lineH = 12f;
        size(240f / 2 - 10, lineH);
    }

    @Override
    public KeyBindComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        final float nameSize = 7f;
        final float rowH = getHeight();
        final float rectH = 11f;
        final float rightPad = 4f;
        final float nameY = getY();
        float visibility = getGlobalAlpha();

        final float bindBoxW = valueWidth.get();
        final float bindX = getX() + getWidth() - bindBoxW - rightPad;
        final float bindY = getY() - 1;

        final String name = getSetting().getName();
        final float fullTextW = Builder.INTER.get().getWidth(name, nameSize);

        final float clipX = getX();
        final float gapToBind = 6f;
        final float defaultClipPadding = 19f;
        final float clipLimitRight = getX() + getWidth() - clipPadding(defaultClipPadding);
        final float bindLimitRight = bindX - gapToBind;
        final float clipRight = Math.max(clipX, Math.min(clipLimitRight, bindLimitRight));
        final float clipW = Math.max(0f, clipRight - clipX);

        final boolean isEditing = bindSetting.get().getSelected();
        float selectionProgress = bindSetting.get().getAnimation().getOutput().floatValue();
        float backgroundAlpha = 150f + 25f * selectionProgress;
        int backgroundAlphaInt = Math.round(backgroundAlpha * visibility);

        isHovered = MouseClick.isClick(mouseX, mouseY, bindX, bindY, bindBoxW, rectH);
        if (!isEditing && isHovered) {
            CursorsUtil.setCursor(CursorsUtil.HAND);
        }

        final boolean isNameHoveredRaw = MouseClick.isClick(mouseX, mouseY, clipX, nameY, clipW, rowH);
        final boolean isNameHovered = isEditing ? false : isNameHoveredRaw;
        final float scrollSpeed = 1.25f;
        final float loopOffset = fullTextW + 20f;

        if (fullTextW > clipW) {
            if (isNameHovered) {
                if (System.currentTimeMillis() - scrollTimer > 15L) {
                    textScroll += scrollSpeed;
                    if (textScroll > loopOffset) textScroll = 0f;
                    scrollTimer = System.currentTimeMillis();
                }
            } else {
                if (textScroll > loopOffset / 2f) {
                    textScroll = fast(textScroll, loopOffset, 10f);
                    if (textScroll >= loopOffset - 0.5f) textScroll = 0f;
                } else {
                    textScroll = fast(textScroll, 0f, 10f);
                }
            }
        } else {
            textScroll = 0f;
        }

        ScissorUtil.enable(clipX, nameY, clipW, rowH);

        final float baseX = clipX - textScroll;
        Builder.TEXT_BUILDER
                .size(nameSize)
                .color(HelperElements.themedText(Math.round(ColorUtils.getAlpha(-1) * visibility)))
                .text(name)
                .font(Builder.INTER.get())
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), baseX, nameY);

        if (fullTextW > clipW) {
            Builder.TEXT_BUILDER
                    .size(nameSize)
                    .color(HelperElements.themedText(Math.round(ColorUtils.getAlpha(-1) * visibility)))
                    .text(name)
                    .font(Builder.INTER.get())
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), baseX + loopOffset, nameY);
        }

        ScissorUtil.disable();

        if (!Interface.INSTANCE.isExcellentMode() && Interface.INSTANCE.blurStrength.getValue() > 0) {
            Builder.BLUR_BUILDER
                    .blurRadius(Math.min(10f, Math.max(0f, Interface.INSTANCE.blurStrength.getValue())))
                    .radius(new QuadRadiusState(2f))
                    .size(new SizeState(bindBoxW, rectH))
                    .alpha(visibility)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), bindX, bindY);
        }

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(bindBoxW, rectH))
                .color(new QuadColorState(
                        HelperElements.themedPanel(28, 30, 35, backgroundAlphaInt),
                        HelperElements.themedPanel(24, 26, 30, backgroundAlphaInt),
                        HelperElements.themedPanel(22, 24, 28, backgroundAlphaInt),
                        HelperElements.themedPanel(26, 28, 33, backgroundAlphaInt)
                ))
                .radius(new QuadRadiusState(2f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), bindX, bindY);

        String displayKey = getBindText();
        Builder.TEXT_BUILDER
                .size(6)
                .color(ColorUtils.setAlpha(
                        -1,
                        Math.round(ColorUtils.getAlpha(-1) * visibility)
                ))
                .text(displayKey)
                .font(Builder.INTER.get())
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), bindX + 4.5f, bindY + 2f);

        return null;
    }

    private String getBindText() {
        if (bindSetting.get().getSelected()) return "...";
        int key = bindSetting.get().getKey();
        String text = (key == 0 || key == GLFW.GLFW_KEY_UNKNOWN) ? "N/A" : KeyboardStorage.getKey(key);
        if (text == null || text.isEmpty() || "Unknown".equalsIgnoreCase(text)) text = "N/A";
        return text;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bindSetting.get().getSelected() && button != 0) {
            bindSetting.get().set(-100 + button);
            bindSetting.get().setSelected(false);
            return true;
        }

        if (isHovered) {
            if (button == 0) {
                if (!bindSetting.get().getSelected()) {
                    deselectAllKeybindsExcept(bindSetting.get());
                }
                bindSetting.get().setSelected(!bindSetting.get().getSelected());
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!bindSetting.get().getSelected()) return false;

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            bindSetting.get().set(0);
        } else {
            bindSetting.get().set(keyCode);
        }
        return true;
    }

    private void deselectAllKeybindsExcept(KeyBind self) {
        LynxWave.getInstance().getModuleManager().getModules().forEach(m ->
                m.getSettings().forEach(s -> {
                    if (s instanceof KeyBind kb && kb != self && Boolean.TRUE.equals(kb.getSelected())) {
                        kb.setSelected(false);
                    }
                })
        );
    }

    private static float fast(float from, float to, float speed) {
        return from + (to - from) / Math.max(1f, speed);
    }
}
