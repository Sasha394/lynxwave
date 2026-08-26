package dev.client.api.nullcry;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MouseClick {

    public boolean isClick(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX > x && mouseX < x + width && mouseY > y && mouseY < y + height;
    }

    public boolean isClick(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public boolean isClick(int mouseX, int mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
