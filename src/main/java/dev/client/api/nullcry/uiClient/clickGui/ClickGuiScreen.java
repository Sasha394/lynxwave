package dev.client.api.nullcry.uiClient.clickGui;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.GLUtils;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.modules.core.render.Interface;
import dev.client.api.nullcry.render.core.animations.client.Animation;
import dev.client.api.nullcry.render.core.animations.client.Direction;
import dev.client.api.nullcry.render.core.animations.client.implement.SineOutAnimation;
import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import dev.client.api.nullcry.uiClient.clickGui.components.config.ConfigManagerComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.panel.PanelsLayer;
import dev.client.api.nullcry.uiClient.clickGui.components.search.SearchBarComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.theme.ThemeEditorComponent;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.title.TitleScreenSettings;
import dev.client.api.nullcry.uiClient.title.TitleBackgroundRenderer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClickGuiScreen extends Screen implements ClientApi {
    List<Component> componentsList = new ArrayList<>();
    SearchBarComponent searchBar = new SearchBarComponent();
    @Getter PanelsLayer panelsLayer = new PanelsLayer();
    @Getter ThemeEditorComponent themeEditor = new ThemeEditorComponent();
    //ConfigManagerComponent configManager = new ConfigManagerComponent();

    Animation revealAnimation = new SineOutAnimation().setMs(420).setValue(1f);
    Animation fadeAnimation = new SineOutAnimation().setMs(320).setValue(1f);
    public static boolean closing = false;
    @Getter @Setter private static boolean openedFromTitle = false;

    // Main panels area (center)
    final float panelsWidth = 650f;
    final float panelsHeight = 550f / 2;

    // Side panel sizes
    final float themeEditorWidth = 130f;
    final float configManagerWidth = 130f;
    final float configManagerHeight = 120f;
    final float sideGap = 8f;

    // Total layout width for centering
    final float totalWidth = themeEditorWidth + sideGap + panelsWidth + sideGap + configManagerWidth;

    Supplier<Float> baseX = () -> (mc.getWindow().getScaledWidth() - totalWidth) / 2;
    Supplier<Float> baseY = () -> (mc.getWindow().getScaledHeight() - panelsHeight) / 2;
    @NonFinal float frozenBaseX = 0f;
    @NonFinal float frozenBaseY = 0f;
    @NonFinal boolean layoutFrozen = false;

    public ClickGuiScreen() {
        super(Text.of("lynxwave.click_gui"));

        componentsList.addAll(List.of(
                //themeEditor,
                panelsLayer,
                //configManager,
                searchBar
        ));

        LynxWave.getInstance().getEventBus().register(this);
    }

    @Override
    protected void init() {
        GLUtils.setupOverlayRendering(2);
        try {
            componentsList.forEach(Component::init);

            freezeLayout();
            float bx = getLayoutBaseX();
            float by = getLayoutBaseY();

            // Theme Editor - left side, full height
            themeEditor.position(bx, by).size(themeEditorWidth, panelsHeight);

            // Main panels - center
            float panelsX = bx + themeEditorWidth + sideGap;
            panelsLayer.position(panelsX, by).size(panelsWidth, panelsHeight);

            // Search bar - below center panels
            float searchW = 207f;
            float searchX = panelsX + (panelsWidth - searchW) / 2f;
            searchBar.position(searchX, by + panelsHeight + 8f).size(searchW, 24f);

            // Config Manager - bottom right
            float configX = panelsX + panelsWidth + sideGap;
            float configY = by + panelsHeight - configManagerHeight;
            //configManager.position(configX, configY).size(configManagerWidth, configManagerHeight);

        } finally {
            GLUtils.restoreRendering();
        }

        closing = false;
        revealAnimation.setDirection(Direction.FORWARDS);
        fadeAnimation.setDirection(Direction.FORWARDS);
        revealAnimation.reset();
        fadeAnimation.reset();

        super.init();
    }

    private void freezeLayout() {
        frozenBaseX = baseX.get();
        frozenBaseY = baseY.get();
        layoutFrozen = true;
    }

    @Override
    public void close() {
        if (!closing) {
            startClosing();
            return;
        }

        finishClose();
    }

    private void startClosing() {
        closing = true;
        revealAnimation.setDirection(Direction.BACKWARDS);
        fadeAnimation.setDirection(Direction.BACKWARDS);
        revealAnimation.reset();
        fadeAnimation.reset();
    }

    private void finishClose() {
        closing = false;
        layoutFrozen = false;
        if (openedFromTitle) {
            openedFromTitle = false;
            mc.setScreen(new TitleScreen());
        } else {
            mc.setScreen(null);
        }
        searchBar.setListening(false);
        CursorsUtil.resetState();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private float getUiScale() {
        if (Interface.INSTANCE != null && Interface.INSTANCE.scale != null) {
            Float v = Interface.INSTANCE.scale.getValue();
            if (v != null) {
                float s = v / 100.0f;
                return MathHelper.clamp(s, 0.5f, 1.0f);
            }
        }
        return 1.0f;
    }

    private float getCenterX() {
        return getLayoutBaseX() + totalWidth / 2f;
    }

    private float getCenterY() {
        return getLayoutBaseY() + panelsHeight / 2f;
    }

    private float getLayoutBaseX() {
        return layoutFrozen ? frozenBaseX : baseX.get();
    }

    private float getLayoutBaseY() {
        return layoutFrozen ? frozenBaseY : baseY.get();
    }

    private double[] toLogicalMouse(double mouseX, double mouseY, float totalScale, float centerX, float centerY) {
        double overlayX = GLUtils.scaleMouseX(mouseX, 2);
        double overlayY = GLUtils.scaleMouseY(mouseY, 2);
        double logicalX = centerX + (overlayX - centerX) / totalScale;
        double logicalY = centerY + (overlayY - centerY) / totalScale;
        return new double[]{logicalX, logicalY};
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GLUtils.setupOverlayRendering(2);
        try {
            CursorsUtil.beginFrame();

            if (openedFromTitle) {
                var m = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
                int sw = GLUtils.getScaledWidthFor(2);
                int sh = GLUtils.getScaledHeightFor(2);
                String backgroundPath = TitleScreenSettings.getBackgroundPath();
                Builder.TEXTURE_BUILDER
                        .size(new SizeState(sw, sh))
                        .texture(0, 0, 1, 1, ClientTexture.of(backgroundPath))
                        .color(TitleBackgroundRenderer.resolveTextureColor(backgroundPath))
                        .build()
                        .render(m, 0, 0);
            }

            float revealProgress = revealAnimation.getOutput().floatValue();
            float fadeProgress = fadeAnimation.getOutput().floatValue();
            float combinedProgress = MathHelper.clamp(revealProgress * fadeProgress, 0f, 1f);

            float animScale = MathHelper.lerp(combinedProgress, 1.06f, 1f);
            float uiScale = getUiScale();
            float totalScale = animScale * uiScale;

            float centerX = getCenterX();
            float centerY = getCenterY();

            double[] logicalMouse = toLogicalMouse(mouseX, mouseY, totalScale, centerX, centerY);
            int renderMouseX = (int) Math.round(logicalMouse[0]);
            int renderMouseY = (int) Math.round(logicalMouse[1]);

            panelsLayer.setRevealProgress(combinedProgress);
            searchBar.setRevealProgress(combinedProgress);
            themeEditor.setRevealProgress(combinedProgress);
           // configManager.setRevealProgress(combinedProgress);
            searchBar.setListener(panelsLayer::updateFilter);

            ScissorUtil.setTransform(totalScale, centerX, centerY);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(centerX, centerY);
            context.getMatrices().scale(totalScale, totalScale);
            context.getMatrices().translate(-centerX, -centerY);

            if (combinedProgress > 0.01f) {
                componentsList.forEach(e -> e.render(context, renderMouseX, renderMouseY, delta));
            }

            context.getMatrices().popMatrix();

            ScissorUtil.resetTransform();

            super.render(context, renderMouseX, renderMouseY, delta);
            CursorsUtil.applyFrameCursor();
        } finally {
            GLUtils.restoreRendering();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (closing) {
            keepMovementWhileClosing();

            if (revealAnimation.isFinished(Direction.BACKWARDS)) {
                finishClose();
            }
        }
    }

    /**
     * Пока доигрывается анимация закрытия, экран ещё открыт, а Minecraft держит клавиши
     * движения отжатыми — игрок стоял на месте всю анимацию. Возвращаем биндам реальное
     * состояние клавиш каждый тик, как это делает GuiWalk.
     */
    private void keepMovementWhileClosing() {
        if (mc.player == null) return;

        for (KeyBinding keyBinding : MovingUtil.getMovementKeys(false)) {
            InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(keyBinding);
            keyBinding.setPressed(bound != null && InputUtil.isKeyPressed(mc.getWindow(), bound.getCode()));
        }
    }

    @Subscribe
    public void keyListener(KeyBindEvent event) {
        if (Objects.isNull(mc.currentScreen) && event.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT)
            mc.setScreen(LynxWave.getInstance().getClickGuiScreen());
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (closing) {
            return super.mouseClicked(click, doubled);
        }
        float revealProgress = revealAnimation.getOutput().floatValue();
        float fadeProgress = fadeAnimation.getOutput().floatValue();
        float combinedProgress = MathHelper.clamp(revealProgress * fadeProgress, 0f, 1f);
        float animScale = MathHelper.lerp(combinedProgress, 1.06f, 1f);
        float uiScale = getUiScale();
        float totalScale = animScale * uiScale;
        float centerX = getCenterX();
        float centerY = getCenterY();
        double[] logicalMouse = toLogicalMouse(mouseX, mouseY, totalScale, centerX, centerY);
        double renderMouseX = logicalMouse[0];
        double renderMouseY = logicalMouse[1];
        // Сверху вниз: кто первым принял клик, тот его и забирает.
        for (int i = componentsList.size() - 1; i >= 0; i--) {
            if (componentsList.get(i).mouseClicked(renderMouseX, renderMouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(new Click(renderMouseX, renderMouseY, new MouseInput(button, click.modifiers())), doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (closing) {
            return super.mouseReleased(click);
        }
        float revealProgress = revealAnimation.getOutput().floatValue();
        float fadeProgress = fadeAnimation.getOutput().floatValue();
        float combinedProgress = MathHelper.clamp(revealProgress * fadeProgress, 0f, 1f);
        float animScale = MathHelper.lerp(combinedProgress, 1.06f, 1f);
        float uiScale = getUiScale();
        float totalScale = animScale * uiScale;
        float centerX = getCenterX();
        float centerY = getCenterY();
        double[] logicalMouse = toLogicalMouse(mouseX, mouseY, totalScale, centerX, centerY);
        double renderMouseX = logicalMouse[0];
        double renderMouseY = logicalMouse[1];
        componentsList.forEach(e -> e.mouseReleased(renderMouseX, renderMouseY, button));
        return super.mouseReleased(new Click(renderMouseX, renderMouseY, new MouseInput(button, click.modifiers())));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (closing) {
            return super.keyPressed(input);
        }
        componentsList.forEach(e -> e.keyPressed(keyCode, scanCode, modifiers));
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (closing) {
            return super.keyReleased(input);
        }
        componentsList.forEach(e -> e.keyReleased(keyCode, scanCode, modifiers));
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (closing) {
            return super.charTyped(input);
        }
        componentsList.forEach(e -> e.charTyped(chr, modifiers));
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        float revealProgress = revealAnimation.getOutput().floatValue();
        float fadeProgress = fadeAnimation.getOutput().floatValue();
        float combinedProgress = MathHelper.clamp(revealProgress * fadeProgress, 0f, 1f);
        float animScale = MathHelper.lerp(combinedProgress, 1.06f, 1f);
        float uiScale = getUiScale();
        float totalScale = animScale * uiScale;
        float centerX = getCenterX();
        float centerY = getCenterY();
        double[] logicalMouse = toLogicalMouse(mouseX, mouseY, totalScale, centerX, centerY);
        double renderMouseX = logicalMouse[0];
        double renderMouseY = logicalMouse[1];
        // Иначе колесо над выдачей поиска доезжало и до панели под ней.
        for (int i = componentsList.size() - 1; i >= 0; i--) {
            if (componentsList.get(i).mouseScrolled(renderMouseX, renderMouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(renderMouseX, renderMouseY, horizontalAmount, verticalAmount);
    }
}
