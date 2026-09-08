package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.irc.PartyManager;
import dev.client.api.nullcry.helper.client.irc.PartyManager.PartyMemberSnapshot;
import dev.client.api.nullcry.helper.client.irc.PartyManager.PositionSnapshot;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.*;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import lombok.Getter;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Arrows extends Module {

    public Arrows() {
        super("Arrows", ModuleCategory.Render, "Отображает стрелочки к целям");
    }

    CheckBox showInThirdPerson = new CheckBox("Отображать при F5", () -> true).defaultValue(false).register(this);
    Slider radius = new Slider("Радиус", () -> true).set(10f, 100f, 0.1f).defaultValue(50f).register(this);
    Slider radiusArrows = new Slider("Радиус Стрелок", () -> true).set(0, 10, 1).defaultValue(5).register(this);

    SelectElements filter = new SelectElements("Фильтр на", () -> true).set("Игроков", "Мобов", "Животных", "Друзей", "Голых", "Инвизок", "Предметы").defaultValue("Игроков", "Инвизок").register(this);

    SelectElements type = new SelectElements("Отображать", () -> true).set("Никнейм", "Метры").defaultValue("Никнейм", "Метры").register(this);
    ModeElement color = new ModeElement("Цвет", () -> true).set("Клиент", "Свой").defaultValue("Клиент").register(this);
    ColorPicker customColor = new ColorPicker("Выбор цвета", () -> color.isSelected("Свой")).set(-1).register(this);
    CheckBox itemStack = new CheckBox("Отображать стак-предметы", () -> filter.isSelected("Предметы")).defaultValue(false).register(this);
    CheckBox jumpArrows = new CheckBox("Анимация прыганья", () -> true).defaultValue(false).register(this);

    private final List<Target> renderableTargets = new ArrayList<>();

    private static final long FADE_IN_MS = 220L;
    private static final long FADE_OUT_MS = 260L;

    private final java.util.Map<String, FadingTarget> fadingTargets = new java.util.LinkedHashMap<>();
    private long lastFrameTime;

    float animatedYaw;
    float animatedPitch;
    float animationStep;
    float textAnimation;
    float lastYaw;
    float lastPitch;
    float yaw;
    float arrowBounce;

    @Subscribe
    public void onRender2D(RenderEvent.Draw2D draw2D) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        collectTargets();
        updateFadingTargets();


        if (fadingTargets.isEmpty()) {
            return;
        }

        float smoothingFactor = 8;

        if (dev.client.api.compat.InputCompat.movementSideways(mc.player.input) == 0) {
            animatedYaw = MathUtil.fast(animatedYaw, dev.client.api.compat.InputCompat.movementSideways(mc.player.input) * 10, smoothingFactor);
        } else {
            animatedYaw = 0;
        }

        animatedYaw = MathUtil.fast(animatedYaw, (dev.client.api.compat.InputCompat.movementSideways(mc.player.input)) * 10, smoothingFactor);
        animatedPitch = MathUtil.fast(animatedPitch, (dev.client.api.compat.InputCompat.movementForward(mc.player.input)) * 10, smoothingFactor);

        if (jumpArrows.getEnabled()) {
            arrowBounce = (float) (Math.sin(System.currentTimeMillis() % 1000 / 1000.0 * Math.PI * 2) * 5);
        } else {
            arrowBounce = 0;
        }

        yaw = MathUtil.fast(yaw, mc.player.getYaw(), smoothingFactor);
        textAnimation = MathUtil.fast(textAnimation, 1.0f, smoothingFactor);

        float size = 50 + radiusArrows.getValue();
        if (mc.currentScreen instanceof InventoryScreen) {
            size += 80 + radiusArrows.getValue();
        }
        if (MovingUtil.isMoving()) {
            size += 10;
        }
        animationStep = MathUtil.fast(animationStep, size, smoothingFactor);

        if (mc.options.getPerspective() == Perspective.FIRST_PERSON || showInThirdPerson.getEnabled()) {
            boolean renderedAny = false;
            for (FadingTarget fading : fadingTargets.values()) {
                renderArrowAndText(draw2D, fading.target, easeOutQuart(fading.progress));
                renderedAny = true;
            }
            if (renderedAny) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
        }
    }


    private void collectTargets() {
        renderableTargets.clear();
        if (mc.world == null || mc.player == null) {
            return;
        }

        PartyManager partyManager = PartyManager.getInstance();
        boolean partyActive = partyManager.hasParty();
        Set<String> seenPartyNames = new HashSet<>();
        double radiusValue = radius.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) {
                continue;
            }

            boolean isPartyMember = false;
            String partyDisplayName = null;
            String gameName = null;
            boolean isFriend = false;

            if (entity instanceof AbstractClientPlayerEntity player) {
                gameName = player.getGameProfile().name();
                isFriend = handlerClient().getFriendManager().isFriend(gameName);
                PartyMemberSnapshot snapshot = partyManager.getMemberByGameName(gameName).orElse(null);
                if (snapshot != null) {
                    isPartyMember = true;
                    partyDisplayName = resolvePartyDisplayName(snapshot);
                    markSeenPartyMember(seenPartyNames, snapshot);
                }
            }

            double distanceToPlayer = entity.distanceTo(mc.player);
            if (!isPartyMember && distanceToPlayer > radiusValue) {
                continue;
            }

            if (!shouldRenderEntity(entity, isPartyMember)) {
                continue;
            }

            renderableTargets.add(Target.forEntity(entity, gameName, isFriend, isPartyMember, partyDisplayName));
        }

        if (partyActive) {
            long maxAgeMs = 15000L;
            for (PartyMemberSnapshot snapshot : partyManager.getMembersSnapshot()) {
                if (snapshot == null) {
                    continue;
                }
                if (isSelfSnapshot(snapshot)) {
                    continue;
                }
                if (isAlreadyTracked(seenPartyNames, snapshot)) {
                    continue;
                }

                PositionSnapshot positionSnapshot = partyManager.getLatestPosition(snapshot).orElse(null);
                if (positionSnapshot == null || !positionSnapshot.isRecent(maxAgeMs)) {
                    continue;
                }

                boolean friend = snapshot.getGameName() != null && handlerClient().getFriendManager().isFriend(snapshot.getGameName());
                renderableTargets.add(Target.forRemote(snapshot, positionSnapshot, friend));
                markSeenPartyMember(seenPartyNames, snapshot);
            }
        }
    }

    /**
     * Сливает свежий список целей с уже видимыми: новые начинают проявляться,
     * пропавшие остаются в списке и гаснут, пока не дойдут до нуля.
     */
    private void updateFadingTargets() {
        long now = System.currentTimeMillis();
        float delta = lastFrameTime == 0L ? 0f : Math.min(200L, now - lastFrameTime);
        lastFrameTime = now;

        Set<String> present = new HashSet<>();
        for (Target target : renderableTargets) {
            String key = target.key();
            present.add(key);

            FadingTarget fading = fadingTargets.computeIfAbsent(key, ignored -> new FadingTarget());
            fading.target = target;
            fading.progress = Math.min(1f, fading.progress + delta / FADE_IN_MS);
        }

        fadingTargets.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) return false;

            FadingTarget fading = entry.getValue();
            fading.progress -= delta / FADE_OUT_MS;
            return fading.progress <= 0f || fading.target == null;
        });
    }

    private static float easeOutQuart(float x) {
        float inverted = 1f - MathHelper.clamp(x, 0f, 1f);
        return 1f - inverted * inverted * inverted * inverted;
    }

    private static final class FadingTarget {
        private Target target;
        private float progress;
    }

    private void renderArrowAndText(RenderEvent.Draw2D draw2D, Target target, float alpha) {
        if (mc.getEntityRenderDispatcher().camera == null || alpha <= 0.01f) {
            return;
        }

        double tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d cameraPos = mc.getEntityRenderDispatcher().camera.getCameraPos();
        Vec3d relativePos = target.toRelativePosition(tickDelta, cameraPos);

        double x = relativePos.x;
        double z = relativePos.z;

        double cos = MathHelper.cos((float) (yaw * (Math.PI * 2 / 360)));
        double sin = MathHelper.sin((float) (yaw * (Math.PI * 2 / 360)));
        double rotY = -(z * cos - x * sin);
        double rotX = -(x * cos + z * sin);

        float angle = (float) (Math.atan2(rotY, rotX) * 180 / Math.PI);
        double x2 = animationStep * MathHelper.cos((float) Math.toRadians(angle)) + mc.getWindow().getScaledWidth() / 2f;
        double y2 = animationStep * MathHelper.sin((float) Math.toRadians(angle)) + mc.getWindow().getScaledHeight() / 2f;

        x2 += animatedYaw;
        y2 += animatedPitch + arrowBounce;

        if (x2 > 0 && y2 > 0 && x2 < mc.getWindow().getScaledWidth() && y2 < mc.getWindow().getScaledHeight()) {
            draw2D.getContext().getMatrices().pushMatrix();
            draw2D.getContext().getMatrices().translate((float) x2, (float) y2);
            draw2D.getContext().getMatrices().rotate((float) Math.toRadians(angle));

            double distance = target.distanceToPlayer(mc.player);

            int baseArrowColor = ColorUtils.resolveModeColor(color, customColor, Interface.INSTANCE.getMainColor(4));
            int arrowColor = target.isPartyMember()
                    ? new Color(71, 163, 255).getRGB()
                    : target.isFriend()
                    ? Color.GREEN.getRGB()
                    : baseArrowColor;
            // Стрелка ещё и подрастает, а не просто проявляется.
            float scale = 0.55f + 0.45f * alpha;

            renderBloom(draw2D, 24f * scale * 2.4f, ColorUtils.setAlpha(arrowColor, (int) (70 * alpha)));
            renderArrowTexture(draw2D, 42f * scale, ColorUtils.setAlpha(arrowColor, (int) (24 * alpha)));
            renderArrowTexture(draw2D, 34f * scale, ColorUtils.setAlpha(arrowColor, (int) (45 * alpha)));
            renderArrowTexture(draw2D, 28f * scale, ColorUtils.setAlpha(arrowColor, (int) (70 * alpha)));
            renderArrowTexture(draw2D, 24f * scale, ColorUtils.setAlpha(arrowColor, (int) (255 * alpha)));
            if (type.isSelected("Никнейм") || type.isSelected("Метры")) {
                int nameColor = target.isPartyMember()
                        ? new Color(71, 163, 255).getRGB()
                        : target.isFriend() ? Color.GREEN.getRGB() : Color.WHITE.getRGB();
                renderTargetText(draw2D, target, angle, nameColor, distance);
            }
            draw2D.getContext().getMatrices().popMatrix();
        }

        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
    }

    /** Засветка под стрелкой тем же спрайтом bloom.png, что и у 3D-частиц клиента. */
    private void renderBloom(RenderEvent.Draw2D draw2D, float size, int color) {
        if ((color >>> 24) <= 0) return;

        Builder.TEXTURE_BUILDER
                .size(new SizeState(size, size))
                .texture(0, 0, 1, 1, ClientTexture.of("particle/bloom.png"))
                .color(new QuadColorState(color))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(draw2D.getContext()), -size / 2f, -size / 2f);
    }

    private void renderArrowTexture(RenderEvent.Draw2D draw2D, float size, int color) {
        Builder.TEXTURE_BUILDER
                .size(new SizeState(size, size))
                .texture(0, 0, 1, 1, ClientTexture.of("images/world/arrows.png"))
                .color(new QuadColorState(color))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(draw2D.getContext()), -size / 2f, -size / 2f);
    }

    private void renderTargetText(RenderEvent.Draw2D draw2D, Target target, float angle, int nameColor, double distance) {
        String nameText = "";

        if (type.isSelected("Никнейм")) {
            nameText = getDisplayName(target);
        }

        if (type.isSelected("Метры")) {
            if (!nameText.isEmpty()) {
                nameText += " ";
            }
            if (!Double.isNaN(distance) && !Double.isInfinite(distance)) {
                nameText += String.format("%.0fm", distance);
            } else {
                nameText += "?m";
            }
        }

        if (!nameText.isEmpty()) {
            var matrices = draw2D.getContext().getMatrices();
            matrices.pushMatrix();
            matrices.rotate((float) Math.toRadians(-angle));
            matrices.translate(-Builder.INTER.get().getWidth(nameText, 8) / 2.0f, -21f * textAnimation);
            Builder.TEXT_BUILDER
                    .text(nameText)
                    .size(8)
                    .font(Builder.INTER.get())
                    .color(nameColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(draw2D.getContext()), 0, 0);
            matrices.popMatrix();
        }
    }

    private boolean shouldRenderEntity(Entity entity, boolean isPartyMember) {
        if (entity instanceof AbstractClientPlayerEntity) return shouldRenderPlayer((AbstractClientPlayerEntity) entity, isPartyMember);
        if (entity instanceof MobEntity) return filter.isSelected("Мобов");
        if (entity instanceof AnimalEntity) return filter.isSelected("Животных");
        return entity instanceof ItemEntity && filter.isSelected("Предметы");
    }

    private boolean shouldRenderPlayer(AbstractClientPlayerEntity player, boolean isPartyMember) {
        if (isPartyMember) {
            return true;
        }
        if (filter.get("Друзей").getEnabled() && handlerClient().getFriendManager().isFriend(player.getGameProfile().name())) {
            return true;
        }

        if (!filter.get("Игроков").getEnabled()) return false;

        boolean hasArmor = false;
        for (ItemStack armorPiece : dev.client.api.compat.InventoryCompat.armorItems(player)) {
            if (!armorPiece.isEmpty()) {
                hasArmor = true;
                break;
            }
        }

        if (!hasArmor && !filter.get("Голых").getEnabled()) return false;

        if (player.isInvisible()) {
            if (!filter.get("Инвизок").getEnabled()) return false;
            return hasArmor || filter.get("Голых").getEnabled();
        }

        return true;
    }

    private String getDisplayName(Target target) {
        if (target.isPartyMember() && target.getPartyDisplayName() != null) {
            return target.getPartyDisplayName();
        }

        Entity entity = target.getEntity();
        if (entity instanceof AbstractClientPlayerEntity playerEntity) {
            String name = playerEntity.getGameProfile().name();
            return playerEntity.getName().getString();
        }

        if (entity instanceof ItemEntity && filter.isSelected("Предметы")) {
            ItemStack stack = ((ItemEntity) entity).getStack();
            int count = stack.getCount();
            String displayName = stack.getName().getString();
            if (itemStack.getEnabled() && count > 1) {
                displayName += " (" + count + ")";
            }
            return displayName;
        }

        if (entity != null) {
            return entity.getType().getName().getString();
        }

        if (target.getGameName() != null && !target.getGameName().isBlank()) {
            return target.getGameName();
        }
        if (target.getPartyDisplayName() != null) {
            return target.getPartyDisplayName();
        }
        return "unknown";
    }

    private boolean isSelfSnapshot(PartyMemberSnapshot snapshot) {
        if (mc.player == null || snapshot == null) {
            return false;
        }
        String gameName = snapshot.getGameName();
        return gameName != null && gameName.equalsIgnoreCase(mc.player.getGameProfile().name());
    }

    private boolean isAlreadyTracked(Set<String> seen, PartyMemberSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        String gameName = snapshot.getGameName();
        if (gameName != null && seen.contains(gameName.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String ircName = snapshot.getIrcName();
        return ircName != null && seen.contains(ircName.toLowerCase(Locale.ROOT));
    }

    private void markSeenPartyMember(Set<String> seen, PartyMemberSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        String gameName = snapshot.getGameName();
        if (gameName != null) {
            seen.add(gameName.toLowerCase(Locale.ROOT));
        }
        String ircName = snapshot.getIrcName();
        if (ircName != null) {
            seen.add(ircName.toLowerCase(Locale.ROOT));
        }
    }

    private String resolvePartyDisplayName(PartyMemberSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        String ircName = snapshot.getIrcName();
        if (ircName != null && !ircName.isBlank()) {
            return ircName;
        }
        return snapshot.getDisplayName();
    }

    @Getter
    public static final class Target {
        private final Entity entity;
        private final double remoteX;
        private final double remoteY;
        private final double remoteZ;
        private final boolean partyMember;
        private final boolean friend;
        private final String partyDisplayName;
        private final String gameName;
        private final String ircName;

        private Target(Entity entity, double remoteX, double remoteY, double remoteZ, boolean partyMember, boolean friend, String partyDisplayName, String gameName, String ircName) {
            this.entity = entity;
            this.remoteX = remoteX;
            this.remoteY = remoteY;
            this.remoteZ = remoteZ;
            this.partyMember = partyMember;
            this.friend = friend;
            this.partyDisplayName = partyDisplayName;
            this.gameName = gameName;
            this.ircName = ircName;
        }

        public static Target forEntity(Entity entity, String gameName, boolean friend, boolean partyMember, String partyDisplayName) {
            return new Target(entity, Double.NaN, Double.NaN, Double.NaN, partyMember, friend, partyDisplayName, gameName, null);
        }

        public static Target forRemote(PartyMemberSnapshot snapshot, PositionSnapshot position, boolean friend) {
            String display = snapshot.getIrcName() != null && !snapshot.getIrcName().isBlank()
                    ? snapshot.getIrcName()
                    : snapshot.getDisplayName();
            return new Target(null, position.getX(), position.getY(), position.getZ(), true, friend, display, snapshot.getGameName(), snapshot.getIrcName());
        }

        public double distanceToPlayer(PlayerEntity player) {
            if (player == null) {
                return Double.NaN;
            }
            if (entity != null) {
                return entity.distanceTo(player);
            }
            double dx = remoteX - player.getX();
            double dy = remoteY - player.getY();
            double dz = remoteZ - player.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** Устойчивый ключ цели: по нему стрелка узнаётся между кадрами. */
        public String key() {
            if (entity != null) return "e:" + entity.getUuidAsString();
            if (ircName != null && !ircName.isBlank()) return "i:" + ircName;
            if (gameName != null && !gameName.isBlank()) return "g:" + gameName;
            return "p:" + remoteX + ":" + remoteY + ":" + remoteZ;
        }

        public Vec3d toRelativePosition(double tickDelta, Vec3d cameraPos) {
            if (entity != null) {
                double x = entity.lastRenderX + (entity.getX() - entity.lastRenderX) * tickDelta - cameraPos.x;
                double y = entity.lastRenderY + (entity.getY() - entity.lastRenderY) * tickDelta - cameraPos.y;
                double z = entity.lastRenderZ + (entity.getZ() - entity.lastRenderZ) * tickDelta - cameraPos.z;
                return new Vec3d(x, y, z);
            }
            return new Vec3d(remoteX - cameraPos.x, remoteY - cameraPos.y, remoteZ - cameraPos.z);
        }
    }
}
