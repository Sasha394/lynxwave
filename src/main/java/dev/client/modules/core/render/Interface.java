package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.cmdHelper.managers.dragHandler.DraggableManager;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.other.PushUtils;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.animations.client.Animation;
import dev.client.api.nullcry.render.core.animations.client.Direction;
import dev.client.api.nullcry.render.core.animations.client.implement.DecelerateAnimation;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.uiClient.draggables.core.*;
import dev.client.api.nullcry.uiClient.notification.NotificationDragging;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import lombok.Getter;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Interface extends Module {
    public static Interface INSTANCE;
    public static final String MODE_MINIMALISM = "Минимализм";
    public static final String MODE_LIQUID_GLASS = "Жидкое стекло";
    public static final String MODE_EXCELLENT = "Excellent";
    public static final String EXCELLENT_RAINBOW = "Rainbow";
    public static final String EXCELLENT_ASTOLFO = "Astolfo";
    public static final String EXCELLENT_CUSTOM = "Свой";

    public SelectElements elements = new SelectElements("Показывать", () -> true)
            .set("Watermark", "PlayerInfo", "Keybinds", "Potions", "StaffList", "Cooldowns", "TargetHud", "MusicPlayer", "Notifications", "ScoreBoard", "HotBar", "InventoryHud", "ArmorHud", "Overlay", "PartyList")
            .defaultValue("Watermark", "PlayerInfo", "Keybinds", "StaffList", "Cooldowns", "Potions", "TargetHud", "Notifications", "ScoreBoard", "HotBar", "Overlay", "PartyList")
            .register(this);

    public ModeElement clientRenderMode = new ModeElement("Режим клиента", () -> true)
            .set(MODE_MINIMALISM, MODE_LIQUID_GLASS, MODE_EXCELLENT)
            .defaultValue(MODE_MINIMALISM)
            .register(this);

    public ModeElement minimalismTheme = new ModeElement("Тема минимализма", this::isMinimalismMode)
            .set("Тёмный", "Белый", "Кастом")
            .defaultValue("Тёмный")
            .register(this);

    public ModeElement excellentTheme = new ModeElement("Цвет Excellent", this::isExcellentMode)
            .set(EXCELLENT_RAINBOW, EXCELLENT_ASTOLFO, EXCELLENT_CUSTOM)
            .defaultValue(EXCELLENT_RAINBOW)
            .register(this);

    public ColorPicker excellentCustomColor1 = new ColorPicker("Цвет Excellent 1", this::isExcellentCustom)
            .set(new java.awt.Color(255, 70, 180, 255).getRGB())
            .defaultValue(new java.awt.Color(255, 70, 180, 255).getRGB())
            .register(this);

    public ColorPicker excellentCustomColor2 = new ColorPicker("Цвет Excellent 2", this::isExcellentCustom)
            .set(new java.awt.Color(70, 220, 255, 255).getRGB())
            .defaultValue(new java.awt.Color(70, 220, 255, 255).getRGB())
            .register(this);

    public ColorPicker minimalismCustomBg = new ColorPicker("Фон (кастом)", this::isMinimalismCustom)
            .set(new java.awt.Color(20, 21, 26, 255).getRGB())
            .defaultValue(new java.awt.Color(20, 21, 26, 255).getRGB())
            .register(this);

    public ColorPicker minimalismCustomText = new ColorPicker("Текст (кастом)", this::isMinimalismCustom)
            .set(-1)
            .defaultValue(-1)
            .register(this);

    public Slider blurStrength = new Slider("Сила размытия", () -> !isExcellentMode()).set(5, 14, 1).defaultValue(3).register(this);
    public Slider glassOpacity = new Slider("Прозрачность стекла", this::isLiquidGlassMode).set(0, 100, 1).defaultValue(100).register(this);
    public Slider glassStrength = new Slider("Сила стекла", this::isLiquidGlassMode).set(0, 100, 1).defaultValue(50).register(this);
    public Slider glassDistortion = new Slider("Искажение стекла", this::isLiquidGlassMode).set(-0.2f, 0.2f, 0.01f).defaultValue(0.08f).register(this);
    public Slider scale = new Slider("Масштаб", () -> true).set(50, 100, 5).applyDefault(100).register(this);
    public ModeElement theme = new ModeElement("Тема", () -> !isExcellentMode());

    public ColorPicker customThemeColor = new ColorPicker("Цвет", () -> !isExcellentMode() && theme.isSelected("Своя"))
            .set(-1)
            .defaultValue(-1);

    private final Animation hudAnimation = new DecelerateAnimation().setMs(400).setValue(1);
    private boolean hudAnimating = false;
    private long mainColorCacheMillis = Long.MIN_VALUE;
    private int cachedFirstColor = -1;
    private int cachedSecondColor = -1;
    private final int[] cachedMainColors = new int[32];
    private final int[] cachedMainColorIndexes = new int[32];
    private final boolean[] cachedMainColorsValid = new boolean[32];

    final String[] Spec = {"Spec", "Спек", "spec", "спек", "SPEC", "СПЕК", "Спек"};

    // уебище
    final Watermark watermark;
    final PlayerInfo playerInfo;
    final ArmorHud armorHud;
    final InventoryHud inventoryHud;
    final Keybinds keybinds;
    final Potions potions;
    @Getter final StaffList staffList;
    final Cooldowns cooldowns;
    final TargetHud targetHud;
    final Scoreboard scoreboard;
    final PartyList partyList;
    final NotificationDragging notificationDragging;
    final MusicPlayer musicPlayer;

    public Interface() {
        super("Interface", ModuleCategory.Render, "Интерфейс клиента");
        INSTANCE = this;
        watermark = new Watermark();
        playerInfo = new PlayerInfo();
        armorHud = new ArmorHud();
        inventoryHud = new InventoryHud();
        keybinds = new Keybinds();
        partyList = new PartyList();
        potions = new Potions();
        staffList = new StaffList();
        cooldowns = new Cooldowns();
        targetHud = new TargetHud();
        scoreboard = new Scoreboard();
        notificationDragging = NotificationDragging.INSTANCE;
        musicPlayer = new MusicPlayer();

        String[] themes = ThemePalette.displayNames();
        String[] allThemes = Arrays.copyOf(themes, themes.length + 1);
        allThemes[themes.length] = "Своя";
        theme.set(allThemes).applyDefault(ThemePalette.BLOODY_NIGHT.displayName()).register(this);
        customThemeColor.register(this);
        for (ThemePalette palette : ThemePalette.values()) {
            theme.withColor(palette.displayName(), palette.previewColor());
        }
        theme.withColor("Своя", customThemeColor.getColorRGBA());
    }

    public ThemePalette getSelectedTheme() {
        return ThemePalette.byDisplayName(theme.getValue()).orElse(ThemePalette.BLOODY_NIGHT);
    }

    public int getFirstColor() {
        if (isExcellentMode()) {
            return getExcellentColor(0);
        }
        if (theme.isSelected("Своя")) {
            int color = customThemeColor.getColorRGBA();
            theme.withColor("Своя", color);
            return color;
        }
        return getSelectedTheme().mainColor();
    }

    public int getSecondColor() {
        if (isExcellentMode()) {
            return getExcellentColor(180);
        }
        if (theme.isSelected("Своя")) {
            return customThemeColor.getColorRGBA();
        }
        return getSelectedTheme().mainColor();
    }

    public int getMainColor() {
        return getMainColor(0);
    }

    public int getMainColor(int index) {
        if (isExcellentMode()) {
            return getExcellentColor(index * 15);
        }

        long now = System.currentTimeMillis();
        if (now != mainColorCacheMillis) {
            mainColorCacheMillis = now;
            cachedFirstColor = getFirstColor();
            cachedSecondColor = getSecondColor();
            Arrays.fill(cachedMainColorsValid, false);
        }

        int slot = Math.floorMod(index, cachedMainColors.length);
        if (!cachedMainColorsValid[slot] || cachedMainColorIndexes[slot] != index) {
            cachedMainColors[slot] = animatedGradient(cachedFirstColor, cachedSecondColor, index);
            cachedMainColorIndexes[slot] = index;
            cachedMainColorsValid[slot] = true;
        }
        return cachedMainColors[slot];
    }

    private int animatedGradient(int color1, int color2, int index) {
        float time = (System.currentTimeMillis() % 3000L) / 3000f;
        float offset = index * 0.05f;
        float t = (float) (Math.sin((time + offset) * Math.PI * 2) * 0.5 + 0.5);
        return ColorUtils.interpolate(color1, color2, t);
    }

    public int getExcellentColor(int index) {
        int angle = Math.floorMod((int) (System.currentTimeMillis() / 10L) + index, 360);
        float hue = angle / 360.0f;

        if (isExcellentCustom()) {
            int customAngle = angle >= 180 ? 360 - angle : angle;
            float progress = (customAngle * 2) / 360.0f;
            return ColorUtils.interpolate(
                    excellentCustomColor1.getColorRGBA(),
                    excellentCustomColor2.getColorRGBA(),
                    progress
            );
        }

        if (excellentTheme != null && excellentTheme.isSelected(EXCELLENT_ASTOLFO)) {
            // The original Excellent "sky rainbow": pastel pink/blue loop.
            if (hue < 0.5f) {
                hue = -hue;
            }
            return java.awt.Color.HSBtoRGB(hue, 0.5f, 1.0f);
        }

        // The original Excellent rainbow uses full saturation and brightness.
        return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
    }

    public boolean isLiquidGlassMode() {
        return clientRenderMode != null && clientRenderMode.isSelected(MODE_LIQUID_GLASS);
    }

    public boolean isExcellentMode() {
        return clientRenderMode != null && clientRenderMode.isSelected(MODE_EXCELLENT);
    }

    public boolean isExcellentCustom() {
        return isExcellentMode()
                && excellentTheme != null
                && excellentTheme.isSelected(EXCELLENT_CUSTOM);
    }

    public boolean isMinimalismMode() {
        return clientRenderMode == null || clientRenderMode.isSelected(MODE_MINIMALISM);
    }

    public boolean isMinimalismDark() {
        return isMinimalismMode() && (minimalismTheme == null || minimalismTheme.isSelected("Тёмный"));
    }

    public boolean isMinimalismLight() {
        return isMinimalismMode() && minimalismTheme != null && minimalismTheme.isSelected("Белый");
    }

    public boolean isMinimalismCustom() {
        return isMinimalismMode() && minimalismTheme != null && minimalismTheme.isSelected("Кастом");
    }

    public int getMinimalismCustomBg() {
        return minimalismCustomBg != null ? minimalismCustomBg.getColorRGBA() : new java.awt.Color(20, 21, 26, 255).getRGB();
    }

    public int getMinimalismCustomText() {
        return minimalismCustomText != null ? minimalismCustomText.getColorRGBA() : -1;
    }

    public float getGlassOpacityFactor() {
        if (glassOpacity == null || glassOpacity.getValue() == null) {
            return 1.0f;
        }
        return clamp(glassOpacity.getValue() / 100.0f, 0.0f, 1.0f);
    }

    public float getGlassStrengthFactor() {
        if (glassStrength == null || glassStrength.getValue() == null) {
            return 0.5f;
        }
        return clamp(glassStrength.getValue() / 100.0f, 0.0f, 1.0f);
    }

    public float getGlassDistortionValue() {
        if (glassDistortion == null || glassDistortion.getValue() == null) {
            return 0.08f;
        }
        return clamp(glassDistortion.getValue(), -0.2f, 0.2f);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        hudAnimation.reset();
        hudAnimation.setDirection(Direction.FORWARDS);
        hudAnimating = true;
    }

    @Override
    public void onDisabled() {
        hudAnimation.reset();
        hudAnimation.setDirection(Direction.BACKWARDS);
        hudAnimating = true;
    }

    @Subscribe
    public void onPlayerAttack(PlayerAttackEvent event) {
        if (elements.isSelected("TargetHud")) targetHud.onPlayerAttack(event);
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (elements.isSelected("StaffList")) staffList.onUpdate(event);
        if (elements.isSelected("MusicPlayer")) musicPlayer.onUpdate(event);
    }

    @Subscribe
    public void onDraw2D(RenderEvent.Draw2D event) {
        if (hudAnimating && hudAnimation.getDirection() == Direction.BACKWARDS && hudAnimation.isDone()) {
            hudAnimating = false;
            super.onDisabled();
            return;
        }

        float animProgress = (float) hudAnimation.getOutput().doubleValue();

        if (mc.getDebugHud().shouldShowDebugHud()) {
            if (elements.isSelected("TargetHud")) targetHud.renderScaled(event);
            return;
        }

        boolean hideScoreboard = NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("ScoreBoard");

        var matrices = event.getContext().getMatrices();
        float scaleValue = 0.8f + 0.2f * animProgress;
        float cx = mc.getWindow().getScaledWidth() / 2f;
        float cy = mc.getWindow().getScaledHeight() / 2f;

        matrices.pushMatrix();
        matrices.translate(cx, cy);
        matrices.scale(scaleValue, scaleValue);
        matrices.translate(-cx, -cy);
        RenderSystem.setShaderColor(1f, 1f, 1f, animProgress);

        if (elements.isSelected("Watermark")) watermark.renderScaled(event);
        if (elements.isSelected("PlayerInfo")) playerInfo.renderScaled(event);
        if (elements.isSelected("ArmorHud")) armorHud.renderScaled(event);
        if (elements.isSelected("InventoryHud")) inventoryHud.renderScaled(event);
        if (elements.isSelected("Keybinds")) keybinds.renderScaled(event);
        if (elements.isSelected("PartyList")) partyList.onRender(event);
        if (elements.isSelected("Potions")) potions.renderScaled(event);
        if (elements.isSelected("StaffList")) staffList.renderScaled(event);
        if (elements.isSelected("Cooldowns")) cooldowns.renderScaled(event);
        if (elements.isSelected("TargetHud")) targetHud.renderScaled(event);
        if (elements.isSelected("ScoreBoard") && !hideScoreboard) {scoreboard.renderScaled(event);}
        if (elements.isSelected("MusicPlayer")) musicPlayer.renderScaled(event);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        matrices.popMatrix();
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (!(event.getPacket() instanceof ChatMessageS2CPacket chatPacket)) return;
        var unsigned = chatPacket.unsignedContent();
        if (unsigned == null) return;

        String originalMessage = unsigned.getString();
        if (originalMessage == null || originalMessage.isEmpty()) return;

        String formattedMessage = Formatting.strip(originalMessage);
        String lowerCaseMessage = formattedMessage.toLowerCase(Locale.ROOT);

        String localPlayer = mc.player != null ? mc.player.getName().getString() : "";
        if (!localPlayer.isEmpty() && formattedMessage.contains(localPlayer)) {
            return;
        }

        if (NotificationDragging.INSTANCE.getElementsNotifications().isSelected("Просят спек")) {
            for (String spectText : Spec) {
                if (lowerCaseMessage.contains(spectText.toLowerCase(Locale.ROOT))) {
                    String playerName = extractPlayerName(formattedMessage);
                    handlerClient().getNotificationManager().add(
                            "Игрок " + playerName + " просит проследить за ним!",
                            2, NotificationType.WARNING, RenderType.WORLD
                    );
                    break;
                }
            }
        }

        if (NotificationDragging.INSTANCE.getElementsNotifications().isSelected("Забанен игрок") && lowerCaseMessage.contains("забанен")) {
            String playerName = bannedPlayerName(formattedMessage);
            String reason = bannedReasonPlayer(unsigned);

            if (!playerName.equalsIgnoreCase(localPlayer) && reason != null) {
                String message = "Игрок " + playerName + " был забанен. " + reason;
                handlerClient().getNotificationManager().add(message, 2, NotificationType.WARNING, RenderType.WORLD);
                if (NotificationDragging.INSTANCE.getNotificationToggle().getEnabled()) {
                    PushUtils.sendPush("Предупреждение", message);
                }
            }
        }
    }


    private String extractPlayerName(String message) {
        Pattern pattern = Pattern.compile("\\b[a-zA-Z0-9_]{3,16}\\b");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group();
        }
        return "Неизвестный";
    }

    private String bannedPlayerName(String message) {
        Pattern pattern = Pattern.compile("\\b[a-zA-Z0-9_]{3,16}(?=\\s+забанен)");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group();
        }
        return "Неизвестный";
    }

    private String bannedReasonPlayer(Text component) {
        HoverEvent hoverEvent = component.getStyle().getHoverEvent();
        if (hoverEvent instanceof HoverEvent.ShowText showText) {
            Text hoverTextComponent = showText.value();
            if (hoverTextComponent != null) {
                String hoverText = hoverTextComponent.getString();

                if (hoverText.contains("[FunAC]")) {
                    return null;
                }

                int reasonIndex = hoverText.indexOf("Причина:");
                if (reasonIndex != -1) {
                    String afterReason = hoverText.substring(reasonIndex + 8).trim();
                    int endIndex = afterReason.indexOf("\n");
                    if (endIndex != -1) {
                        return afterReason.substring(0, endIndex).trim();
                    }
                    return afterReason;
                }
            }
        }
        return "Причина отсутствует";
    }

    public enum ThemePalette {
        BLOODY_NIGHT("Дефолт", new java.awt.Color(138, 146, 236, 255)),
        FROSTBYTE("Я уже красный!", new java.awt.Color(255, 0, 0, 255)),
        TOXIC_FOG("Персик", new java.awt.Color(200, 132, 90, 255));

        private final String displayName;
        private final int mainColor;

        ThemePalette(String displayName, java.awt.Color color) {
            this.displayName = displayName;
            this.mainColor = color.getRGB();
        }

        public String displayName() {
            return displayName;
        }

        public int mainColor() {
            return mainColor;
        }

        public int secondColor() {
            return mainColor;
        }

        public int previewColor() {
            return mainColor;
        }

        public static String[] displayNames() {
            String[] names = new String[values().length];
            for (int i = 0; i < values().length; i++) {
                names[i] = values()[i].displayName;
            }
            return names;
        }

        public static java.util.Optional<ThemePalette> byDisplayName(String name) {
            if (name == null) {
                return java.util.Optional.empty();
            }
            for (ThemePalette palette : values()) {
                if (palette.displayName.equalsIgnoreCase(name)) {
                    return java.util.Optional.of(palette);
                }
            }
            return java.util.Optional.empty();
        }
    }
}
