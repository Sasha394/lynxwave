package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.helper.player.PotionUtil;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.DraggableHeaderRenderer;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ThreadLocalRandom;

public class Potions implements IHelper, SettingProvider {
    final DraggableHandler draggableHandler;

    private final List<PotionAnimation> potionAnimations = new ArrayList<>();
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

    public CompactAnimation potionsWidth = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation potionsHeight = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation showAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    private boolean lastChatOpen = false;
    private StatusEffectInstance previewEffect;
    private StatusEffectInstance lastPreviewEffect;

    public final CheckBox showAlways = new CheckBox("Показывать всегда", () -> true)
            .defaultValue(false)
            .register(this);

    public Potions() {
        this.draggableHandler = addDraggable("Potions", 4, 136);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("Potions");
        });
        potionsWidth.setValue(draggableHandler.getWidth());
        potionsHeight.setValue(draggableHandler.getHeight());
        showAnim.setValue(0.0);
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
    }

    float width;
    float height;

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();
        float baseWidth = 80f;

        List<StatusEffectInstance> effects = getActiveEffects();

        boolean chatOpen = mc.inGameHud.getChatHud().isChatFocused();
        boolean alwaysShow = showAlways.getEnabled();
        boolean preview = chatOpen && effects.isEmpty();
        if (preview && !lastChatOpen) {
            previewEffect = pickPreviewEffect();
        }
        List<StatusEffectInstance> displayEffects = preview ? getPreviewEffects() : effects;
        boolean shouldRender = chatOpen || !effects.isEmpty() || alwaysShow;
        lastChatOpen = chatOpen;
        showAnim.run(shouldRender ? 1.0 : 0.0);
        showAnim.update();
        float show = (float) showAnim.getValue();
        if (show <= 0f) return;

        AtomicReference<Float> targetWidth = new AtomicReference<>(baseWidth);
        AtomicReference<Float> targetHeight = new AtomicReference<>(21f);

        for (StatusEffectInstance statusEffectInstance : displayEffects) {
            String name = statusEffectInstance.getEffectType().value().getName().getString() + getAmplifier(statusEffectInstance.getAmplifier());
            boolean previewDuration = preview || isPreviewEffect(statusEffectInstance);
            String duration = previewDuration ? "**.**" : StatusEffectUtil.getDurationText(statusEffectInstance, 1.0F, 20F).getString();

            float nameWidth = Builder.INTER.get().getWidth(name, 7);
            float durationWidth = Builder.INTER.get().getWidth(duration, 7);

            float iconWidth = statusEffectInstance.shouldShowIcon() ? 9f + 3f : 0f;
            float iconDividerExtra = statusEffectInstance.shouldShowIcon() ? 3f : 0f;

            float animationOffset = 15f;
            float padding = 7f;
            float safety = 8f;

            float totalWidth = iconWidth
                    + iconDividerExtra
                    + nameWidth
                    + durationWidth
                    + animationOffset
                    + (padding * 2)
                    + safety;

            if (totalWidth > targetWidth.get()) {
                targetWidth.set(totalWidth);
            }
            targetHeight.set(targetHeight.get() + 13f);
        }

        potionsWidth.run(targetWidth.get());
        potionsHeight.run(targetHeight.get());
        potionsWidth.update();
        potionsHeight.update();

        float animatedWidth = (float) potionsWidth.getValue();
        float animatedHeight = (float) potionsHeight.getValue();

        width = animatedWidth;
        height = animatedHeight;

        draggableHandler.setWidth(animatedWidth);
        draggableHandler.setHeight(animatedHeight);

        List<StatusEffectInstance> current = displayEffects;
        for (StatusEffectInstance effect : current) {
            potionAnimations.removeIf(ae -> ae.effect.getEffectType() == effect.getEffectType() && (ae.effect.getDuration() != effect.getDuration() || ae.effect.getAmplifier() != effect.getAmplifier()));
            boolean exists = potionAnimations.stream().anyMatch(ae -> ae.isSame(effect));
            if (!exists) {
                PotionAnimation pa = new PotionAnimation(effect);
                pa.show();
                potionAnimations.add(pa);
            }
        }

        potionAnimations.forEach(ae -> {
            boolean stillActive = current.stream().anyMatch(ae::isSame);
            if (!stillActive) ae.hide();
        });

        potionAnimations.removeIf(PotionAnimation::isFinished);
        potionAnimations.forEach(PotionAnimation::tick);

        float headerHeight = 20f;
        DraggableHeaderRenderer.render(
                event,
                x,
                y,
                width,
                show,
                headerHeight,
                "G",
                8f,
                "Potions",
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
        ScissorUtil.enableContext(event.getContext(), x, y, animatedWidth, animatedHeight);
        try {
            potionAnimations.sort((a, b) -> {
                boolean aDead = displayEffects.stream().noneMatch(a::isSame);
                boolean bDead = displayEffects.stream().noneMatch(b::isSame);
                if (aDead && !bDead) return -1;
                if (!aDead && bDead) return 1;
                return a.effect.getEffectType().value().getName().getString().compareTo(b.effect.getEffectType().value().getName().getString());
            });

            for (PotionAnimation potionAnimation : potionAnimations) {
                float progress = potionAnimation.getProgress();
                if (progress <= 0f) continue;
                StatusEffectInstance effect = potionAnimation.effect;

                String name = effect.getEffectType().value().getName().getString() + getAmplifier(effect.getAmplifier());
                boolean previewDuration = preview || isPreviewEffect(effect);
                String duration = previewDuration ? "**.**" : StatusEffectUtil.getDurationText(effect, 1.0F, 20F).getString();

                float durationWidth = Builder.INTER.get().getWidth(duration, 7);
                float padding = 7f;

                float itemHeight = 13f;
                float itemY = y + headerHeight + offset.get();

                HelperElements.rectElements(event.getContext(), x, itemY, width, itemHeight, progress);

                float nameX = x + padding;
                float durationX = x + animatedWidth - durationWidth - padding;
                float baseY = itemY + (itemHeight - 7f) / 2f - 0.5f;

                int baseAlpha = (int) (255 * progress);
                int pulseAlpha = (int) ((Math.sin(System.currentTimeMillis() / 100.0) * 0.5 + 0.5) * 255 * progress);

                boolean isLowDuration = !effect.isInfinite() && effect.getDuration() <= 200;
                boolean isBad = PotionUtil.isBadEffect(effect);

                int nameColor = isLowDuration
                        ? (isBad
                        ? ColorUtils.rgba(255, 30, 30, pulseAlpha)
                        : HelperElements.themedText(pulseAlpha))
                        : (isBad
                        ? ColorUtils.rgba(255, 85, 85, baseAlpha)
                        : HelperElements.themedText(baseAlpha));

                int durationColor = isLowDuration
                        ? HelperElements.themedText(pulseAlpha)
                        : HelperElements.themedText(baseAlpha);

                float nameSlideX = nameX + 14f - (1f - progress) * 15f;
                float durationSlideX = durationX + (1f - progress) * 15f;

                if (effect.shouldShowIcon()) {
                    float effectIconX = x + 6.5f;
                    float iconSlideX = effectIconX - (1f - progress) * 15f;
                    float iconY = baseY + 0.5f;

                    event.getContext().drawGuiTexture(
                            RenderPipelines.GUI_TEXTURED,
                            InGameHud.getEffectTexture(effect.getEffectType()),
                            (int) iconSlideX,
                            (int) iconY,
                            9,
                            9,
                            HelperElements.themedWhite((int) (255 * progress))
                    );

                    Builder.RECTANGLE_BUILDER
                            .size(new SizeState(1f, 7f))
                            .color(new QuadColorState(ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), (int) (255 * progress))))
                            .radius(new QuadRadiusState(0f))
                            .build()
                            .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), Math.round(iconSlideX + 10f), Math.round(baseY) + 0.5f);
                }

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(name)
                        .color(nameColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameSlideX, baseY);

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(duration)
                        .color(durationColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), durationSlideX, baseY);

                offset.set(offset.get() + 13f * potionAnimation.getHeightProgress());
            }
        } finally {
            ScissorUtil.disableContext(event.getContext());
        }
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    private static List<StatusEffectInstance> getActiveEffects() {
        ClientPlayerEntity player = mc.player;
        return player != null ? player.getStatusEffects().stream().toList() : List.of();
    }

    private static String getAmplifier(int amplifier) {
        return amplifier > 0 ? " " + (amplifier + 1) : "";
    }

    private List<StatusEffectInstance> getPreviewEffects() {
        if (previewEffect == null) {
            previewEffect = pickPreviewEffect();
        }
        return List.of(previewEffect);
    }

    private StatusEffectInstance pickPreviewEffect() {
        StatusEffectInstance[] pool = new StatusEffectInstance[]{
                new StatusEffectInstance(StatusEffects.SPEED, 1800, 1),
                new StatusEffectInstance(StatusEffects.STRENGTH, 1200, 0),
                new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1),
                new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 2400, 0)
        };
        int idx = ThreadLocalRandom.current().nextInt(pool.length);
        StatusEffectInstance base = pool[idx];
        if (lastPreviewEffect != null && pool.length > 1) {
            while (base.getEffectType() == lastPreviewEffect.getEffectType()) {
                idx = ThreadLocalRandom.current().nextInt(pool.length);
                base = pool[idx];
            }
        }
        lastPreviewEffect = base;
        return new StatusEffectInstance(base.getEffectType(), base.getDuration(), base.getAmplifier());
    }

    private boolean isPreviewEffect(StatusEffectInstance effect) {
        if (previewEffect == null) return false;
        return effect.getEffectType() == previewEffect.getEffectType()
                && effect.getAmplifier() == previewEffect.getAmplifier()
                && effect.getDuration() == previewEffect.getDuration();
    }

    static class PotionAnimation {
        public final StatusEffectInstance effect;
        public final CompactAnimation anim = new CompactAnimation(Easing.EASE_OUT_QUAD, 250);

        public PotionAnimation(StatusEffectInstance effect) {
            this.effect = effect;
            anim.setValue(0.0);
        }

        public void tick() {
            anim.update();
        }

        public float getProgress() {
            return (float) anim.getValue();
        }

        public void show() {
            anim.run(1.0);
        }

        public void hide() {
            anim.run(0.0);
        }

        public boolean isSame(StatusEffectInstance other) {
            return effect.getEffectType() == other.getEffectType()
                    && effect.getDuration() == other.getDuration()
                    && effect.getAmplifier() == other.getAmplifier();
        }

        public boolean isFinished() {
            return anim.getDestinationValue() == 0.0 && anim.isDone() && getProgress() <= 0.001f;
        }

        public float getHeightProgress() {
            return anim.getDestinationValue() == 0.0 ? getProgress() : 1f;
        }
    }
}
