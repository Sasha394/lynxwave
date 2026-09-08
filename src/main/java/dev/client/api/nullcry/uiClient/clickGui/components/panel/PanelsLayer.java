package dev.client.api.nullcry.uiClient.clickGui.components.panel;

import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import dev.client.api.nullcry.uiClient.clickGui.components.search.SearchRanker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class PanelsLayer extends Component {
    List<Component> componentsList = new ArrayList<>();
    private float revealProgress = 1f;

    public void setRevealProgress(float revealProgress) {
        this.revealProgress = MathHelper.clamp(revealProgress, 0f, 1f);
    }

    @Override
    public void init() {
        componentsList.clear();
        Arrays.stream(ModuleCategory.values()).map(PanelComponent::new).forEach(componentsList::add);
    }

    @Override
    public PanelsLayer render(DrawContext context, int mouseX, int mouseY, float delta) {
        AtomicReference<Float> offset = new AtomicReference<>(0f);
        int count = componentsList.size();
        float pivot = count <= 1 ? 0f : (count - 1) / 2f;
        float step = 0.12f;

        float panelWidth = 250f / 2f + 20f;
        float spacing = 8f;
        float totalWidth = count * panelWidth + Math.max(0, count - 1) * spacing;
        float startX = getX() + (getWidth() - totalWidth) / 2f;

        for (int i = 0; i < componentsList.size(); i++) {
            Component component = componentsList.get(i);
            float baseX = startX + offset.get();
            float baseY = getY();
            float width = panelWidth;
            float height = getHeight();

            float distance = Math.abs(i - pivot);
            float delay = distance * step;
            float duration = Math.max(0.0001f, 1f - delay);
            float localProgress = MathHelper.clamp(revealProgress <= delay ? 0f : (revealProgress - delay) / duration, 0f, 1f);

            float easedProgress = easeOutCubic(localProgress);
            float translateY = (1f - easedProgress) * 12f;
            float scale = MathHelper.lerp(easedProgress, 0.9f, 1f);
            float centerX = baseX + width / 2f;
            float centerY = baseY + height / 2f;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0f, translateY);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(centerX, centerY);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-centerX, -centerY);
            try {
                if (component instanceof PanelComponent panelComponent) {
                    panelComponent.revealProgress(easedProgress);
                }

                component.position(baseX, baseY)
                        .size(width, height)
                        .render(context, mouseX, mouseY, delta);
            } finally {
                context.getMatrices().popMatrix();
                context.getMatrices().popMatrix();
            }

            offset.set(offset.get() + width + spacing);
        }

        return null;
    }

    public void updateFilter(String searchText) {
        boolean searching = !SearchRanker.normalize(searchText).isEmpty();

        componentsList.clear();
        Arrays.stream(ModuleCategory.values())
                .map(category -> {
                    PanelComponent panel = new PanelComponent(category);
                    panel.updateFilter(searchText);
                    return panel;
                })
                // При активном поиске пустые категории не занимают место — выдача остаётся плотной.
                .filter(panel -> !searching || panel.hasModules())
                .forEach(componentsList::add);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int count = componentsList.size();
        float panelWidth = 250f / 2f + 20f;
        float spacing = 8f;
        float totalWidth = count * panelWidth + Math.max(0, count - 1) * spacing;
        float overflow = Math.max(0f, (totalWidth - getWidth()) / 2f);

        float scissorX = getX() - 20f - overflow;
        float scissorY = getY();
        float scissorWidth = getWidth() + 40f + overflow * 2f;
        float scissorHeight = getHeight() - 4f;

        if (MouseClick.isClick(mouseX, mouseY, scissorX, scissorY, scissorWidth, scissorHeight)) {
            componentsList.forEach(e -> e.mouseClicked(mouseX, mouseY, button));
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        componentsList.forEach(e -> e.mouseReleased(mouseX, mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        componentsList.forEach(e -> e.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount));
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        componentsList.forEach(e -> e.keyPressed(keyCode, scanCode, modifiers));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        componentsList.forEach(e -> e.keyReleased(keyCode, scanCode, modifiers));
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        componentsList.forEach(e -> e.charTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    private float easeOutCubic(float x) {
        return 1f - (float) Math.pow(1f - x, 3);
    }
}
