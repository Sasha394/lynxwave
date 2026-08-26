package dev.client.api.nullcry.uiClient.autobuy.settings;

import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.autobuy.originalitems.ItemRegistry;
import dev.client.api.nullcry.uiClient.autobuy.settings.AutoBuySettingsManager;
import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public abstract class AutoBuySettingsComponent extends Component {
    protected final AutoBuyItemSettings settings;

    protected AutoBuySettingsComponent(AutoBuyItemSettings settings) {
        this.settings = settings;
        this.size(0f, 18f);
    }

    protected boolean isHover(double mouseX, double mouseY) {
        return MouseClick.isClick(mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
    }

    public static class BuyBelowComponent extends AutoBuySettingsComponent {
        private boolean editing = false;
        private String inputText = "";

        public BuyBelowComponent(AutoBuyItemSettings settings) {
            super(settings);
        }

        @Override
        public Component render(DrawContext context, int mouseX, int mouseY, float delta) {
            float labelSize = 7.5f;
            float valueSize = 7.5f;

            Builder.TEXT_BUILDER
                    .text("Покупать ниже:")
                    .size(labelSize)
                    .font(Builder.INTER.get())
                    .color(ColorUtils.setAlpha(-1, 200))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), getX() + 10, getY() + 6);

            String displayText = editing ? inputText + "_" : settings.getBuyBelow() + "$";
            float textWidth = Builder.INTER.get().getWidth(displayText, valueSize);

            float boxX = getX() + getWidth() - textWidth - 16f;
            int outline = editing ? new Color(41, 42, 40, 60).getRGB() : new Color(41, 42, 40, 140).getRGB();
            int fill = new Color(41, 42, 40, 60).getRGB();

            Builder.BORDER_BUILDER
                    .size(new SizeState(textWidth + 9f, 12f))
                    .radius(new QuadRadiusState(3f))
                    .color(new QuadColorState(outline))
                    .thickness(-0.2f)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), boxX, getY() + 4f);

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(textWidth + 9f, 12f))
                    .radius(new QuadRadiusState(3f))
                    .color(new QuadColorState(fill))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), boxX, getY() + 4f);

            Builder.TEXT_BUILDER
                    .text(displayText)
                    .size(valueSize)
                    .font(Builder.INTER.get())
                    .color(ColorUtils.setAlpha(-1, 220))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), boxX + 4f, getY() + 6f);
            return this;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            String displayText = editing ? inputText + "_" : settings.getBuyBelow() + "$";
            float textWidth = Builder.INTER.get().getWidth(displayText, 7.5f);

            if (MouseClick.isClick(mouseX, mouseY, getX() + getWidth() - textWidth - 20, getY() + 4, textWidth + 15, 15) && button == 0) {
                editing = true;
                inputText = String.valueOf(settings.getBuyBelow());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (editing) {
                if (keyCode == GLFW.GLFW_KEY_ENTER) {
                    try {
                        int value = Integer.parseInt(inputText);
                        settings.setBuyBelow(Math.max(1, value));
                        AutoBuySettingsManager.getInstance().saveSettings(settings.getItemName(), settings);
                        ItemRegistry.reloadSettings();
                    } catch (NumberFormatException ignored) {
                    }
                    editing = false;
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    editing = false;
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !inputText.isEmpty()) {
                    inputText = inputText.substring(0, inputText.length() - 1);
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (editing && Character.isDigit(chr) && inputText.length() < 9) {
                inputText += chr;
                return true;
            }
            return super.charTyped(chr, modifiers);
        }
    }

    public static class MinQuantityComponent extends AutoBuySettingsComponent {
        private boolean editing = false;
        private String inputText = "";

        public MinQuantityComponent(AutoBuyItemSettings settings) {
            super(settings);
        }

        @Override
        public Component render(DrawContext context, int mouseX, int mouseY, float delta) {
            float labelSize = 7.5f;
            float valueSize = 7.5f;

            Builder.TEXT_BUILDER
                    .text("Количество от:")
                    .size(labelSize)
                    .font(Builder.INTER.get())
                    .color(ColorUtils.setAlpha(-1, 200))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), getX() + 10, getY() + 6);

            String displayText = editing ? inputText + "_" : String.valueOf(settings.getMinQuantity());
            float textWidth = Builder.INTER.get().getWidth(displayText, valueSize);

            float boxX = getX() + getWidth() - textWidth - 16f;
            int outline = editing ? new Color(41, 42, 40, 60).getRGB() : new Color(41, 42, 40, 140).getRGB();
            int fill = new Color(41, 42, 40, 60).getRGB();

            Builder.BORDER_BUILDER
                    .size(new SizeState(textWidth + 9f, 12f))
                    .radius(new QuadRadiusState(3f))
                    .color(new QuadColorState(outline))
                    .thickness(-0.2f)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), boxX, getY() + 4f);

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(textWidth + 9f, 12f))
                    .radius(new QuadRadiusState(3f))
                    .color(new QuadColorState(fill))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), boxX, getY() + 4f);

            Builder.TEXT_BUILDER
                    .text(displayText)
                    .size(valueSize)
                    .font(Builder.INTER.get())
                    .color(ColorUtils.setAlpha(-1, 220))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), boxX + 4f, getY() + 6f);
            return this;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            String displayText = editing ? inputText + "_" : String.valueOf(settings.getMinQuantity());
            float textWidth = Builder.INTER.get().getWidth(displayText, 7.5f);

            if (MouseClick.isClick(mouseX, mouseY, getX() + getWidth() - textWidth - 20, getY() + 4, textWidth + 15, 15) && button == 0) {
                editing = true;
                inputText = String.valueOf(settings.getMinQuantity());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (editing) {
                if (keyCode == GLFW.GLFW_KEY_ENTER) {
                    try {
                        int value = Integer.parseInt(inputText);
                        settings.setMinQuantity(Math.max(1, Math.min(64, value)));
                        AutoBuySettingsManager.getInstance().saveSettings(settings.getItemName(), settings);
                        ItemRegistry.reloadSettings();
                    } catch (NumberFormatException ignored) {
                    }
                    editing = false;
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    editing = false;
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !inputText.isEmpty()) {
                    inputText = inputText.substring(0, inputText.length() - 1);
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (editing && Character.isDigit(chr) && inputText.length() < 2) {
                inputText += chr;
                return true;
            }
            return super.charTyped(chr, modifiers);
        }
    }
}
