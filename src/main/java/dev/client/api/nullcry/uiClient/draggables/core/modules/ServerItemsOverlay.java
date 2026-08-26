package dev.client.api.nullcry.uiClient.draggables.core.modules;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.keyboard.KeyboardStorage;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.DraggableHeaderRenderer;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.modules.core.misc.ServerHelper;
import dev.client.modules.core.render.Interface;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ServerItemsOverlay implements IHelper {
    private final DraggableHandler draggableHandler;

    private final List<ItemAnimation> itemAnimations = new ArrayList<>();

    public final CompactAnimation bindsWidth = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public final CompactAnimation bindsHeight = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public final CompactAnimation showAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);

    private static final float HEADER_HEIGHT = 20f;
    private static final float ROW_HEIGHT = 13f;
    private static final float PADDING = 6f;
    private static final float ICON_SCALE = 0.60f;
    private static final float ICON_DRAW = 16f * ICON_SCALE;
    private static final float COUNT_PAD = 2f;
    private static final float COUNT_SHIFT = -3f;
    private static final float COUNT_SIZE = 4.5f;

    private final List<Item> allShulkerItems = new ArrayList<>();
    private boolean shulkersInit = false;

    float width;
    float height;

    public ServerItemsOverlay() {
        this.draggableHandler = addDraggable("ServerItems", 920, 4);
        bindsWidth.setValue(draggableHandler.getWidth());
        bindsHeight.setValue(draggableHandler.getHeight());
        showAnim.setValue(0.0);
    }

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        ServerHelper helper = ServerHelper.INSTANCE;
        if (helper == null) {
            return;
        }

        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        OverlayMode mode = resolveMode(helper);
        Map<ServerHelper.ItemType, Integer> keyItems = helper.getKeyItems();
        boolean chatOpen = mc.inGameHud.getChatHud().isChatFocused();
        boolean preview = chatOpen && keyItems.isEmpty();

        boolean baseEnabled = helper.isEnabled() && (mode == OverlayMode.HOLY_WORLD
                ? helper.shouldRenderHolyWorldOverlay()
                : helper.shouldRenderFunTimeOverlay()
        );
        boolean shouldRender = baseEnabled && (!keyItems.isEmpty() || preview);

        showAnim.run(shouldRender ? 1.0 : 0.0);
        showAnim.update();
        float show = (float) showAnim.getValue();
        if (show <= 0f) {
            itemAnimations.clear();
            return;
        }

        float targetWidth = 90f;
        float targetHeight = HEADER_HEIGHT;

        if (preview) {
            itemAnimations.clear();
            ServerHelper.ItemType type = ServerHelper.ItemType.CROSSBOW;
            String keyName = "Key";

            float nameWidth = Builder.INTER.get().getWidth(type.displayName, 7);
            float keyWidth = Builder.INTER.get().getWidth(keyName, 7);

            float rowWidth = PADDING + ICON_DRAW + 6f + nameWidth + 6f + keyWidth + PADDING + 4;
            if (rowWidth > targetWidth) targetWidth = rowWidth;

            targetHeight += ROW_HEIGHT;
        } else {
            for (Map.Entry<ServerHelper.ItemType, Integer> entry : keyItems.entrySet()) {
                ItemAnimation anim = findAnimation(entry.getKey());
                if (anim == null) {
                    anim = new ItemAnimation(entry.getKey());
                    itemAnimations.add(anim);
                }
                anim.show();
            }

            for (ItemAnimation anim : itemAnimations) {
                if (!keyItems.containsKey(anim.type)) anim.hide();
                anim.tick();
            }

            itemAnimations.removeIf(ItemAnimation::isFinished);

            for (ItemAnimation anim : itemAnimations) {
                float progress = anim.getProgress();
                if (progress <= 0f) continue;

                ServerHelper.ItemType type = anim.type;
                int keyCode = keyItems.getOrDefault(type, -1);
                String keyName = getKeyName(keyCode);

                float nameWidth = Builder.INTER.get().getWidth(type.displayName, 7);
                float keyWidth = Builder.INTER.get().getWidth(keyName, 7);

                int totalCount = HelperElements.getTotalCount(type.item);
                float countWidth = 0f;
                if (totalCount > 0) {
                    String cnt = Integer.toString(totalCount);
                    float cntW = Builder.INTER.get().getWidth(cnt, COUNT_SIZE);
                    countWidth = Math.max(0f, COUNT_PAD + COUNT_SHIFT + cntW);
                }

                float rowWidth = PADDING + ICON_DRAW + countWidth + 6f + nameWidth + 6f + keyWidth + PADDING + 4;
                if (rowWidth > targetWidth) targetWidth = rowWidth;

                targetHeight += 0.1f + ROW_HEIGHT * anim.getHeightProgress();
            }
        }

        bindsWidth.run(targetWidth);
        bindsHeight.run(targetHeight);
        bindsWidth.update();
        bindsHeight.update();

        width = (float) bindsWidth.getValue();
        height = (float) bindsHeight.getValue();

        draggableHandler.setWidth(width);
        draggableHandler.setHeight(height);

        DraggableHeaderRenderer.render(
                event,
                x,
                y,
                width,
                show,
                HEADER_HEIGHT,
                mode.iconChar != 0 ? String.valueOf(mode.iconChar) : "",
                8f,
                mode.title,
                8f,
                -1,
                -1,
                () -> Interface.INSTANCE.getMainColor()
        );

        if (Interface.INSTANCE.blurStrength.getValue() > 0) {
            HelperElements.rectElements(event.getContext(), x, y + HEADER_HEIGHT, width, Math.max(0f, height - HEADER_HEIGHT), show);
        }

        ScissorUtil.enableContext(event.getContext(), x, y, width, height);
        float offsetY = 0f;
        if (preview) {
            float progress = show;
            ServerHelper.ItemType type = ServerHelper.ItemType.CROSSBOW;
            String keyName = "Key";
            float countWidth = 0f;

            float itemY = y + HEADER_HEIGHT + offsetY;
            float slide = (1f - progress) * 15f;

            HelperElements.rectElements(event.getContext(), x, itemY, width, ROW_HEIGHT, progress * show);

            float iconLeftX = x + PADDING - slide;
            float iconY = itemY + (ROW_HEIGHT - ICON_DRAW) / 2f + 0.5f;

            ItemStack stack = new ItemStack(type.item);
            MathUtil.scaleStart(event.getContext().getMatrices(), iconLeftX + 0.25f, iconY, ICON_SCALE);
            event.getContext().drawItem(stack, (int) (iconLeftX + 0.25f), (int) iconY);
            event.getContext().drawStackOverlay(mc.textRenderer, stack, (int) (iconLeftX + 0.25f), (int) iconY, null);
            MathUtil.scaleEnd(event.getContext().getMatrices());

            float separatorHeight = 9f;
            float separatorX = Math.round(iconLeftX + ICON_DRAW + countWidth + 3f);
            float separatorY = Math.round(itemY + (ROW_HEIGHT - separatorHeight) / 2f) + 0.5f;
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(1f, separatorHeight))
                    .color(new QuadColorState(Interface.INSTANCE.getMainColor()))
                    .radius(new QuadRadiusState(1f))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), separatorX, separatorY);

            float textBaseY = itemY + (ROW_HEIGHT - 7f) / 2f - 0.5f;
            float nameX = iconLeftX + ICON_DRAW + countWidth + 8f - slide;
            float keyWidth = Builder.INTER.get().getWidth(keyName, 7);
            float keyX = x + width - keyWidth - PADDING + slide;

            int textColor = HelperElements.themedText((int) (220 * progress * show));

            Builder.TEXT_BUILDER
                    .size(7)
                    .font(Builder.INTER.get())
                    .text(type.displayName)
                    .color(textColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, textBaseY);

            Builder.TEXT_BUILDER
                    .size(7)
                    .font(Builder.INTER.get())
                    .text(keyName)
                    .color(textColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), keyX, textBaseY);

            offsetY += ROW_HEIGHT;
        } else {
            List<ItemAnimation> visible = new ArrayList<>(itemAnimations);
            visible.sort(Comparator.comparing(anim -> anim.type.displayName));

            for (ItemAnimation anim : visible) {
                float progress = anim.getProgress();
                if (progress <= 0f) continue;

                ServerHelper.ItemType type = anim.type;
                int keyCode = keyItems.getOrDefault(type, -1);
                String keyName = getKeyName(keyCode);

                int totalCount = HelperElements.getTotalCount(type.item);
                float countWidth = 0f;
                String countText = null;
                if (totalCount > 0) {
                    countText = Integer.toString(totalCount);
                    float cntW = Builder.INTER.get().getWidth(countText, COUNT_SIZE);
                    countWidth = Math.max(0f, COUNT_PAD + COUNT_SHIFT + cntW);
                }

                float itemY = y + HEADER_HEIGHT + offsetY;
                float slide = (1f - progress) * 15f;

                HelperElements.rectElements(event.getContext(), x, itemY, width, ROW_HEIGHT, progress * show);

                float iconLeftX = x + PADDING - slide;
                float iconY = itemY + (ROW_HEIGHT - ICON_DRAW) / 2f + 0.5f;

                ItemStack stack = getDisplayStack(type);
                MathUtil.scaleStart(event.getContext().getMatrices(), iconLeftX + 0.25f, iconY, ICON_SCALE);
                event.getContext().drawItem(stack, (int) (iconLeftX + 0.25f), (int) iconY);
                event.getContext().drawStackOverlay(mc.textRenderer, stack, (int) (iconLeftX + 0.25f), (int) iconY, null);
                MathUtil.scaleEnd(event.getContext().getMatrices());

                if (countText != null) {
                    float cntX = iconLeftX + ICON_DRAW + COUNT_PAD + COUNT_SHIFT;
                    float cntY = itemY + (ROW_HEIGHT - COUNT_SIZE) / 2f;
                    Builder.TEXT_BUILDER
                            .text(countText)
                            .size(COUNT_SIZE)
                            .font(Builder.INTER.get())
                            .color(HelperElements.themedText((int) (255 * progress * show)))
                            .build()
                            .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), cntX, cntY);
                }

                float separatorHeight = 9f;
                float separatorX = Math.round(iconLeftX + ICON_DRAW + countWidth + 3f);
                float separatorY = Math.round(itemY + (ROW_HEIGHT - separatorHeight) / 2f) + 0.5f;
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(1f, separatorHeight))
                        .color(new QuadColorState(Interface.INSTANCE.getMainColor()))
                        .radius(new QuadRadiusState(1f))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), separatorX, separatorY);

                float textBaseY = itemY + (ROW_HEIGHT - 7f) / 2f - 0.5f;
                float nameX = iconLeftX + ICON_DRAW + countWidth + 8f - slide;
                float keyWidth = Builder.INTER.get().getWidth(keyName, 7);
                float keyX = x + width - keyWidth - PADDING + slide;

                int textColor = HelperElements.themedText((int) (220 * progress * show));

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(type.displayName)
                        .color(textColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, textBaseY);

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(keyName)
                        .color(textColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), keyX, textBaseY);

                offsetY += ROW_HEIGHT * anim.getHeightProgress();
            }
        }

        ScissorUtil.disableContext(event.getContext());
    }

    private ItemStack getDisplayStack(ServerHelper.ItemType type) {
        if (type == ServerHelper.ItemType.BACKPACK) {
            ensureShulkersLoaded();
            if (!allShulkerItems.isEmpty()) {
                long periodMs = 1000L;
                int idx = (int) ((System.currentTimeMillis() / periodMs) % allShulkerItems.size());
                return new ItemStack(allShulkerItems.get(idx));
            }
        }
        return new ItemStack(type.item);
    }

    private void ensureShulkersLoaded() {
        if (shulkersInit) return;
        shulkersInit = true;
        for (Item item : Registries.ITEM) {
            if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                allShulkerItems.add(item);
            }
        }
    }

    private ItemAnimation findAnimation(ServerHelper.ItemType type) {
        for (ItemAnimation anim : itemAnimations) if (anim.isSame(type)) return anim;
        return null;
    }

    private static String getKeyName(int key) {
        if (key == 0 || key == GLFW.GLFW_KEY_UNKNOWN) return "-";
        String name = KeyboardStorage.getKey(key);
        return name != null ? name : String.valueOf(key);
    }

    enum OverlayMode {
        FUN_TIME("Items FunTime", 'J'),
        HOLY_WORLD("Items HolyWorld", 'J'),
        SPECIAL("Items Special", 'J');

        final String title;
        final char iconChar;

        OverlayMode(String title, char iconChar) {
            this.title = title;
            this.iconChar = iconChar;
        }
    }

    private OverlayMode resolveMode(ServerHelper helper) {
        if (helper.isHW()) {
            return OverlayMode.HOLY_WORLD;
        }
        if (helper.isFT()) {
            return OverlayMode.FUN_TIME;
        }
        return OverlayMode.SPECIAL;
    }

    static class ItemAnimation {
        final ServerHelper.ItemType type;
        final CompactAnimation anim = new CompactAnimation(Easing.EASE_OUT_QUAD, 250);

        ItemAnimation(ServerHelper.ItemType type) {
            this.type = type;
            anim.setValue(0.0);
        }

        void show() {
            anim.run(1.0);
        }

        void hide() {
            anim.run(0.0);
        }

        void tick() {
            anim.update();
        }

        float getProgress() {
            return (float) anim.getValue();
        }

        float getHeightProgress() {
            return anim.getDestinationValue() == 0.0 ? getProgress() : 1f;
        }

        boolean isFinished() {
            return anim.getDestinationValue() == 0.0 && anim.isDone() && getProgress() <= 0.001f;
        }

        boolean isSame(ServerHelper.ItemType other) {
            return type == other;
        }
    }
}
