package dev.client.api.injection;

import com.mojang.authlib.minecraft.BanDetails;
import dev.client.LynxWave;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.core.GLUtils;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.BuiltBlur;
import dev.client.api.nullcry.render.core.renderers.core.BuiltLiquidGlass;
import dev.client.api.nullcry.uiClient.altManager.TitleScreenAlphaAccess;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.helper.other.SoundUtil;
import dev.client.api.nullcry.uiClient.title.TitleMenuScreen;
import dev.client.api.nullcry.uiClient.title.TitleBackgroundRenderer;
import dev.client.api.nullcry.uiClient.title.TitleScreenSettings;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixins extends Screen implements TitleScreenAlphaAccess {

    protected TitleScreenMixins(Text title) {
        super(title);
    }

    @Unique private static boolean nc$welcomePlayed = false;
    @Unique private final List<String> nc$texts = new ArrayList<>();
    @Unique private final List<Runnable> nc$actions = new ArrayList<>();
    @Unique private final List<Boolean> nc$disabled = new ArrayList<>();

    @Unique private float btnWidth = 145f;
    @Unique private float btnHeight = 21f;
    @Unique private float btnSpacing = 4f;

    /** Кнопка Menu справа снизу: квадрат с иконкой гайки */
    @Unique private static final float nc$menuBtnSize = 36f;
    @Unique private static final float nc$menuBtnMargin = 16f;
    @Unique private static final String nc$menuGearTexture = "images/gear.png";

    @Unique private final CompactAnimation nc$alphaAnimation = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    @Unique private boolean nc$alphaInitialized = false;
    @Unique private boolean nc$transitioning = false;
    @Unique private Runnable nc$pendingAction = null;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        GLUtils.setupOverlayRendering(2);
        boolean restored = false;
        try {
            CursorsUtil.beginFrame();

            if (this.client != null) {
                this.width = GLUtils.getScaledWidthFor(2);
                this.height = GLUtils.getScaledHeightFor(2);
            }

            int renderMouseX = (int) Math.round(GLUtils.scaleMouseX(mouseX, 2));
            int renderMouseY = (int) Math.round(GLUtils.scaleMouseY(mouseY, 2));

            var m = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);

            if (!nc$alphaInitialized) {
                nc$prepareFadeIn();
                nc$alphaInitialized = true;
            }

            double targetAlpha = nc$transitioning ? 0.0 : 1.0;
            nc$alphaAnimation.run(targetAlpha);
            nc$alphaAnimation.update();
            float alpha = Math.max(0f, Math.min(1f, (float) nc$alphaAnimation.getValue()));

            if (nc$transitioning && alpha <= 0f && nc$pendingAction != null) {
                Runnable action = nc$pendingAction;
                nc$pendingAction = null;
                nc$transitioning = false;
                CursorsUtil.applyFrameCursor();
                ci.cancel();
                restored = true;
                GLUtils.restoreRendering();
                action.run();
                return;
            }

            String bgPath = TitleScreenSettings.getBackgroundPath();
            Builder.TEXTURE_BUILDER
                    .size(new SizeState(this.width, this.height))
                    .texture(0, 0, 1, 1, ClientTexture.of(bgPath))
                    .color(TitleBackgroundRenderer.resolveTextureColor(bgPath))
                    .build()
                    .render(m, 0, 0);

            final float centerX = this.width / 2f;

            final float iconSize = 28f;
            final float greetSize = 12f;
            final float subSize = 9f;
            final float spaceIconToGreet = 4f;
            final float spaceGreetToSub = 6f;
            final float spaceSubToBtns = 10f;

            final int count = nc$texts.size();
            final float buttonsBlockHeight = (btnHeight * count) + (btnSpacing * Math.max(0, count - 1));

            final float totalHeight = iconSize + spaceIconToGreet + greetSize + spaceGreetToSub + subSize + spaceSubToBtns + buttonsBlockHeight;
            final float topY = Math.round((this.height - totalHeight) / 2f);

            final int iconsColor = nc$applyAlpha(Interface.INSTANCE.getMainColor(), alpha);
            final int textColor = nc$applyAlpha(-1, alpha);

            float iconW = Builder.ICONS.get().getWidth("A", iconSize);
            float iconX = Math.round(centerX - iconW / 2f);

            Builder.TEXT_BUILDER
                    .text("A").size(iconSize).font(Builder.ICONS.get())
                    .color(iconsColor)
                    .build()
                    .render(m, iconX, topY + 2);

            String lang = this.client.getLanguageManager().getLanguage();
            var now = LocalTime.now();
            String timeOfDay = now.isBefore(LocalTime.of(6, 0))
                    ? (lang.equals("ru_ru")
                       ? "Доброй ночи" : "Good night")
                    : now.isBefore(LocalTime.NOON) ? (lang.equals("ru_ru") ? "Доброе утро" : "Good morning")
                      : now.isBefore(LocalTime.of(18, 0)) ? (lang.equals("ru_ru") ? "Добрый день" : "Good afternoon")
                        : (lang.equals("ru_ru") ? "Добрый вечер" : "Good evening");

            String user = LynxWave.getInstance().getUserProfile().getUser();
            String greet = timeOfDay + ", " + user + "!";
            float greetW = Builder.INTER.get().getWidth(greet, greetSize);
            float greetX = Math.round(centerX - greetW / 2f);
            float greetYBase = Math.round(topY + iconSize + spaceIconToGreet);
            float desiredGap = 10f;
            float greetShift = Math.max(0f, spaceGreetToSub - desiredGap);

            Builder.TEXT_BUILDER
                    .text(greet).size(greetSize).font(Builder.INTER.get())
                    .color(textColor)
                    .build()
                    .render(m, greetX, greetYBase + greetShift + 6);

            float subY = Math.round(greetYBase + greetSize + spaceGreetToSub);
            final float btnX = Math.round(centerX - btnWidth / 2f);
            float btnY = Math.round(subY + subSize + spaceSubToBtns);

            boolean anyHover = false;

            for (int i = 0; i < count; i++) {
                String label = nc$texts.get(i);
                boolean disabled = nc$disabled.get(i);
                boolean hover = !disabled && MouseClick.isClick(renderMouseX, renderMouseY, btnX, btnY, btnWidth, btnHeight);
                if (hover) anyHover = true;
                float buttonShow = alpha * (hover ? 1.0f : 0.65f);
                if (disabled) buttonShow *= 0.45f;
                buttonShow = Math.max(0f, Math.min(1f, buttonShow));

                HelperElements.rect(m, btnX, btnY, btnWidth, btnHeight, buttonShow);

                float tw = Builder.INTER.get().getWidth(label, 8f);
                float tX = Math.round(btnX + (btnWidth - tw) / 2f);
                float tY = Math.round(btnY + (btnHeight - 8f) / 2f - 0.5f);

                int renderColor;
                if (label.equalsIgnoreCase("Exit") && hover) {
                    renderColor = ColorUtils.setAlpha(ColorUtils.red, 255);
                } else {
                    renderColor = ColorUtils.setAlpha(textColor,
                            Math.round(ColorUtils.alpha(textColor) * (disabled ? 0.55f : hover ? 1f : 0.9f)));
                }

                Builder.TEXT_BUILDER
                        .text(label).size(8f).font(Builder.INTER.get())
                        .color(renderColor)
                        .build()
                        .render(m, tX, tY);

                btnY += btnHeight + btnSpacing;
            }

            float menuX = this.width - nc$menuBtnMargin - nc$menuBtnSize;
            float menuY = this.height - nc$menuBtnMargin - nc$menuBtnSize;
            boolean menuHover = MouseClick.isClick(renderMouseX, renderMouseY, menuX, menuY, nc$menuBtnSize, nc$menuBtnSize);
            if (menuHover) anyHover = true;
            float menuShow = alpha * (menuHover ? 1.0f : 0.7f);
            menuShow = Math.max(0f, Math.min(1f, menuShow));
            HelperElements.rect(m, menuX, menuY, nc$menuBtnSize, nc$menuBtnSize, menuShow);
            float iconPad = 6f;
            float gearIconSize = nc$menuBtnSize - iconPad * 2f;
            Builder.TEXTURE_BUILDER
                    .size(new SizeState(gearIconSize, gearIconSize))
                    .texture(0, 0, 1, 1, ClientTexture.of(nc$menuGearTexture))
                    .color(new QuadColorState(nc$applyAlpha(-1, alpha)))
                    .build()
                    .render(m, menuX + iconPad, menuY + iconPad);

            if (anyHover) {
                CursorsUtil.setCursor(CursorsUtil.HAND);
            }

            CursorsUtil.applyFrameCursor();
            ci.cancel();
        } finally {
            if (!restored) {
                GLUtils.restoreRendering();
            }
        }
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        BuiltBlur.resetCaptureState();
        BuiltLiquidGlass.resetCaptureState();

        nc$texts.clear();
        nc$actions.clear();
        nc$disabled.clear();

        nc$prepareFadeIn();
        nc$alphaInitialized = true;
        nc$transitioning = false;
        nc$pendingAction = null;

        if (!nc$welcomePlayed) {
            new SoundUtil("welcome").play();
            nc$welcomePlayed = true;
        }

        nc$texts.add("Singleplayer");
        nc$actions.add(() -> this.client.setScreen(new SelectWorldScreen((Screen) (Object) this)));
        nc$disabled.add(false);

        Text disabled = onMultiplayerDisabledText();
        nc$texts.add("Multiplayer");
        if (disabled == null) {
            nc$actions.add(() -> {
                Screen screen = this.client.options.skipMultiplayerWarning
                        ? new MultiplayerScreen((Screen) (Object) this)
                        : new MultiplayerWarningScreen((Screen) (Object) this);
                this.client.setScreen(screen);
            });
            nc$disabled.add(false);
        } else {
            nc$actions.add(() -> SystemToast.addWorldAccessFailureToast(this.client, disabled.getString()));
            nc$disabled.add(true);
        }

        nc$texts.add("Altmanager");
        nc$actions.add(() -> nc$startTransition(() -> {
            if (this.client == null) return;
            var alt = LynxWave.getInstance().getAltScreenManager();
            if (alt != null) {
                alt.restartReveal();
            }
            this.client.setScreen(alt);
        }));
        nc$disabled.add(false);


        nc$texts.add("Options");
        nc$actions.add(() -> this.client.setScreen(new OptionsScreen((Screen) (Object) this, this.client.options)));
        nc$disabled.add(false);

        nc$texts.add("Exit");
        nc$actions.add(() -> this.client.scheduleStop());
        nc$disabled.add(false);

        ci.cancel();
    }


    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> ci) {
        if (nc$transitioning) {
            ci.setReturnValue(false);
            ci.cancel();
            return;
        }
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button != 0) return;

        if (this.client != null) {
            this.width = GLUtils.getScaledWidthFor(2);
            this.height = GLUtils.getScaledHeightFor(2);
        }

        double renderMouseX = GLUtils.scaleMouseX(mouseX, 2);
        double renderMouseY = GLUtils.scaleMouseY(mouseY, 2);

        final int count = nc$texts.size();
        final float centerX = this.width / 2f;

        final float iconSize = 28f;
        final float greetSize = 12f;
        final float subSize = 9f;
        final float spaceIconToGreet = 4f;
        final float spaceGreetToSub = 6f;
        final float spaceSubToBtns = 10f;

        final float buttonsBlockHeight = (btnHeight * count) + (btnSpacing * Math.max(0, count - 1));
        final float totalH = iconSize + spaceIconToGreet + greetSize + spaceGreetToSub + subSize + spaceSubToBtns + buttonsBlockHeight;
        final float topY = Math.round((this.height - totalH) / 2f);

        final float greetYBase = Math.round(topY + iconSize + spaceIconToGreet);
        final float subY = Math.round(greetYBase + greetSize + spaceGreetToSub);

        final float bx = Math.round(centerX - btnWidth / 2f);
        float by = Math.round(subY + subSize + spaceSubToBtns);

        for (int i = 0; i < count; i++) {
            if (!nc$disabled.get(i) && MouseClick.isClick(renderMouseX, renderMouseY, bx, by, btnWidth, btnHeight)) {
                nc$actions.get(i).run();
                ci.setReturnValue(true);
                ci.cancel();
                return;
            }
            by += btnHeight + btnSpacing;
        }

        float menuX = this.width - nc$menuBtnMargin - nc$menuBtnSize;
        float menuY = this.height - nc$menuBtnMargin - nc$menuBtnSize;
        if (MouseClick.isClick(renderMouseX, renderMouseY, menuX, menuY, nc$menuBtnSize, nc$menuBtnSize)) {
            this.client.setScreen(new TitleMenuScreen((Screen) (Object) this));
            ci.setReturnValue(true);
            ci.cancel();
        }
    }

    @Unique
    private void nc$startTransition(Runnable action) {
        if (action == null || nc$transitioning) return;
        nc$pendingAction = action;
        nc$transitioning = true;
    }

    @Unique
    private int nc$applyAlpha(int color, float factor) {
        float clamped = Math.max(0f, Math.min(1f, factor));
        int baseAlpha = ColorUtils.alpha(color);
        int newAlpha = Math.round(baseAlpha * clamped);
        return ColorUtils.setAlpha(color, newAlpha);
    }

    @Unique
    private void nc$prepareFadeIn() {
        nc$alphaAnimation.setValue(0.0);
        nc$alphaAnimation.setDestinationValue(1.0);
        nc$alphaAnimation.reset();
    }

    @Unique
    private @Nullable Text onMultiplayerDisabledText() {
        if (this.client.isMultiplayerEnabled()) return null;
        else if (this.client.isUsernameBanned()) return Text.translatable("title.multiplayer.disabled.banned.name");
        else {
            BanDetails banDetails = this.client.getMultiplayerBanDetails();
            if (banDetails != null) {
                return banDetails.expires() != null
                        ? Text.translatable("title.multiplayer.disabled.banned.temporary")
                        : Text.translatable("title.multiplayer.disabled.banned.permanent");
            } else {
                return Text.translatable("title.multiplayer.disabled");
            }
        }
    }

    @Override
    public void lumora$showInstantly() {
        nc$alphaInitialized = true;
        nc$transitioning = false;
        nc$pendingAction = null;
        nc$alphaAnimation.force(1.0);
    }
}
