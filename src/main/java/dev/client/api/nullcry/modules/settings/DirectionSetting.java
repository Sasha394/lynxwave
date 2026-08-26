package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.MathHelper;

import java.util.function.Supplier;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DirectionSetting extends Setting {
    float x;
    float y;

    public DirectionSetting(String name, Supplier<Boolean> shown) {
        super(name, shown);
        this.x = 0f;
        this.y = 0f;
    }

    public DirectionSetting applyDefault(float x, float y) {
        this.x = MathHelper.clamp(x, -1f, 1f);
        this.y = MathHelper.clamp(y, -1f, 1f);
        return this;
    }

    public DirectionSetting set(float x, float y) {
        return applyDefault(x, y);
    }

    @Override
    public DirectionSetting collection(Collection collection) {
        collection.put(this);
        return this;
    }
}
