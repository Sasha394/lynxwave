package dev.client.api.nullcry.uiClient.clickGui.components.theme;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Theme {

    public static final String PRIMARY = "Основной";
    public static final String VISUAL_MODULES = "Визуальные модули";
    public static final String TEXT = "Текст";
    public static final String INACTIVE_TEXT = "Неактивный текст";
    public static final String HEADER_TEXT = "Текст заголовок";
    public static final String PREMIUM_TEXT = "Премиум текст";
    public static final String SLIDER = "Слайдер";
    public static final String SLIDER_KNOB = "Круг слайдера";
    public static final String SLIDER_WINDOW = "Окно слайдера";
    public static final String TOGGLE = "Переключатель";
    public static final String INACTIVE_TOGGLE = "Неактивный переключатель";
    public static final String BUTTON = "Кнопка";
    public static final String INACTIVE_BUTTON = "Неактивная кнопка";

    public static final String[] ORDER = {
            PRIMARY, VISUAL_MODULES, TEXT, INACTIVE_TEXT,
            HEADER_TEXT, PREMIUM_TEXT, SLIDER, SLIDER_KNOB,
            SLIDER_WINDOW, TOGGLE, INACTIVE_TOGGLE, BUTTON, INACTIVE_BUTTON
    };

    private String name;
    private final List<ElementColor> elementColors = new ArrayList<>();

    public Theme(String name) {
        this.name = name;
        loadDefaults();
    }

    private void loadDefaults() {
        Map<String, Color> defaults = new LinkedHashMap<>();
        defaults.put(PRIMARY, new Color(180, 20, 35));
        defaults.put(VISUAL_MODULES, new Color(180, 20, 35, 180));
        defaults.put(TEXT, new Color(255, 255, 255));
        defaults.put(INACTIVE_TEXT, new Color(150, 150, 150));
        defaults.put(HEADER_TEXT, new Color(180, 20, 35));
        defaults.put(PREMIUM_TEXT, new Color(255, 215, 0));
        defaults.put(SLIDER, new Color(180, 20, 35));
        defaults.put(SLIDER_KNOB, new Color(255, 255, 255));
        defaults.put(SLIDER_WINDOW, new Color(40, 42, 48));
        defaults.put(TOGGLE, new Color(180, 20, 35));
        defaults.put(INACTIVE_TOGGLE, new Color(60, 62, 68));
        defaults.put(BUTTON, new Color(180, 20, 35));
        defaults.put(INACTIVE_BUTTON, new Color(60, 62, 68));

        for (String key : ORDER) {
            elementColors.add(new ElementColor(key, defaults.get(key)));
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ElementColor> getElementColors() {
        return elementColors;
    }

    public Color getElementColor(String elementName) {
        for (ElementColor element : elementColors) {
            if (element.getName().equalsIgnoreCase(elementName)) {
                return element.getColor();
            }
        }
        return new Color(-1);
    }

    public Color getPrimaryColor() { return getElementColor(PRIMARY); }

    public static class ElementColor {
        private final String name;
        private Color color;

        public ElementColor(String name, Color color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public Color getColor() { return color; }
        public void setColor(Color color) { this.color = color; }
    }
}
