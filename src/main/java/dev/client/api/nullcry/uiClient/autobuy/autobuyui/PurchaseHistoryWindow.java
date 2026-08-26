package dev.client.api.nullcry.uiClient.autobuy.autobuyui;

import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.autobuy.items.AutoBuyableItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PurchaseHistoryWindow {
    private static final List<PurchaseRecord> purchases = new CopyOnWriteArrayList<>();
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private float scroll = 0f;
    private float smoothedScroll = 0f;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public static void addPurchase(AutoBuyableItem item, int price) {
        String cleanName = item.getDisplayName();
        long currentTime = System.currentTimeMillis();
        for (PurchaseRecord record : purchases) {
            if (record.itemName.equals(cleanName) && record.price == price && (currentTime - record.timestamp) < 1000) {
                return;
            }
        }
        ItemStack stackCopy = item.createItemStack();
        stackCopy.setCount(1);
        purchases.add(0, new PurchaseRecord(stackCopy, cleanName, price, item.getSettings().getMinQuantity(), currentTime));
        if (purchases.size() > 50) {
            purchases.remove(purchases.size() - 1);
        }
    }

    public static void addPurchase(String itemName, int price) {
        String cleanName = itemName;
        long currentTime = System.currentTimeMillis();
        for (PurchaseRecord record : purchases) {
            if (record.itemName.equals(cleanName) && record.price == price && (currentTime - record.timestamp) < 1000) {
                return;
            }
        }
        purchases.add(0, new PurchaseRecord(null, cleanName, price, 1, currentTime));
        if (purchases.size() > 50) {
            purchases.remove(purchases.size() - 1);
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta, int width, int height, int backgroundWidth, int backgroundHeight) {
        float panelX = (width - backgroundWidth) / 2f - 200f;
        float panelY = (height - backgroundHeight) / 2f;
        float panelWidth = 180f;
        float panelHeight = backgroundHeight;
        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(panelWidth, panelHeight))
                .radius(new QuadRadiusState(6f))
                .color(new QuadColorState(ColorUtils.rgba(12, 12, 12, 200)))
                .build()
                .render(matrix, panelX, panelY);

        Builder.TEXT_BUILDER
                .text("История покупок")
                .size(8f)
                .font(Builder.INTER.get())
                .color(ColorUtils.setAlpha(-1, 230))
                .build()
                .render(matrix, panelX + 18f, panelY + 10f);

        if (!purchases.isEmpty()) {
            float contentHeight = purchases.size() * 50f;
            float maxScroll = Math.max(0, contentHeight - (panelHeight - 30));
            scroll = Math.min(0, Math.max(scroll, -maxScroll));
            smoothedScroll = MathHelper.lerp(0.15f, smoothedScroll, scroll);

            float itemY = panelY + 30 + smoothedScroll;
            List<PurchaseRecord> purchasesCopy = new ArrayList<>(purchases);

            ScissorUtil.enableContext(context, panelX, panelY + 30, panelWidth, panelHeight - 30);
            for (PurchaseRecord record : purchasesCopy) {
                if (itemY + 50 >= panelY + 30 && itemY <= panelY + panelHeight - 10) {
                    if (record.itemStack != null && !record.itemStack.isEmpty()) {
                        DrawUtil.drawItemStack(context, record.itemStack, panelX + 6, itemY + 13.5f, 0.9f);
                    }
                    String displayName = record.itemName;
                    if (displayName.length() > 18) {
                        displayName = displayName.substring(0, 15) + "...";
                    }
                    Builder.TEXT_BUILDER
                            .text(displayName)
                            .size(7.5f)
                            .font(Builder.INTER.get())
                            .color(ColorUtils.setAlpha(-1, 230))
                            .build()
                            .render(matrix, panelX + 30, itemY + 5);

                    String priceText = "$" + formatPrice(record.price);
                    if (record.quantity > 1) {
                        priceText += " x" + record.quantity;
                    }
                    Builder.TEXT_BUILDER
                            .text(priceText)
                            .size(7f)
                            .font(Builder.INTER.get())
                            .color(ColorUtils.rgba(100, 255, 100, 230))
                            .build()
                            .render(matrix, panelX + 30, itemY + 16);

                    String timeText = timeFormat.format(new Date(record.timestamp));
                    Builder.TEXT_BUILDER
                            .text(timeText)
                            .size(6.5f)
                            .font(Builder.INTER.get())
                            .color(ColorUtils.rgba(150, 150, 150, 200))
                            .build()
                            .render(matrix, panelX + 30, itemY + 26);
                }
                itemY += 50;
            }
            ScissorUtil.disableContext(context);

            if (maxScroll > 0) {
                float viewable = panelHeight - 30;
                float scrollbarHeight = Math.max(20, viewable * viewable / contentHeight);
                float scrollPercent = -smoothedScroll / maxScroll;
                float scrollbarY = panelY + 30 + scrollPercent * (viewable - scrollbarHeight);

                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(3f, viewable))
                        .radius(new QuadRadiusState(1f))
                        .color(new QuadColorState(ColorUtils.rgba(30, 30, 30, 100)))
                        .build()
                        .render(matrix, panelX + panelWidth - 6f, panelY + 30);

                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(3f, scrollbarHeight))
                        .radius(new QuadRadiusState(1.5f))
                        .color(new QuadColorState(ColorUtils.rgba(100, 100, 100, 180)))
                        .build()
                        .render(matrix, panelX + panelWidth - 6f, scrollbarY);
            }
        } else {
            Builder.TEXT_BUILDER
                    .text("Покупки отсутствуют")
                    .size(7.5f)
                    .font(Builder.INTER.get())
                    .color(ColorUtils.rgba(150, 150, 150, 220))
                    .build()
                    .render(matrix, panelX + panelWidth / 2f - 45f, panelY + 105f);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount, int width, int height, int backgroundWidth, int backgroundHeight) {
        if (purchases.isEmpty()) return false;
        float panelX = (width - backgroundWidth) / 2f - 200f;
        float panelY = (height - backgroundHeight) / 2f;
        float panelWidth = 180f;
        float panelHeight = backgroundHeight;
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            scroll += amount * 20;
            return true;
        }
        return false;
    }

    private String formatPrice(int price) {
        if (price >= 1_000_000) {
            return String.format("%.1fM", price / 1_000_000.0);
        } else if (price >= 1_000) {
            return String.format("%.1fK", price / 1_000.0);
        }
        return String.valueOf(price);
    }

    public static void clear() {
        purchases.clear();
    }

    public static class PurchaseRecord {
        private final ItemStack itemStack;
        private final String itemName;
        private final int price;
        private final int quantity;
        private final long timestamp;

        public PurchaseRecord(ItemStack itemStack, String itemName, int price, int quantity, long timestamp) {
            this.itemStack = itemStack;
            this.itemName = itemName;
            this.price = price;
            this.quantity = quantity;
            this.timestamp = timestamp;
        }
    }
}
