package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.msdf.core.MsdfFont;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.api.nullcry.uiClient.draggables.settings.PanelAlphaProvider;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.misc.NameProtect;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class TargetHud implements IHelper, SettingProvider, PanelAlphaProvider {
    final DraggableHandler draggableHandler;
    private final List<Setting> settings = new ArrayList<>();

    public final Slider scale = new Slider("Масштаб", () -> true).set(50, 150, 5).applyDefault(100).register(this);

    @Override
    public DraggableHandler draggable() {
        return draggableHandler;
    }

    @Override
    public float hudScale() {
        Float scaleValue = scale.getValue();
        return scaleValue == null ? 1f : scaleValue / 100f;
    }

    public final ModeElement mode = new ModeElement("Режим", () -> true)
            .set("Nursultan", "Rockstar")
            .defaultValue("Nursultan")
            .register(this);

    public final ModeElement colorBar = new ModeElement("Цвет BossBar", () -> !isExpa())
            .set("Клиентский", "Состояние здоровья")
            .defaultValue("Клиентский")
            .register(this);

    public final CheckBox particleTargetHud = new CheckBox("Частицы", () -> !isExpa())
            .defaultValue(true)
            .register(this);

    public final CheckBox armorStack = new CheckBox("Отображать броню", () -> !isExpa())
            .defaultValue(true)
            .register(this);

    public final CheckBox rayTrace = new CheckBox("RayTrace", () -> true)
            .defaultValue(true)
            .register(this);

    public final Slider particleValue = new Slider("Кол-во частиц", () -> !isExpa() && particleTargetHud.getEnabled())
            .set(1, 20, 1)
            .defaultValue(10)
            .register(this);

    public final CompactAnimation alphaAnimation = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    final CopyOnWriteArrayList<HeadParticle> headParticles = new CopyOnWriteArrayList<>();
    private final CompactAnimation copyHoverAnimation = new CompactAnimation(Easing.EASE_OUT_CUBIC, 200);
    private final CompactAnimation rockstarHealthAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 220);
    private final CompactAnimation rockstarAbsorbAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 220);
    private final CompactAnimation rockstarHpTextAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 220);
    private final CompactAnimation expFadeAnimation = new CompactAnimation(Easing.LINEAR, 200);
    private final CompactAnimation expHealthAnimation = new CompactAnimation(Easing.LINEAR, 160);
    private final CompactAnimation expAbsorptionAnimation = new CompactAnimation(Easing.LINEAR, 180);
    private LivingEntity expTarget;
    private boolean expModeWasActive;
    private static final String COPY_LABEL = "Скопировать";

    private static final float NURSULTAN_PANEL_W = 110f;
    private static final float NURSULTAN_PANEL_H = 39f;
    private static final float NURSULTAN_HEAD_SIZE = 32f;
    private static final float NURSULTAN_HEAD_SPACING = 3f;

    private static final float EXP_MINIMAL_PANEL_W = NURSULTAN_PANEL_W;
    private static final float EXP_MINIMAL_PANEL_H = NURSULTAN_PANEL_H;
    private static final float EXP_COMPACT_HEAD_SIZE = 27f;
    private static final float EXP_COMPACT_HEAD_SPACING = 6f;

    private static final float DANQ_PANEL_W = 120f;
    private static final float DANQ_PANEL_H = 30f;
    private static final float DANQ_HEAD_SIZE = 22f;
    private static final float DANQ_HEAD_SPACING = 4f;

    private static final float ROCKSTAR_PANEL_W = 120f;
    private static final float ROCKSTAR_BASE_H = 34f;
    private static final float ROCKSTAR_HEAD_SIZE = 26f;
    private static final float ROCKSTAR_HEAD_SPACING = 4f;
    private static final float ROCKSTAR_ARMOR_ROW_H = 11f;
    private static final float ROCKSTAR_HAND_ROW_H = 16f;
    private static final float ROCKSTAR_SECTION_GAP = 3f;
    private static final float ROCKSTAR_HAND_GAP = 2f;

    private static final float EXP_PADDING = 10f;
    private static final float EXP_CORNER_RADIUS = 8f;
    private static final float EXP_ITEM_SIZE = 12f;
    private static final float EXP_ITEM_SPACING = 3f;
    private static final float EXP_ICON_SIZE = 10f;
    private static final float EXP_HALF_DIVISOR = 2f;
    private static final float EXP_SECTION_GAP = 4f;
    private static final float EXP_MIN_WIDTH = 180f;
    private static final String EXP_BACKGROUND_TEXTURE = "targethud/hud_background.png";
    private static final String EXP_HEART_TEXTURE = "targethud/heartpulse.png";
    private static final String EXP_GAPPLE_TEXTURE = "targethud/gapple.png";
    private static final int EXP_DEFAULT_ACCENT = 0xFF6E74E3;
    private static final int EXP_ABSORPTION_COLOR = 0xFFE9B444;

    private float copyAreaX;
    private float copyAreaY;
    private float copyAreaW;
    private float copyAreaH;
    private String copyRealName = "";
    private boolean canCopyName = false;

    public TargetHud() {
        this.draggableHandler = addDraggable("Target Hud", 418, 359);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("TargetHud");
        });

        this.draggableHandler.setClickHandler((mouseX, mouseY, button, self) -> {
            if (button != 0) return false;
            if (!canCopyName) return false;
            if (!mc.inGameHud.getChatHud().isChatFocused()) return false;
            float localMouseX = toHudLocalX(mouseX);
            float localMouseY = toHudLocalY(mouseY);
            if (!MouseClick.isClick(localMouseX, localMouseY, copyAreaX, copyAreaY, copyAreaW, copyAreaH)) return false;
            if (copyRealName.isEmpty()) return false;
            if (mc.keyboard != null) {
                mc.keyboard.setClipboard(copyRealName);
            }
            return true;
        });
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
        this.draggableHandler.setHoverHandler((mouseX, mouseY, self) -> isHovering(mouseX, mouseY));
    }

    float width;
    float height;
    private final List<HoverRect> hoverRects = new ArrayList<>();

    float animationValue;
    float headHitAnim = 0f;

    float hpTextValue = 0f;
    boolean hpTextInit = false;
    boolean rockstarHpTextInit = false;
    float rockstarHpPulse = 0f;
    float lastRockstarHealth = 0f;

    @Override
    public void onPlayerAttack(PlayerAttackEvent event) {
        if (isExpa()) return;
        if (!particleTargetHud.getEnabled()) return;
        if (!(event.getEntity() instanceof LivingEntity attacked)) return;
        if (attacked instanceof ArmorStandEntity) return;

        LivingEntity target = KillAura.INSTANCE.getTarget();
        if (target != null && attacked != target) return;

        Vec3d center = getHeadCenterForMode();

        int particleCount = (int) particleValue.getValue().floatValue();
        for (int i = 0; i < particleCount; ++i) {
            headParticles.add(new HeadParticle(center));
        }
    }

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        boolean chatOpen = mc.inGameHud.getChatHud().isChatFocused();

        if (isExpa()) {
            expModeWasActive = true;
            renderExpFrame(event, resolveExpTarget(), chatOpen);
            return;
        }

        if (expModeWasActive) {
            expModeWasActive = false;
            expTarget = null;
            expFadeAnimation.force(0.0);
            expHealthAnimation.force(0.0);
            expAbsorptionAnimation.force(0.0);
        }

        LivingEntity target = resolveTarget();
        boolean shouldRender = target != null || chatOpen;

        alphaAnimation.run(shouldRender ? 1.0 : 0.0);
        alphaAnimation.update();
        float a = (float) alphaAnimation.getValue();
        if (a <= 0f) {
            resetCopyInteraction();
            hoverRects.clear();
            return;
        }

        if (target == null) target = mc.player;
        if (target == null) {
            resetCopyInteraction();
            hoverRects.clear();
            return;
        }

        int mouseX = 0;
        int mouseY = 0;
        Window window = mc.getWindow();
        if (window != null && window.getWidth() > 0 && window.getHeight() > 0) {
            mouseX = (int) (mc.mouse.getX() * window.getScaledWidth() / (double) window.getWidth());
            mouseY = (int) (mc.mouse.getY() * window.getScaledHeight() / (double) window.getHeight());
        }

        float health = PlayerHelper.getHealth(target);
        float maxHealth = Math.max(1f, target.getMaxHealth());
        float healthRatio = MathHelper.clamp(health / maxHealth, 0f, 1f);
        animationValue = MathHelper.lerp(0.1f, animationValue, healthRatio);

        if (!hpTextInit) {
            hpTextValue = health;
            hpTextInit = true;
        }
        float dtHp = Math.max(0.001f, mc.getRenderTickCounter().getDynamicDeltaTicks());
        float speed = 8.0f * dtHp;
        hpTextValue = MathHelper.lerp(MathHelper.clamp(speed, 0f, 1f), hpTextValue, health);

        float absorption = target.getAbsorptionAmount();
        float absorptionRatio = MathHelper.clamp(absorption / maxHealth, 0f, 1f);
        rockstarHealthAnim.run(healthRatio);
        rockstarHealthAnim.update();
        rockstarAbsorbAnim.run(absorptionRatio);
        rockstarAbsorbAnim.update();

        if (!rockstarHpTextInit) {
            rockstarHpTextInit = true;
            lastRockstarHealth = health;
            rockstarHpTextAnim.force(health);
        }
        rockstarHpTextAnim.run(health);
        rockstarHpTextAnim.update();

        if (Math.abs(health - lastRockstarHealth) > 0.01f) {
            rockstarHpPulse = 1f;
            lastRockstarHealth = health;
        }
        rockstarHpPulse = Math.max(0f, rockstarHpPulse - dtHp * 2.5f);

        renderTX(event, target, a, chatOpen, mouseX, mouseY);
        renderParticles(event, a);
    }

    private void renderExpFrame(RenderEvent.Draw2D event, LivingEntity nextTarget, boolean chatOpen) {
        if (nextTarget == null && chatOpen) {
            nextTarget = mc.player;
        }

        if (nextTarget != null) {
            if (expTarget != nextTarget) {
                expTarget = nextTarget;
                float health = getExpHealth(nextTarget);
                float maxHealth = getExpEffectiveMaxHealth(nextTarget, health);
                expHealthAnimation.force(MathHelper.clamp(maxHealth > 0f ? health / maxHealth : 0f, 0f, 1f));
                expAbsorptionAnimation.force(MathHelper.clamp(nextTarget.getAbsorptionAmount() / 20f, 0f, 1f));
            }
            expFadeAnimation.run(1.0);
        } else {
            expFadeAnimation.run(0.0);
        }
        expFadeAnimation.update();

        float a = MathHelper.clamp((float) expFadeAnimation.getValue(), 0f, 1f);
        if (a <= 0f || expTarget == null) {
            if (nextTarget == null) {
                expTarget = null;
                expHealthAnimation.force(0.0);
                expAbsorptionAnimation.force(0.0);
            }
            resetCopyInteraction();
            hoverRects.clear();
            return;
        }

        float health = getExpHealth(expTarget);
        float maxHealth = getExpEffectiveMaxHealth(expTarget, health);
        expHealthAnimation.run(MathHelper.clamp(maxHealth > 0f ? health / maxHealth : 0f, 0f, 1f));
        expHealthAnimation.update();
        expAbsorptionAnimation.run(MathHelper.clamp(expTarget.getAbsorptionAmount() / 20f, 0f, 1f));
        expAbsorptionAnimation.update();

        int mouseX = 0;
        int mouseY = 0;
        Window window = mc.getWindow();
        if (window != null && window.getWidth() > 0 && window.getHeight() > 0) {
            mouseX = (int) (mc.mouse.getX() * window.getScaledWidth() / (double) window.getWidth());
            mouseY = (int) (mc.mouse.getY() * window.getScaledHeight() / (double) window.getHeight());
        }

        renderExpa(event, expTarget, a, chatOpen, mouseX, mouseY);
    }

    private void renderTX(RenderEvent.Draw2D event, LivingEntity target, float a, boolean chatOpen, int mouseX, int mouseY) {
        if (isDanq()) {
            renderDanq(event, target, a, chatOpen, mouseX, mouseY);
        } else if (isRockstar()) {
            renderRockstar(event, target, a, chatOpen, mouseX, mouseY);
        } else {
            renderNursultan(event, target, a, chatOpen, mouseX, mouseY);
        }
    }

    private void renderExpa(RenderEvent.Draw2D event, LivingEntity target, float a,
                            boolean chatOpen, int mouseX, int mouseY) {
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();
        float panelW = EXP_MINIMAL_PANEL_W;
        float panelH = EXP_MINIMAL_PANEL_H;

        this.width = panelW;
        this.height = panelH;
        draggableHandler.setWidth(panelW);
        draggableHandler.setHeight(panelH);
        beginHoverRects();
        addHoverRect(x, y, panelW, panelH);

        List<ItemStack> equipment = collectExpEquipment(target);
        boolean hasEquipment = !equipment.isEmpty();
        float sourcePanelH = hasEquipment ? 70f : 50f;
        float sourceAvatarSize = Math.max(EXP_PADDING, sourcePanelH - 20f);
        MsdfFont nameFont = Builder.INTER_SEMIBOLD.get();
        MsdfFont valueFont = Builder.INTER_BOLD.get();
        String displayName = getExpDisplayName(target);
        float itemRowWidth = hasEquipment
                ? equipment.size() * EXP_ITEM_SIZE + (equipment.size() - 1) * EXP_ITEM_SPACING
                : 0f;
        float nameOrCopyWidth = Math.max(
                expTextWidth(nameFont, displayName, 13f),
                expTextWidth(nameFont, COPY_LABEL, 13f)
        );
        float cappedNameWidth = Math.min(
                nameOrCopyWidth,
                Math.max(0f, 160f - sourceAvatarSize - EXP_CORNER_RADIUS)
        );
        float healthTextWidth = Math.max(
                expTextWidth(valueFont, "20.0", EXP_PADDING),
                expTextWidth(valueFont, "Unknown", EXP_PADDING)
        );
        float absorptionTextWidth = expTextWidth(valueFont, "20.0", EXP_PADDING);
        float healthGroupWidth = healthTextWidth + EXP_HALF_DIVISOR + EXP_ICON_SIZE;
        float absorptionGroupWidth = absorptionTextWidth + EXP_HALF_DIVISOR + EXP_ICON_SIZE;
        boolean showAbsorption = shouldShowExpAbsorption(target);
        float rightReserved = hasEquipment
                ? Math.max(healthGroupWidth, absorptionGroupWidth)
                : healthGroupWidth + (showAbsorption ? EXP_SECTION_GAP + absorptionGroupWidth : 0f);
        float sourcePanelW = EXP_PADDING + sourceAvatarSize + EXP_CORNER_RADIUS
                + Math.max(itemRowWidth, cappedNameWidth)
                + EXP_CORNER_RADIUS + rightReserved + EXP_PADDING;
        if (hasEquipment) {
            sourcePanelW = Math.max(EXP_MIN_WIDTH, sourcePanelW);
        }

        float sourceAvatarX = EXP_PADDING;
        float sourceAvatarY = sourcePanelH / EXP_HALF_DIVISOR - sourceAvatarSize / EXP_HALF_DIVISOR;
        float sourceContentX = sourceAvatarX + sourceAvatarSize + EXP_CORNER_RADIUS;
        float sourceContentY = sourceAvatarY;
        float sourceContentW = sourcePanelW - 20f - sourceAvatarSize - EXP_CORNER_RADIUS;
        float sourceContentH = sourceAvatarSize;

        float fitScale = Math.min(panelW / sourcePanelW, panelH / sourcePanelH);
        float fittedW = sourcePanelW * fitScale;
        float fittedH = sourcePanelH * fitScale;
        float fittedX = x + (panelW - fittedW) / EXP_HALF_DIVISOR;
        float fittedY = y + (panelH - fittedH) / EXP_HALF_DIVISOR;
        ExpRenderTransform transform = new ExpRenderTransform(fittedX, fittedY, fitScale);

        HelperElements.rectElements(event.getContext(), x, y, panelW, panelH, a);

        ScissorUtil.enableContext(event.getContext(), x, y, panelW, panelH);
        var matrices = event.getContext().getMatrices();
        matrices.pushMatrix();
        matrices.translate(fittedX, fittedY);
        matrices.scale(fitScale, fitScale);
        try {
            renderExpPattern(event, sourcePanelW, sourcePanelH, hasEquipment, a);
            renderExpAvatar(event, target, sourceAvatarX, sourceAvatarY, sourceAvatarSize,
                    hasEquipment ? EXP_CORNER_RADIUS : 6f, a);
            if (hasEquipment) {
                renderExpEquipment(event, equipment, sourceContentX, sourceContentY);
            }
            renderExpContent(event, target, hasEquipment, a,
                    sourceContentX, sourceContentY, sourceContentW, sourceContentH,
                    transform, chatOpen, mouseX, mouseY);
        } finally {
            matrices.popMatrix();
            ScissorUtil.disableContext(event.getContext());
        }
    }

    private void renderExpPattern(RenderEvent.Draw2D event, float panelW, float panelH,
                                  boolean hasEquipment, float a) {
        float textureY = hasEquipment ? 15f : EXP_PADDING;
        float textureH = Math.max(0f, panelH - (hasEquipment ? 40f : 20f));
        if (textureH <= 0f) return;

        Builder.TEXTURE_BUILDER
                .size(new SizeState(panelW, textureH))
                .texture(0f, 0f, 1f, 1f, ClientTexture.of(EXP_BACKGROUND_TEXTURE))
                .color(new QuadColorState(HelperElements.themedText(Math.round(255f * a))))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), 0f, textureY);
    }

    private void renderExpAvatar(RenderEvent.Draw2D event, LivingEntity target, float x, float y, float size, float radius, float a) {
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        int hurtTime = target.hurtTime;
        float recovery = 1f - ((hurtTime - (hurtTime != 0 ? tickDelta : 0f)) / 10f);
        recovery = MathHelper.clamp(recovery, 0f, 1f);
        int channel = MathHelper.clamp(Math.round(100f + 155f * recovery), 0, 255);
        int tint = ColorUtils.rgba(255, channel, channel, Math.round(255f * a * a));

        ExpAvatarTexture avatarTexture = resolveExpAvatarTexture(target, tickDelta);
        if (avatarTexture != null) {
            ExpUv uv = avatarTexture.uv();
            DrawUtil.renderHeadTexture(
                    event.getContext(),
                    x, y,
                    size, size,
                    radius,
                    uv.u0(), uv.v0(),
                    uv.u1() - uv.u0(), uv.v1() - uv.v0(),
                    avatarTexture.texture(),
                    new Color(tint, true),
                    1f
            );
        } else if (target instanceof AbstractClientPlayerEntity player) {
            DrawUtil.renderHeadTexture(
                    event.getContext(),
                    x, y,
                    size, size,
                    radius,
                    0.125f, 0.125f,
                    0.125f, 0.125f,
                    player.getSkin().body().texturePath(),
                    new Color(tint, true),
                    1f
            );
        } else {
            Builder.TEXTURE_BUILDER
                    .size(new SizeState(size, size))
                    .radius(new QuadRadiusState(radius))
                    .texture(0f, 0f, 1f, 1f, ClientTexture.of("images/world/entity.png"))
                    .color(new QuadColorState(tint))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), x, y);
        }

        int transparentTop = ColorUtils.rgba(6, 7, 18, 0);
        int darkBottom = ColorUtils.rgba(6, 7, 18, Math.round(127.5f * a));
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(size, size))
                .radius(new QuadRadiusState(radius))
                .color(new QuadColorState(transparentTop, darkBottom, darkBottom, transparentTop))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), x, y);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ExpAvatarTexture resolveExpAvatarTexture(LivingEntity target, float tickDelta) {
        try {
            if (mc.getEntityRenderDispatcher() == null) return null;
            if (mc.getEntityRenderDispatcher().getRenderer(target) instanceof LivingEntityRenderer renderer) {
                Object rawState = renderer.getAndUpdateRenderState(target, tickDelta);
                if (!(rawState instanceof LivingEntityRenderState state)) return null;
                Identifier texture = (Identifier) renderer.getTexture(state);
                if (texture == null) return null;
                return new ExpAvatarTexture(texture, getExpFaceUv(renderer));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ExpUv getExpFaceUv(LivingEntityRenderer<?, ?, ?> renderer) {
        if (renderer == null) return ExpUv.DEFAULT;
        EntityModel<?> model = renderer.getModel();
        if (model == null) return ExpUv.DEFAULT;

        for (ModelPart part : model.getParts()) {
            ExpUv uv = getExpPartFaceUv(part);
            if (uv != null) return uv;
        }
        return ExpUv.DEFAULT;
    }

    private ExpUv getExpPartFaceUv(ModelPart part) {
        ExpUv largest = null;
        for (ModelPart.Cuboid cuboid : part.cuboids) {
            ExpUv median = selectExpMedianUv(
                    getExpSideUv(cuboid, Direction.NORTH),
                    getExpSideUv(cuboid, Direction.SOUTH),
                    getExpSideUv(cuboid, Direction.WEST),
                    getExpSideUv(cuboid, Direction.EAST)
            );
            largest = pickLargerExpUv(largest, median);
        }
        return largest;
    }

    private ExpUv getExpSideUv(ModelPart.Cuboid cuboid, Direction direction) {
        int index = direction.getIndex();
        if (index < 0 || index >= cuboid.sides.length) return null;
        ModelPart.Quad quad = cuboid.sides[index];
        if (quad == null || quad.vertices() == null || quad.vertices().length == 0) return null;

        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (ModelPart.Vertex vertex : quad.vertices()) {
            minU = Math.min(minU, vertex.u());
            minV = Math.min(minV, vertex.v());
            maxU = Math.max(maxU, vertex.u());
            maxV = Math.max(maxV, vertex.v());
        }
        if (minU == Float.POSITIVE_INFINITY || minV == Float.POSITIVE_INFINITY) return null;
        return new ExpUv(minU, minV, maxU, maxV);
    }

    private ExpUv selectExpMedianUv(ExpUv... values) {
        ArrayList<ExpUv> present = new ArrayList<>();
        for (ExpUv value : values) {
            if (value != null) present.add(value);
        }
        if (present.isEmpty()) return null;
        if (present.size() == 1) return present.getFirst();
        present.sort(Comparator.comparing(ExpUv::u0));
        return present.size() >= 4 ? present.get(1) : present.get(present.size() / 2);
    }

    private ExpUv pickLargerExpUv(ExpUv current, ExpUv candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        float currentArea = (current.u1() - current.u0()) * (current.v1() - current.v0());
        float candidateArea = (candidate.u1() - candidate.u0()) * (candidate.v1() - candidate.v0());
        return candidateArea != currentArea && candidateArea <= currentArea ? current : candidate;
    }

    private void renderExpEquipment(RenderEvent.Draw2D event, List<ItemStack> equipment, float x, float y) {
        float itemX = x;
        var matrices = event.getContext().getMatrices();
        for (ItemStack stack : equipment) {
            matrices.pushMatrix();
            matrices.translate(itemX, y);
            matrices.scale(0.75f, 0.75f);
            event.getContext().drawItem(stack, 0, 0);
            matrices.popMatrix();
            itemX += EXP_ITEM_SIZE + EXP_ITEM_SPACING;
        }
    }

    private void renderExpContent(RenderEvent.Draw2D event, LivingEntity target, boolean hasEquipment, float a,
                                  float contentX, float contentY, float contentW, float contentH,
                                  ExpRenderTransform transform, boolean chatOpen, int mouseX, int mouseY) {
        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext());
        MsdfFont nameFont = Builder.INTER_SEMIBOLD.get();
        MsdfFont valueFont = Builder.INTER_BOLD.get();
        String displayName = getExpDisplayName(target);
        boolean showAbsorption = shouldShowExpAbsorption(target);

        float healthReserved = Math.max(expTextWidth(valueFont, "20.0", EXP_PADDING), expTextWidth(valueFont, "Unknown", EXP_PADDING))
                + EXP_HALF_DIVISOR + EXP_ICON_SIZE;
        float absorptionReserved = expTextWidth(valueFont, "20.0", EXP_PADDING) + EXP_HALF_DIVISOR + EXP_ICON_SIZE;
        float nameAvailableWidth = contentW - (hasEquipment
                ? Math.max(healthReserved, absorptionReserved)
                : healthReserved + (showAbsorption ? EXP_SECTION_GAP + absorptionReserved : 0f));
        nameAvailableWidth = Math.max(0f, nameAvailableWidth);

        float nameY = hasEquipment ? contentY + EXP_ITEM_SIZE + EXP_HALF_DIVISOR : contentY;
        renderExpCopyableName(matrix, nameFont, target, displayName,
                contentX, nameY, nameAvailableWidth, a, transform, chatOpen, mouseX, mouseY);

        float barX = contentX;
        float barW = Math.max(0f, contentW - EXP_SECTION_GAP);
        float contentBottom = contentY + contentH;
        float barY = hasEquipment
                ? contentBottom - 7f - EXP_ITEM_SPACING
                : contentBottom - EXP_ITEM_SPACING - EXP_ITEM_SPACING;
        float healthRatio = MathHelper.clamp((float) expHealthAnimation.getValue(), 0f, 1f);
        float absorptionRatio = MathHelper.clamp((float) expAbsorptionAnimation.getValue(), 0f, 1f);
        int accentRaw = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : EXP_DEFAULT_ACCENT;
        int accent = expAlpha(accentRaw, a);
        int gold = expAlpha(EXP_ABSORPTION_COLOR, a);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(barW, EXP_ITEM_SPACING))
                .radius(new QuadRadiusState(EXP_HALF_DIVISOR))
                .color(new QuadColorState(HelperElements.themedText(Math.round(42f * a))))
                .build()
                .render(matrix, barX, barY);
        if (barW * healthRatio > 0f) {
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(barW * healthRatio, EXP_ITEM_SPACING))
                    .radius(new QuadRadiusState(EXP_HALF_DIVISOR))
                    .color(new QuadColorState(accent))
                    .build()
                    .render(matrix, barX, barY);
        }
        if (showAbsorption && barW * absorptionRatio > 0f) {
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(barW * absorptionRatio, EXP_ITEM_SPACING))
                    .radius(new QuadRadiusState(EXP_HALF_DIVISOR))
                    .color(new QuadColorState(gold))
                    .build()
                    .render(matrix, barX, barY);
        }

        float rawHealth = getExpHealth(target);
        float effectiveMaxHealth = getExpEffectiveMaxHealth(target, rawHealth);
        boolean unknownHealth = rawHealth > target.getMaxHealth() + 20f;
        float animatedHealth = healthRatio * effectiveMaxHealth;
        float maxAbsorption = target.getMaxAbsorption();
        float animatedAbsorption = absorptionRatio * (maxAbsorption != 0f ? maxAbsorption : 20f);
        String healthText = formatExpValue(animatedHealth, unknownHealth);
        String absorptionText = !showAbsorption || animatedAbsorption <= 0f
                ? ""
                : formatExpValue(animatedAbsorption, false);
        float valueLineHeight = expFontHeight(valueFont, EXP_PADDING);

        if (hasEquipment) {
            float healthY = showAbsorption
                    ? contentY
                    : contentBottom - 7f - EXP_ITEM_SPACING - EXP_CORNER_RADIUS - valueLineHeight;
            float healthX = contentX + contentW - expTextWidth(valueFont, healthText, EXP_PADDING);
            renderExpText(matrix, valueFont, healthText, healthX, healthY, EXP_PADDING, accent);
            renderExpIcon(event, EXP_HEART_TEXTURE, healthX - EXP_HALF_DIVISOR - EXP_ICON_SIZE, healthY, valueLineHeight, accent);

            if (!absorptionText.isEmpty()) {
                float absorptionY = contentBottom - 7f - EXP_ITEM_SPACING - EXP_CORNER_RADIUS - valueLineHeight;
                float absorptionX = contentX + contentW - expTextWidth(valueFont, absorptionText, EXP_PADDING);
                renderExpText(matrix, valueFont, absorptionText, absorptionX, absorptionY, EXP_PADDING, gold);
                renderExpIcon(event, EXP_GAPPLE_TEXTURE, absorptionX - EXP_HALF_DIVISOR - EXP_ICON_SIZE,
                        absorptionY, valueLineHeight, expAlpha(0xFFFFFFFF, a));
            }
        } else {
            float nameLineHeight = expFontHeight(nameFont, 13f);
            float valueY = nameY + nameLineHeight - valueLineHeight;
            float right = barX + barW;
            float healthWidth = expTextWidth(valueFont, healthText, EXP_PADDING);

            if (absorptionText.isEmpty()) {
                float healthX = right - healthWidth;
                renderExpText(matrix, valueFont, healthText, healthX, valueY, EXP_PADDING, accent);
                renderExpIcon(event, EXP_HEART_TEXTURE, healthX - EXP_HALF_DIVISOR - EXP_ICON_SIZE,
                        valueY, valueLineHeight, accent);
            } else {
                float absorptionWidth = expTextWidth(valueFont, absorptionText, EXP_PADDING);
                float absorptionX = right - absorptionWidth;
                renderExpText(matrix, valueFont, absorptionText, absorptionX, valueY, EXP_PADDING, gold);
                renderExpIcon(event, EXP_GAPPLE_TEXTURE, absorptionX - EXP_HALF_DIVISOR - EXP_ICON_SIZE,
                        valueY, valueLineHeight, expAlpha(0xFFFFFFFF, a));

                float healthGroupRight = right - (absorptionWidth + EXP_HALF_DIVISOR + EXP_ICON_SIZE) - EXP_SECTION_GAP;
                float healthX = healthGroupRight - healthWidth;
                renderExpText(matrix, valueFont, healthText, healthX, valueY, EXP_PADDING, accent);
                renderExpIcon(event, EXP_HEART_TEXTURE, healthX - EXP_HALF_DIVISOR - EXP_ICON_SIZE,
                        valueY, valueLineHeight, accent);
            }
        }
    }

    private void renderExpCopyableName(Matrix4f matrix, MsdfFont font, LivingEntity target, String displayName,
                                       float x, float y, float maxWidth, float a,
                                       ExpRenderTransform transform, boolean chatOpen, int mouseX, int mouseY) {
        String realName = target instanceof PlayerEntity player
                ? player.getGameProfile().name()
                : target.getName().getString();
        boolean eligible = target instanceof PlayerEntity && realName != null && !realName.isEmpty();
        copyRealName = eligible ? realName : "";
        canCopyName = eligible;

        float displayWidth = expTextWidth(font, displayName, 13f);
        float copyWidth = expTextWidth(font, COPY_LABEL, 13f);
        float interactionWidth = Math.max(1f, Math.min(maxWidth, Math.max(displayWidth, copyWidth)));
        copyAreaX = transform.mapX(x);
        copyAreaY = transform.mapY(y);
        copyAreaW = interactionWidth * transform.scale();
        copyAreaH = expFontHeight(font, 13f) * transform.scale();

        float localMouseX = toHudLocalX(mouseX);
        float localMouseY = toHudLocalY(mouseY);
        boolean hovering = chatOpen && canCopyName
                && MouseClick.isClick(localMouseX, localMouseY, copyAreaX, copyAreaY, copyAreaW, copyAreaH);
        copyHoverAnimation.run(hovering ? 1.0 : 0.0);
        copyHoverAnimation.update();

        float progress = MathHelper.clamp((float) copyHoverAnimation.getValue(), 0f, 1f);
        if (progress < 1f) {
            renderExpName(matrix, font, displayName, x, y, maxWidth, a * (1f - progress));
        }
        if (progress > 0f) {
            renderExpName(matrix, font, COPY_LABEL, x, y, maxWidth, a * progress);
        }
    }

    private void renderExpName(Matrix4f matrix, MsdfFont font, String name, float x, float y, float maxWidth, float a) {
        renderExpName(matrix, font, name, x, y, maxWidth, a, 13f);
    }

    private void renderExpName(Matrix4f matrix, MsdfFont font, String name, float x, float y,
                               float maxWidth, float a, float size) {
        if (name.isEmpty() || maxWidth <= 0f) return;

        int textColor = HelperElements.themedText(MathHelper.clamp(Math.round(255f * a), 0, 255));
        float fullWidth = expTextWidth(font, name, size);
        if (fullWidth <= maxWidth) {
            renderExpText(matrix, font, name, x, y, size, textColor);
            return;
        }

        String truncated = truncateExpText(font, name, size, maxWidth);
        float truncatedWidth = expTextWidth(font, truncated, size);
        if (truncated.isEmpty() || truncatedWidth <= 0f) return;

        Builder.TEXT_BUILDER
                .font(font)
                .text(truncated)
                .color(textColor)
                .size(size)
                .thickness(0.05f)
                .build()
                .renderHorizontalGradientExact(matrix, x, y, textColor, textColor & 0x00FFFFFF);
    }

    private void renderExpText(Matrix4f matrix, MsdfFont font, String text, float x, float y, float size, int color) {
        Builder.TEXT_BUILDER
                .font(font)
                .text(text)
                .color(color)
                .size(size)
                .thickness(0.05f)
                .build()
                .renderExact(matrix, x, y);
    }

    private void renderExpIcon(RenderEvent.Draw2D event, String texture, float x, float textY, float lineHeight, int color) {
        float y = textY + lineHeight / EXP_HALF_DIVISOR - EXP_ICON_SIZE / EXP_HALF_DIVISOR;
        renderExpIconAt(event, texture, x, y, EXP_ICON_SIZE, color);
    }

    private void renderExpIconAt(RenderEvent.Draw2D event, String texture, float x, float y, float size, int color) {
        Builder.TEXTURE_BUILDER
                .size(new SizeState(size, size))
                .texture(0f, 0f, 1f, 1f, ClientTexture.of(texture))
                .color(new QuadColorState(color))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), x, y);
    }

    private void renderNursultan(RenderEvent.Draw2D event, LivingEntity target, float a, boolean chatOpen, int mouseX, int mouseY) {
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        float panelW = NURSULTAN_PANEL_W;
        float panelH = NURSULTAN_PANEL_H;
        this.width = panelW;
        this.height = panelH;
        draggableHandler.setWidth(panelW);
        draggableHandler.setHeight(panelH);
        beginHoverRects();
        addHoverRect(x, y, panelW, panelH);

        float headSize = NURSULTAN_HEAD_SIZE;
        float headSpacing = NURSULTAN_HEAD_SPACING;
        float rightPad = 5f;

        float headX = x + headSpacing;
        float headY = y + (panelH - headSize) * 0.5f;
        float dt = Math.max(0.001f, mc.getRenderTickCounter().getDynamicDeltaTicks());

        renderPanelBackground(event, x, y, panelW, panelH, a, headX, headY, headSize);

        ScissorUtil.enableContext(event.getContext(), x, y, panelW - 2, panelH);
        try {
            renderHead(event, target, headX, headY, headSize, 5, a, dt);
            renderNursultanBarsAndText(event, target, x, y, panelW, panelH, a, hpTextValue, headX, headSize, rightPad, chatOpen, mouseX, mouseY);
        } finally {
            ScissorUtil.disableContext(event.getContext());
        }

        renderArmorRow(event, target, x, y, panelW, a);
    }

    private void renderDanq(RenderEvent.Draw2D event, LivingEntity target, float a, boolean chatOpen, int mouseX, int mouseY) {
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        float panelW = DANQ_PANEL_W;
        float panelH = DANQ_PANEL_H;
        this.width = panelW;
        this.height = panelH;
        draggableHandler.setWidth(panelW);
        draggableHandler.setHeight(panelH);
        beginHoverRects();
        addHoverRect(x, y, panelW, panelH);

        float headSize = DANQ_HEAD_SIZE;
        float headSpacing = DANQ_HEAD_SPACING;

        float headX = x + headSpacing;
        float headY = y + (panelH - headSize) * 0.5f;
        float dt = Math.max(0.001f, mc.getRenderTickCounter().getDynamicDeltaTicks());

        renderPanelBackground(event, x, y, panelW, panelH, a, headX, headY, headSize);

        ScissorUtil.enableContext(event.getContext(), x, y, panelW - 2, panelH);
        try {
            renderHead(event, target, headX, headY, headSize, 4, a, dt);
            renderDanqContent(event, target, x, y, panelW, panelH, a, headX, headSize, chatOpen, mouseX, mouseY);
        } finally {
            ScissorUtil.disableContext(event.getContext());
        }
    }

    private void renderDanqContent(RenderEvent.Draw2D event, LivingEntity target, float x, float y, float panelW, float panelH, float a, float headX, float headSize, boolean chatOpen, int mouseX, int mouseY) {
        float textLeftX = headX + headSize + 3f;
        float nameX = textLeftX;
        float nameY = y + 5f;
        float nameSize = 7.5f;

        renderName(event, target, nameX, nameY, nameSize, a, chatOpen, mouseX, mouseY, -1f);

        float health = PlayerHelper.getHealth(target);
        String hpText = String.valueOf(Math.round(health));
        float hpSize = 7f;
        float hpTextW = Builder.INTER.get().getWidth(hpText, hpSize);

        float heartSize = 7f;
        float heartGap = 2f;
        float rightPad = 5f;

        float hpBlockW = heartSize + heartGap + hpTextW;
        float hpBlockX = x + panelW - rightPad - hpBlockW;
        float hpY = y + panelH - 13f;

        int heartColor = ColorUtils.setAlpha(ColorUtils.rgb(255, 85, 85), (int) (255 * a));
        Builder.RECTANGLE_BUILDER
                .radius(new QuadRadiusState(1.5f))
                .color(new QuadColorState(heartColor))
                .size(new SizeState(heartSize, heartSize))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), hpBlockX, hpY);

        int hpColor = HelperElements.themedText((int) (255 * a));
        Builder.TEXT_BUILDER
                .font(Builder.INTER.get())
                .text(hpText)
                .color(hpColor)
                .size(hpSize)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), hpBlockX + heartSize + heartGap, hpY);
    }

    private void renderRockstar(RenderEvent.Draw2D event, LivingEntity target, float a, boolean chatOpen, int mouseX, int mouseY) {
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        float panelW = ROCKSTAR_PANEL_W;
        float baseH = ROCKSTAR_BASE_H;
        List<ItemStack> armor = collectArmorItems(target);
        boolean showArmor = armorStack.getEnabled() && !armor.isEmpty();

        ItemStack mainHand = target.getMainHandStack();
        ItemStack offHand = target.getOffHandStack();
        boolean showHands = armorStack.getEnabled() && (!mainHand.isEmpty() || !offHand.isEmpty());

        float armorRowY = 0f;
        float handsRowY = 0f;
        float cursorY = y + baseH;
        if (showArmor || showHands) {
            cursorY -= 2f;
        }

        if (showArmor) {
            cursorY += ROCKSTAR_SECTION_GAP;
            armorRowY = cursorY;
            cursorY += ROCKSTAR_ARMOR_ROW_H;
        }

        if (showHands) {
            cursorY += showArmor ? ROCKSTAR_HAND_GAP : ROCKSTAR_SECTION_GAP;
            handsRowY = cursorY - 1f;
            cursorY += ROCKSTAR_HAND_ROW_H;
        }

        float totalH = cursorY - y;
        this.width = panelW;
        this.height = totalH;
        draggableHandler.setWidth(panelW);
        draggableHandler.setHeight(totalH);
        beginHoverRects();
        addHoverRect(x, y, panelW, baseH);

        float headSize = ROCKSTAR_HEAD_SIZE;
        float headSpacing = ROCKSTAR_HEAD_SPACING;
        float rightPad = 5f;

        float headX = x + headSpacing;
        float headY = y + (baseH - headSize) * 0.5f;
        float dt = Math.max(0.001f, mc.getRenderTickCounter().getDynamicDeltaTicks());

        renderPanelBackground(event, x, y, panelW, baseH, a, headX, headY, headSize);

        ScissorUtil.enableContext(event.getContext(), x, y, panelW - 2, baseH);
        try {
            renderHead(event, target, headX, headY, headSize, 4, a, dt);
            renderRockstarBarsAndText(event, target, x, y, panelW, baseH, a, (float) rockstarHpTextAnim.getValue(), headX, headSize, rightPad, chatOpen, mouseX, mouseY);
        } finally {
            ScissorUtil.disableContext(event.getContext());
        }

        if (showArmor) {
            renderArmorDurabilityRow(event, armor, x, armorRowY, panelW, a);
        }
        if (showHands) {
            renderHandsRow(event, mainHand, offHand, x, handsRowY, panelW, a);
        }
    }


    private void renderHead(RenderEvent.Draw2D event, LivingEntity target, float headX, float headY, float headSize, float radius, float a, float dt) {
        if (target.hurtTime > 0) headHitAnim = 1f;
        else headHitAnim = Math.max(0f, headHitAnim - 0.28f * dt);

        float e = 1f - (float) Math.cos(Math.min(1f, headHitAnim) * (float) Math.PI * 0.5f);
        int baseColor = 0xFFFFFFFF;
        int hitColor = 0xFFFF0000;
        int mixed = ColorUtils.interpolate(hitColor, baseColor, e);
        int mixedWithAlpha = ColorUtils.setAlpha(mixed, (int) (255 * a));

        if (target instanceof AbstractClientPlayerEntity player) {
            DrawUtil.renderHeadTexture(
                    event.getContext(),
                    headX, headY,
                    headSize, headSize,
                    radius,
                    0.125f, 0.125f,
                    0.125f, 0.125f,
                    player.getSkin().body().texturePath(),
                    new Color(mixedWithAlpha, true),
                    1.0f
            );
        } else {
            Builder.TEXTURE_BUILDER
                    .size(new SizeState(headSize, headSize))
                    .radius(new QuadRadiusState(radius))
                    .texture(0f, 0f, 1f, 1f, ClientTexture.of("images/world/entity.png"))
                    .color(new QuadColorState(new Color(mixedWithAlpha, true)))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), headX, headY);
        }
    }

    private void renderNursultanBarsAndText(RenderEvent.Draw2D event, LivingEntity target, float x, float y, float panelW, float panelH, float a, float health, float headX, float headSize, float rightPad, boolean chatOpen, int mouseX, int mouseY) {
        float textLeftX = headX + headSize + 2;
        float nameX = textLeftX + 1f;
        float nameY = y + 5f;

        renderName(event, target, nameX, nameY, 8f, a, chatOpen, mouseX, mouseY, -1f);

        float barY = y + panelH - 12;
        float barW = Math.max(8f, panelW - (textLeftX - x) - rightPad);
        float barH = 7.5f;
        float filledW = barW * animationValue;

        Builder.RECTANGLE_BUILDER
                .radius(new QuadRadiusState(2f))
                .color(new QuadColorState(getBossBarColor(false, a, target)))
                .size(new SizeState(barW, barH + 0.5f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textLeftX, barY);

        if (filledW > 0.5f) {
            int c1raw = darkenRgb(getBarColorRaw(target), 0.30f);
            int c2raw = getBarColorRaw2(target);
            int fillAlpha = (int) (255 * a);
            int barLeft = ColorUtils.setAlpha(c1raw, fillAlpha);
            int barRight = ColorUtils.setAlpha(c2raw, fillAlpha);
            Builder.RECTANGLE_BUILDER
                    .radius(new QuadRadiusState(2f))
                    .color(new QuadColorState(barLeft, barLeft, barRight, barRight))
                    .size(new SizeState(filledW, barH))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textLeftX, barY);
        }

        float absorbW = barW * MathHelper.clamp((float) rockstarAbsorbAnim.getValue(), 0f, 1f);
        if (absorbW > 0.5f) {
            int absorbColor = ColorUtils.setAlpha(ColorUtils.rgb(255, 215, 0), (int) (235 * a));
            Builder.GLOW_BUILDER
                    .radius(new QuadRadiusState(1.6f))
                    .color(new QuadColorState(ColorUtils.setAlpha(ColorUtils.rgb(255, 215, 0), (int) (90 * a))))
                    .size(new SizeState(absorbW, barH))
                    .softness(0.9f)
                    .glowRadius(2.4f)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textLeftX + barW - absorbW, barY);
            Builder.RECTANGLE_BUILDER
                    .radius(new QuadRadiusState(2f))
                    .color(new QuadColorState(absorbColor))
                    .size(new SizeState(absorbW, barH))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textLeftX + barW - absorbW, barY);
        }

        String hpValue = String.format(Locale.ROOT, "%.1f", health);
        String hpText = "HP: " + hpValue;

        float hpSize = 6.5f;
        float hpY = nameY + 12.5f;

        Builder.TEXT_BUILDER
                .font(Builder.INTER.get())
                .text(hpText)
                .color(HelperElements.themedText((int) (255 * a)))
                .size(hpSize)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, hpY);
    }

    private void renderRockstarBarsAndText(RenderEvent.Draw2D event, LivingEntity target, float x, float y, float panelW, float panelH, float a, float health, float headX, float headSize, float rightPad, boolean chatOpen, int mouseX, int mouseY) {
        float textLeftX = headX + headSize + 2f;
        float nameX = textLeftX + 1f;
        float nameY = y + 5f;

        String hpValue = String.format(Locale.ROOT, "%.1f", health);
        float hpSize = 6.5f;
        float hpTextW = Builder.INTER.get().getWidth(hpValue, hpSize);
        float hpTextX = x + panelW - rightPad - hpTextW - 2f;
        float hpTextY = nameY + 1f;

        float clipW = Math.max(2f, hpTextX - nameX - 3f);
        ScissorUtil.enableContext(event.getContext(), nameX, y, clipW, panelH);
        try {
            renderName(event, target, nameX, nameY, 8f, a, chatOpen, mouseX, mouseY, clipW);
        } finally {
            ScissorUtil.disableContext(event.getContext());
        }

        float barY = y + panelH - 11.5f;
        float barW = Math.max(8f, panelW - (textLeftX - x) - rightPad);
        float barH = 4.5f;
        float filledW = barW * MathHelper.clamp((float) rockstarHealthAnim.getValue(), 0f, 1f);
        float absorbW = barW * MathHelper.clamp((float) rockstarAbsorbAnim.getValue(), 0f, 1f);

        Builder.RECTANGLE_BUILDER
                .radius(new QuadRadiusState(1.5f))
                .color(new QuadColorState(getBossBarColor(false, a, target)))
                .size(new SizeState(barW, barH + 0.25f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textLeftX, barY);

        if (filledW > 0.5f) {
            int c1raw = darkenRgb(getBarColorRaw(target), 0.30f);
            int c2raw = getBarColorRaw2(target);
            int barLeft = c1raw;
            int barRight = c2raw;

            int glowLeft = ColorUtils.setAlpha(barLeft, (int) (90 * a));
            int glowRight = ColorUtils.setAlpha(barRight, (int) (90 * a));
            Builder.GLOW_BUILDER
                    .radius(new QuadRadiusState(1.2f))
                    .color(new QuadColorState(glowLeft, glowLeft, glowRight, glowRight))
                    .size(new SizeState(filledW, barH))
                    .softness(0.9f)
                    .glowRadius(2f)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textLeftX, barY);

            int fillLeft = ColorUtils.setAlpha(barLeft, (int) (255 * a));
            int fillRight = ColorUtils.setAlpha(barRight, (int) (255 * a));
            Builder.RECTANGLE_BUILDER
                    .radius(new QuadRadiusState(1.5f))
                    .color(new QuadColorState(fillLeft, fillLeft, fillRight, fillRight))
                    .size(new SizeState(filledW, barH))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textLeftX, barY);
        }

        if (absorbW > 0.5f) {
            int absorbColor = ColorUtils.setAlpha(ColorUtils.rgb(255, 215, 0), (int) (235 * a));
            Builder.GLOW_BUILDER
                    .radius(new QuadRadiusState(1.2f))
                    .color(new QuadColorState(ColorUtils.setAlpha(ColorUtils.rgb(255, 215, 0), (int) (90 * a))))
                    .size(new SizeState(absorbW, barH))
                    .softness(0.9f)
                    .glowRadius(2f)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textLeftX + barW - absorbW, barY);
            Builder.RECTANGLE_BUILDER
                    .radius(new QuadRadiusState(1.5f))
                    .color(new QuadColorState(absorbColor))
                    .size(new SizeState(absorbW, barH))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textLeftX + barW - absorbW, barY);
        }

        float hpTextH = Builder.INTER.get().getHeight(hpValue, hpSize);
        float hpCenterX = hpTextX + hpTextW * 0.5f;
        float hpCenterY = hpTextY + hpTextH * 0.5f;
        float pulseScale = 1f + 0.15f * MathHelper.clamp(rockstarHpPulse, 0f, 1f);

        MathUtil.scaleStart(event.getContext().getMatrices(), hpCenterX, hpCenterY, pulseScale);
        Builder.TEXT_BUILDER
                .font(Builder.INTER.get())
                .text(hpValue)
                .color(ColorUtils.setAlpha(Interface.INSTANCE != null ? Interface.INSTANCE.getFirstColor() : -1, (int) (255 * a)))
                .size(hpSize)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), hpTextX, hpTextY);
        MathUtil.scaleEnd(event.getContext().getMatrices());
    }

    private void renderName(RenderEvent.Draw2D event, LivingEntity target, float nameX, float nameY, float size, float a, boolean chatOpen, int mouseX, int mouseY, float clipW) {
        String displayName = target.getName().getString();
        String realName = target instanceof PlayerEntity player ? player.getGameProfile().name() : target.getName().getString();

        float displayWidth = Builder.INTER.get().getWidth(displayName, size);
        float copyWidth = Builder.INTER.get().getWidth(COPY_LABEL, size);
        float areaW = Math.max(Math.max(displayWidth, copyWidth), 1f);
        if (clipW > 0f) {
            areaW = Math.max(1f, Math.min(areaW, clipW));
        }
        float areaH = Builder.INTER.get().getHeight(COPY_LABEL, size);

        copyAreaX = nameX;
        copyAreaY = nameY;
        copyAreaW = areaW;
        copyAreaH = areaH;

        boolean eligible = target instanceof PlayerEntity;
        copyRealName = eligible ? realName : "";
        canCopyName = eligible && !realName.isEmpty();

        float localMouseX = toHudLocalX(mouseX);
        float localMouseY = toHudLocalY(mouseY);
        boolean hovering = chatOpen && canCopyName
                && MouseClick.isClick(localMouseX, localMouseY, copyAreaX, copyAreaY, copyAreaW, copyAreaH);

        copyHoverAnimation.run(hovering ? 1.0 : 0.0);
        copyHoverAnimation.update();

        float progress = (float) copyHoverAnimation.getValue();
        int displayAlpha = MathHelper.clamp((int) Math.round(255f * a * (1f - progress)), 0, 255);
        int copyAlpha = MathHelper.clamp((int) Math.round(255f * a * progress), 0, 255);

        if (displayAlpha > 0) {
            Builder.TEXT_BUILDER
                    .font(Builder.INTER.get())
                    .text(displayName)
                    .color(HelperElements.themedText(displayAlpha))
                    .size(size)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, nameY);
        }

        if (copyAlpha > 0) {
            Builder.TEXT_BUILDER
                    .font(Builder.INTER.get())
                    .text(COPY_LABEL)
                    .color(HelperElements.themedText(copyAlpha))
                    .size(size)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX, nameY);
        }
    }

    private void resetCopyInteraction() {
        canCopyName = false;
        copyRealName = "";
        copyAreaX = copyAreaY = copyAreaW = copyAreaH = 0f;
        copyHoverAnimation.run(0.0);
        copyHoverAnimation.update();
    }

    private void beginHoverRects() {
        hoverRects.clear();
    }

    private void addHoverRect(float x, float y, float w, float h) {
        if (w <= 0f || h <= 0f) return;
        hoverRects.add(new HoverRect(x, y, w, h));
    }

    private boolean isHovering(float mouseX, float mouseY) {
        float localMouseX = toHudLocalX(mouseX);
        float localMouseY = toHudLocalY(mouseY);
        if (hoverRects.isEmpty()) {
            return MouseClick.isClick(localMouseX, localMouseY,
                    draggableHandler.getX(), draggableHandler.getY(), width, height);
        }
        for (HoverRect rect : hoverRects) {
            if (rect.contains(localMouseX, localMouseY)) {
                return true;
            }
        }
        return false;
    }

    private float toHudLocalX(float screenX) {
        float appliedScale = Math.max(1.0E-4f, draggableHandler.getScale());
        float anchorX = draggableHandler.getX();
        return anchorX + (screenX - anchorX) / appliedScale;
    }

    private float toHudLocalY(float screenY) {
        float appliedScale = Math.max(1.0E-4f, draggableHandler.getScale());
        float anchorY = draggableHandler.getY();
        return anchorY + (screenY - anchorY) / appliedScale;
    }

    private void renderArmorRow(RenderEvent.Draw2D event, LivingEntity target, float x, float y, float panelW, float a) {
        if (!armorStack.getEnabled()) return;

        ArrayList<ItemStack> armor = new ArrayList<>(4);
        armor.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
        armor.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
        armor.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
        armor.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
        armor.removeIf(ItemStack::isEmpty);

        if (armor.isEmpty()) return;

        float baseScale = 0.66f;
        float slot = 17f * baseScale;
        float spacing = 1f;
        float pad = 4f;
        float rowH = 14f;
        float gap = 1f;

        float contentW = armor.size() * slot + Math.max(0, armor.size() - 1) * spacing;
        float bgW = contentW + pad * 2f;

        float bx = x + (panelW - bgW) * 0.5f;
        float by = y - gap - rowH - 3;

        addHoverRect(bx, by, bgW, rowH);

        HelperElements.rectElements(event.getContext(), bx, by, bgW, rowH, a);

        float ix = bx + pad;
        float iy = by + (rowH - slot) * 0.5f + 0.5f;

        double animValue = alphaAnimation.getValue();
        float pulse = (float) (0.95f + 0.05f * Math.sin(animValue * Math.PI));
        float itemScale = baseScale * (float) animValue * pulse;

        for (ItemStack st : armor) {
            var ms = event.getContext().getMatrices();
            ms.pushMatrix();
            ms.translate(ix + 8f * baseScale, iy + 8f * baseScale);
            ms.scale(itemScale, itemScale);
            ms.translate(-8f, -8f);
            event.getContext().drawItem(st, 0, 0);
            ms.popMatrix();

            ix += slot + spacing;
        }
    }

    private void renderArmorDurabilityRow(RenderEvent.Draw2D event, List<ItemStack> armor, float x, float y, float panelW, float a) {
        if (armor.isEmpty()) return;

        float textSize = 6.5f;
        float boxH = ROCKSTAR_ARMOR_ROW_H;
        float pad = 4f;
        float spacing = 2f;

        float sampleW = Builder.INTER.get().getWidth("100%", textSize);
        float boxW = Math.max(18f, sampleW + pad * 2f);
        float rowW = armor.size() * boxW + Math.max(0, armor.size() - 1) * spacing;
        float startX = x + (panelW - rowW) * 0.5f;

        float textH = Builder.INTER.get().getHeight("100%", textSize);
        int bgColor = HelperElements.themedPanel(26, 28, 33, getBackgroundAlpha(a));
        int textColor = HelperElements.themedText((int) (255 * a));

        float bx = startX;
        for (ItemStack st : armor) {
            addHoverRect(bx, y, boxW, boxH);

            Builder.RECTANGLE_BUILDER
                    .radius(new QuadRadiusState(2f))
                    .color(new QuadColorState(bgColor))
                    .size(new SizeState(boxW, boxH))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), bx, y);

            String perc = String.format(Locale.ROOT, "%d%%", Math.round(getDurability01(st) * 100f));
            float textW = Builder.INTER.get().getWidth(perc, textSize);
            float textX = bx + (boxW - textW) * 0.5f;
            float textY = y + (boxH - textH) * 0.5f - 0.5f;

            Builder.TEXT_BUILDER
                    .font(Builder.INTER.get())
                    .text(perc)
                    .color(textColor)
                    .size(textSize)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textX, textY);

            bx += boxW + spacing;
        }
    }

    private void renderHandsRow(RenderEvent.Draw2D event, ItemStack mainHand, ItemStack offHand, float x, float y, float panelW, float a) {
        if (mainHand.isEmpty() && offHand.isEmpty()) return;

        float box = ROCKSTAR_HAND_ROW_H;
        float spacing = 4f;
        List<ItemStack> hands = new ArrayList<>(2);
        if (!offHand.isEmpty()) hands.add(offHand);
        if (!mainHand.isEmpty()) hands.add(mainHand);
        if (hands.isEmpty()) return;

        float rowW = hands.size() * box + Math.max(0, hands.size() - 1) * spacing;
        float startX = x + (panelW - rowW) * 0.5f;

        int bgColor = HelperElements.themedPanel(26, 28, 33, getBackgroundAlpha(a));
        double animValue = alphaAnimation.getValue();
        float pulse = (float) (0.95f + 0.05f * Math.sin(animValue * Math.PI));
        float scale = 0.75f * (float) animValue * pulse;

        float cursorX = startX;
        for (ItemStack stack : hands) {
            addHoverRect(cursorX, y, box, box);
            renderHandBox(event, stack, cursorX, y, box, bgColor, scale);
            cursorX += box + spacing;
        }
    }

    private void renderHandBox(RenderEvent.Draw2D event, ItemStack stack, float x, float y, float box, int bgColor, float scale) {
        Builder.RECTANGLE_BUILDER
                .radius(new QuadRadiusState(2f))
                .color(new QuadColorState(bgColor))
                .size(new SizeState(box, box))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), x, y);

        if (stack.isEmpty() || scale <= 0.01f) return;

        var ms = event.getContext().getMatrices();
        ms.pushMatrix();
        float centerX = x + box * 0.5f;
        float centerY = y + box * 0.5f;
        ms.translate(centerX, centerY);
        ms.scale(scale, scale);
        ms.translate(-8f, -8f);
        event.getContext().drawItem(stack, 0, 0);
        ms.popMatrix();
    }

    private List<ItemStack> collectArmorItems(LivingEntity target) {
        ArrayList<ItemStack> armor = new ArrayList<>(4);
        armor.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
        armor.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
        armor.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
        armor.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
        armor.removeIf(ItemStack::isEmpty);
        return armor;
    }

    private List<ItemStack> collectExpEquipment(LivingEntity target) {
        ArrayList<ItemStack> equipment = new ArrayList<>(6);
        equipment.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
        equipment.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
        equipment.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
        equipment.add(target.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
        equipment.removeIf(ItemStack::isEmpty);

        ItemStack mainHand = target.getMainHandStack();
        ItemStack offHand = target.getOffHandStack();
        if (!mainHand.isEmpty()) equipment.add(mainHand);
        if (!offHand.isEmpty()) equipment.add(offHand);
        return equipment;
    }

    private static float getDurability01(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) return 1f;
        int max = stack.getMaxDamage();
        int dmg = stack.getDamage();
        if (max <= 0) return 1f;
        float left = (max - dmg) / (float) max;
        return MathHelper.clamp(left, 0f, 1f);
    }

    private LivingEntity resolveTarget() {
        LivingEntity kaTarget = KillAura.INSTANCE.getTarget();
        if (kaTarget instanceof ArmorStandEntity) {
            kaTarget = null;
        }

        LivingEntity rayTraced = null;
        if (rayTrace.getEnabled()) {
            HitResult crosshairTarget = mc.crosshairTarget;
            if (crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity playerEntity) {
                rayTraced = playerEntity;
            }
        }

        LivingEntity target = kaTarget;
        if (target == null && rayTraced != null) {
            target = rayTraced;
        }
        if (target instanceof ArmorStandEntity) {
            target = null;
        }
        return target;
    }

    private LivingEntity resolveExpTarget() {
        LivingEntity auraTarget = KillAura.INSTANCE != null ? KillAura.INSTANCE.getTarget() : null;
        if (auraTarget != null && !auraTarget.getName().getString().isEmpty()) {
            return auraTarget;
        }

        if (rayTrace.getEnabled()) {
            LivingEntity raycastTarget = raycastExpTarget();
            if (raycastTarget != null && !raycastTarget.getName().getString().isEmpty()) {
                return raycastTarget;
            }
        }
        return null;
    }

    private LivingEntity raycastExpTarget() {
        if (mc.player == null || mc.world == null) return null;
        Entity camera = mc.getCameraEntity();
        if (camera == null) return null;

        double range = mc.player.getEntityInteractionRange();
        Vec3d start = camera.getCameraPosVec(1f);
        Vec3d direction = camera.getRotationVec(1f);
        Vec3d end = start.add(direction.multiply(range));
        List<LivingEntity> candidates = mc.world.getEntitiesByClass(
                LivingEntity.class,
                camera.getBoundingBox().stretch(direction.multiply(range)).expand(1d),
                entity -> entity != mc.player && entity.isAlive() && !entity.isSpectator()
        );

        LivingEntity retained = raycastExpSingle(expTarget, 0.7d, start, end);
        if (retained != null) return retained;
        if (candidates.size() > 2) return null;

        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            var hit = candidate.getBoundingBox().expand(0.5d).raycast(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.squaredDistanceTo(hit.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    private LivingEntity raycastExpSingle(LivingEntity target, double expand, Vec3d start, Vec3d end) {
        if (target == null || !target.isAlive()) return null;
        return target.getBoundingBox().expand(expand).raycast(start, end).isPresent() ? target : null;
    }

    private float getExpHealth(LivingEntity target) {
        if (target == null) return 20f;
        try {
            if (target instanceof PlayerEntity player && player != mc.player && mc.world != null) {
                var scoreboard = mc.world.getScoreboard();
                ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
                if (objective != null) {
                    ReadableScoreboardScore score = scoreboard.getScore(player, objective);
                    if (score != null && score.getScore() != 0) {
                        return score.getScore();
                    }
                }
            }
            return target.getHealth();
        } catch (Throwable ignored) {
            return target.getHealth();
        }
    }

    private float getExpEffectiveMaxHealth(LivingEntity target, float health) {
        float maxHealth = target.getMaxHealth();
        return health > maxHealth + 20f ? maxHealth : Math.max(maxHealth, health);
    }

    private boolean shouldShowExpAbsorption(LivingEntity target) {
        return target != null
                && !isExpFunTime()
                && target.getAbsorptionAmount() > 0f
                && target.getMaxAbsorption() <= 20f;
    }

    private boolean isExpFunTime() {
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getBrand() == null) return false;
        return mc.getNetworkHandler().getBrand().toLowerCase(Locale.ROOT).contains("botfilter");
    }

    private String getExpDisplayName(LivingEntity target) {
        if (target == null) return "";
        String name = Formatting.strip(target.getName().getString());
        if (name == null) name = "";
        if (NameProtect.INSTANCE != null && NameProtect.INSTANCE.isEnabled()) {
            String protectedName = NameProtect.INSTANCE.patchName(name);
            if (protectedName != null) name = protectedName;
        }
        return name;
    }

    private static String truncateExpText(MsdfFont font, String text, float size, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0f) return "";
        if (expTextWidth(font, text, size) <= maxWidth) return text;

        int low = 0;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (expTextWidth(font, text.substring(0, middle), size) <= maxWidth) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best <= 0 ? "" : text.substring(0, best);
    }

    private static float expTextWidth(MsdfFont font, String text, float size) {
        return font.getAdvanceWidth(text, size);
    }

    private static String formatExpValue(float value, boolean unknown) {
        if (unknown) return "Unknown";
        double rounded = Math.round((Math.round(value / 0.5d) * 0.5d) * 100d) / 100d;
        return String.valueOf(rounded);
    }

    private static float expFontHeight(MsdfFont font, float size) {
        return font.getMetrics().lineHeight() * size;
    }

    private static int expAlpha(int color, float alpha) {
        int sourceAlpha = ColorUtils.alpha(color);
        int resultAlpha = MathHelper.clamp(Math.round(sourceAlpha * MathHelper.clamp(alpha, 0f, 1f)), 0, 255);
        return ColorUtils.setAlpha(color, resultAlpha);
    }

    private void renderParticles(RenderEvent.Draw2D event, float a) {
        if (!particleTargetHud.getEnabled()) return;

        final long lifetimeMs = 2000L;
        long now = System.currentTimeMillis();
        headParticles.removeIf(p -> now - p.time > lifetimeMs);

        int clientColor = Interface.INSTANCE.getMainColor(4);

        for (HeadParticle p : headParticles) {
            p.update();
            float lifeK = 1.0f - (float) (now - p.time) / (float) lifetimeMs;
            if (lifeK < 0f) lifeK = 0f;

            float alphaEase = p.alpha * lifeK * lifeK * a;
            int col = ColorUtils.setAlpha(clientColor, (int) (255.0f * alphaEase));

            DrawUtil.drawFilledCircle(
                    event.getContext().getMatrices(),
                    (float) p.pos.x, (float) p.pos.y,
                    1.5f,
                    col
            );
        }
    }

    private Vec3d getHeadCenterForMode() {
        float headSize;
        float panelH;
        float headSpacing;
        if (isDanq()) {
            headSize = DANQ_HEAD_SIZE;
            panelH = DANQ_PANEL_H;
            headSpacing = DANQ_HEAD_SPACING;
        } else if (isRockstar()) {
            headSize = ROCKSTAR_HEAD_SIZE;
            panelH = ROCKSTAR_BASE_H;
            headSpacing = ROCKSTAR_HEAD_SPACING;
        } else if (isExpa()) {
            panelH = EXP_MINIMAL_PANEL_H;
            headSize = EXP_COMPACT_HEAD_SIZE;
            headSpacing = EXP_COMPACT_HEAD_SPACING;
        } else {
            headSize = NURSULTAN_HEAD_SIZE;
            panelH = NURSULTAN_PANEL_H;
            headSpacing = NURSULTAN_HEAD_SPACING;
        }
        float cx = draggableHandler.getX() + headSpacing + headSize * 0.5f;
        float cy = draggableHandler.getY() + (panelH - headSize) * 0.5f + headSize * 0.5f;
        return new Vec3d(cx, cy, 0.0);
    }

    private boolean isRockstar() {
        return mode.isSelected("Rockstar");
    }

    private boolean isDanq() {
        return mode.isSelected("Danq");
    }

    private boolean isExpa() {
        return mode.isSelected("exp") || mode.isSelected("Expa");
    }


    private int getBossBarColor(boolean filled, float a, LivingEntity target) {
        int base;
        if (colorBar.isSelected("Состояние здоровья")) {
            base = PlayerHelper.getHealthColorHSV(target);
        } else {
            base = Interface.INSTANCE.getFirstColor();
        }

        int alpha = filled ? (int) (255 * a) : (int) (50 * a);
        alpha = Math.max(0, Math.min(255, alpha));
        return ColorUtils.setAlpha(base, alpha);
    }

    private float getBarGradientShift() {
        float time = (System.currentTimeMillis() % 3000L) / 3000f;
        return (float) (Math.sin(time * Math.PI * 2) * 0.5 + 0.5);
    }

    private int getFlowGradient(int c1, int c2, int offsetDeg, int alpha) {
        double time = System.currentTimeMillis() / 5.0;
        float t = (float) ((Math.sin(Math.toRadians(time + offsetDeg)) + 1.0) * 0.5);
        return ColorUtils.setAlpha(ColorUtils.interpolate(c1, c2, t), alpha);
    }

    private int getBarColorRaw(LivingEntity target) {
        if (colorBar.isSelected("Состояние здоровья")) {
            return PlayerHelper.getHealthColorHSV(target);
        }
        return Interface.INSTANCE.getFirstColor();
    }

    private int getBarColorRaw2(LivingEntity target) {
        if (colorBar.isSelected("Состояние здоровья")) {
            return PlayerHelper.getHealthColorHSV(target);
        }
        return Interface.INSTANCE.getSecondColor();
    }

    private static int darkenRgb(int color, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        int a = (color >>> 24) & 0xFF;
        int r = Math.round(((color >>> 16) & 0xFF) * f);
        int g = Math.round(((color >>> 8) & 0xFF) * f);
        int b = Math.round((color & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderPanelBackground(RenderEvent.Draw2D event, float x, float y, float panelW, float panelH, float showProgress, float headX, float headY, float headSize) {
        float show = MathHelper.clamp(showProgress, 0f, 1f);
        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext());
        Interface ui = Interface.INSTANCE;

        if (ui != null && (ui.isLiquidGlassMode() || ui.isExcellentMode())) {
            HelperElements.rectElements(event.getContext(), x, y, panelW, panelH, show);
            return;
        }

        float radius = getRadius();
        int backgroundAlpha = getBackgroundAlpha(show);

        float blurRadius = Interface.INSTANCE.blurStrength.getValue();
        float blurAlpha = getBlurAlpha(show);

        renderBlurSegment(matrix, x, y, panelW, panelH, new QuadRadiusState(radius), blurRadius, blurAlpha);

        if (backgroundAlpha > 0) {
            int darkColor = HelperElements.themedPanel(18, 20, 24, backgroundAlpha);
            int lightColor = HelperElements.themedPanel(32, 34, 40, backgroundAlpha);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(panelW, panelH))
                    .color(new QuadColorState(
                            darkColor,   // top-left
                            lightColor,  // top-right
                            lightColor,  // bottom-right
                            darkColor    // bottom-left
                    ))
                    .radius(new QuadRadiusState(radius))
                    .build()
                    .render(matrix, x, y);
        }
    }

    private void renderBlurSegment(Matrix4f matrix, float sx, float sy, float sw, float sh, QuadRadiusState radiusState, float blurRadius, float blurAlpha) {
        if (sw <= 0f || sh <= 0f || blurRadius <= 0f || blurAlpha <= 0f) {
            return;
        }

        Builder.BLUR_BUILDER
                .size(new SizeState(sw, sh))
                .radius(radiusState)
                .blurRadius(blurRadius)
                .alpha(blurAlpha)
                .build()
                .render(matrix, sx, sy);
    }
    
    private int getBackgroundAlpha(float showProgress) {
        int base = 150;
        base = MathHelper.clamp(base, 0, 255);
        float clamped = MathHelper.clamp(showProgress, 0f, 1f);
        int alpha = Math.round(base * clamped);
        return MathHelper.clamp(alpha, 0, 255);
    }

    private float getBlurAlpha(float showProgress) {
        return MathHelper.clamp(showProgress, 0f, 1f);
    }

    private float getRadius() {
        return mode.isSelected("Nursultan") ? 6f : 4f;
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    @Override
    public float getPanelAlpha() {
        CompactAnimation animation = isExpa() ? expFadeAnimation : alphaAnimation;
        return MathHelper.clamp((float) animation.getValue(), 0f, 1f);
    }

    private record ExpRenderTransform(float x, float y, float scale) {
        private float mapX(float sourceX) {
            return x + sourceX * scale;
        }

        private float mapY(float sourceY) {
            return y + sourceY * scale;
        }
    }

    private record ExpUv(float u0, float v0, float u1, float v1) {
        private static final ExpUv DEFAULT = new ExpUv(0.125f, 0.125f, 0.25f, 0.25f);
    }

    private record ExpAvatarTexture(Identifier texture, ExpUv uv) {
    }

    private static final class HoverRect {
        private final float x;
        private final float y;
        private final float w;
        private final float h;

        private HoverRect(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        private boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public static class HeadParticle {
        public Vec3d pos;
        public final Vec3d end;
        public final long time;
        public float alpha;

        public HeadParticle(Vec3d pos) {
            this.pos = pos;
            this.time = System.currentTimeMillis();

            double radius;
            double heightOffset;
            double angle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);

            radius = ThreadLocalRandom.current().nextDouble(60.0, 100.0);
            heightOffset = ThreadLocalRandom.current().nextDouble(-50.0, 50.0);

            double offsetX = radius * Math.cos(angle);
            double offsetY = radius * Math.sin(angle);
            this.end = pos.add(offsetX, offsetY, heightOffset);
        }

        public void update() {
            this.alpha = MathUtil.linear(this.alpha, 1.0F, 10.0F);
            this.pos = MathUtil.fast(this.pos, this.end, 0.5F);
        }
    }
}
