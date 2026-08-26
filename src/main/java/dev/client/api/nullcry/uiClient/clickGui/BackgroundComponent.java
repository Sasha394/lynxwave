package dev.client.api.nullcry.uiClient.clickGui;

import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BackgroundComponent extends Component {
    float revealProgress = 1f;

    public void setRevealProgress(float progress) {
        this.revealProgress = Math.max(0f, Math.min(1f, progress));
    }

    @Override
    public BackgroundComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        HelperElements.rectGui(context, getX(), getY(), getWidth(), getHeight(), revealProgress);
        return this;
    }
}
