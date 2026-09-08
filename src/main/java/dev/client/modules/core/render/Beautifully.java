package dev.client.modules.core.render;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.render.beautifully.AnimatedValue;
import dev.client.modules.core.render.beautifully.Easing;
import net.minecraft.util.math.MathHelper;

/**
 * Beautifully — набор мелких визуальных доработок ванильного интерфейса:
 * плавный переход в третье лицо, анимации чата, таба и инвентаря,
 * сохранение истории чата и свои колонки в табе.
 */
public class Beautifully extends Module implements ClientApi {
    public static Beautifully INSTANCE;

    public static final String SMOOTH_F5 = "Плавный F5";
    public static final String CHAT_ANIMATION = "Анимация чата";
    public static final String TAB_ANIMATION = "Анимация таба";
    public static final String INVENTORY_ANIMATION = "Анимация инвентаря";
    public static final String CHAT_HISTORY = "История чата";
    public static final String TAB_COLUMNS = "Свои колонки таба";

    /** Переход между первым и третьим лицом. */
    private final AnimatedValue thirdPersonAnimation = new AnimatedValue(300L, Easing.SINE_IN_OUT);
    /** Переход во вид "сзади наперёд" (второе нажатие F5). */
    private final AnimatedValue inverseViewAnimation = new AnimatedValue(300L, Easing.SINE_IN_OUT);
    /** Появление списка игроков по Tab. */
    private final AnimatedValue tabListAnimation = new AnimatedValue(200L, Easing.CUBIC_OUT);

    public final SelectElements features = new SelectElements("Функции", () -> true)
            .set(SMOOTH_F5, CHAT_ANIMATION, TAB_ANIMATION, INVENTORY_ANIMATION, CHAT_HISTORY, TAB_COLUMNS)
            .defaultValue(SMOOTH_F5, CHAT_ANIMATION, TAB_ANIMATION, INVENTORY_ANIMATION)
            .register(this);

    /** Объявлен последним: по нему проверяем, что поля модуля уже созданы. */
    public final Slider tabColumns = new Slider("Колонок в табе", () -> features.isSelected(TAB_COLUMNS))
            .set(1f, 5f, 1f)
            .defaultValue(2f)
            .register(this);

    public Beautifully() {
        super("Beautifully", ModuleCategory.Render, "Анимации интерфейса и плавная камера");
    }

    public AnimatedValue getThirdPersonAnimation() {
        return this.thirdPersonAnimation;
    }

    public AnimatedValue getInverseViewAnimation() {
        return this.inverseViewAnimation;
    }

    public AnimatedValue getTabListAnimation() {
        return this.tabListAnimation;
    }

    /**
     * Модуль с уже проинициализированными полями (независимо от того, включён он или нет).
     * INSTANCE выставляется конструктором Module, то есть до инициализации полей наследника,
     * поэтому проверяем последнее объявленное поле.
     */
    public static Beautifully ready() {
        Beautifully module = INSTANCE;
        return module != null && module.tabColumns != null ? module : null;
    }

    private static Beautifully active() {
        Beautifully module = ready();
        return module != null && module.isEnabled() ? module : null;
    }

    private static boolean selected(String feature) {
        Beautifully module = active();
        return module != null && module.features.isSelected(feature);
    }

    public static boolean smoothF5() {
        return selected(SMOOTH_F5);
    }

    public static boolean chatAnimation() {
        if (!selected(CHAT_ANIMATION)) return false;
        // Модуль Chat рисует свой чат вместо ванильного — анимировать там нечего.
        Chat chat = Chat.INSTANCE;
        return chat == null || !chat.isEnabled();
    }

    public static boolean tabAnimation() {
        return selected(TAB_ANIMATION);
    }

    public static boolean inventoryAnimation() {
        return selected(INVENTORY_ANIMATION);
    }

    public static boolean keepChatHistory() {
        return selected(CHAT_HISTORY);
    }

    /** Сколько колонок в табе просит игрок; 0 — оставить ванильное поведение. */
    public static int tabColumns() {
        Beautifully module = active();
        if (module == null || !module.features.isSelected(TAB_COLUMNS)) return 0;
        return Math.round(module.tabColumns.getValue());
    }

    /**
     * Ванильный предел строк в колонке таба (20) с учётом настройки колонок:
     * если игрок просит N колонок, поднимаем предел так, чтобы список в них уложился.
     */
    public static int tabRowLimit(int entryCount, int vanillaLimit) {
        int columns = tabColumns();
        if (columns <= 0 || entryCount <= 0) return vanillaLimit;
        return Math.max(1, MathHelper.ceil((float) entryCount / (float) columns));
    }

    /** Идёт ли сейчас переход камеры — в это время модель игрока плавно проявляется. */
    public static boolean playerFading() {
        Beautifully module = active();
        return module != null
                && module.features.isSelected(SMOOTH_F5)
                && !module.thirdPersonAnimation.isFinished();
    }

    /** Текущая непрозрачность модели игрока во время перехода. */
    public static float playerFadeAlpha() {
        Beautifully module = active();
        return module == null ? 1.0f : module.thirdPersonAnimation.get();
    }

    /** Умножает альфа-канал цвета на непрозрачность перехода. */
    public static int applyFade(int argb) {
        float opacity = MathHelper.clamp(playerFadeAlpha(), 0.0f, 1.0f);
        int alpha = MathHelper.clamp(Math.round(((argb >>> 24) & 0xFF) * opacity), 0, 255);
        return argb & 0x00FFFFFF | alpha << 24;
    }
}
