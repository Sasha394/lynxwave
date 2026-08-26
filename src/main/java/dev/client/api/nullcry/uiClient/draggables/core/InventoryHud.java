package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class InventoryHud implements IHelper, SettingProvider {
    final DraggableHandler draggableHandler;

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

    public final CheckBox showAlways = new CheckBox("Показывать всегда", () -> true)
            .defaultValue(false)
            .register(this);

    public InventoryHud() {
        this.draggableHandler = addDraggable("InventoryHud", 4, 4);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("InventoryHud");
        });
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
    }

    final float PAD_H = 6f;
    final float PAD_V = 6f;
    final float ICON = 16f;
    final float SCALE = 0.62f;
    final float DRAW = ICON * SCALE;
    final float GAP = 4f;
    final float SEP = 1f;
    final float HEADER_H = 20f;

    final int COLS = 9;
    final int ROWS = 4;

    float width;
    float height;

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        boolean alwaysShow = showAlways.getEnabled();
        boolean hasItems = hasAnyItem(mc.player);
        if (!alwaysShow && !hasItems) return;
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();
        width = PAD_H * 2 + COLS * DRAW + (COLS - 1) * GAP;
        height = HEADER_H + PAD_V * 2 + ROWS * DRAW + (ROWS - 1) * GAP + 1f;

        draggableHandler.setWidth(width);
        draggableHandler.setHeight(height);

        DraggableHeaderRenderer.render(
                event,
                x,
                y,
                width,
                1f,
                HEADER_H,
                "K",
                9f,
                "Inventory",
                8f,
                -1,
                -1,
                () -> Interface.INSTANCE.getMainColor()
        );

        HelperElements.rectElements(event.getContext(), x, y + HEADER_H, width, height - HEADER_H, 1f);

        ScissorUtil.enableContext(event.getContext(), x, y, width, height);
        int sepColor = HelperElements.themedWhite(255);
        float startX = x + PAD_H;
        float startY = y + HEADER_H + PAD_V - 1f;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                ItemStack stack = getStack(mc.player, row, col);
                float xStack = startX + col * (DRAW + GAP);
                float yStack = startY + row * (DRAW + GAP);

                if (!stack.isEmpty()) {
                    MathUtil.scaleStart(event.getContext().getMatrices(), xStack, yStack + 1, SCALE);
                    event.getContext().drawItem(stack, (int) xStack, (int) ((int) yStack + 1));
                    event.getContext().drawStackOverlay(mc.textRenderer, stack, (int) xStack, (int) ((int) yStack + 1), null);
                    MathUtil.scaleEnd(event.getContext().getMatrices());
                }

                if (col < COLS - 1) {
                    float sx = xStack + DRAW + (GAP - SEP) / 2f;
                    float sy = yStack + 1f;
                    float sh = DRAW - 2f;
                    drawSep(event.getContext(), sx, sy, SEP, sh, sepColor);
                }

                if (row < ROWS - 1) {
                    float sx = xStack + 1f;
                    float sy = yStack + DRAW + (GAP - SEP) / 2f;
                    float sw = DRAW - 2f;
                    drawSep(event.getContext(), sx, sy, sw, SEP, sepColor);
                }
            }
        }

        ScissorUtil.disableContext(event.getContext());
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    private static void drawSep(DrawContext ctx, float x, float y, float w, float h, int color) {
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(w, h))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(0f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx), Math.round(x), Math.round(y));
    }

    private static ItemStack getStack(ClientPlayerEntity player, int row, int col) {
        if (player == null) return ItemStack.EMPTY;
        int index;
        if (row < 3) {
            index = 9 + row * 9 + col;
        } else {
            index = col;
        }
        if (index < 0 || index >= player.getInventory().getMainStacks().size()) return ItemStack.EMPTY;
        return player.getInventory().getStack(index);
    }

    private static boolean hasAnyItem(ClientPlayerEntity player) {
        if (player == null) return false;
        for (int i = 0; i < player.getInventory().getMainStacks().size(); i++) {
            if (!player.getInventory().getStack(i).isEmpty()) return true;
        }
        return false;
    }
}
