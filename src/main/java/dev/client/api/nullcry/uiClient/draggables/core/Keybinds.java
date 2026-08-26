package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.keyboard.KeyboardStorage;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.DraggableHeaderRenderer;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.render.Interface;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Keybinds implements IHelper, SettingProvider {
    final DraggableHandler draggableHandler;

    private final List<KeybindAnimation> keybindAnimations = new ArrayList<>();
    private final List<Setting> settings = new ArrayList<>();

    public final Slider scale = new Slider("Масштаб", () -> true).set(50, 100, 5).applyDefault(100).register(this);

    @Override
    public DraggableHandler draggable() {
        return draggableHandler;
    }

    @Override
    public float hudScale() {
        Float scaleValue = scale.getValue();
        return scaleValue == null ? 1f : scaleValue / 100f;
    }

    public CompactAnimation keyBindsWidth = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation keyBindsHeight = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation showAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);

    public final CheckBox showAlways = new CheckBox("Показывать всегда", () -> true)
            .defaultValue(false)
            .register(this);

    public Keybinds() {
        this.draggableHandler = addDraggable("Keybinds", 4, 114);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("Keybinds");
        });
        keyBindsWidth.setValue(draggableHandler.getWidth());
        keyBindsHeight.setValue(draggableHandler.getHeight());
        showAnim.setValue(0.0);
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
    }

    Supplier<List<Module>> modules = () -> handlerClient().getModuleManager().getModules().stream()
            .filter(m -> m.getKey() != -1 && m.isEnabled())
            .toList();

    float width;
    float height;

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();
        float baseWidth = 80f;

        AtomicReference<Float> targetWidth = new AtomicReference<>(baseWidth);
        AtomicReference<Float> targetHeight = new AtomicReference<>(20f);

        boolean chatOpen = mc.inGameHud.getChatHud().isChatFocused();
        boolean hasKeybinds = handlerClient().getModuleManager().getModules().stream().anyMatch(m -> m.getKey() != -1 && m.isEnabled());
        boolean alwaysShow = showAlways.getEnabled();
        boolean preview = chatOpen && !hasKeybinds;
        boolean shouldRender = chatOpen || hasKeybinds || alwaysShow;

        showAnim.run(shouldRender ? 1.0 : 0.0);
        showAnim.update();
        float show = (float) showAnim.getValue();
        if (show <= 0f) return;

        List<Module> currentModules = modules.get();

        for (Module module : currentModules) {
            KeybindAnimation anim = findAnim(module);
            if (anim == null) {
                anim = new KeybindAnimation(module);
                keybindAnimations.add(anim);
            }
            anim.show();
        }

        for (KeybindAnimation anim : keybindAnimations) {
            if (!currentModules.contains(anim.module)) {
                anim.hide();
            }
            anim.tick();
        }

        keybindAnimations.removeIf(KeybindAnimation::isFinished);

        if (preview) {
            String previewName = "Aura";
            String previewKey = "Key";
            float combined = Builder.INTER.get().getWidth(previewName, 7) + Builder.INTER.get().getWidth(previewKey, 7) + 30f;
            if (combined > targetWidth.get()) targetWidth.set(combined);
            targetHeight.set(20f + 14f);
        } else {
            for (Module m : currentModules) {
                String name = m.getName();
                String key = KeyboardStorage.getKey(m.getKey());
                if (key == null) key = String.valueOf(m.getKey());
                float combined = Builder.INTER.get().getWidth(name, 7) + Builder.INTER.get().getWidth(key, 7) + 30f;
                if (combined > targetWidth.get()) targetWidth.set(combined);
            }

            float th = 20f;
            for (KeybindAnimation anim : keybindAnimations) {
                th += 14f * anim.getHeightProgress();
            }
            targetHeight.set(th);
        }

        keyBindsWidth.run(targetWidth.get());
        keyBindsHeight.run(targetHeight.get());
        keyBindsWidth.update();
        keyBindsHeight.update();

        float animatedWidth = (float) keyBindsWidth.getValue();
        float animatedHeight = (float) keyBindsHeight.getValue();

        draggableHandler.setWidth(animatedWidth);
        draggableHandler.setHeight(animatedHeight);

        width = animatedWidth;
        height = animatedHeight;

        float headerHeight = 20f;
        DraggableHeaderRenderer.render(
                event,
                x,
                y,
                width,
                show,
                headerHeight,
                "F",
                8f,
                "Keybinds",
                8f,
                -1,
                -1,
                () -> Interface.INSTANCE.getMainColor()
        );

        float bodyHeight = height - headerHeight;
        if (bodyHeight > 2f) {
            HelperElements.rectElements(event.getContext(), x, y + headerHeight, width, bodyHeight, show);
        }

        AtomicReference<Float> offset = new AtomicReference<>(0f);
        ScissorUtil.enable(x, y, width, height);

        if (preview) {
            float a = show;
            String moduleName = KillAura.INSTANCE != null ? KillAura.INSTANCE.getName() : "Aura";
            String keyText = "Key";
            float keyTextW = Builder.INTER.get().getWidth(keyText, 7);
            float itemHeight = 14f;
            float itemY = y + headerHeight;
            float padding = 7f;
            HelperElements.rectElements(event.getContext(), x, itemY, width, itemHeight, a);

            float moduleX = x + padding - 0.5f - (1f - a) * 15f;
            float keyX = x + width - keyTextW - padding + (1f - a) * 15f;
            float baseY = itemY + (itemHeight - 7f) / 2f - 0.5f;
            int txtColor = HelperElements.themedText((int) (255 * a));

            Builder.TEXT_BUILDER
                    .size(7)
                    .font(Builder.INTER.get())
                    .text(moduleName)
                    .color(txtColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), moduleX, baseY);

            Builder.TEXT_BUILDER
                    .size(7)
                    .font(Builder.INTER.get())
                    .text(keyText)
                    .color(txtColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), keyX, baseY);
        } else {
            for (KeybindAnimation anim : keybindAnimations) {
                float a = anim.getProgress();
                if (a <= 0f) continue;

                Module module = anim.module;
                int txtColor = HelperElements.themedText((int) (255 * a));

                String moduleName = module.getName();
                String keyText = KeyboardStorage.getKey(module.getKey());
                if (keyText == null) keyText = String.valueOf(module.getKey());
                float keyTextW = Builder.INTER.get().getWidth(keyText, 7);

                float itemHeight = 14f;
                float itemY = y + headerHeight + offset.get();
                float padding = 7f;
                HelperElements.rectElements(event.getContext(), x, itemY, width, itemHeight, a);

                float moduleX = x + padding - 0.5f - (1f - a) * 15f;
                float keyX = x + width - keyTextW - padding + (1f - a) * 15f;
                float baseY = itemY + (itemHeight - 7f) / 2f - 0.5f;

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(moduleName)
                        .color(txtColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), moduleX, baseY);

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(keyText)
                        .color(txtColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), keyX, baseY);

                offset.set(offset.get() + itemHeight);
            }
        }

        ScissorUtil.disable();
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    private KeybindAnimation findAnim(Module m) {
        for (KeybindAnimation a : keybindAnimations) if (a.isSame(m)) return a;
        return null;
    }

    static class KeybindAnimation {
        final Module module;
        final CompactAnimation anim = new CompactAnimation(Easing.EASE_OUT_QUAD, 250);

        KeybindAnimation(Module module) {
            this.module = module;
            anim.setValue(0.0);
        }

        void tick() {
            anim.update();
        }

        void show() {
            anim.run(1.0);
        }

        void hide() {
            anim.run(0.0);
        }

        float getProgress() {
            return (float) anim.getValue();
        }

        boolean isFinished() {
            return anim.getDestinationValue() == 0.0 && anim.isDone() && getProgress() <= 0.001f;
        }

        float getHeightProgress() {
            return anim.getDestinationValue() == 0.0 ? getProgress() : 1f;
        }

        boolean isSame(Module other) {
            return module.equals(other);
        }
    }
}
