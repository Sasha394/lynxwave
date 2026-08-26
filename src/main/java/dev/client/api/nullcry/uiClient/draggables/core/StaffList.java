package dev.client.api.nullcry.uiClient.draggables.core;

import com.mojang.authlib.GameProfile;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.helper.other.ModeratorSHIPUCHKA;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.DraggableHeaderRenderer;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.api.nullcry.uiClient.notification.NotificationDragging;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.modules.core.render.Interface;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class StaffList implements IHelper, SettingProvider {
    final DraggableHandler draggableHandler;

    private final List<StaffAnimation> staffAnimations = new ArrayList<>();
    public CompactAnimation staffsWidth = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation staffsHeight = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation showAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);

    private final List<Setting> settings = new ArrayList<>();

    public final Slider scale = new Slider("Масштаб", () -> true).set(50, 100, 5).applyDefault(100).register(this);

    @Override
    public DraggableHandler draggable() {
        return draggableHandler;
    }

    @Override
    public float hudScale() {
        Float scaleValue = scale.getValue();
        return scaleValue == null ? 1f : scaleValue / 100f;
    }

    public final CheckBox showAlways = new CheckBox("Показывать всегда", () -> true)
            .defaultValue(false)
            .register(this);
    public final CheckBox showStaffHead = new CheckBox("Показывать голову стаффа", () -> true)
            .defaultValue(false)
            .register(this);

    private static final float HEAD_SIZE = 10f;
    private static final float HEAD_RADIUS = 4f;
    private static final float HEAD_SPACING = 2f;
    private static final float ROW_HEIGHT = 13f;
    private static final float HEAD_U = 0.125f;
    private static final float HEAD_V = 0.125f;
    private static final float HEAD_TEX_SIZE = 0.125f;
    private static final float HEAD_OVERLAY_U = 0.625f;
    private static final float HEAD_OVERLAY_V = 0.125f;
    private static final float HEAD_SMOOTHNESS = 0.35f;

    public StaffList() {
        this.draggableHandler = addDraggable("Staff List", 4, 158);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("StaffList");
        });
        staffsWidth.setValue(draggableHandler.getWidth());
        staffsHeight.setValue(draggableHandler.getHeight());
        showAnim.setValue(0.0);
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
    }

    float width;
    float height;

    final List<Staff> staffPlayers = new ArrayList<>();
    final List<String> staffPrefix = List.of("helper", "moder", "staff", "admin", "curator", "spectator", "стажёр", "стажер", "staff", "сотрудник", "спектатор", "помощник", "админ", "модер");
    private final Map<UUID, PlayerListEntry> renderEntryByUuid = new HashMap<>();
    private final Map<String, PlayerListEntry> renderEntryByName = new HashMap<>();

    @Override
    public void onUpdate(UpdateEvent event) {
        staffPlayers.clear();
        staffPlayers.addAll(getDetectedStaffs());

        for (Staff staff : staffPlayers) {
            Optional<StaffAnimation> existing = staffAnimations.stream()
                    .filter(as -> as.isSame(staff))
                    .findFirst();

            if (existing.isPresent()) {
                StaffAnimation as = existing.get();
                as.staff.setDisplay(staff.getDisplay());
                as.staff.setPrefix(staff.getPrefix());
                as.staff.setUuid(staff.getUuid());
                as.staff.setSpec(staff.isSpec());
                as.staff.setStatus(staff.getStatus());
            } else {
                staffAnimations.add(new StaffAnimation(staff));
                Text joined = Text.empty().append(staff.getDisplay()).append(" - Зашел на сервер!");
                if (Interface.INSTANCE.isEnabled() && NotificationDragging.INSTANCE.getElementsNotifications().isSelected("Зашел стафф")) {
                    handlerClient().getNotificationManager().add(joined, 3, NotificationType.WARNING, RenderType.WORLD);
                }
            }
        }

        staffAnimations.forEach(as -> {
            boolean stillExists = staffPlayers.stream().anyMatch(as::isSame);
            if (!stillExists) as.hide();
        });

        staffAnimations.removeIf(StaffAnimation::isFinished);
    }

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        float x = draggableHandler.getX();
        float y = draggableHandler.getY();
        float baseWidth = 80f;

        staffAnimations.forEach(StaffAnimation::tick);

        boolean chatOpen = mc.inGameHud.getChatHud().isChatFocused();
        boolean alwaysShow = showAlways.getEnabled();
        boolean preview = chatOpen && staffPlayers.isEmpty();
        boolean shouldRender = chatOpen || !staffPlayers.isEmpty() || alwaysShow;
        showAnim.run(shouldRender ? 1.0 : 0.0);
        showAnim.update();
        float show = (float) showAnim.getValue();
        if (show <= 0f) return;

        final boolean renderHeads = showStaffHead.getEnabled();
        final float headContribution = renderHeads ? (HEAD_SIZE + HEAD_SPACING) : 0f;

        float targetWidth = baseWidth;
        float targetHeight = 21f;

        if (preview) {
            String previewName = "Anon1mysxd";
            float displayWidth = Builder.INTER.get().getWidth(previewName, 7);
            float statusWidth = Builder.INTER.get().getWidth(Status.ONLINE.string, 7) + 8;

            float totalWidth = displayWidth + statusWidth + 14f + headContribution;
            if (totalWidth > targetWidth) targetWidth = totalWidth;
            targetHeight += 13f;
        } else {
            for (StaffAnimation staff : staffAnimations) {
                float progress = staff.getProgress();
                if (progress <= 0f) continue;
                float displayWidth = Builder.INTER.get().getWidth(staff.staff.getDisplay().getString(), 7);
                float statusWidth = Builder.INTER.get().getWidth(staff.staff.getStatus().string, 7) + 8;

                float totalWidth = displayWidth + statusWidth + 14f + headContribution;
                if (totalWidth > targetWidth) targetWidth = totalWidth;
                targetHeight += 13f * progress;
            }
        }

        staffsWidth.run(targetWidth);
        staffsHeight.run(targetHeight);
        staffsWidth.update();
        staffsHeight.update();

        float animatedWidth = (float) staffsWidth.getValue();
        float animatedHeight = (float) staffsHeight.getValue();

        width = animatedWidth;
        height = animatedHeight;

        draggableHandler.setWidth(animatedWidth);
        draggableHandler.setHeight(animatedHeight);

        float headerHeight = 20f;
        float paddingX = 6f;
        DraggableHeaderRenderer.render(
                event,
                x,
                y,
                width,
                show,
                headerHeight,
                "H",
                8f,
                "StaffList",
                8f,
                -1,
                -1,
                () -> Interface.INSTANCE.getMainColor()
        );

        float bodyHeight = height - headerHeight;
        if (bodyHeight > 2f) {
            HelperElements.rectElements(event.getContext(), x, y + headerHeight, width, bodyHeight, show);
        }

        staffAnimations.sort((a, b) -> a.staff.getName().compareToIgnoreCase(b.staff.getName()));

        float offset = 0f;
        renderEntryByUuid.clear();
        renderEntryByName.clear();
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                GameProfile profile = entry.getProfile();
                if (profile == null) continue;
                if (profile.id() != null) {
                    renderEntryByUuid.put(profile.id(), entry);
                }
                if (profile.name() != null) {
                    renderEntryByName.put(profile.name().toLowerCase(Locale.ROOT), entry);
                }
            }
        }

        float textOffset = renderHeads ? (HEAD_SIZE + HEAD_SPACING) : 0f;

        ScissorUtil.enable(x, y, animatedWidth, animatedHeight);

        if (preview) {
            float progress = show;
            float itemHeight = ROW_HEIGHT;
            float itemY = y + headerHeight + offset;

            HelperElements.rectElements(event.getContext(), x, itemY, width, itemHeight, progress);

            float baseY = itemY + (itemHeight - 7f) / 2f - 0.5f;
            int baseAlpha = (int) (255 * progress);

            String status = Status.ONLINE.string;
            int statusColor = Status.ONLINE.color;
            String previewName = "Anon1mysxd";

            float statusW = Builder.INTER.get().getWidth(status, 7);

            float leftX = x + paddingX - 0.5f - (1f - progress) * 15f;
            float statusX = x + width - statusW - 6f + (1f - progress) * 15f;

            if (renderHeads && mc.player instanceof AbstractClientPlayerEntity clientPlayer && clientPlayer.getSkin() != null) {
                float headCenterY = baseY + ROW_HEIGHT / 2f;
                float headY = headCenterY - HEAD_SIZE / 2f - 2f;
                Identifier texture = clientPlayer.getSkin().body().texturePath();
                int headColor = ColorUtils.setAlpha(0xFFFFFFFF, baseAlpha);

                DrawUtil.renderHeadTexture(
                        event.getContext(),
                        leftX,
                        headY,
                        HEAD_SIZE,
                        HEAD_SIZE,
                        HEAD_RADIUS,
                        HEAD_U,
                        HEAD_V,
                        HEAD_TEX_SIZE,
                        HEAD_TEX_SIZE,
                        texture,
                        new Color(headColor, true),
                        HEAD_SMOOTHNESS
                );

                DrawUtil.renderHeadTexture(
                        event.getContext(),
                        leftX,
                        headY,
                        HEAD_SIZE,
                        HEAD_SIZE,
                        HEAD_RADIUS,
                        HEAD_OVERLAY_U,
                        HEAD_OVERLAY_V,
                        HEAD_TEX_SIZE,
                        HEAD_TEX_SIZE,
                        texture,
                        new Color(headColor, true),
                        HEAD_SMOOTHNESS
                );
            }

            Builder.TEXT_BUILDER
                    .size(7)
                    .font(Builder.INTER.get())
                    .text(previewName)
                    .color(HelperElements.themedText(baseAlpha))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), leftX + textOffset, baseY);

            Builder.TEXT_BUILDER
                    .size(7)
                    .font(Builder.INTER.get())
                    .text(status)
                    .color(ColorUtils.setAlpha(statusColor, baseAlpha))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), statusX, baseY);
        } else {
            for (StaffAnimation staffAnimation : staffAnimations) {
                float progress = staffAnimation.getProgress();
                if (progress <= 0f) continue;

                Staff staff = staffAnimation.staff;
                float itemHeight = ROW_HEIGHT;
                float itemY = y + headerHeight + offset;

                HelperElements.rectElements(event.getContext(), x, itemY, width, itemHeight, progress);

                float baseY = itemY + (itemHeight - 7f) / 2f - 0.5f;
                int baseAlpha = (int) (255 * progress);

                String status = staff.getStatus().string;
                int statusColor = staff.getStatus().color;

                float statusW = Builder.INTER.get().getWidth(status, 7);

                float leftX = x + paddingX - 0.5f - (1f - progress) * 15f;
                float statusX = x + width - statusW - 6f + (1f - progress) * 15f;

                if (renderHeads) {
                    float headCenterY = baseY + ROW_HEIGHT / 2f;
                    float headY = headCenterY - HEAD_SIZE / 2f - 1f;
                    PlayerListEntry entry = null;
                    if (staff.getUuid() != null) {
                        entry = renderEntryByUuid.get(staff.getUuid());
                    }
                    if (entry == null && staff.getName() != null) {
                        entry = renderEntryByName.get(staff.getName().toLowerCase(Locale.ROOT));
                    }

                    if (entry != null && entry.getSkinTextures() != null) {
                        int headColor = ColorUtils.setAlpha(0xFFFFFFFF, baseAlpha);
                        Identifier texture = entry.getSkinTextures().body().texturePath();

                        DrawUtil.renderHeadTexture(
                                event.getContext(),
                                leftX,
                                headY,
                                HEAD_SIZE,
                                HEAD_SIZE,
                                HEAD_RADIUS,
                                HEAD_U,
                                HEAD_V,
                                HEAD_TEX_SIZE,
                                HEAD_TEX_SIZE,
                                texture,
                                new Color(headColor, true),
                                HEAD_SMOOTHNESS
                        );

                        if (entry.shouldShowHat()) {
                            DrawUtil.renderHeadTexture(
                                    event.getContext(),
                                    leftX,
                                    headY,
                                    HEAD_SIZE,
                                    HEAD_SIZE,
                                    HEAD_RADIUS,
                                    HEAD_OVERLAY_U,
                                    HEAD_OVERLAY_V,
                                    HEAD_TEX_SIZE,
                                    HEAD_TEX_SIZE,
                                    texture,
                                    new Color(headColor, true),
                                    HEAD_SMOOTHNESS
                            );
                        }
                    }
                }

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(staff.getDisplay())
                        .color(HelperElements.themedText(baseAlpha))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), leftX + textOffset, baseY);

                Builder.TEXT_BUILDER
                        .size(7)
                        .font(Builder.INTER.get())
                        .text(status)
                        .color(ColorUtils.setAlpha(statusColor, baseAlpha))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), statusX, baseY);

                offset += 13f * staffAnimation.getHeightProgress();
            }
        }

        ScissorUtil.disable();
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public List<Staff> getDetectedStaffs() {
        List<Staff> detected = new ArrayList<>();
        Collection<PlayerListEntry> playerList = Objects.requireNonNull(mc.player).networkHandler.getPlayerList();
        Set<String> inTab = new HashSet<>();
        for (PlayerListEntry e : playerList) {
            GameProfile gp = e.getProfile();
            if (gp != null && gp.name() != null) {
                inTab.add(gp.name());
            }
        }

        Set<String> knownStaffNames = new HashSet<>();
        knownStaffNames.addAll(handlerClient().getStaffManager().getStaffs());
        if (isFT()) {
            knownStaffNames.addAll(ModeratorSHIPUCHKA.MODERATOR_NAMES);
        }
        knownStaffNames.retainAll(inTab);

        Set<String> staffPrefixes = new LinkedHashSet<>();
        staffPrefixes.addAll(handlerClient().getStaffPrefixManager().getStaffs());
        staffPrefixes.addAll(this.staffPrefix);

        for (PlayerListEntry entry : playerList) {
            GameProfile profile = entry.getProfile();
            Text displayName = entry.getDisplayName();
            if (profile == null) continue;

            String name = profile.name();
            if (name == null) continue;
            if (mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString())) continue;

            String raw = displayName != null ? displayName.getString() : "";
            String prefix = raw.replace(name, "");

            Team team = entry.getScoreboardTeam();
            if ((prefix == null || prefix.isEmpty()) && team != null && team.getPrefix() != null) {
                prefix = team.getPrefix().getString();
            }

            String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
            boolean byPrefix = false;
            for (String s : staffPrefixes) {
                if (!s.isEmpty() && lower.contains(s.toLowerCase(Locale.ROOT))) {
                    byPrefix = true;
                    break;
                }
            }

            boolean byKnownList = knownStaffNames.contains(name);
            if (!byPrefix && !byKnownList) continue;

            boolean entityPresent = mc.world != null && mc.world.getPlayerByUuid(profile.id()) != null;
            boolean isSpec = entry.getGameMode() == GameMode.SPECTATOR;

            boolean teamHidesNameTag = false;
            if (team != null) {
                AbstractTeam.VisibilityRule vr = team.getNameTagVisibilityRule();
                teamHidesNameTag = (vr == AbstractTeam.VisibilityRule.NEVER || vr == AbstractTeam.VisibilityRule.HIDE_FOR_OTHER_TEAMS);
            }

            Status status;
            if (!entityPresent && (isSpec || teamHidesNameTag)) {
                status = Status.VANISHED;
            } else if (entityPresent && mc.player != null && isPlayerNear(mc.player, name)) {
                status = Status.NEAR;
            } else {
                status = Status.ONLINE;
            }

            Text finalPrefix;
            if (team != null && team.getPrefix() != null && !team.getPrefix().getString().isEmpty()) {
                finalPrefix = team.getPrefix();
            } else if (prefix != null && !prefix.isEmpty()) {
                finalPrefix = Text.literal(prefix);
            } else {
                finalPrefix = Text.empty();
            }

            Text teamDecorated = Team.decorateName(team, Text.literal(name));
            Text fullDisplay = displayName != null ? displayName : teamDecorated;

            UUID uuid = profile.id();
            if (uuid == null) {
                uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            }

            detected.add(new Staff(fullDisplay, finalPrefix, name, uuid, isSpec, status));
        }

        Map<String, Staff> uniq = new LinkedHashMap<>();
        for (Staff s : detected) {
            String key = s.getUuid() != null ? s.getUuid().toString() : s.getName().toLowerCase(Locale.ROOT);
            uniq.put(key, s);
        }
        return new ArrayList<>(uniq.values());
    }

    private boolean isPlayerNear(PlayerEntity player, String staffName) {
        for (PlayerListEntry info : mc.getNetworkHandler().getPlayerList()) {
            if (info.getProfile().name().equals(staffName)) {
                PlayerEntity staffPlayer = mc.world.getPlayerByUuid(info.getProfile().id());
                if (staffPlayer != null) {
                    double distance = player.distanceTo(staffPlayer);
                    return distance <= 30.0;
                }
            }
        }
        return false;
    }

    @Data
    @AllArgsConstructor
    public static class Staff {
        Text display;
        Text prefix;
        String name;
        UUID uuid;
        boolean isSpec;
        Status status;
    }

    public enum Status {
        VANISHED("Vanished", ColorUtils.rgb(255, 0, 0)),
        ONLINE("-", ColorUtils.rgb(0, 255, 0)),
        NEAR("Near", ColorUtils.rgb(255, 255, 0));

        final String string;
        final int color;

        Status(String string, int color) {
            this.string = string;
            this.color = color;
        }
    }

    static class StaffAnimation {
        public final Staff staff;
        public final CompactAnimation anim = new CompactAnimation(Easing.EASE_OUT_QUAD, 250);

        public StaffAnimation(Staff staff) {
            this.staff = staff;
            anim.setValue(0.0);
            anim.run(1.0);
        }

        public void tick() {
            anim.update();
        }

        public float getProgress() {
            return (float) anim.getValue();
        }

        public void hide() {
            anim.run(0.0);
        }

        public boolean isSame(Staff other) {
            if (staff.getUuid() != null && other.getUuid() != null) {
                return staff.getUuid().equals(other.getUuid());
            }
            return staff.getName().equalsIgnoreCase(other.getName());
        }

        public boolean isFinished() {
            return anim.getDestinationValue() == 0.0 && anim.isDone() && getProgress() <= 0.001f;
        }

        public float getHeightProgress() {
            return anim.getDestinationValue() == 0.0 ? getProgress() : 1f;
        }
    }
}
