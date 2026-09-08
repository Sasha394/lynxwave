package dev.client.api.injection;

import com.mojang.datafixers.util.Pair;
import dev.client.LynxWave;
import dev.client.api.compat.GuiMatrixCompat;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.other.ScoreBoardEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.events.core.render.ShaderHand2DEvent;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.GLUtils;
import dev.client.api.nullcry.render.core.RenderCoreCompat;
import dev.client.api.nullcry.render.core.animations.nova.extended.core.EaseInOutQuad;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.BuiltText;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.modules.core.misc.NameProtect;
import dev.client.modules.core.render.Crosshair;
import dev.client.modules.core.render.Interface;
import dev.client.modules.core.render.NoRender;
import dev.client.modules.core.render.Beautifully;
import dev.client.modules.core.render.beautifully.AnimatedValue;
import dev.client.modules.core.render.beautifully.Easing;
import dev.other.scoreboard.SidebarEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Mixin(InGameHud.class)
public abstract class InGameHudMixins {
    @Shadow
    public abstract TextRenderer getTextRenderer();

    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    @Final
    private static Comparator<ScoreboardEntry> SCOREBOARD_ENTRY_COMPARATOR;

    // hotbar
    @Unique
    private int lastSelectedSlot = -1;
    @Unique
    private double animStartX = 0;
    @Unique
    private double animTargetX = 0;
    @Unique
    private final EaseInOutQuad hotbarAnim = new EaseInOutQuad(180, 1.0);
    @Unique
    private int lastHotbarScreenWidth = -1;
    @Unique
    private int lastHotbarScreenHeight = -1;

    @Unique
    private static int px(double v) {
        return (int) Math.round(v);
    }

    // overlay
    @Shadow
    private ItemStack currentStack;
    @Shadow
    private int heldItemTooltipFade;

    @Shadow
    @Nullable
    private Text overlayMessage;

