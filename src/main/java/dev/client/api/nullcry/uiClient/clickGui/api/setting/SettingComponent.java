package dev.client.api.nullcry.uiClient.clickGui.api.setting;

import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.util.math.MathHelper;

@Getter
@RequiredArgsConstructor()
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class SettingComponent extends Component {
    Setting setting;

    @NonFinal
    float globalAlpha = 1f;

    @NonFinal
    Float clipPaddingOverride = null;

    public void setGlobalAlpha(float alpha) {
        this.globalAlpha = MathHelper.clamp(alpha, 0f, 1f);
    }

    public void setClipPaddingOverride(float padding) {
        this.clipPaddingOverride = padding;
    }

    protected float clipPadding(float fallback) {
        return clipPaddingOverride != null ? clipPaddingOverride : fallback;
    }
}
