package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scoreboard implements IHelper, SettingProvider {
    final DraggableHandler draggableHandler;

    private final List<Setting> settings = new ArrayList<>();

    public final Slider scale = new Slider("Масштаб", () -> true).set(50, 100, 5).applyDefault(100).register(this);

    public CompactAnimation widthAnim = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation heightAnim = new CompactAnimation(Easing.EASE_OUT_EXPO, 350);
    public CompactAnimation showAnim = new CompactAnimation(Easing.EASE_OUT_CUBIC, 300);

    public Scoreboard() {
        this.draggableHandler = addDraggable("Scoreboard", 840, 200);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("ScoreBoard");
        });
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
        widthAnim.setValue(draggableHandler.getWidth());
        heightAnim.setValue(draggableHandler.getHeight());
        showAnim.setValue(0.0);
    }

    @Override
    public DraggableHandler draggable() {
        return draggableHandler;
    }

    @Override
    public float hudScale() {
        Float scaleValue = scale.getValue();
        return scaleValue == null ? 1f : scaleValue / 100f;
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    float width, height;

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        if (!(Interface.INSTANCE.isEnabled() && Interface.INSTANCE.elements.isSelected("ScoreBoard"))) return;
        if (mc.world == null) return;

        net.minecraft.scoreboard.Scoreboard sb = mc.world.getScoreboard();
        ScoreboardObjective objective = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        boolean visibleTarget = objective != null;
        showAnim.run(visibleTarget ? 1.0 : 0.0);
        showAnim.update();
        float show = (float) showAnim.getValue();
        if (show <= 0.001f || objective == null) return;

        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);
        List<ScoreboardEntry> base = sb.getScoreboardEntries(objective).stream()
                .filter(e -> !e.hidden())
                .sorted(Comparator
                        .comparingInt((ScoreboardEntry e) -> {
                            ReadableScoreboardScore rs = sb.getScore(ScoreHolder.fromName(e.owner()), objective);
                            return rs != null ? rs.getScore() : 0;
                        })
                        .reversed()
                        .thenComparing(ScoreboardEntry::owner, String::compareTo))
                .limit(15)
                .toList();

        record Row(Text name, Text score) {
        }
        List<Row> rows = new ArrayList<>();
        for (ScoreboardEntry e : base) {
            Team team = sb.getScoreHolderTeam(e.owner());
            Text name = Team.decorateName(team, e.name());
            Text score = e.formatted(numberFormat);
            rows.add(new Row(applyPlayerProtect(name), score));
        }

        Text title = applyAnarchyProtect(objective.getDisplayName());

        final float titleSize = 8.5f;
        final float lineSize = 8.0f;
        final float padX = 6f;
        final float padY = 6f;
        final float lineH = 9f;
        final float gapTitle = 3f;

        float maxWidth = Math.max(Builder.INTER.get().getWidth(title, titleSize), 0f);
        float colonW = Builder.INTER.get().getWidth(Text.literal(": "), lineSize);

        for (Row r : rows) {
            float nameW = Builder.INTER.get().getWidth(r.name(), lineSize);
            float scoreW = Builder.INTER.get().getWidth(r.score(), lineSize);
            float total = nameW + (scoreW > 0 ? colonW + scoreW : 0);
            if (total > maxWidth) maxWidth = total;
        }

        float boxW = maxWidth + padX * 6f;
        float boxH = padY + lineH + gapTitle + rows.size() * lineH + padY;

        widthAnim.run(boxW);
        heightAnim.run(boxH);
        widthAnim.update();
        heightAnim.update();

        float animW = (float) widthAnim.getValue();
        float animH = (float) heightAnim.getValue();
        draggableHandler.setWidth(animW);
        draggableHandler.setHeight(animH);
        width = animW;
        height = animH;

        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        HelperElements.rectElements(event.getContext(), x, y, animW, animH, show);

        float titleX = x + (animW - Builder.INTER.get().getWidth(title, titleSize)) / 2f;
        float titleY = y + padY - 1f;
        int titleColor = HelperElements.themedText((int) (255 * show));
        if (((titleColor >>> 24) & 0xFF) > 0 && Builder.INTER.get().getWidth(title, titleSize) > 0.001f) {
            Builder.TEXT_BUILDER.font(Builder.INTER.get()).size(titleSize)
                    .color(titleColor)
                    .text(title)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), titleX, titleY);
        }

        float nameX = x + padX;
        float rightX = x + animW - padX;
        float lineY = titleY + lineH + gapTitle;

        ScissorUtil.enableContext(event.getContext(), x, y, animW, animH);
        for (Row r : rows) {
            float slide = (1f - show) * 12f;
            int nameColor = HelperElements.themedText((int) (255 * show));
            if (((nameColor >>> 24) & 0xFF) > 0 && Builder.INTER.get().getWidth(r.name(), lineSize) > 0.001f) {
                Builder.TEXT_BUILDER.font(Builder.INTER.get()).size(lineSize)
                        .color(nameColor)
                        .text(r.name())
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), nameX - slide, lineY);
            }

            float scoreW = Builder.INTER.get().getWidth(r.score(), lineSize);
            float colonX = rightX - scoreW - colonW + slide;
            float scoreX = rightX - scoreW + slide;
            if (scoreW > 0.001f) {
                int dimColor = HelperElements.themedText((int) (220 * show));
                int scoreColor = HelperElements.themedText((int) (255 * show));
                if (((dimColor >>> 24) & 0xFF) > 0) {
                    Text colon = Text.literal(": ");
                    if (Builder.INTER.get().getWidth(colon, lineSize) > 0.001f) {
                        Builder.TEXT_BUILDER.font(Builder.INTER.get()).size(lineSize)
                                .color(dimColor)
                                .text(colon)
                                .build()
                                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), colonX, lineY);
                    }
                }

                if (((scoreColor >>> 24) & 0xFF) > 0 && Builder.INTER.get().getWidth(r.score(), lineSize) > 0.001f) {
                    Builder.TEXT_BUILDER.font(Builder.INTER.get()).size(lineSize)
                            .color(scoreColor)
                            .text(r.score())
                            .build()
                            .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), scoreX, lineY);
                }
            }

            lineY += lineH;
        }
        ScissorUtil.disableContext(event.getContext());
    }

    private static Text applyAnarchyProtect(Text in) {
        return in;
    }

    private static Text applyPlayerProtect(Text name) {
        return name;
    }
}
