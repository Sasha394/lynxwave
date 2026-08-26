package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.helper.player.PlayerUtil;
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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ThreadLocalRandom;

public class Cooldowns implements IHelper, SettingProvider {
    final DraggableHandler draggableHandler;

    private final List<CooldownsAnimation> cooldownsAnimations = new ArrayList<>();
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

    public CompactAnimation cooldownsWidth = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation cooldownsHeight = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation showAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    private boolean lastChatOpen = false;
    private ItemStack previewStack = ItemStack.EMPTY;
    private Item lastPreviewItem;

    public final CheckBox showAlways = new CheckBox("Показывать всегда", () -> true)
            .defaultValue(false)
            .register(this);

    public Cooldowns() {
        this.draggableHandler = addDraggable("Cooldowns", 4, 180);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("Cooldowns");
        });
        cooldownsWidth.setValue(draggableHandler.getWidth());
        cooldownsHeight.setValue(draggableHandler.getHeight());
        showAnim.setValue(0.0);
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
    }

    float width;
    float height;

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        Map<ItemStack, Double> items = PlayerUtil.getCooldownItems();
        for (Map.Entry<ItemStack, Double> entry : items.entrySet()) {
            boolean exists = cooldownsAnimations.stream().anyMatch(ca -> ca.isSame(entry.getKey()));
            if (!exists) {
                CooldownsAnimation ca = new CooldownsAnimation(entry.getKey());
                ca.show();
                cooldownsAnimations.add(ca);
            }
        }
        cooldownsAnimations.forEach(anim -> {
            boolean stillExists = items.keySet().stream().anyMatch(anim::isSame);
            if (!stillExists) anim.hide();
        });
        cooldownsAnimations.removeIf(CooldownsAnimation::isFinished);
        cooldownsAnimations.forEach(CooldownsAnimation::tick);

        boolean chatOpen = mc.inGameHud.getChatHud().isChatFocused();
        boolean alwaysShow = showAlways.getEnabled();
        boolean preview = chatOpen && items.isEmpty();
        if (preview && !lastChatOpen) {
            previewStack = pickPreviewStack();
        }
        boolean shouldRender = chatOpen || !cooldownsAnimations.isEmpty() || alwaysShow;
        lastChatOpen = chatOpen;
        showAnim.run(shouldRender ? 1.0 : 0.0);
        showAnim.update();
        float show = (float) showAnim.getValue();
        if (show <= 0f) return;
        float baseWidth = 80f;
        float baseHeight = 21f;
        float rowH = 13f;

        float targetWidth = baseWidth;
        if (preview) {
            String name = previewStack.getName().getString();
            String duration = "**.**";

            float nameW = Builder.INTER.get().getWidth(name, 7);
            float timeW = Builder.INTER.get().getWidth(duration, 7);

            float totalW = 5f + 11.2f + 2f + 1f + 4f + nameW + 6f + timeW + 6f;
            if (totalW > targetWidth) targetWidth = totalW;
        } else {
            for (Map.Entry<ItemStack, Double> entry : items.entrySet()) {
                String name = entry.getKey().getName().getString();
                String duration = String.format("%.1f", entry.getValue());

                float nameW = Builder.INTER.get().getWidth(name, 7);
                float timeW = Builder.INTER.get().getWidth(duration, 7);

                float totalW = 5f + 11.2f + 2f + 1f + 4f + nameW + 6f + timeW + 6f;
                if (totalW > targetWidth) targetWidth = totalW;
            }
        }

        float targetHeight = baseHeight;
        if (preview) {
            targetHeight += rowH;
        } else {
            for (CooldownsAnimation a : cooldownsAnimations) {
                targetHeight += rowH * a.getHeightProgress();
            }
        }

        cooldownsWidth.run(targetWidth);
        cooldownsHeight.run(targetHeight);
        cooldownsWidth.update();
        cooldownsHeight.update();

        float animatedWidth = (float) cooldownsWidth.getValue();
        float animatedHeight = (float) cooldownsHeight.getValue();

        width = animatedWidth;
        height = animatedHeight;

        draggableHandler.setWidth(animatedWidth);
        draggableHandler.setHeight(animatedHeight);

        float headerHeight = 20f;
        float paddingX = 6f;
        DraggableHeaderRenderer.render(
                event,
                x,
                y,
                width,
                show,
                headerHeight,
                "I",
                8f,
                "Cooldowns",
                8f,
                -1,
                -1,
                () -> Interface.INSTANCE.getMainColor()
        );

        float bodyHeight = height - headerHeight;
        if (bodyHeight > 2f) {
            HelperElements.rectElements(event.getContext(), x, y + headerHeight, width, bodyHeight, show);
        }

        AtomicReference<Float> offsetY = new AtomicReference<>(0f);
        ScissorUtil.enableContext(event.getContext(), x, y, animatedWidth, animatedHeight);
        try {
            if (preview) {
                float p = show;
                ItemStack stack = previewStack;
                String duration = "**.**";

                float itemY = y + headerHeight + offsetY.get();
                HelperElements.rectElements(event.getContext(), x, itemY, width, rowH, p);

                float itemX = x + 5f - (1f - p) * 10f;

                MathUtil.scaleStart(event.getContext().getMatrices(), itemX, itemY + 1.5f, 0.7f);
                event.getContext().drawItem(stack, (int) itemX, (int) (itemY + 1f));
                MathUtil.scaleEnd(event.getContext().getMatrices());

                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(1f, 7f))
                        .color(new QuadColorState(ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), (int) (255 * p))))
                        .radius(new QuadRadiusState(0f))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), Math.round(itemX + 13f), Math.round(itemY + 2.5f) + 0.5f);

                String name = stack.getName().getString();

                float textBaseY = itemY + (rowH - 7f) / 2f - 0.5f;
                float nameX = x + paddingX + 16f - (1f - p) * 15f;
                float durationX = x + animatedWidth - Builder.INTER.get().getWidth(duration, 7) - paddingX + (1f - p) * 15f;

                int alpha = (int) (255 * p);
                int color = HelperElements.themedText(alpha);


                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(name)
                        .color(color)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, textBaseY);

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(duration)
                        .color(color)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), durationX, textBaseY);
            } else {
                for (CooldownsAnimation cooldownsAnimation : cooldownsAnimations) {
                    float p = cooldownsAnimation.getProgress();
                    if (p <= 0f) continue;

                    ItemStack stack = cooldownsAnimation.stack;

                    double timeLeft = 0.0;
                    for (Map.Entry<ItemStack, Double> e : items.entrySet()) {
                        if (ItemStack.areItemsEqual(e.getKey(), stack)) {
                            timeLeft = e.getValue();
                            break;
                        }
                    }

                    float itemY = y + headerHeight + offsetY.get();

                    HelperElements.rectElements(event.getContext(), x, itemY, width, rowH, p);

                    float itemX = x + 5f - (1f - p) * 10f;

                    MathUtil.scaleStart(event.getContext().getMatrices(), itemX, itemY + 1.5f, 0.7f);
                    event.getContext().drawItem(stack, (int) itemX, (int) (itemY + 1f));
                    MathUtil.scaleEnd(event.getContext().getMatrices());

                    Builder.RECTANGLE_BUILDER
                            .size(new SizeState(1f, 7f))
                            .color(new QuadColorState(ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), (int) (255 * p))))
                            .radius(new QuadRadiusState(0f))
                            .build()
                            .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), Math.round(itemX + 13f), Math.round(itemY + 2.5f) + 0.5f);

                    String name = stack.getName().getString();
                    String duration = String.format("%.1f", timeLeft);

                    float textBaseY = itemY + (rowH - 7f) / 2f - 0.5f;
                    float nameX = x + paddingX + 16f - (1f - p) * 15f;
                    float durationX = x + animatedWidth - Builder.INTER.get().getWidth(duration, 7) - paddingX + (1f - p) * 15f;

                    int alpha = (int) (255 * p);
                    int color = HelperElements.themedText(alpha);

                    Builder.TEXT_BUILDER
                            .size(7)
                            .font(Builder.INTER.get())
                            .text(name)
                            .color(color)
                            .build()
                            .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, textBaseY);

                    Builder.TEXT_BUILDER
                            .size(7)
                            .font(Builder.INTER.get())
                            .text(duration)
                            .color(color)
                            .build()
                            .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), durationX, textBaseY);

                    offsetY.set(offsetY.get() + rowH * cooldownsAnimation.getHeightProgress());
                }
            }
        } finally {
            ScissorUtil.disableContext(event.getContext());
        }
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    static class CooldownsAnimation {
        public final ItemStack stack;
        public final CompactAnimation anim = new CompactAnimation(Easing.EASE_OUT_QUAD, 250);

        public CooldownsAnimation(ItemStack stack) {
            this.stack = stack;
            anim.setValue(0.0);
        }

        public void show() {
            anim.run(1.0);
        }

        public void tick() {
            anim.update();
        }

        public float getProgress() {
            return (float) anim.getValue();
        }

        public void hide() {
            anim.run(0.0);
        }

        public boolean isSame(ItemStack other) {
            return ItemStack.areItemsEqual(stack, other);
        }

        public boolean isFinished() {
            return anim.getDestinationValue() == 0.0 && anim.isDone() && getProgress() <= 0.001f;
        }

        public float getHeightProgress() {
            return anim.getDestinationValue() == 0.0 ? getProgress() : 1f;
        }
    }

    private ItemStack pickPreviewStack() {
        ItemStack[] pool = new ItemStack[]{
                new ItemStack(Items.ENDER_PEARL),
                new ItemStack(Items.GOLDEN_APPLE),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)
        };
        int idx = ThreadLocalRandom.current().nextInt(pool.length);
        if (lastPreviewItem != null && pool.length > 1) {
            while (pool[idx].getItem() == lastPreviewItem) {
                idx = ThreadLocalRandom.current().nextInt(pool.length);
            }
        }
        ItemStack selected = pool[idx];
        lastPreviewItem = selected.getItem();
        return selected;
    }
}
