package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.animations.client.Animation;
import dev.client.api.nullcry.render.core.animations.client.Direction;
import dev.client.api.nullcry.render.core.animations.client.implement.DecelerateAnimation;
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
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ArmorHud implements IHelper, SettingProvider {
    final DraggableHandler draggableHandler;

    private final List<ArmorAnim> slots = new ArrayList<>();
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

    public CompactAnimation widthAnimation = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation heightAnimation = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation showAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    private boolean lastChatOpen = false;
    private Slot previewSlot = Slot.HELMET;
    private ItemStack previewStack = ItemStack.EMPTY;
    private Slot lastPreviewSlot;

    public final CheckBox showAlways = new CheckBox("Показывать всегда", () -> true)
            .defaultValue(false)
            .register(this);

    public ArmorHud() {
        this.draggableHandler = addDraggable("ArmorHud", 4, 4);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("ArmorHud");
        });
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));

        widthAnimation.setValue(draggableHandler.getWidth());
        heightAnimation.setValue(draggableHandler.getHeight());
        showAnim.setValue(0.0);

        slots.add(new ArmorAnim(Slot.HELMET));
        slots.add(new ArmorAnim(Slot.CHEST));
        slots.add(new ArmorAnim(Slot.LEGS));
        slots.add(new ArmorAnim(Slot.BOOTS));
    }

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        boolean alwaysShow = showAlways.getEnabled();
        boolean chatOpen = mc != null && mc.inGameHud != null && mc.inGameHud.getChatHud().isChatFocused();
        boolean shouldRender = chatOpen || hasAnyArmor() || alwaysShow;
        boolean preview = chatOpen && !hasAnyArmor();

        float baseWidth = 110f;
        float rowHeight = 12f;
        float headerH = 20f;

        showAnim.run(shouldRender ? 1.0 : 0.0);
        showAnim.update();
        float show = (float) showAnim.getValue();

        if (!shouldRender && show <= 0f) {
            widthAnimation.run(baseWidth);
            heightAnimation.run(headerH + 1f);
            widthAnimation.update();
            heightAnimation.update();
            draggableHandler.setWidth((float) widthAnimation.getValue());
            draggableHandler.setHeight((float) heightAnimation.getValue());
            return;
        }

        if (preview && !lastChatOpen) {
            pickPreviewArmor();
        }
        lastChatOpen = chatOpen;

        for (ArmorAnim anim : slots) {
            ItemStack stack = preview ? (anim.slot == previewSlot ? previewStack : ItemStack.EMPTY) : anim.slot.get(mc.player);
            boolean present = stack != null && !stack.isEmpty();
            anim.setStack(present ? stack : ItemStack.EMPTY);
            if (present) anim.show();
            else anim.hide();
            anim.tick();
        }

        float maxWidth = baseWidth;
        float targetHeight = headerH + 1f;

        for (ArmorAnim anim : slots) {
            float p = anim.progress();
            if (p <= 0f) continue;

            Text name = anim.displayName();
            String perc = anim.percentText();
            float totalWidth = 5f + (16f * 0.8f) + 3f + 1f + 4f + Builder.INTER.get().getWidth(name.getString(), 7) + 6f + Builder.INTER.get().getWidth(perc, 7) + 6f;
            if (totalWidth > maxWidth) maxWidth = totalWidth;

            targetHeight += rowHeight * anim.heightProgress();
        }

        widthAnimation.run(maxWidth);
        heightAnimation.run(targetHeight);
        widthAnimation.update();
        heightAnimation.update();

        float animatedWidth = (float) widthAnimation.getValue();
        float animatedHeight = (float) heightAnimation.getValue();
        draggableHandler.setWidth(animatedWidth);
        draggableHandler.setHeight(animatedHeight);

        if (show <= 0f) {
            return;
        }

        float iconSize = 9f;
        float textSize = 8f;
        float paddingX = 6f;

        DraggableHeaderRenderer.render(
                event,
                x,
                y,
                animatedWidth,
                show,
                headerH,
                "L",
                iconSize,
                "Armor",
                textSize,
                -1,
                -1,
                () -> Interface.INSTANCE.getMainColor()
        );

        float bodyHeight = animatedHeight - headerH;
        if (bodyHeight > 2f) {
            HelperElements.rectElements(event.getContext(), x, y + headerH, animatedWidth, bodyHeight, show);
        }

        ScissorUtil.enableContext(event.getContext(), x, y, animatedWidth, animatedHeight);
        try {
            float offsetY = 0f;

            for (ArmorAnim anim : slots) {
                float p = anim.progress();
                if (p <= 0f) continue;
                ItemStack stack = anim.stack;
                float itemY = y + headerH + offsetY + 0.5f;

                HelperElements.rectElements(event.getContext(), x, itemY, animatedWidth, rowHeight, p * show);

                float itemX = x + 5.5f - (1f - p) * 10f;
                float slideY = itemY + (1.25f - p) * 6f;

                if (!stack.isEmpty()) {
                    MathUtil.scaleStart(event.getContext().getMatrices(), itemX + 0.25f, slideY, 0.60f);
                    event.getContext().drawItem(stack, (int) (itemX + 0.25f), (int) slideY);
                    MathUtil.scaleEnd(event.getContext().getMatrices());

                    float separatorHeight = 9f;
                    float separatorY = Math.round(itemY + (rowHeight - separatorHeight) / 2f) + 0.5f;
                    Builder.RECTANGLE_BUILDER
                            .size(new SizeState(1f, separatorHeight))
                            .color(new QuadColorState(ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), (int) (255 * p * show))))
                            .radius(new QuadRadiusState(0f))
                            .build()
                            .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), Math.round(itemX + 12f), separatorY);
                }

                Text name = anim.displayName();
                String perc = anim.percentText();
                float textBaseY = itemY + (rowHeight - 7f) / 2f - 0.5f;
                float nameX = x + paddingX + 17f - (1f - p) * 15f;
                float percX = x + animatedWidth - Builder.INTER.get().getWidth(perc, 7) - paddingX + (1f - p) * 15f;

                int alpha = (int) (170 * p * show);
                int txtColor = HelperElements.themedText(alpha);

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(name)
                        .color(txtColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, textBaseY);

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(perc)
                        .color(txtColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), percX, textBaseY);

                float barW = Math.max(40f, (animatedWidth - 32f) * 0.45f);
                float barY = itemY + rowHeight - 2f;
                float h = 2.25f;
                float fill = barW * anim.durability01();

                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(barW, h))
                        .color(new QuadColorState(HelperElements.themedWhite((int) (30 * p * show))))
                        .radius(new QuadRadiusState(0.5f))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, barY);

                int barAlpha = (int) (180 * p * show);
                int barBase = Interface.INSTANCE.getMainColor();
                int barLeft = ColorUtils.setAlpha(darkenRgb(barBase, 0.30f), barAlpha);
                int barRight = ColorUtils.setAlpha(barBase, barAlpha);
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(fill, h))
                        .color(new QuadColorState(barLeft, barLeft, barRight, barRight))
                        .radius(new QuadRadiusState(0.5f))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, barY);

                offsetY += rowHeight * anim.heightProgress();
            }
        } finally {
            ScissorUtil.disableContext(event.getContext());
        }
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    private static int darkenRgb(int color, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        int a = (color >>> 24) & 0xFF;
        int r = Math.round(((color >>> 16) & 0xFF) * f);
        int g = Math.round(((color >>> 8) & 0xFF) * f);
        int b = Math.round((color & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean hasAnyArmor() {
        if (mc.player == null) return false;
        var inv = mc.player.getInventory();
        boolean hasArmor = !dev.client.api.compat.InventoryCompat.getArmorStack(inv, 3).isEmpty() || !dev.client.api.compat.InventoryCompat.getArmorStack(inv, 2).isEmpty() || !dev.client.api.compat.InventoryCompat.getArmorStack(inv, 1).isEmpty() || !dev.client.api.compat.InventoryCompat.getArmorStack(inv, 0).isEmpty();
        boolean hasShield = !mc.player.getOffHandStack().isEmpty() && mc.player.getOffHandStack().isOf(Items.SHIELD);
        return hasArmor || hasShield;
    }

    private void pickPreviewArmor() {
        Slot[] slotsPool = new Slot[]{Slot.HELMET, Slot.CHEST, Slot.LEGS, Slot.BOOTS};
        Item[] itemsPool = new Item[]{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        int idx = ThreadLocalRandom.current().nextInt(slotsPool.length);
        if (lastPreviewSlot != null && slotsPool.length > 1) {
            while (slotsPool[idx] == lastPreviewSlot) {
                idx = ThreadLocalRandom.current().nextInt(slotsPool.length);
            }
        }
        previewSlot = slotsPool[idx];
        previewStack = new ItemStack(itemsPool[idx]);
        lastPreviewSlot = previewSlot;
    }

    enum Slot {
        HELMET, CHEST, LEGS, BOOTS;

        public ItemStack get(net.minecraft.client.network.ClientPlayerEntity player) {
            if (player == null) return ItemStack.EMPTY;
            return switch (this) {
                case HELMET -> dev.client.api.compat.InventoryCompat.getArmorStack(player.getInventory(), 3);
                case CHEST -> dev.client.api.compat.InventoryCompat.getArmorStack(player.getInventory(), 2);
                case LEGS -> dev.client.api.compat.InventoryCompat.getArmorStack(player.getInventory(), 1);
                case BOOTS -> dev.client.api.compat.InventoryCompat.getArmorStack(player.getInventory(), 0);
                default -> ItemStack.EMPTY;
            };
        }
    }

    static class ArmorAnim {
        final Slot slot;
        final Animation anim = new DecelerateAnimation().setMs(250).setValue(1);
        ItemStack stack = ItemStack.EMPTY;

        ArmorAnim(Slot slot) {
            this.slot = slot;
            anim.setDirection(Direction.BACKWARDS);
        }

        void setStack(ItemStack stack) {
            this.stack = stack == null ? ItemStack.EMPTY : stack;
        }

        void show() {
            anim.setDirection(Direction.FORWARDS);
        }

        void hide() {
            anim.setDirection(Direction.BACKWARDS);
        }

        void tick() {
            anim.getOutput();
        }

        boolean isVisible() {
            return progress() > 0f;
        }

        float progress() {
            return anim.getOutput().floatValue();
        }

        float heightProgress() {
            return anim.getDirection() == Direction.BACKWARDS ? anim.getOutput().floatValue() : 1f;
        }

        float durability01() {
            if (stack.isEmpty() || !stack.isDamageable()) return 1f;
            int max = stack.getMaxDamage();
            int dmg = stack.getDamage();
            if (max <= 0) return 1f;
            float left = (max - dmg) / (float) max;
            return Math.max(0f, Math.min(1f, left));
        }

        Text displayName() {
            return stack.isEmpty() ? Text.of(" ") : stack.getName();
        }

        String percentText() {
            return String.format("%d%%", Math.round(durability01() * 100f));
        }
    }
}
