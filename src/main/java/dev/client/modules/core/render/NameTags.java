package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.helper.client.irc.ClientLabelRegistry;
import dev.client.api.nullcry.helper.client.irc.PartyManager;
import dev.client.api.nullcry.helper.client.projection.ProjectionUtil;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.math.MathVector;
import dev.client.api.nullcry.helper.player.ItemUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.modules.core.misc.IRC;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

public class NameTags extends Module {
    private static final float TAG_TEXT_SIZE = 6.5f;
    private static final float TAG_HEIGHT = 13.0f;
    private static final float TAG_PAD_X = 3.0f;
    private static final float TAG_ICON_SIZE = 9.0f;
    private static final float TAG_ICON_GAP = 4.0f;
    private static final float TAG_ICON_CELL = 15.0f;
    private static final float TAG_CELL_GAP = 2.0f;
    private static final float TAG_TEXT_PAD_X = 4.0f;
    private static final float TAG_RADIUS = 3.0f;
    private static final int TAG_HP = ColorUtils.rgb(215, 75, 86);
    private static final float HEAD_U = 8f / 64f;
    private static final float HEAD_V = 8f / 64f;
    private static final float HEAD_OVERLAY_U = 40f / 64f;
    private static final float HEAD_OVERLAY_V = 8f / 64f;
    private static final float HEAD_TEX_SIZE = 8f / 64f;
    private static final float ACTION_POPUP_WIDTH = 132f;
    private static final float ACTION_POPUP_HEIGHT = 58f;
    private static final float ACTION_POPUP_RADIUS = 4f;
    private static final float ACTION_BUTTON_HEIGHT = 16f;
    private static final double DEFAULT_SCAN_RADIUS = 64.0;
    private static final double OPTIMIZED_SCAN_RADIUS = 48.0;
    private static final int OPTIMIZED_MAX_TAGS = 24;
    private static final double OPTIMIZED_ARMOR_DISTANCE_SQ = 20.0 * 20.0;
    private static final double OPTIMIZED_EXTRA_DISTANCE_SQ = 16.0 * 16.0;
    private static final float SCREEN_CULL_MARGIN = 48.0f;
    private static final String TAG_HINT_TEXT = "ПКМ для взаимодействия";

    public NameTags() {
        super("Name Tags", ModuleCategory.Render, "Красиво отображает тег цели, улучшая её видимость");
    }

    SelectElements filter = new SelectElements("Фильтр на", () -> true).set("Игроков", "Мобов", "Животных", "Жителей", "Друзей", "Невидимых", "Голых", "Себя", "Предметы").defaultValue("Игроков", "Голых", "Невидимых", "Друзей", "Себя").register(this);
    SelectElements elements = new SelectElements("Отображать", () -> true).set("Здоровье", "Пинг", "Броню", "Эффекты", "Зачарования").defaultValue("Здоровье", "Пинг", "Броню").register(this);

    CheckBox showDonatItem = new CheckBox("Только донатные предметы", () -> filter.isSelected("Предметы")).defaultValue(false).register(this);
    CheckBox optimization = new CheckBox("Оптимизация", () -> true).defaultValue(true).register(this);
    CheckBox optimizationPrefix = new CheckBox("Оптимизация префиксов", () -> optimization.getEnabled()).defaultValue(true).register(this);
    CheckBox showHandTags = new CheckBox("Отображать предметы в руках", () -> true).defaultValue(false).register(this);
    Slider radiusBlur = new Slider("Радиус блюра", () -> !optimization.getEnabled()).set(1, 10, 1).defaultValue(5).register(this);

    CheckBox clickableTags = new CheckBox("Кликабельные nametags", () -> true).defaultValue(true).register(this);
    private final List<TagHitbox> tagHitboxes = new ArrayList<>();
    private final Map<Integer, Float> tagAlpha = new HashMap<>();
    private final Map<Integer, Float> tagHoverAlpha = new HashMap<>();
    private final Set<Integer> currentEntityIds = new HashSet<>();
    private final List<Badge> renderBadges = new ArrayList<>(4);
    private String selectedTagName = null;
    private String fadingTagName = null;
    private float selectedPopupX = 0f;
    private float selectedPopupY = 0f;
    private float popupAlpha = 0f;
    private boolean mouseWasDown = false;

    @Subscribe
    public void onRenderLabels(RenderEvent.RenderLabelsEvent<? extends Entity, ? extends EntityRenderState> renderLabelsEvent) {
        EntityRenderState state = renderLabelsEvent.getState();
        if (state instanceof net.minecraft.client.render.entity.state.ArmorStandEntityRenderState) return;
        renderLabelsEvent.cancelled();
    }

