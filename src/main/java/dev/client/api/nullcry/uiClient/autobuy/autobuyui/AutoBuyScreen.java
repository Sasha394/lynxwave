package dev.client.api.nullcry.uiClient.autobuy.autobuyui;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public class AutoBuyScreen extends Screen implements ClientApi {
    private static final float BACKGROUND_WIDTH = 400f;
    private static final float BACKGROUND_HEIGHT = 250f;

    private final AutoBuyGuiComponent autoBuyGuiComponent = new AutoBuyGuiComponent();
    private final PurchaseHistoryWindow purchaseHistoryWindow = new PurchaseHistoryWindow();

    public AutoBuyScreen() {
        super(Text.of("AutoBuy"));
    }

    @Override
    protected void init() {
        autoBuyGuiComponent.init();
        super.init();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float baseX = (this.width - BACKGROUND_WIDTH) / 2f;
        float baseY = (this.height - BACKGROUND_HEIGHT) / 2f;

        HelperElements.rectGui(context, baseX - 20f, baseY, BACKGROUND_WIDTH + 40f, BACKGROUND_HEIGHT);

        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
        Builder.TEXT_BUILDER
                .text("AutoBuy")
                .size(9f)
                .font(Builder.INTER.get())
                .color(ColorUtils.setAlpha(-1, 220))
                .build()
                .render(matrix, baseX - 8f, baseY + 8f);

        autoBuyGuiComponent.position(baseX - 20f, baseY)
                .size(BACKGROUND_WIDTH + 40f, BACKGROUND_HEIGHT + 30f)
                .render(context, mouseX, mouseY, delta);

        purchaseHistoryWindow.render(context, mouseX, mouseY, delta, this.width, this.height, (int) BACKGROUND_WIDTH, (int) BACKGROUND_HEIGHT);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (autoBuyGuiComponent.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        autoBuyGuiComponent.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (purchaseHistoryWindow.mouseScrolled(mouseX, mouseY, verticalAmount, this.width, this.height, (int) BACKGROUND_WIDTH, (int) BACKGROUND_HEIGHT)) {
            return true;
        }
        if (autoBuyGuiComponent.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int scanCode = keyInput.scancode();
        int modifiers = keyInput.modifiers();
        autoBuyGuiComponent.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        char chr = (char) charInput.codepoint();
        int modifiers = charInput.modifiers();
        autoBuyGuiComponent.charTyped(chr, modifiers);
        return super.charTyped(charInput);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
