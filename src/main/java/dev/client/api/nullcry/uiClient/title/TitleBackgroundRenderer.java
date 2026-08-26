package dev.client.api.nullcry.uiClient.title;

import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.modules.core.render.Interface;

/**
 * Provides a vertex tint for the built-in "Альтернативный" wallpaper.
 * Texture multiplication makes its white highlights inherit Interface
 * colors while dark pixels remain dark instead of receiving a flat overlay.
 */
public final class TitleBackgroundRenderer {
    public static final String ALTERNATIVE_BACKGROUND = "images/title2.png";

    private TitleBackgroundRenderer() {
    }

    public static QuadColorState resolveTextureColor(String path) {
        if (!isAlternative(path) || Interface.INSTANCE == null) {
            return QuadColorState.WHITE;
        }

        // A single animated colour prevents opposite rainbow corners from
        // averaging back to grey in the bright centre of the wallpaper.
        int accent = ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), 255);
        return new QuadColorState(accent);
    }

    private static boolean isAlternative(String path) {
        return path != null && ALTERNATIVE_BACKGROUND.equalsIgnoreCase(path.replace('\\', '/'));
    }

}