    @Subscribe
    public void drawEvent(RenderEvent.Draw2D draw2D) {
        if (mc.player == null || mc.world == null) return;

        DrawContext context = draw2D.getContext();
        double radius = optimization.getEnabled() ? OPTIMIZED_SCAN_RADIUS : DEFAULT_SCAN_RADIUS;
        tagHitboxes.clear();
        currentEntityIds.clear();

        Box box = new Box(
                mc.player.getX() - radius, mc.player.getY() - radius, mc.player.getZ() - radius,
                mc.player.getX() + radius, mc.player.getY() + radius, mc.player.getZ() + radius
        );

        List<LivingEntity> entities = mc.world.getEntitiesByClass(
                LivingEntity.class,
                box,
                entity -> true
        );

        if (optimization.getEnabled()) {
            entities.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo));
        }

        int renderedTags = 0;
        for (LivingEntity entity : entities) {
            currentEntityIds.add(entity.getId());
            boolean shouldRender = shouldRenderEntity(entity);
            float alpha = updateTagAlpha(entity, shouldRender ? 1.0f : 0.0f);
            if (alpha <= 0.01f) {
                continue;
            }
            if (optimization.getEnabled() && renderedTags >= OPTIMIZED_MAX_TAGS) {
                continue;
            }
            if (renderTag(context, entity, alpha)) {
                renderedTags++;
            }
        }
        cleanupTagAlpha(currentEntityIds);

        if (filter.isSelected("Предметы")) {
            List<ItemEntity> items = mc.world.getEntitiesByType(
                    TypeFilter.instanceOf(ItemEntity.class),
                    box,
                    it -> true
            );

            for (ItemEntity item : items) {
                if (!shouldRenderEntity(item)) continue;
                if (showDonatItem.getEnabled() && !ItemUtil.isRare(item.getStack())) continue;
                if (!item.isAlive()) continue;
                renderItemTag(context, item);
            }
        }

        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        if (!chatOpen || !clickableTags.getEnabled()) {
            selectedTagName = null;
            mouseWasDown = false;
        }

        if (clickableTags.getEnabled()) {
            renderActionPopup(context);
        }

        if (clickableTags.getEnabled() && chatOpen) {
            handleTagMouseClick();
        }
    }

    private String buildExtras(LivingEntity e) {
        StringBuilder sb = new StringBuilder();

        if (elements.isSelected("Здоровье")) {
            float health = PlayerHelper.getHealth(e);
            int healthInt = Math.max(0, Math.round(health));
            sb.append(" [").append(healthInt).append("]");
        }

        if (elements.isSelected("Пинг")) {
            String raw = e instanceof PlayerEntity p ? PlayerHelper.getPlayerPing(p) : "-";
            boolean numeric = raw != null && raw.chars().allMatch(Character::isDigit);

            if (!sb.isEmpty()) sb.append(" ");
            if (numeric) {
                sb.append("[").append(raw).append(" ms]");
            } else {
                sb.append("[").append(raw == null ? "N/A" : raw).append("]");
            }
        }

        return sb.toString();
    }

    private void renderExtras(DrawContext context, double startX, double y, LivingEntity e) {
        double x = startX;

        if (elements.isSelected("Здоровье")) {
            float health = PlayerHelper.getHealth(e);
            int hp = Math.max(0, Math.round(health));
            int maxHp = Math.max(1, Math.round(e.getMaxHealth()));
            int hpColor = healthColor(hp, maxHp);

            String open = " [";
            Builder.TEXT_BUILDER.size(6).color(-1).font(Builder.INTER.get()).text(open).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
            x += Builder.INTER.get().getWidth(open, 6, 0.05f, 0);

            String hpStr = String.valueOf(hp);
            Builder.TEXT_BUILDER.size(6).color(hpColor).font(Builder.INTER.get()).text(hpStr).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
            x += Builder.INTER.get().getWidth(hpStr, 6, 0.05f, 0);

            String close = "]";
            Builder.TEXT_BUILDER.size(6).color(-1).font(Builder.INTER.get()).text(close).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
            x += Builder.INTER.get().getWidth(close, 6, 0.05f, 0);
        }

        if (elements.isSelected("Пинг")) {
            String raw = e instanceof PlayerEntity p ? PlayerHelper.getPlayerPing(p) : "-";
            int pingVal;
            try {
                pingVal = Integer.parseInt(raw);
            } catch (Exception ex) {
                pingVal = -1;
            }

            if (elements.isSelected("Здоровье")) {
                String sp = " ";
                Builder.TEXT_BUILDER.size(6).color(-1).font(Builder.INTER.get()).text(sp).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
                x += Builder.INTER.get().getWidth(sp, 6, 0.05f, 0);
            }

            String open = "[";
            Builder.TEXT_BUILDER.size(6).color(-1).font(Builder.INTER.get()).text(open).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
            x += Builder.INTER.get().getWidth(open, 6, 0.05f, 0);

            if (pingVal >= 0) {
                int pingColor = pingColor(pingVal);
                String pingNum = String.valueOf(pingVal);
                Builder.TEXT_BUILDER.size(6).color(pingColor).font(Builder.INTER.get()).text(pingNum).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
                x += Builder.INTER.get().getWidth(pingNum, 6, 0.05f, 0);

                String ms = " ms";
                Builder.TEXT_BUILDER.size(6).color(-1).font(Builder.INTER.get()).text(ms).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
                x += Builder.INTER.get().getWidth(ms, 6, 0.05f, 0);
            } else {
                String pingTxt = raw == null ? "N/A" : raw;
                Builder.TEXT_BUILDER.size(6).color(-1).font(Builder.INTER.get()).text(pingTxt).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
                x += Builder.INTER.get().getWidth(pingTxt, 6, 0.05f, 0);
            }

            String close = "]";
            Builder.TEXT_BUILDER.size(6).color(-1).font(Builder.INTER.get()).text(close).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
            x += Builder.INTER.get().getWidth(close, 6, 0.05f, 0);
        }
    }

    private boolean renderTag(DrawContext context, LivingEntity entity, float alpha) {
        String playerName = entity.getName().getString();
        var team = entity.getScoreboardTeam();
        Text rawPrefix = (team != null && team.getPrefix() != null) ? team.getPrefix() : Text.empty();
        Text prefix = convertDisplayName(rawPrefix);

        boolean hasPrefix = !prefix.getString().isEmpty();
        boolean friend = handlerClient().getFriendManager().isFriend(playerName);
        ClientLabelRegistry registry = ClientLabelRegistry.getInstance();
        boolean ircEnabled = IRC.isActive();
        boolean partyMember = false;
        String clientLabel = null;
        if (entity instanceof AbstractClientPlayerEntity playerEntity) {
            String actualName = playerEntity.getGameProfile().name();
            if (ircEnabled) {
                clientLabel = registry.labelForPlayer(actualName);
                if ((clientLabel == null || clientLabel.isBlank()) && mc.player != null && mc.player.getUuid().equals(playerEntity.getUuid())) {
                    clientLabel = registry.getSelfLabel();
                }
                if ((clientLabel == null || clientLabel.isBlank())) {
                    clientLabel = registry.labelForPlayer(entity.getName().getString());
                }
            }
            partyMember = PartyManager.getInstance().isMemberGame(actualName);
        }

        List<Badge> badges = renderBadges;
        badges.clear();
        if (ircEnabled && clientLabel != null && !clientLabel.isBlank()) {
            net.minecraft.util.Identifier icon = registry.textureForLabel(clientLabel);
            if (icon != null) {
                // Иконка вместо текста [LynxWave]
                badges.add(new Badge("", Interface.INSTANCE.getMainColor(), icon, 8f));
            } else {
                badges.add(new Badge("[" + clientLabel + "] ", Interface.INSTANCE.getMainColor()));
            }
        }
        if (friend) {
            badges.add(new Badge("[F] ", ColorUtils.rgba(0, 255, 0, 255)));
        }
        if (partyMember) {
            badges.add(new Badge("[Party] ", ColorUtils.rgba(71, 163, 255, 255)));
        }

        final float textSize = TAG_TEXT_SIZE;
        final float height = TAG_HEIGHT;
        final float baselineOffset = (height - textSize) / 2f - 0.5f;
        String hpText = elements.isSelected("Р—РґРѕСЂРѕРІСЊРµ")
                ? "[" + Math.max(0, Math.round(PlayerHelper.getHealth(entity))) + "]"
                : "";
        hpText = "[" + Math.max(0, Math.round(PlayerHelper.getHealth(entity))) + "]";
        String pingText = "";
        if (elements.isSelected("РџРёРЅРі")) {
            String raw = entity instanceof PlayerEntity p ? PlayerHelper.getPlayerPing(p) : "-";
            pingText = raw != null && raw.chars().allMatch(Character::isDigit)
                    ? "[" + raw + " ms]"
                    : "[" + (raw == null ? "N/A" : raw) + "]";
        }
        pingText = "";

        float nameWidth = Builder.INTER.get().getWidth(playerName, textSize, 0.05f, 0);
        float hpWidth = hpText.isEmpty() ? 0f : Builder.INTER.get().getWidth(hpText, textSize, 0.05f, 0);
        float pingWidth = pingText.isEmpty() ? 0f : Builder.INTER.get().getWidth(pingText, textSize, 0.05f, 0);
        float prefixWidth = hasPrefix ? Builder.INTER.get().getWidth(prefix.getString(), textSize, 0.05f, 0) : 0f;
        float badgeCellsWidth = 0f;
        float textBadgesWidth = 0f;
        for (Badge badge : badges) {
            if (badge.hasIcon()) {
                // иконка + 3px отступ справа
                badgeCellsWidth += TAG_ICON_CELL + TAG_CELL_GAP;
            } else {
                textBadgesWidth += Builder.INTER.get().getWidth(badge.text(), textSize, 0.05f, 0);
            }
        }
        float spaceWidth = Builder.INTER.get().getWidth(" ", textSize, 0.05f, 0);
        float textBoxWidth = TAG_TEXT_PAD_X
                + textBadgesWidth
                + prefixWidth + (hasPrefix ? spaceWidth : 0f)
                + nameWidth
                + (hpText.isEmpty() ? 0f : spaceWidth + hpWidth)
                + (pingText.isEmpty() ? 0f : spaceWidth + pingWidth)
                + TAG_TEXT_PAD_X;

        float totalWidth = TAG_ICON_CELL + TAG_CELL_GAP + badgeCellsWidth + textBoxWidth;

        Vec3d tagPos = ProjectionUtil.projectCoordinates(MathVector.lerpPosition(entity).add(0, entity.getHeight() + 0.5, 0));
        if (tagPos.z <= 0 || tagPos.z >= 1) return false;
        if (optimization.getEnabled() && isOutsideScreen(context, tagPos)) return false;
        boolean noHelmet = entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
        float headlessYOffset = noHelmet ? 3f : 0f;

        double rectX = tagPos.x - (totalWidth / 2.0);
        double rectY = tagPos.y + 2 + headlessYOffset;
        boolean interactive = clickableTags.getEnabled() && mc.currentScreen instanceof ChatScreen && entity instanceof PlayerEntity && entity != mc.player;
        if (interactive) {
            tagHitboxes.add(new TagHitbox(playerName, (float) rectX, (float) rectY, totalWidth, height));
        }
        boolean hoveredTag = interactive && isMouseInside(rectX, rectY, totalWidth, height);
        float hoverAlpha = updateTagHoverAlpha(entity, hoveredTag ? 1.0f : 0.0f) * alpha;

        if (!optimization.getEnabled()) {
            Builder.BLUR_BUILDER
                    .blurRadius(Math.min(10f, Math.max(1f, radiusBlur.getValue())))
                    .radius(new QuadRadiusState(TAG_RADIUS))
                    .size(new SizeState(totalWidth, height))
                    .alpha(alpha)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), rectX, rectY);
        }

        renderHudPanel(context, (float) rectX, (float) rectY, TAG_ICON_CELL, height, TAG_RADIUS, alpha);

        double distanceSq = mc.player.squaredDistanceTo(entity);
        boolean renderArmorDetails = !optimization.getEnabled() || distanceSq <= OPTIMIZED_ARMOR_DISTANCE_SQ;
        boolean renderExtraDetails = !optimization.getEnabled() || distanceSq <= OPTIMIZED_EXTRA_DISTANCE_SQ;
        if (renderArmorDetails) {
            renderArmor(context, rectX, rectY + baselineOffset + 2, (int) totalWidth, entity);
        }
        if (renderExtraDetails) {
            renderHandTags(context, entity, friend);
            renderStatusEffects(context, rectX, rectY, totalWidth, entity);
        }

        double tx = rectX;
        double ty = rectY + baselineOffset;
        int textColor = alphaColor(tagTextColor(), alpha);
        int hpColor = alphaColor(tagHpColor(), alpha);
        renderEntityIcon(context, entity, (float) (tx + (TAG_ICON_CELL - TAG_ICON_SIZE) / 2f), (float) (rectY + (height - TAG_ICON_SIZE) / 2f), TAG_ICON_SIZE, alpha);
        tx += TAG_ICON_CELL + TAG_CELL_GAP;

        for (Badge badge : badges) {
            if (badge.hasIcon()) {
                float size = badge.iconSize();
                // вертикально по центру строки тега
                renderHudPanel(context, (float) tx, (float) rectY, TAG_ICON_CELL, height, TAG_RADIUS, alpha);

                float iconX = (float) (tx + (TAG_ICON_CELL - size) / 2f);
                float iconY = (float) (rectY + (height - size) / 2f);
                Builder.TEXTURE_BUILDER
                        .size(new SizeState(size, size))
                        .texture(0, 0, 1, 1, dev.client.api.nullcry.render.ClientTexture.of(badge.icon().getPath()))
                        .color(new QuadColorState(alphaColor(-1, alpha)))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), iconX, iconY);
                tx += TAG_ICON_CELL + TAG_CELL_GAP;
            }
        }

        renderHudPanel(context, (float) tx, (float) rectY, textBoxWidth, height, TAG_RADIUS, alpha);
        tx += TAG_TEXT_PAD_X;

        if (optimization.getEnabled()) {
            MutableText optimizedLine = Text.empty();
            for (Badge badge : badges) {
                if (!badge.hasIcon()) {
                    int badgeRgb = badge.color() & 0x00FFFFFF;
                    optimizedLine.append(Text.literal(badge.text()).styled(style -> style.withColor(badgeRgb)));
                }
            }
            if (hasPrefix) {
                optimizedLine.append(optimizationPrefix.getEnabled()
                        ? Text.literal(prefix.getString())
                        : prefix.copy());
                optimizedLine.append(Text.literal(" "));
            }
            optimizedLine.append(Text.literal(playerName));
            if (!hpText.isEmpty()) {
                int hpRgb = tagHpColor() & 0x00FFFFFF;
                optimizedLine.append(Text.literal(" " + hpText).styled(style -> style.withColor(hpRgb)));
            }
            if (!pingText.isEmpty()) {
                optimizedLine.append(Text.literal(" " + pingText));
            }

            Builder.TEXT_BUILDER
                    .size(textSize)
                    .color(textColor)
                    .font(Builder.INTER.get())
                    .text(optimizedLine)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);

            if (hoverAlpha > 0.01f) {
                renderInteractiveHover(context, (float) rectX, (float) rectY, totalWidth, height, hoverAlpha);
            }
            return true;
        }

        for (Badge badge : badges) {
            if (!badge.hasIcon()) {
                Builder.TEXT_BUILDER.size(textSize).color(alphaColor(badge.color(), alpha)).font(Builder.INTER.get()).text(badge.text()).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
                tx += Builder.INTER.get().getWidth(badge.text(), textSize, 0.05f, 0);
            }
        }

        if (hasPrefix) {
            if (optimizationPrefix.getEnabled()) {
                Builder.TEXT_BUILDER.size(textSize).color(textColor).font(Builder.INTER.get()).text(prefix.getString()).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
            } else {
                Builder.TEXT_BUILDER.size(textSize).color(textColor).font(Builder.INTER.get()).text(prefix).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
            }
            tx += prefixWidth;

            Builder.TEXT_BUILDER.size(textSize).color(textColor).font(Builder.INTER.get()).text(" ").build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
            tx += spaceWidth;
        }

        Builder.TEXT_BUILDER.size(textSize).color(textColor).font(Builder.INTER.get()).text(playerName).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
        tx += nameWidth;

        if (!hpText.isEmpty()) {
            Builder.TEXT_BUILDER.size(textSize).color(textColor).font(Builder.INTER.get()).text(" ").build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
            tx += spaceWidth;
            Builder.TEXT_BUILDER.size(textSize).color(hpColor).font(Builder.INTER.get()).text(hpText).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
            tx += hpWidth;
        }

        if (!pingText.isEmpty()) {
            Builder.TEXT_BUILDER.size(textSize).color(textColor).font(Builder.INTER.get()).text(" ").build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
            tx += spaceWidth;
            Builder.TEXT_BUILDER.size(textSize).color(textColor).font(Builder.INTER.get()).text(pingText).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
        }

        if (hoverAlpha > 0.01f) {
            renderInteractiveHover(context, (float) rectX, (float) rectY, totalWidth, height, hoverAlpha);
        }
        return true;
    }

    private boolean isOutsideScreen(DrawContext context, Vec3d projected) {
        return projected.x < -SCREEN_CULL_MARGIN
                || projected.y < -SCREEN_CULL_MARGIN
                || projected.x > context.getScaledWindowWidth() + SCREEN_CULL_MARGIN
                || projected.y > context.getScaledWindowHeight() + SCREEN_CULL_MARGIN;
    }

    private void renderActionPopup(DrawContext context) {
        if (selectedTagName != null && !selectedTagName.isBlank()) {
            fadingTagName = selectedTagName;
        }
        float alpha = updatePopupAlpha(selectedTagName != null ? 1.0f : 0.0f);
        if (fadingTagName == null || fadingTagName.isBlank() || alpha <= 0.01f) return;

        float x = Math.min(selectedPopupX, context.getScaledWindowWidth() - ACTION_POPUP_WIDTH - 4f);
        float y = Math.min(selectedPopupY, context.getScaledWindowHeight() - ACTION_POPUP_HEIGHT - 4f);
        x = Math.max(4f, x);
        y = Math.max(4f, y);
        selectedPopupX = x;
        selectedPopupY = y;

        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
        float addY = y + 18f;
        float copyY = y + 36f;
        boolean active = selectedTagName != null;
        boolean addHovered = active && isMouseInside(x + 5f, addY, ACTION_POPUP_WIDTH - 10f, ACTION_BUTTON_HEIGHT);
        boolean copyHovered = active && isMouseInside(x + 5f, copyY, ACTION_POPUP_WIDTH - 10f, ACTION_BUTTON_HEIGHT);
        boolean isFriend = handlerClient().getFriendManager().isFriend(fadingTagName);

        renderHudPanel(context, x, y, ACTION_POPUP_WIDTH, ACTION_POPUP_HEIGHT, ACTION_POPUP_RADIUS, alpha);

        Builder.BORDER_BUILDER
                .size(new SizeState(ACTION_POPUP_WIDTH, ACTION_POPUP_HEIGHT))
                .color(new QuadColorState(ColorUtils.setAlpha(accentColor(), (int) (85 * alpha))))
                .radius(new QuadRadiusState(ACTION_POPUP_RADIUS))
                .thickness(-0.25f)
                .build()
                .render(matrix, x, y);

        drawCenteredText(context, fadingTagName, x, y + 5f, ACTION_POPUP_WIDTH, 7.5f, alphaColor(tagTextColor(), alpha));
        int friendActionColor = alphaColor(isFriend ? ColorUtils.rgb(235, 78, 78) : ColorUtils.rgb(67, 190, 92), alpha);
        drawPopupButton(context, x + 5f, addY, ACTION_POPUP_WIDTH - 10f, ACTION_BUTTON_HEIGHT, isFriend ? "Удалить из друзей" : "Добавить в друзья", addHovered, friendActionColor, alpha);
        drawPopupButton(context, x + 5f, copyY, ACTION_POPUP_WIDTH - 10f, ACTION_BUTTON_HEIGHT, "Скопировать ник", copyHovered, alphaColor(tagTextColor(), alpha), alpha);
    }

    private void renderInteractiveHover(DrawContext context, float x, float y, float width, float height, float alpha) {
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
        float pulse = 0.72f + 0.28f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        int accent = ColorUtils.setAlpha(accentColor(), Math.max(0, Math.min(255, Math.round(190 * alpha * pulse))));
        int white = HelperElements.themedWhite(Math.max(0, Math.min(255, Math.round(180 * alpha))));

        Builder.GLOW_BUILDER
                .size(new SizeState(width, height))
                .radius(new QuadRadiusState(TAG_RADIUS))
                .color(new QuadColorState(ColorUtils.setAlpha(accentColor(), Math.round(55 * alpha * pulse))))
                .softness(0.8f)
                .glowRadius(4f)
                .build()
                .render(matrix, x, y);

        Builder.BORDER_BUILDER
                .size(new SizeState(width, height))
                .color(new QuadColorState(accent))
                .radius(new QuadRadiusState(TAG_RADIUS))
                .thickness(-0.35f)
                .build()
                .render(matrix, x, y);

        float corner = Math.min(16f, Math.max(6f, width * 0.22f)) * alpha;
        float line = 0.8f;
        renderHoverLine(context, x - 1f, y - 1f, corner, line, white);
        renderHoverLine(context, x - 1f, y - 1f, line, Math.min(7f, height) * alpha, white);
        renderHoverLine(context, x + width - corner + 1f, y - 1f, corner, line, white);
        renderHoverLine(context, x + width + 1f, y - 1f, line, Math.min(7f, height) * alpha, white);
        renderHoverLine(context, x - 1f, y + height + 1f, corner, line, white);
        renderHoverLine(context, x - 1f, y + height - Math.min(7f, height) * alpha + 1f, line, Math.min(7f, height) * alpha, white);
        renderHoverLine(context, x + width - corner + 1f, y + height + 1f, corner, line, white);
        renderHoverLine(context, x + width + 1f, y + height - Math.min(7f, height) * alpha + 1f, line, Math.min(7f, height) * alpha, white);

        float hintTextSize = 6.5f;
        float hintTextWidth = Builder.INTER.get().getWidth(TAG_HINT_TEXT, hintTextSize, 0.05f, 0);
        float hintW = hintTextWidth + 11f;
        float hintH = 13f;
        float hintX = x + (width - hintW) / 2f;
        float hintY = Math.max(4f, y - hintH - 4f - 2f * alpha);

        renderHudPanel(context, hintX, hintY, hintW, hintH, 3f, alpha);
        Builder.BORDER_BUILDER
                .size(new SizeState(hintW, hintH))
                .color(new QuadColorState(ColorUtils.setAlpha(accentColor(), Math.round(70 * alpha))))
                .radius(new QuadRadiusState(3f))
                .thickness(-0.2f)
                .build()
                .render(matrix, hintX, hintY);
        drawCenteredText(context, TAG_HINT_TEXT, hintX, hintY + 3.2f, hintW, hintTextSize, alphaColor(tagTextColor(), alpha));
    }

    private void renderHoverLine(DrawContext context, float x, float y, float width, float height, int color) {
        if (width <= 0.01f || height <= 0.01f) return;
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, height))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(0.6f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
    }

    private void drawPopupButton(DrawContext context, float x, float y, float w, float h, String text, boolean hovered, boolean accentText) {
        drawPopupButton(context, x, y, w, h, text, hovered, accentText ? accentColor() : tagTextColor());
    }

    private void drawPopupButton(DrawContext context, float x, float y, float w, float h, String text, boolean hovered, int textColor) {
        drawPopupButton(context, x, y, w, h, text, hovered, textColor, 1.0f);
    }

    private void drawPopupButton(DrawContext context, float x, float y, float w, float h, String text, boolean hovered, int textColor, float alpha) {
        int bg = hovered
                ? ColorUtils.setAlpha(textColor, (int) (45 * alpha))
                : alphaColor(HelperElements.themedPanel(24, 26, 30, 145), alpha);
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(w, h))
                .color(new QuadColorState(bg))
                .radius(new QuadRadiusState(3f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);

        drawCenteredText(context, text, x, y + 4.2f, w, 7f, textColor);
    }

    private void drawCenteredText(DrawContext context, String text, float x, float y, float w, float size, int color) {
        float tw = Builder.INTER.get().getWidth(text, size, 0.05f, 0);
        Builder.TEXT_BUILDER
                .size(size)
                .font(Builder.INTER.get())
                .color(color)
                .text(text)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x + (w - tw) / 2f, y);
    }

    private void handleTagMouseClick() {
        long handle = mc.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean mouseDown = leftDown || rightDown;
        if (mouseDown && !mouseWasDown) {
            float mx = mouseX();
            float my = mouseY();

            if (selectedTagName != null && clickPopup(mx, my)) {
                mouseWasDown = true;
                return;
            }

            if (rightDown) {
                for (int i = tagHitboxes.size() - 1; i >= 0; i--) {
                    TagHitbox hitbox = tagHitboxes.get(i);
                    if (hitbox.contains(mx, my)) {
                        selectedTagName = hitbox.name();
                        selectedPopupX = hitbox.x() + hitbox.w() + 7f;
                        selectedPopupY = hitbox.y() - 3f;
                        mouseWasDown = true;
                        return;
                    }
                }
            }

            selectedTagName = null;
        }
        mouseWasDown = mouseDown;
    }

    private boolean clickPopup(float mx, float my) {
        float x = selectedPopupX;
        float y = selectedPopupY;
        if (!inBounds(mx, my, x, y, ACTION_POPUP_WIDTH, ACTION_POPUP_HEIGHT)) {
            return false;
        }

        float buttonX = x + 5f;
        float buttonW = ACTION_POPUP_WIDTH - 10f;
        if (inBounds(mx, my, buttonX, y + 18f, buttonW, ACTION_BUTTON_HEIGHT)) {
            if (handlerClient().getFriendManager().isFriend(selectedTagName)) {
                handlerClient().getFriendManager().delete(selectedTagName);
                printClient(selectedTagName + " удалён из друзей");
            } else {
                handlerClient().getFriendManager().add(selectedTagName);
                printClient(selectedTagName + " добавлен в друзья");
            }
            return true;
        }

        if (inBounds(mx, my, buttonX, y + 36f, buttonW, ACTION_BUTTON_HEIGHT)) {
            mc.keyboard.setClipboard(selectedTagName);
            printClient("Ник скопирован: " + selectedTagName);
            return true;
        }

        return true;
    }

    private boolean isMouseInside(double x, double y, double w, double h) {
        return inBounds(mouseX(), mouseY(), (float) x, (float) y, (float) w, (float) h);
    }

    private boolean inBounds(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private float mouseX() {
        return (float) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth());
    }

    private float mouseY() {
        return (float) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight());
    }

    private int accentColor() {
        return Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : ColorUtils.rgb(170, 92, 255);
    }

    private void renderHudPanel(DrawContext context, float x, float y, float width, float height, float radius) {
        renderHudPanel(context, x, y, width, height, radius, 1.0f);
    }

    private void renderHudPanel(DrawContext context, float x, float y, float width, float height, float radius, float alpha) {
        HelperElements.rectElements(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y, width, height, alpha);
    }

    private float updateTagAlpha(Entity entity, float target) {
        int id = entity.getId();
        float current = tagAlpha.getOrDefault(id, target <= 0f ? 0f : 0.0f);
        float next = MathUtil.fast(current, target, 12f);
        if (Math.abs(next - target) < 0.01f) {
            next = target;
        }
        tagAlpha.put(id, next);
        return next;
    }

    private float updateTagHoverAlpha(Entity entity, float target) {
        int id = entity.getId();
        float current = tagHoverAlpha.getOrDefault(id, 0.0f);
        float next = MathUtil.fast(current, target, 16f);
        if (Math.abs(next - target) < 0.01f) {
            next = target;
        }
        if (next <= 0.01f && target <= 0f) {
            tagHoverAlpha.remove(id);
            return 0f;
        }
        tagHoverAlpha.put(id, next);
        return next;
    }

    private float updatePopupAlpha(float target) {
        popupAlpha = MathUtil.fast(popupAlpha, target, 14f);
        if (Math.abs(popupAlpha - target) < 0.01f) {
            popupAlpha = target;
        }
        if (popupAlpha <= 0.01f && target <= 0f) {
            fadingTagName = null;
            popupAlpha = 0f;
        }
        return popupAlpha;
    }

    private void cleanupTagAlpha(Set<Integer> currentEntityIds) {
        Iterator<Map.Entry<Integer, Float>> iterator = tagAlpha.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Float> entry = iterator.next();
            if (!currentEntityIds.contains(entry.getKey())) {
                float next = MathUtil.fast(entry.getValue(), 0.0f, 12f);
                if (next <= 0.01f) {
                    iterator.remove();
                } else {
                    entry.setValue(next);
                }
            }
        }

        Iterator<Map.Entry<Integer, Float>> hoverIterator = tagHoverAlpha.entrySet().iterator();
        while (hoverIterator.hasNext()) {
            Map.Entry<Integer, Float> entry = hoverIterator.next();
            if (!currentEntityIds.contains(entry.getKey())) {
                float next = MathUtil.fast(entry.getValue(), 0.0f, 16f);
                if (next <= 0.01f) {
                    hoverIterator.remove();
                } else {
                    entry.setValue(next);
                }
            }
        }
    }

    private int alphaColor(int color, float alpha) {
        int originalAlpha = (color >>> 24) & 0xFF;
        if (originalAlpha == 0 && (color & 0xFF000000) == 0) {
            originalAlpha = 255;
        }
        return ColorUtils.setAlpha(color, Math.max(0, Math.min(255, Math.round(originalAlpha * alpha))));
    }

    private void renderEntityIcon(DrawContext context, LivingEntity entity, float x, float y, float size, float alpha) {
        if (entity instanceof AbstractClientPlayerEntity player) {
            Identifier skin = player.getSkin().body().texturePath();
            DrawUtil.renderHeadTexture(context, x, y, size, size, 0f, HEAD_U, HEAD_V, HEAD_TEX_SIZE, HEAD_TEX_SIZE, skin, Color.WHITE, alpha);
            DrawUtil.renderHeadTexture(context, x, y, size, size, 0f, HEAD_OVERLAY_U, HEAD_OVERLAY_V, HEAD_TEX_SIZE, HEAD_TEX_SIZE, skin, Color.WHITE, alpha);
            return;
        }

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(size, size))
                .color(new QuadColorState(ColorUtils.rgba(255, 255, 255, Math.round(45 * alpha))))
                .radius(new QuadRadiusState(TAG_RADIUS))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
    }

    private QuadColorState tagBackground() {
        Interface module = Interface.INSTANCE;
        int alpha = 150;

        if (module != null && module.isMinimalismCustom()) {
            int bg = module.getMinimalismCustomBg();
            int r = (bg >> 16) & 0xFF;
            int g = (bg >> 8) & 0xFF;
            int b = bg & 0xFF;
            return new QuadColorState(
                    ColorUtils.setAlpha(ColorUtils.rgb(clamp255(r - 2), clamp255(g - 2), clamp255(b - 2)), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(clamp255(r + 2), clamp255(g + 2), clamp255(b + 2)), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(clamp255(r - 4), clamp255(g - 4), clamp255(b - 4)), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(r, g, b), alpha)
            );
        }

        if (module != null && module.isMinimalismLight()) {
            return new QuadColorState(
                    ColorUtils.setAlpha(ColorUtils.rgb(238, 239, 244), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(245, 246, 250), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(230, 231, 236), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(236, 237, 242), alpha)
            );
        }

        return new QuadColorState(
                ColorUtils.setAlpha(ColorUtils.rgb(18, 19, 24), alpha),
                ColorUtils.setAlpha(ColorUtils.rgb(22, 23, 28), alpha),
                ColorUtils.setAlpha(ColorUtils.rgb(16, 17, 22), alpha),
                ColorUtils.setAlpha(ColorUtils.rgb(20, 21, 26), alpha)
        );
    }

    private int tagTextColor() {
        Interface module = Interface.INSTANCE;
        if (module != null && module.isMinimalismCustom()) {
            return ColorUtils.setAlpha(module.getMinimalismCustomText(), 255);
        }
        if (module != null && module.isMinimalismLight()) {
            return ColorUtils.rgb(24, 26, 32);
        }
        return ColorUtils.rgb(245, 245, 245);
    }

    private int tagHpColor() {
        Interface module = Interface.INSTANCE;
        if (module != null && module.isMinimalismLight()) {
            return ColorUtils.rgb(175, 45, 58);
        }
        return TAG_HP;
    }

    private int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void renderArmor(DrawContext context, double startX, double y, int totalWidth, LivingEntity e) {
        if (!elements.isSelected("Броню")) return;

        List<ItemStack> stacks = new ArrayList<>(4);
        ItemStack head = e.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack chest = e.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack legs = e.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack feet = e.getEquippedStack(EquipmentSlot.FEET);

        if (!head.isEmpty()) stacks.add(head);
        if (!chest.isEmpty()) stacks.add(chest);
        if (!legs.isEmpty()) stacks.add(legs);
        if (!feet.isEmpty()) stacks.add(feet);

        if (stacks.isEmpty()) return;

        int icon = 10;

        List<Boolean> enchanted = new ArrayList<>(stacks.size());
        if (elements.isSelected("Зачарования")) {
            for (ItemStack st : stacks) {
                var enchComp = EnchantmentHelper.getEnchantments(st);
                enchanted.add(!enchComp.getEnchantmentEntries().isEmpty());
            }
        } else {
            for (int i = 0; i < stacks.size(); i++) enchanted.add(false);
        }

        int rowWidth = stacks.size() * icon;
        for (int i = 0; i < stacks.size() - 1; i++) {
            int gapForThis = enchanted.get(i) ? 6 : 2;
            rowWidth += gapForThis;
        }

        double rowStartX = startX + (totalWidth - rowWidth) / 2.0;
        int yIcon = (int) (y - icon - 5);

        int xIcon = (int) rowStartX;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack st = stacks.get(i);
            MathUtil.scaleStart(context.getMatrices(), xIcon, yIcon, 0.55f);
            context.drawItem(st, xIcon, yIcon);
            MathUtil.scaleEnd(context.getMatrices());

            if (i < stacks.size() - 1) {
                int gapForThis = enchanted.get(i) ? 6 : 2;
                xIcon += icon + gapForThis;
            }
        }

        xIcon = (int) rowStartX;
        if (elements.isSelected("Зачарования")) {
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack st = stacks.get(i);
                var enchantmentsComponent = EnchantmentHelper.getEnchantments(st);
                var entries = enchantmentsComponent.getEnchantmentEntries();

                if (!entries.isEmpty()) {
                    double centerX = xIcon + icon / 2.0;
                    double yText = yIcon - 5;
                    final float step = 7.0f;
                    final int fontSize = (int) 3.5f;
                    final int maxName = 5;

                    for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>> entry : entries) {
                        var enchRef = entry.getKey();
                        int lvl = entry.getIntValue();

                        String full = enchRef.value().description().getString();
                        String name = stripEnchant(full, maxName);
                        String line = name + " " + lvl;

                        double w = Builder.INTER.get().getWidth(line, fontSize, 0.05f, 0);
                        double drawX = centerX - w / 2.0;

                        Builder.TEXT_BUILDER
                                .size(fontSize)
                                .color(-1)
                                .font(Builder.INTER.get())
                                .text(line)
                                .build()
                                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), drawX, yText);

                        yText -= step;
                    }
                }

                if (i < stacks.size() - 1) {
                    int gapForThis = enchanted.get(i) ? 6 : 2;
                    xIcon += icon + gapForThis;
                }
            }
        }
    }

    private void renderHandTags(DrawContext context, LivingEntity e, boolean friend) {
        if (!showHandTags.getEnabled()) return;
        ItemStack main = e.getMainHandStack();
        ItemStack off = e.getOffHandStack();
        boolean hasMain = !main.isEmpty();
        boolean hasOff = !off.isEmpty();

        if (!hasMain && !hasOff) return;

        Vec3d footPos = ProjectionUtil.projectCoordinates(MathVector.lerpPosition(e).add(0, 0.05, 0));
        if (footPos.z <= 0 || footPos.z >= 1) return;

        double centerX = footPos.x;
        double baseY = EntityESP.INSTANCE.isEnabled() ? footPos.y + 25 : footPos.y + 12;

        if (hasMain) {
            renderCompactItemTag(context, main, centerX, baseY - 5f);
            baseY += TAG_HEIGHT + 1f;
        }

        if (hasOff) {
            renderCompactItemTag(context, off, centerX, baseY - 5f);
        }
    }

    private void renderCompactItemTag(DrawContext context, ItemStack stack, double centerX, double rectY) {
        Text name = stack.getName();
        String qty = stack.getCount() > 1 ? " x" + stack.getCount() : "";

        final float textSize = TAG_TEXT_SIZE;
        final float iconCellWidth = 15f;
        final float iconGap = 2f;
        final float textPadX = 4f;
        final float height = TAG_HEIGHT;
        final float baselineOffset = (height - textSize) / 2f - 0.5f;

        float nameWidth = Builder.INTER.get().getWidth(name.getString(), textSize, 0.05f, 0);
        float qtyWidth = Builder.INTER.get().getWidth(qty, textSize, 0.05f, 0);
        float textBoxWidth = textPadX + nameWidth + qtyWidth + textPadX;
        float totalWidth = iconCellWidth + iconGap + textBoxWidth;

        double startX = centerX - totalWidth / 2.0;
        double iconX = startX;
        double textX = startX + iconCellWidth + iconGap;
        double tx = textX + textPadX;
        double ty = rectY + baselineOffset;

        if (!optimization.getEnabled()) {
            Builder.BLUR_BUILDER
                    .blurRadius(Math.min(10f, Math.max(0f, radiusBlur.getValue())))
                    .radius(new QuadRadiusState(TAG_RADIUS))
                    .size(new SizeState(totalWidth, height))
                    .alpha(1)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), startX, rectY);
        }

        renderHudPanel(context, (float) iconX, (float) rectY, iconCellWidth, height, TAG_RADIUS);
        renderHudPanel(context, (float) textX, (float) rectY, textBoxWidth, height, TAG_RADIUS);

        float itemX = (float) (iconX + 3f);
        float itemY = (float) (rectY + 2f);
        MathUtil.scaleStart(context.getMatrices(), itemX, itemY, 0.55f);
        context.drawItem(stack, (int) itemX, (int) itemY);
        MathUtil.scaleEnd(context.getMatrices());

        if (optimization.getEnabled()) {
            Builder.TEXT_BUILDER.size(textSize).color(tagTextColor()).font(Builder.INTER.get()).text(name.getString()).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
        } else {
            Builder.TEXT_BUILDER.size(textSize).color(tagTextColor()).rarityFallback(stack).font(Builder.INTER.get()).text(name).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
        }

        tx += nameWidth;
        if (stack.getCount() > 1) {
            Builder.TEXT_BUILDER.size(textSize).color(ColorUtils.rgb(255, 85, 85)).font(Builder.INTER.get()).text(qty).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
        }
    }

    // HolyWorld донат-префиксы: иконка → читаемое имя + цвет
    private record PrefixInfo(String name, Formatting color) {}

    private static final Map<String, PrefixInfo> DONAT_PREFIXES = new HashMap<>();
    static {
        DONAT_PREFIXES.put("ꔀ", new PrefixInfo("PLAYER", Formatting.GRAY));
        DONAT_PREFIXES.put("ꔄ", new PrefixInfo("HERO", Formatting.BLUE));
        DONAT_PREFIXES.put("ꔈ", new PrefixInfo("TITAN", Formatting.YELLOW));
        DONAT_PREFIXES.put("ꔒ", new PrefixInfo("AVENGER", Formatting.GREEN));
        DONAT_PREFIXES.put("ꔖ", new PrefixInfo("OVERLORD", Formatting.AQUA));
        DONAT_PREFIXES.put("ꔠ", new PrefixInfo("MAGISTER", Formatting.GOLD));
        DONAT_PREFIXES.put("ꔤ", new PrefixInfo("IMPERATOR", Formatting.RED));
        DONAT_PREFIXES.put("ꔨ", new PrefixInfo("DRAGON", Formatting.LIGHT_PURPLE));
        DONAT_PREFIXES.put("ꔲ", new PrefixInfo("BULL", Formatting.DARK_PURPLE));
        DONAT_PREFIXES.put("ꕒ", new PrefixInfo("RABBIT", Formatting.WHITE));
        DONAT_PREFIXES.put("ꔶ", new PrefixInfo("TIGER", Formatting.GOLD));
        DONAT_PREFIXES.put("ꕄ", new PrefixInfo("DRACULA", Formatting.RED));
        DONAT_PREFIXES.put("ꕖ", new PrefixInfo("BUNNY", Formatting.WHITE));
        DONAT_PREFIXES.put("ꕀ", new PrefixInfo("HYDRA", Formatting.DARK_GREEN));
        DONAT_PREFIXES.put("ꕈ", new PrefixInfo("COBRA", Formatting.GREEN));
        DONAT_PREFIXES.put("ꕅ", new PrefixInfo("VAMPIRE", Formatting.RED));
        DONAT_PREFIXES.put("ꔁ", new PrefixInfo("MEDIA", Formatting.BLUE));
        DONAT_PREFIXES.put("ꔅ", new PrefixInfo("YT", Formatting.RED));
        DONAT_PREFIXES.put("ꕠ", new PrefixInfo("D.HELPER", Formatting.GREEN));
        DONAT_PREFIXES.put("ꔉ", new PrefixInfo("HELPER", Formatting.YELLOW));
        DONAT_PREFIXES.put("ꔓ", new PrefixInfo("ML.MODER", Formatting.YELLOW));
        DONAT_PREFIXES.put("ꔗ", new PrefixInfo("MODER", Formatting.YELLOW));
        DONAT_PREFIXES.put("ꔡ", new PrefixInfo("MODER+", Formatting.YELLOW));
        DONAT_PREFIXES.put("ꔥ", new PrefixInfo("ST.MODER", Formatting.YELLOW));
        DONAT_PREFIXES.put("ꔩ", new PrefixInfo("GL.MODER", Formatting.GOLD));
        DONAT_PREFIXES.put("ꔳ", new PrefixInfo("ML.ADMIN", Formatting.RED));
        DONAT_PREFIXES.put("ꔷ", new PrefixInfo("ADMIN", Formatting.DARK_RED));
    }

    private static Text convertDisplayName(Text original) {
        MutableText result = Text.empty();
        processTextComponent(original, result);
        return result;
    }

    private static void processTextComponent(Text component, MutableText result) {
        String content = "";
        if (component.getContent() instanceof net.minecraft.text.PlainTextContent.Literal literal) {
            content = literal.string();
        }

        if (!content.isEmpty()) {
            boolean foundPrefix = false;
            for (Map.Entry<String, PrefixInfo> entry : DONAT_PREFIXES.entrySet()) {
                if (content.contains(entry.getKey())) {
                    String replaced = content.replace(entry.getKey(), entry.getValue().color() + entry.getValue().name());
                    result.append(Text.literal(replaced));
                    foundPrefix = true;
                    break;
                }
            }
            if (!foundPrefix) {
                result.append(Text.literal(content).setStyle(component.getStyle()));
            }
        }

        for (Text sibling : component.getSiblings()) {
            processTextComponent(sibling, result);
        }
    }

    private static final class Badge {
        private final String text;
        private final int color;
        private final net.minecraft.util.Identifier icon;
        private final float iconSize;

        private Badge(String text, int color) {
            this(text, color, null, 0f);
        }

        private Badge(String text, int color, net.minecraft.util.Identifier icon, float iconSize) {
            this.text = text;
            this.color = color;
            this.icon = icon;
            this.iconSize = iconSize;
        }

        public String text() { return text; }
        public int color() { return color; }
        public net.minecraft.util.Identifier icon() { return icon; }
        public float iconSize() { return iconSize; }
        public boolean hasIcon() { return icon != null; }
    }

    private void renderStatusEffects(DrawContext context, double startX, double yBase, float totalWidth, LivingEntity e) {
        if (!elements.isSelected("Эффекты")) return;
        if (e.getStatusEffects().isEmpty()) return;

        double x = startX + totalWidth - 8;
        double y = yBase + 17;
        final float lineStep = 8.5f;

        for (StatusEffectInstance inst : e.getStatusEffects()) {
            String name = inst.getEffectType().value().getName().getString();
            int amp = inst.getAmplifier();

            String duration = StatusEffectUtil.getDurationText(inst, 1.0f, 20.0f).getString();

            Builder.TEXT_BUILDER
                    .size(6)
                    .color(-1)
                    .font(Builder.INTER.get())
                    .text(name)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);

            double nameWidth = Builder.INTER.get().getWidth(name, 6, 0.05f, 0);

            if (amp >= 0) {
                String lvlStr = " " + (amp + 1);
                Builder.TEXT_BUILDER
                        .size(6)
                        .color(ColorUtils.red)
                        .font(Builder.INTER.get())
                        .text(lvlStr)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x + nameWidth, y);

                nameWidth += Builder.INTER.get().getWidth(lvlStr, 6, 0.05f, 0);
            }

            String durStr = " " + duration;
            Builder.TEXT_BUILDER
                    .size(6)
                    .color(-1)
                    .font(Builder.INTER.get())
                    .text(durStr)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x + nameWidth, y);

            y += lineStep;
        }
    }

    private void renderItemTag(DrawContext context, ItemEntity item) {
        ItemStack stack = item.getStack();
        if (stack.isEmpty()) return;

        Text name = stack.getName();
        int count = stack.getCount();
        String qty = count > 1 ? " x" + count : "";

        final float textSize = TAG_TEXT_SIZE;
        final float iconCellWidth = 15f;
        final float iconGap = 2f;
        final float textPadX = 4f;
        final float height = TAG_HEIGHT;
        final float baselineOffset = (height - textSize) / 2f - 0.5f;

        int nameWidth = (int) Builder.INTER.get().getWidth(name.getString(), textSize, 0.05f, 0);
        int qtyWidth = (int) Builder.INTER.get().getWidth(qty, textSize, 0.05f, 0);

        float textBoxWidth = textPadX + nameWidth + qtyWidth + textPadX;
        float totalWidth = iconCellWidth + iconGap + textBoxWidth;

        Vec3d tagPos = ProjectionUtil.projectCoordinates(MathVector.lerpPosition(item).add(0, item.getHeight() + 0.3, 0));
        if (tagPos.z <= 0 || tagPos.z >= 1) return;

        double startX = tagPos.x - (totalWidth / 2.0);
        double rectY = tagPos.y - 5f;
        double iconX = startX;
        double textX = startX + iconCellWidth + iconGap;
        double tx = textX + textPadX;
        double ty = rectY + baselineOffset;

        if (!optimization.getEnabled()) {
            Builder.BLUR_BUILDER
                    .blurRadius(Math.min(10f, Math.max(0f, radiusBlur.getValue())))
                    .radius(new QuadRadiusState(TAG_RADIUS))
                    .size(new SizeState(totalWidth, height))
                    .alpha(1)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), startX, rectY);
        }

        renderHudPanel(context, (float) iconX, (float) rectY, iconCellWidth, height, TAG_RADIUS);
        renderHudPanel(context, (float) textX, (float) rectY, textBoxWidth, height, TAG_RADIUS);

        float itemX = (float) (iconX + 3f);
        float itemY = (float) (rectY + 2f);
        MathUtil.scaleStart(context.getMatrices(), itemX, itemY, 0.55f);
        context.drawItem(stack, (int) itemX, (int) itemY);
        MathUtil.scaleEnd(context.getMatrices());

        if (optimization.getEnabled()) {
            Builder.TEXT_BUILDER.size(textSize).color(tagTextColor()).font(Builder.INTER.get()).text(name.getString()).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
        } else {
            Builder.TEXT_BUILDER.size(textSize).color(tagTextColor()).rarityFallback(stack).font(Builder.INTER.get()).text(name).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
        }

        tx += nameWidth;

        if (count > 1) {
            Builder.TEXT_BUILDER.size(textSize).color(ColorUtils.rgb(255, 85, 85)).font(Builder.INTER.get()).text(qty).build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), tx, ty);
        }
    }

    private boolean shouldRenderEntity(Entity entity) {
        if (entity instanceof AbstractClientPlayerEntity)
            return shouldRenderPlayer((AbstractClientPlayerEntity) entity);
        if (entity instanceof VillagerEntity) return filter.isSelected("Жителей");
        if (entity instanceof AnimalEntity) return filter.isSelected("Животных");
        if (entity instanceof MobEntity) return filter.isSelected("Мобов");
        return entity instanceof ItemEntity && filter.isSelected("Предметы");
    }

    private boolean shouldRenderPlayer(AbstractClientPlayerEntity player) {
        if (player == mc.player && mc.options.getPerspective().isFirstPerson()) return false;
        if (player == mc.player && !filter.isSelected("Себя")) return false;

        if (filter.isSelected("Друзей") && handlerClient().getFriendManager().isFriend(player.getName().getString()))
            return true;
        if (!filter.isSelected("Игроков")) return false;

        boolean hasArmor = false;
        for (ItemStack armorPiece : dev.client.api.compat.InventoryCompat.armorItems(player)) {
            if (!armorPiece.isEmpty()) {
                hasArmor = true;
                break;
            }
        }

        boolean isInvisible = player.isInvisible();

        if (isInvisible && !filter.isSelected("Невидимых")) return false;
        if (!hasArmor && !filter.isSelected("Голых")) return false;

        if (isInvisible && !hasArmor) {
            return filter.isSelected("Невидимых") && filter.isSelected("Голых");
        }

        return true;
    }

    private static String stripEnchant(String full, int max) {
        if (full == null) return "";
        String s = full.trim();
        if (s.isEmpty()) return s;
        int end = s.length();
        int sp = s.indexOf(' ');
        int br = s.indexOf('(');
        int cm = s.indexOf(',');
        if (sp >= 0) end = Math.min(end, sp);
        if (br >= 0) end = Math.min(end, br);
        if (cm >= 0) end = Math.min(end, cm);
        String w = s.substring(0, end);

        if (w.length() <= max) return w;

        final String vowels = "AEIOUYaeiouyАЕЁИОУЫЭЮЯаеёиоуыэюя";
        StringBuilder b = new StringBuilder();
        b.append(w.charAt(0));
        for (int i = 1; i < w.length() && b.length() < max; i++) {
            char c = w.charAt(i);
            if (vowels.indexOf(c) >= 0) continue;
            b.append(c);
        }

        String out = b.toString();
        boolean truncated = out.length() < w.length();
        if (truncated && out.length() + 1 <= max) out += ".";
        if (out.length() > max) out = out.substring(0, max);
        return out;
    }

    private int healthColor(int health, int maxHealth) {
        if (maxHealth <= 0) return ColorUtils.red;
        if (health >= maxHealth) return ColorUtils.green;
        if (health <= 0) return ColorUtils.red;

        float t = Math.max(0f, Math.min(1f, health / (float) maxHealth));
        if (t <= 0.5f) {
            float k = t / 0.5f;
            return ColorUtils.interpolate(ColorUtils.red, ColorUtils.yellow, k);
        } else {
            float k = (t - 0.5f) / 0.5f;
            return ColorUtils.interpolate(ColorUtils.yellow, ColorUtils.green, k);
        }
    }

    private int pingColor(int ping) {
        if (ping > 500) return ColorUtils.red;
        if (ping >= 100) return ColorUtils.yellow;
        return ColorUtils.green;
    }

    private record TagHitbox(String name, float x, float y, float w, float h) {
        private boolean contains(float mx, float my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }
}
