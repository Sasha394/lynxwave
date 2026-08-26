package dev.client.api.nullcry.uiClient.clickGui.api.component;

import net.minecraft.client.gui.DrawContext;

public interface ComponentLayer {
    ComponentLayer position(float x, float y);
    ComponentLayer size(float width, float height);
    ComponentLayer render(DrawContext context, int mouseX, int mouseY, float delta);
    boolean mouseClicked(double mouseX, double mouseY, int button);
    boolean mouseReleased(double mouseX, double mouseY, int button);
    boolean keyPressed(int keyCode, int scanCode, int modifiers);
    boolean keyReleased(int keyCode, int scanCode, int modifiers);
    boolean charTyped(char chr, int modifiers);
    boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount);
    void init();
}