    @Shadow
    private int overlayRemaining;
    @Unique
    private float overlayStackOffset = 0f;
    @Unique
    private static final float OVERLAY_STACK_GAP = 3f;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        overlayStackOffset = 0f;
    }

    @Inject(method = "renderMainHud", at = @At("HEAD"))
    private void onRenderMainHudHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        EventManager.call(new ShaderHand2DEvent(context, tickCounter));
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbar(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        if (!(Interface.INSTANCE.isEnabled() && Interface.INSTANCE.elements.isSelected("HotBar"))) return;

        PlayerEntity player = client.player;
        if (player == null) return;

        ci.cancel();

        final int screenW = ctx.getScaledWindowWidth();
        final int screenH = ctx.getScaledWindowHeight();

        final int width = 182;
        final int height = 22;
        final int slot = 20;
        final int padding = 1;

        boolean layoutChanged = screenW != lastHotbarScreenWidth || screenH != lastHotbarScreenHeight;
        lastHotbarScreenWidth = screenW;
        lastHotbarScreenHeight = screenH;

        final int x = px((screenW - width) / 2.0);
        final int y = px(screenH - height);
        final int innerL = x + padding;
        final int innerR = x + width - padding;
        int themeColor = Interface.INSTANCE.getMainColor();

        var matrices = ctx.getMatrices();
        matrices.pushMatrix();

        int selected = player.getInventory().getSelectedSlot();
        int targetLeft = Math.min(innerL + selected * slot, innerR - slot);

        if (lastSelectedSlot == -1 || layoutChanged) {
            animStartX = animTargetX = targetLeft;
            hotbarAnim.reset();
            lastSelectedSlot = selected;
        }
        if (selected != lastSelectedSlot) {
            double tPrev = (hotbarAnim.getOutput()).doubleValue();
            animStartX = animStartX + (animTargetX - animStartX) * tPrev;
            animTargetX = targetLeft;
            hotbarAnim.setDuration(180).setDirection(true).reset();
            lastSelectedSlot = selected;
        }

        double hotbarAnimation = (hotbarAnim.getOutput()).doubleValue();
        int selX = px(animStartX + (animTargetX - animStartX) * hotbarAnimation);
        int selY = y + padding + 1;

        RenderCoreCompat.withImmediateGuiDraws(() -> {
            HelperElements.rectElements(GuiMatrixCompat.toMatrix4(ctx), x, y, width, height);

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(slot, height - (padding + 1) * 2))
                    .radius(new QuadRadiusState(4f))
                    .color(new QuadColorState(ColorUtils.setAlpha(themeColor, 70)))
                    .build()
                    .render(GuiMatrixCompat.toMatrix4(ctx), selX, selY);
        });

        for (int i = 0; i < 9; i++) {
            int slotLeft = Math.min(innerL + i * slot, innerR - slot);
            int ix = slotLeft + 2;
            int iy = y + 3;
            ItemStack stack = player.getInventory().getMainStacks().get(i);
            drawHotbarStack(ctx, tick, player, stack, ix, iy, i + 1);
        }

        ItemStack off = player.getOffHandStack();
        if (!off.isEmpty()) {
            Arm offArm = player.getMainArm().getOpposite();
            int boxW = 24;
            int boxX = (offArm == Arm.LEFT) ? (x - (boxW + 4)) : (x + width + 4);

            RenderCoreCompat.withImmediateGuiDraws(() ->
                    HelperElements.rectElements(GuiMatrixCompat.toMatrix4(ctx), boxX, y, boxW, height)
            );

            int offX = boxX + 3;
            int offY = y + 4;

            float adjX, adjY;
            if (player.getMainArm() == Arm.LEFT) {
                adjX = 1.0f;
            } else {
                adjX = 1;
            }
            adjY = -0.5f;

            matrices.pushMatrix();
            matrices.translate(adjX, adjY);
            drawHotbarStack(ctx, tick, player, off, offX, offY, 10);
            matrices.popMatrix();
        }

        if (client.options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR) {
            float cd = player.getAttackCooldownProgress(0.0F);
            if (cd < 1.0F) {
                int indX = (player.getMainArm() == Arm.RIGHT) ? (x - 22) : (x + width + 4);
                int indY = y + (height - 18) / 2;

                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(18, 18))
                        .radius(new QuadRadiusState(3))
                        .color(new QuadColorState(ColorUtils.rgba(255, 255, 255, 30)))
                        .build()
                        .render(GuiMatrixCompat.toMatrix4(ctx), indX, indY);

                int fill = (int) MathHelper.clamp(cd * 18f, 1f, 18f);
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(18, fill))
                        .radius(new QuadRadiusState(3))
                        .color(new QuadColorState(ColorUtils.setAlpha(themeColor, 160)))
                        .build()
                        .render(GuiMatrixCompat.toMatrix4(ctx), indX, indY + 18 - fill);
            }
        }

        matrices.popMatrix();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (this.client.currentScreen != null) {
            ci.cancel();
            return;
        }

        if (Crosshair.INSTANCE != null && Crosshair.INSTANCE.isEnabled()) {
            Crosshair.INSTANCE.renderVanillaCrosshairPass(context, tickCounter);
            ci.cancel();
        }
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderHeldItemTooltip(DrawContext ctx, CallbackInfo ci) {
        if (!(Interface.INSTANCE.isEnabled() && Interface.INSTANCE.elements.isSelected("Overlay"))) return;
        if (ctx == null || this.client == null || this.client.player == null || this.client.getWindow() == null) return;
        if (this.heldItemTooltipFade <= 0) return;
        if (this.currentStack == null || this.currentStack.isEmpty()) return;
        if (this.client.interactionManager == null) return;

        try {
            ci.cancel();

            MutableText text = Text.empty().append(this.currentStack.getName()).formatted(this.currentStack.getRarity().getFormatting());
            if (this.currentStack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)) {
                text = text.formatted(net.minecraft.util.Formatting.ITALIC);
            }

            final int sw = ctx.getScaledWindowWidth();
            final int sh = ctx.getScaledWindowHeight();

            int a = (int) ((float) this.heldItemTooltipFade * 256.0F / 10.0F);
            a = MathHelper.clamp(a, 0, 255);
            if (a <= 8) return;

            final float textSize = 8.5f;
            final float padY = 4f;
            final float padX = 6f;

            int theme = Interface.INSTANCE.getMainColor();
            int textColor = ColorUtils.setAlpha(theme, a);

            BuiltText builtText = Builder.TEXT_BUILDER
                    .font(Builder.INTER.get())
                    .size(textSize)
                    .color(textColor)
                    .text(text)
                    .build();

            float textW = Math.max(0f, builtText != null ? builtText.measureWidth() : 0f);
            float textH = Math.max(textSize, Builder.INTER.get().getHeight(text, textSize));

            float baseX = (sw - textW) / 2f;
            float baseY = sh - 59f;
            if (!this.client.interactionManager.hasStatusBars()) baseY += 14f;

            final float boxY = (baseY - padY - 15f) - this.overlayStackOffset;
            final float boxH = textH + padY * 2f;
            final float boxW = textW + padX * 2f;
            final float boxX = baseX - padX;

            HelperElements.rectElements(ctx, boxX, boxY, boxW, boxH, a / 255f);

            if (builtText != null) {
                builtText.render(GuiMatrixCompat.toMatrix4(ctx), baseX - 1f, boxY + (boxH - textH) / 2f - 0.5f);
            }

            this.overlayStackOffset += boxH + OVERLAY_STACK_GAP;
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void onRenderOverlayMessage(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        if (!(Interface.INSTANCE.isEnabled() && Interface.INSTANCE.elements.isSelected("Overlay"))) return;
        if (ctx == null || this.client == null) return;
        if (this.overlayMessage == null || this.overlayRemaining <= 0) return;
        if (this.client.interactionManager == null) return;

        try {
            ci.cancel();

            Text msg = this.overlayMessage;
            if (NameProtect.INSTANCE != null && NameProtect.INSTANCE.isEnabled()) {
                msg = NameProtect.INSTANCE.patchText(msg);
            }

            final int sw = ctx.getScaledWindowWidth();
            final int sh = ctx.getScaledWindowHeight();

            if (msg.getString().isBlank()) return;

            int a = (int) ((float) this.overlayRemaining * 255.0F / 20.0F);
            a = MathHelper.clamp(a, 0, 255);
            if (a <= 8) return;

            final float textSize = 8.5f;
            final float padX = 6f;

            int theme = Interface.INSTANCE.getMainColor();
            int textColor = ColorUtils.setAlpha(theme, a);

            BuiltText builtText = Builder.TEXT_BUILDER
                    .font(Builder.INTER.get())
                    .size(textSize)
                    .color(textColor)
                    .text(msg)
                    .build();

            float textW = Math.max(0f, builtText != null ? builtText.measureWidth() : 0f);
            float baseX = (sw - textW) / 2f;
            float baseY = sh - 59f;
            if (!this.client.interactionManager.hasStatusBars()) baseY += 14f;

            final float padY = 4f;
            final float boxH = textSize + padY * 2f;
            final float boxY = (baseY - padY - 15f) - this.overlayStackOffset;
            final float boxW = textW + padX * 2f;
            final float boxX = baseX - padX;

            HelperElements.rectElements(ctx, boxX, boxY, boxW, boxH, a / 255f);

            if (builtText != null) {
                builtText.render(GuiMatrixCompat.toMatrix4(ctx), baseX - 1f, boxY + boxH / 2f - textSize / 2f - 0.5f);
            }

            this.overlayStackOffset += boxH + OVERLAY_STACK_GAP;
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private void drawHotbarStack(DrawContext ctx, RenderTickCounter tick, PlayerEntity player, ItemStack stack, int x, int y, int seed) {
        if (stack.isEmpty()) return;

        float bob = stack.getBobbingAnimationTime() - tick.getTickProgress(false);
        if (bob > 0.0F) {
            float g = 1.0F + bob / 5.0F;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x + 8, y + 12);
            ctx.getMatrices().scale(1.0F / g, (g + 1.0F) / 2.0F);
            ctx.getMatrices().translate(-(x + 8), -(y + 12));
        }

        ctx.drawItem(player, stack, x, y, seed);
        if (bob > 0.0F) ctx.getMatrices().popMatrix();
        ctx.drawStackOverlay(client.textRenderer, stack, x, y);
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    public void onRenderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Interface.INSTANCE.isEnabled() && Interface.INSTANCE.elements.isSelected("Potions")) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At(value = "RETURN"))
    private void onRenderReturn(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        GLUtils.setupOverlayRendering(2);
        try {
            if (Interface.INSTANCE.isEnabled() && Interface.INSTANCE.elements.isSelected("Notifications")) {
                LynxWave.getInstance().getNotificationManager().draw(context, RenderType.WORLD);
            }

            EventManager.call(new RenderEvent.Draw2D(context, tickCounter));
        } finally {
            GLUtils.restoreRendering();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        Scoreboard scoreboard = objective.getScoreboard();
        List<Pair<ScoreboardEntry, Text>> scoreboardLines = scoreboard.getScoreboardEntries(objective).stream()
                .filter(entry -> !entry.hidden() && !entry.owner().startsWith("#"))
                .sorted(SCOREBOARD_ENTRY_COMPARATOR)
                .limit(15)
                .map(entry -> {
                    Team team = scoreboard.getScoreHolderTeam(entry.owner());
                    Text decorated = Team.decorateName(team, entry.name());
                    return Pair.of(entry, decorated);
                })
                .toList();
        EventManager.call(new ScoreBoardEvent(scoreboardLines));

        boolean noRender = NoRender.INSTANCE.isEnabled()
                && NoRender.INSTANCE.mode.isSelected("ScoreBoard");
        boolean interfaceScoreboard = Interface.INSTANCE.isEnabled()
                && Interface.INSTANCE.elements.isSelected("ScoreBoard");

        if (noRender || interfaceScoreboard) {
            ci.cancel();
            return;
        }

        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);
        SidebarEntry[] sidebarEntrys = (SidebarEntry[]) scoreboard.getScoreboardEntries(objective).stream().filter((score) -> !score.hidden()).sorted(SCOREBOARD_ENTRY_COMPARATOR).limit(15L).map((scoreboardEntry) -> {
            Team team = scoreboard.getScoreHolderTeam(scoreboardEntry.owner());
            Text text = scoreboardEntry.name();
            Text text2 = Team.decorateName(team, text);
            Text text3 = scoreboardEntry.formatted(numberFormat);
            int i = this.getTextRenderer().getWidth(text3);
            return new SidebarEntry(text2, text3, i);
        }).toArray((size) -> new SidebarEntry[size]);
        Text text = objective.getDisplayName();

        // NameProtect removed

        int i = this.getTextRenderer().getWidth(text);
        int j = i;
        int k = this.getTextRenderer().getWidth(": ");

        for (SidebarEntry sidebarEntry : sidebarEntrys) {
            j = Math.max(j, this.getTextRenderer().getWidth(sidebarEntry.name()) + (sidebarEntry.scoreWidth() > 0 ? k + sidebarEntry.scoreWidth() : 0));
        }

        int m = sidebarEntrys.length;
        Objects.requireNonNull(this.getTextRenderer());
        int n = m * 9;
        int o = context.getScaledWindowHeight() / 2 + n / 3;
        int p = 3;
        int q = context.getScaledWindowWidth() - j - 3;
        int r = context.getScaledWindowWidth() - 3 + 2;
        int s = this.client.options.getTextBackgroundColor(0.3F);
        int t = this.client.options.getTextBackgroundColor(0.4F);
        Objects.requireNonNull(this.getTextRenderer());
        int u = o - m * 9;
        int var10001 = q - 2;
        Objects.requireNonNull(this.getTextRenderer());
        context.fill(var10001, u - 9 - 1, r, u - 1, t);
        context.fill(q - 2, u - 1, r, o, s);
        TextRenderer var26 = this.getTextRenderer();
        int var10003 = q + j / 2 - i / 2;
        Objects.requireNonNull(this.getTextRenderer());
        context.drawText(var26, text, var10003, u - 9, -1, false);

        for (int v = 0; v < m; ++v) {
            SidebarEntry sidebarEntry2 = sidebarEntrys[v];
            int var27 = m - v;
            Objects.requireNonNull(this.getTextRenderer());
            Text text2 = sidebarEntry2.name();
            int w = o - var27 * 9;

            context.drawText(this.getTextRenderer(), text2, q, w, -1, false);

            context.drawText(this.getTextRenderer(), sidebarEntry2.score(), r - sidebarEntry2.scoreWidth(), w, -1, false);
        }

        ci.cancel();
    }

    // ---- Beautifully ----

    @Shadow @Final private PlayerListHud playerListHud;

    /**
     * «Анимация таба»: список игроков появляется с лёгким наездом и уезжает обратно.
     * Ванильная отрисовка отменяется и повторяется внутри анимированной матрицы.
     */
    @Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true)
    private void lynx$animatePlayerList(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!Beautifully.tabAnimation()) return;
        if (this.client.world == null || this.client.player == null || this.client.player.networkHandler == null) return;

        Scoreboard scoreboard = this.client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        boolean visible = this.client.options.playerListKey.isPressed()
                && (!this.client.isInSingleplayer()
                || this.client.player.networkHandler.getListedPlayerListEntries().size() > 1
                || objective != null);

        ci.cancel();

        AnimatedValue animation = Beautifully.ready().getTabListAnimation();
        animation.setDuration(200L);
        animation.setEasing(visible ? Easing.BACK_OUT : Easing.CUBIC_IN);
        float progress = animation.update(visible ? 1.0f : 0.0f);

        this.playerListHud.setVisible(visible);
        if (progress <= 0.005f) return;

        // Ваниль поднимает таб в новый слой — иначе он уедет под остальной HUD.
        context.createNewRootLayer();

        float width = context.getScaledWindowWidth();
        float scale = 0.96f + 0.04f * progress;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(width / 2.0f, 0.0f);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-width / 2.0f, (progress - 1.0f) * 10.0f);
        this.playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
        context.getMatrices().popMatrix();
    }

    /** «История чата»: не вычищать чат при смене мира или переподключении. */
    @Redirect(method = "clear", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;clear(Z)V"))
    private void lynx$keepChatHistory(ChatHud chatHud, boolean clearHistory) {
        if (Beautifully.keepChatHistory()) return;
        chatHud.clear(clearHistory);
    }
}
