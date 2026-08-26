package dev.client.api.nullcry.uiClient.autobuy.autobuyui;

import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.autobuy.items.AutoBuyableItem;
import dev.client.api.nullcry.uiClient.autobuy.manager.AutoBuyManager;
import dev.client.api.nullcry.uiClient.autobuy.originalitems.ItemRegistry;
import dev.client.api.nullcry.uiClient.autobuy.window.AutoBuyItemSettingsWindow;
import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AutoBuyGuiComponent extends Component {
    private static final float ITEM_WIDTH = 175f;
    private static final float ITEM_HEIGHT = 45f;
    private static final float ITEM_X_STEP = 190f;

    private float scroll = 0f;
    private float smoothedScroll = 0f;
    private final AutoBuyManager autoBuyManager = AutoBuyManager.getInstance();
    private final List<AutoBuyItemSettingsWindow> windows = new ArrayList<>();

    @Override
    public Component render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderAllItems(context, mouseX, mouseY, delta);
        renderWindows(context, mouseX, mouseY, delta);
        return this;
    }

    private void renderAllItems(DrawContext context, int mouseX, int mouseY, float delta) {
        float listX = getX() + 55f;
        float listY = getY() + 25f;
        float listWidth = getWidth() - 43f - 15f;
        float listHeight = getHeight() - 48f;

        float contentHeight = calculateContentHeight();
        float maxScrollAmount = Math.max(0f, contentHeight - listHeight);
        scroll = MathHelper.clamp(scroll, -maxScrollAmount, 0f);
        smoothedScroll = MathHelper.lerp(0.15f, smoothedScroll, scroll);

        ScissorUtil.enableContext(context, listX, listY + 4, listWidth, listHeight - 11);

        float itemY = listY + 10f + smoothedScroll;
        int globalIndex = 0;

        List<AutoBuyableItem> krushItems = ItemRegistry.getKrush();
        if (!krushItems.isEmpty()) {
            renderCategoryHeader(context, listX, itemY, listWidth, "Крушитель");
            itemY += 20f;
            for (int i = 0; i < krushItems.size(); i++) {
                AutoBuyableItem item = krushItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;
                renderItem(context, item, itemX, itemY, mouseX, mouseY, delta, globalIndex);
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> talismanItems = ItemRegistry.getTalismans();
        if (!talismanItems.isEmpty()) {
            renderCategoryHeader(context, listX, itemY, listWidth, "Талисманы");
            itemY += 20f;
            for (int i = 0; i < talismanItems.size(); i++) {
                AutoBuyableItem item = talismanItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;
                renderItem(context, item, itemX, itemY, mouseX, mouseY, delta, globalIndex);
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> sphereItems = ItemRegistry.getSpheres();
        if (!sphereItems.isEmpty()) {
            renderCategoryHeader(context, listX, itemY, listWidth, "Сферы");
            itemY += 20f;
            for (int i = 0; i < sphereItems.size(); i++) {
                AutoBuyableItem item = sphereItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;
                renderItem(context, item, itemX, itemY, mouseX, mouseY, delta, globalIndex);
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> miscItems = ItemRegistry.getMisc();
        if (!miscItems.isEmpty()) {
            renderCategoryHeader(context, listX, itemY, listWidth, "Разное");
            itemY += 20f;
            for (int i = 0; i < miscItems.size(); i++) {
                AutoBuyableItem item = miscItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;
                renderItem(context, item, itemX, itemY, mouseX, mouseY, delta, globalIndex);
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> donatorItems = ItemRegistry.getDonator();
        if (!donatorItems.isEmpty()) {
            renderCategoryHeader(context, listX, itemY, listWidth, "Донаторские");
            itemY += 20f;
            for (int i = 0; i < donatorItems.size(); i++) {
                AutoBuyableItem item = donatorItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;
                renderItem(context, item, itemX, itemY, mouseX, mouseY, delta, globalIndex);
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> potionItems = ItemRegistry.getPotions();
        if (!potionItems.isEmpty()) {
            renderCategoryHeader(context, listX, itemY, listWidth, "Зелья");
            itemY += 20f;
            for (int i = 0; i < potionItems.size(); i++) {
                AutoBuyableItem item = potionItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;
                renderItem(context, item, itemX, itemY, mouseX, mouseY, delta, globalIndex);
                globalIndex++;
            }
            itemY += 60f;
        }

        ScissorUtil.disableContext(context);
    }

    private void renderWindows(DrawContext context, int mouseX, int mouseY, float delta) {
        if (windows.isEmpty()) return;
        for (Iterator<AutoBuyItemSettingsWindow> it = windows.iterator(); it.hasNext(); ) {
            AutoBuyItemSettingsWindow window = it.next();
            if (window.isClosed()) {
                it.remove();
                continue;
            }
            window.render(context, mouseX, mouseY, delta);
        }
    }

    private float calculateContentHeight() {
        List<AutoBuyableItem> krushItems = ItemRegistry.getKrush();
        List<AutoBuyableItem> talismanItems = ItemRegistry.getTalismans();
        List<AutoBuyableItem> sphereItems = ItemRegistry.getSpheres();
        List<AutoBuyableItem> miscItems = ItemRegistry.getMisc();
        List<AutoBuyableItem> donatorItems = ItemRegistry.getDonator();
        List<AutoBuyableItem> potionItems = ItemRegistry.getPotions();
        float totalHeight = 10f;
        if (!krushItems.isEmpty()) {
            totalHeight += 20f + ((krushItems.size() + 1) / 2) * 50f + 25f;
        }
        if (!talismanItems.isEmpty()) {
            totalHeight += 20f + ((talismanItems.size() + 1) / 2) * 50f + 25f;
        }
        if (!sphereItems.isEmpty()) {
            totalHeight += 20f + ((sphereItems.size() + 1) / 2) * 50f + 25f;
        }
        if (!miscItems.isEmpty()) {
            totalHeight += 20f + ((miscItems.size() + 1) / 2) * 50f + 25f;
        }
        if (!donatorItems.isEmpty()) {
            totalHeight += 20f + ((donatorItems.size() + 1) / 2) * 50f + 25f;
        }
        if (!potionItems.isEmpty()) {
            totalHeight += 20f + ((potionItems.size() + 1) / 2) * 50f + 10f;
        }
        return totalHeight;
    }

    private void renderCategoryHeader(DrawContext context, float x, float y, float width, String categoryName) {
        float textSize = 8f;
        float textWidth = Builder.INTER.get().getWidth(categoryName, textSize);
        float lineWidth = (width - textWidth - 20f) / 2f;
        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(Math.max(0, lineWidth - 10f), 1f))
                .color(new QuadColorState(ColorUtils.rgba(54, 54, 56, 200)))
                .radius(new QuadRadiusState(0))
                .build()
                .render(matrix, x, y + 6f);

        Builder.TEXT_BUILDER
                .text(categoryName)
                .size(textSize)
                .font(Builder.INTER.get())
                .color(ColorUtils.setAlpha(-1, 200))
                .build()
                .render(matrix, x + lineWidth, y + 3.5f);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(Math.max(0, lineWidth - 6f), 1f))
                .color(new QuadColorState(ColorUtils.rgba(54, 54, 56, 200)))
                .radius(new QuadRadiusState(0))
                .build()
                .render(matrix, x + lineWidth + textWidth + 10f, y + 6f);
    }

    private void renderItem(DrawContext context, AutoBuyableItem item, float itemX, float itemY, int mouseX, int mouseY, float delta, int index) {
        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
        ItemStack itemStack = item.createItemStack();

        int bgTopLeft = ColorUtils.rgba(18, 19, 20, 200);
        int bgBottomRight = ColorUtils.rgba(15, 16, 18, 200);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(ITEM_WIDTH, ITEM_HEIGHT))
                .radius(new QuadRadiusState(6f))
                .color(new QuadColorState(bgTopLeft, bgTopLeft, bgBottomRight, bgBottomRight))
                .build()
                .render(matrix, itemX, itemY);

        DrawUtil.drawItemStack(context, itemStack, itemX + 6, itemY + 13.5f, 0.9f);

        int nameColor = ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), 230);
        float nameMaxWidth = ITEM_WIDTH - 30f - 26f;
        String name = trimToWidth(item.getDisplayName(), nameMaxWidth, 8f);
        Builder.TEXT_BUILDER
                .text(name)
                .size(8f)
                .font(Builder.INTER.get())
                .color(nameColor)
                .build()
                .render(matrix, itemX + 30, itemY + 12f);

        int subColor = ColorUtils.setAlpha(-1, 170);
        Builder.TEXT_BUILDER
                .text("Цена покупки: $" + item.getSettings().getBuyBelow())
                .size(7f)
                .font(Builder.INTER.get())
                .color(subColor)
                .build()
                .render(matrix, itemX + 30, itemY + 22f);

        Builder.TEXT_BUILDER
                .text("Количество от: " + item.getSettings().getMinQuantity())
                .size(7f)
                .font(Builder.INTER.get())
                .color(subColor)
                .build()
                .render(matrix, itemX + 30, itemY + 31f);

        renderToggle(matrix, itemX + ITEM_WIDTH - 22f, itemY + 17f, item.isEnabled());
    }

    private String trimToWidth(String text, float maxWidth, float fontSize) {
        if (text == null) {
            return "";
        }
        if (Builder.INTER.get().getWidth(text, fontSize) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        float ellipsisWidth = Builder.INTER.get().getWidth(ellipsis, fontSize);
        int end = text.length();
        while (end > 0) {
            String candidate = text.substring(0, end);
            if (Builder.INTER.get().getWidth(candidate, fontSize) + ellipsisWidth <= maxWidth) {
                return candidate + ellipsis;
            }
            end--;
        }
        return ellipsis;
    }

    private void renderToggle(Matrix4f matrix, float x, float y, boolean enabled) {
        float toggleWidth = 16f;
        float toggleHeight = 10f;
        int baseColor = enabled ? Interface.INSTANCE.getMainColor() : ColorUtils.rgb(60, 60, 60);
        int fillColor = ColorUtils.setAlpha(baseColor, enabled ? 220 : 160);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(toggleWidth, toggleHeight))
                .radius(new QuadRadiusState(toggleHeight / 2f))
                .color(new QuadColorState(fillColor))
                .build()
                .render(matrix, x, y);

        float knobSize = toggleHeight - 2f;
        float knobX = enabled ? x + toggleWidth - knobSize - 1f : x + 1f;
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(knobSize, knobSize))
                .radius(new QuadRadiusState(knobSize / 2f))
                .color(new QuadColorState(ColorUtils.setAlpha(-1, 220)))
                .build()
                .render(matrix, knobX, y + 1f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        float listX = getX() + 55f;
        float listY = getY() + 25f;
        float itemY = listY + 10f + smoothedScroll;
        int globalIndex = 0;

        List<AutoBuyableItem> krushItems = ItemRegistry.getKrush();
        if (!krushItems.isEmpty()) {
            itemY += 20f;
            for (int i = 0; i < krushItems.size(); i++) {
                AutoBuyableItem item = krushItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;

                if (button == 1 && MouseClick.isClick(mouseX, mouseY, itemX, itemY, ITEM_WIDTH, ITEM_HEIGHT)) {
                    openSettingsWindow(item);
                    return true;
                }

                if (button == 0 && MouseClick.isClick(mouseX, mouseY, itemX + ITEM_WIDTH - 22f, itemY + 17f, 16f, 10f)) {
                    autoBuyManager.toggleItem(item);
                    return true;
                }
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> talismanItems = ItemRegistry.getTalismans();
        if (!talismanItems.isEmpty()) {
            itemY += 20f;
            for (int i = 0; i < talismanItems.size(); i++) {
                AutoBuyableItem item = talismanItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;

                if (button == 1 && MouseClick.isClick(mouseX, mouseY, itemX, itemY, ITEM_WIDTH, ITEM_HEIGHT)) {
                    openSettingsWindow(item);
                    return true;
                }

                if (button == 0 && MouseClick.isClick(mouseX, mouseY, itemX + ITEM_WIDTH - 22f, itemY + 17f, 16f, 10f)) {
                    autoBuyManager.toggleItem(item);
                    return true;
                }
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> sphereItems = ItemRegistry.getSpheres();
        if (!sphereItems.isEmpty()) {
            itemY += 20f;
            for (int i = 0; i < sphereItems.size(); i++) {
                AutoBuyableItem item = sphereItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;

                if (button == 1 && MouseClick.isClick(mouseX, mouseY, itemX, itemY, ITEM_WIDTH, ITEM_HEIGHT)) {
                    openSettingsWindow(item);
                    return true;
                }

                if (button == 0 && MouseClick.isClick(mouseX, mouseY, itemX + ITEM_WIDTH - 22f, itemY + 17f, 16f, 10f)) {
                    autoBuyManager.toggleItem(item);
                    return true;
                }
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> miscItems = ItemRegistry.getMisc();
        if (!miscItems.isEmpty()) {
            itemY += 20f;
            for (int i = 0; i < miscItems.size(); i++) {
                AutoBuyableItem item = miscItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;

                if (button == 1 && MouseClick.isClick(mouseX, mouseY, itemX, itemY, ITEM_WIDTH, ITEM_HEIGHT)) {
                    openSettingsWindow(item);
                    return true;
                }

                if (button == 0 && MouseClick.isClick(mouseX, mouseY, itemX + ITEM_WIDTH - 22f, itemY + 17f, 16f, 10f)) {
                    autoBuyManager.toggleItem(item);
                    return true;
                }
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> donatorItems = ItemRegistry.getDonator();
        if (!donatorItems.isEmpty()) {
            itemY += 20f;
            for (int i = 0; i < donatorItems.size(); i++) {
                AutoBuyableItem item = donatorItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;

                if (button == 1 && MouseClick.isClick(mouseX, mouseY, itemX, itemY, ITEM_WIDTH, ITEM_HEIGHT)) {
                    openSettingsWindow(item);
                    return true;
                }

                if (button == 0 && MouseClick.isClick(mouseX, mouseY, itemX + ITEM_WIDTH - 22f, itemY + 17f, 16f, 10f)) {
                    autoBuyManager.toggleItem(item);
                    return true;
                }
                globalIndex++;
            }
            itemY += 75f;
        }

        List<AutoBuyableItem> potionItems = ItemRegistry.getPotions();
        if (!potionItems.isEmpty()) {
            itemY += 20f;
            for (int i = 0; i < potionItems.size(); i++) {
                AutoBuyableItem item = potionItems.get(i);
                float itemX = listX + (i % 2) * ITEM_X_STEP;
                if (i % 2 == 0 && i > 0) itemY += 50;

                if (button == 1 && MouseClick.isClick(mouseX, mouseY, itemX, itemY, ITEM_WIDTH, ITEM_HEIGHT)) {
                    openSettingsWindow(item);
                    return true;
                }

                if (button == 0 && MouseClick.isClick(mouseX, mouseY, itemX + ITEM_WIDTH - 22f, itemY + 17f, 16f, 10f)) {
                    autoBuyManager.toggleItem(item);
                    return true;
                }
                globalIndex++;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (AutoBuyItemSettingsWindow window : windows) {
            window.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (AutoBuyItemSettingsWindow window : windows) {
            if (window.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }

        float listX = getX() + 55f;
        float listY = getY() + 25f;
        float listWidth = getWidth() - 43f - 15f;
        float listHeight = getHeight() - 48f;
        if (MouseClick.isClick(mouseX, mouseY, listX, listY + 13, listWidth, listHeight)) {
            scroll += verticalAmount * 20f;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (AutoBuyItemSettingsWindow window : windows) {
            if (window.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (AutoBuyItemSettingsWindow window : windows) {
            if (window.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    private void openSettingsWindow(AutoBuyableItem item) {
        for (AutoBuyItemSettingsWindow window : windows) {
            if (window.isForItem(item)) {
                return;
            }
        }
        AutoBuyItemSettingsWindow settingsWindow = new AutoBuyItemSettingsWindow(item, item.getSettings());
        settingsWindow.position(getX() + getWidth() + 24f, getY()).size(180f, settingsWindow.getComponentHeight());
        windows.add(settingsWindow);
    }
}
