package dev.client.modules.core.render.beautifully;

/**
 * Значения текущей строки чата, общие для обхода строк в ChatHud и обоих
 * бэкендов отрисовки из 1.21.11 (ChatHud$Hud и ChatHud$Interactable).
 * Рендер однопоточный, а состояние вынесено из миксина, чтобы бэкенды
 * не ссылались на класс-владелец миксина после слияния с классом Minecraft.
 */
public final class ChatAnimationState {
    private static float alpha = 1.0f;
    private static float shift;

    private ChatAnimationState() {
    }

    public static void set(float lineAlpha, float lineShift) {
        alpha = lineAlpha;
        shift = lineShift;
    }

    public static void reset() {
        alpha = 1.0f;
        shift = 0.0f;
    }

    public static float alpha() {
        return alpha;
    }

    public static float shift() {
        return shift;
    }
}
