package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.irc.PartyManager;
import dev.client.api.nullcry.helper.client.irc.PartyManager.PartyMemberSnapshot;
import dev.client.api.nullcry.helper.client.irc.PartyManager.PartyMemberStatus;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.DraggableHeaderRenderer;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.modules.core.misc.IRC;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.MinecraftClient;

import dev.client.api.nullcry.helper.client.irc.IRClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;

public class PartyList implements IHelper, SettingProvider {
    private static final float HEADER_HEIGHT = 20f;
    private static final float ROW_HEIGHT = 13f;
    private static final float TEXT_SIZE = 7f;
    private static final float PADDING = 7f;
    private static final float SEPARATOR_WIDTH = 1f;
    private static final float SEPARATOR_HEIGHT = 7f;
    private static final float SEGMENT_SPACING = 5f;

    private final DraggableHandler draggableHandler;
    private final List<Setting> settings = new ArrayList<>();

    public final CheckBox showAlways = new CheckBox("Показывать всегда", () -> true)
            .defaultValue(false)
            .register(this);

    private final CompactAnimation widthAnimation = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    private final CompactAnimation heightAnimation = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    private final CompactAnimation showAnimation = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);

    public PartyList() {
        this.draggableHandler = addDraggable("Party List", 4, 198);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled()
                    && module.elements.isSelected("PartyList")
                    && IRC.isActive();
        });
        widthAnimation.setValue(draggableHandler.getWidth());
        heightAnimation.setValue(draggableHandler.getHeight());
        showAnimation.setValue(0.0);
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
    }

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        if (!IRC.isActive()) {
            showAnimation.setValue(0.0);
            return;
        }

        PartyManager partyManager = PartyManager.getInstance();
        List<PartyMemberStatus> statuses = partyManager.collectStatuses(MinecraftClient.getInstance());
        statuses.sort(Comparator.comparing(status -> status.getMember().getDisplayName().toLowerCase(Locale.ROOT)));

        boolean chatOpen = mc.inGameHud.getChatHud().isChatFocused();
        boolean hasParty = partyManager.hasParty();
        boolean alwaysShow = showAlways.getEnabled();
        boolean shouldRender = chatOpen || hasParty || alwaysShow;

        showAnimation.run(shouldRender ? 1.0 : 0.0);
        showAnimation.update();
        float show = (float) showAnimation.getValue();
        if (show <= 0f) {
            return;
        }

        List<RowEntry> rows = buildRows(statuses, hasParty);

        float targetWidth = 140f;
        float targetHeight = HEADER_HEIGHT + ROW_HEIGHT * rows.size();

        for (RowEntry row : rows) {
            float width = computeRowWidth(row);
            if (width > targetWidth) {
                targetWidth = width;
            }
        }

        widthAnimation.run(targetWidth);
        widthAnimation.update();
        heightAnimation.run(targetHeight);
        heightAnimation.update();

        float width = (float) widthAnimation.getValue();
        float height = (float) heightAnimation.getValue();
        draggableHandler.setWidth(width);
        draggableHandler.setHeight(height);

        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        DraggableHeaderRenderer.render(
                event,
                x,
                y,
                width,
                show,
                HEADER_HEIGHT,
                "B",
                8f,
                "PartyList",
                8f,
                -1,
                -1,
                () -> Interface.INSTANCE.getMainColor()
        );

        float bodyHeight = height - HEADER_HEIGHT;
        if (bodyHeight > 2f) {
            HelperElements.rectElements(event.getContext(), x, y + HEADER_HEIGHT, width, bodyHeight, show);
        }

        ScissorUtil.enable(x, y, width, height);
        float offsetY = 0f;

        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext());
        for (RowEntry row : rows) {
            float rowY = y + HEADER_HEIGHT + offsetY;
            HelperElements.rectElements(event.getContext(), x, rowY, width, ROW_HEIGHT, show * 0.9f);

            float cursor = x + PADDING;
            boolean firstSegment = true;
            for (Segment segment : row.segments()) {
                if (!firstSegment) {
                    float separatorX = cursor;
                    int separatorAlpha = Math.min(200, (int) (255 * show));
                    int separatorColor = ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), separatorAlpha);
                    Builder.RECTANGLE_BUILDER
                            .size(new SizeState(SEPARATOR_WIDTH, SEPARATOR_HEIGHT))
                            .color(new QuadColorState(separatorColor))
                            .radius(new QuadRadiusState(0f))
                            .build()
                            .render(matrix, separatorX, rowY + (ROW_HEIGHT - SEPARATOR_HEIGHT) / 2f);
                    cursor += SEPARATOR_WIDTH + SEGMENT_SPACING;
                }

                int baseColor = row.highlight() && segment.highlightable()
                        ? Interface.INSTANCE.getMainColor()
                        : segment.color();
                int segmentAlpha = Math.min(255, (int) (segment.alpha() * show));
                if (row.highlight() && segment.highlightable()) {
                    segmentAlpha = Math.min(255, (int) (255 * show));
                }
                int textColor = ColorUtils.setAlpha(baseColor, segmentAlpha);

                Builder.TEXT_BUILDER
                        .size(TEXT_SIZE)
                        .font(Builder.INTER.get())
                        .text(segment.text())
                        .color(textColor)
                        .build()
                        .render(matrix,
                                cursor,
                                rowY + (ROW_HEIGHT - TEXT_SIZE) / 2f - 0.5f);

                cursor += Builder.INTER.get().getWidth(segment.text(), TEXT_SIZE);
                firstSegment = false;
            }

            offsetY += ROW_HEIGHT;
        }

        ScissorUtil.disable();
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    private List<RowEntry> buildRows(List<PartyMemberStatus> statuses, boolean hasParty) {
        List<RowEntry> result = new ArrayList<>();
        if (!hasParty) {
            result.add(RowEntry.single("Нет активной пати"));
            return result;
        }

        IRClient client = LynxWave.getInstance().getIRClient();
        String selfIrc = client != null ? client.getCurrentNickname() : null;
        String selfGame = null;
        MinecraftClient mcClient = MinecraftClient.getInstance();
        if (mcClient != null && mcClient.player != null && mcClient.player.getGameProfile() != null) {
            selfGame = mcClient.player.getGameProfile().name();
        }

        for (PartyMemberStatus status : statuses) {
            PartyMemberSnapshot member = status.getMember();
            if (member == null) {
                continue;
            }
            String ircName = member.getIrcName();
            String gameName = member.getGameName();
            if ((selfIrc != null && ircName != null && ircName.equalsIgnoreCase(selfIrc))
                    || (selfGame != null && gameName != null && gameName.equalsIgnoreCase(selfGame))) {
                continue;
            }

            List<Segment> segments = new ArrayList<>();
            segments.add(new Segment(buildMemberDisplayName(ircName, gameName), -1, 230, true));

            status.getHealth().ifPresent(health -> {
                double max = status.getMaxHealth().orElse(20.0);
                segments.add(new Segment(String.format(Locale.ROOT, "HP %.1f/%.1f", health, max), -1, 200, false));
            });

            status.getPosition().ifPresent(position -> segments.add(new Segment(
                    String.format(Locale.ROOT, "XYZ %d %d %d",
                            Math.round(position.getX()),
                            Math.round(position.getY()),
                            Math.round(position.getZ())),
                    -1,
                    200,
                    false)));

            status.getDistance().ifPresent(distance -> segments.add(new Segment(
                    String.format(Locale.ROOT, "%.1fм", distance),
                    -1,
                    200,
                    false)));

            if (status.isLeader()) {
                segments.add(new Segment("Лидер", -1, 230, true));
            }

            if (!segments.isEmpty()) {
                result.add(new RowEntry(segments, status.isLeader()));
            }
        }

        if (result.isEmpty()) {
            result.add(RowEntry.single("Нет участников пати"));
        }

        return result;
    }

    private float computeRowWidth(RowEntry row) {
        if (row.segments().isEmpty()) {
            return PADDING * 2f;
        }
        float contentWidth = 0f;
        boolean first = true;
        for (Segment segment : row.segments()) {
            if (!first) {
                contentWidth += SEPARATOR_WIDTH + SEGMENT_SPACING;
            }
            contentWidth += Builder.INTER.get().getWidth(segment.text(), TEXT_SIZE);
            first = false;
        }
        return contentWidth + PADDING * 2f;
    }

    private String buildMemberDisplayName(String ircName, String gameName) {
        if (ircName != null && gameName != null && !ircName.equalsIgnoreCase(gameName)) {
            return ircName + " (" + gameName + ")";
        }
        if (ircName != null) {
            return ircName;
        }
        if (gameName != null) {
            return gameName;
        }
        return "unknown";
    }

    private record RowEntry(List<Segment> segments, boolean highlight) {
        static RowEntry single(String text) {
            return new RowEntry(List.of(new Segment(text, -1, 200, false)), false);
        }
    }

    private record Segment(String text, int color, int alpha, boolean highlightable) {
    }
}
