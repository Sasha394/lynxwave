package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.render.core.animations.client.Direction;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public class KeyBind extends Setting {
    Integer key = -1;
    Boolean selected = false;

    public KeyBind(String name, Supplier<Boolean> visible) {
        super(name, visible);
    }

    public KeyBind set(Integer keyCode) {
        this.key = keyCode;
        this.setSelected(false);
        return this;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        this.getAnimation().setDirection(selected ? Direction.FORWARDS : Direction.BACKWARDS);
        this.getAnimation().reset();
    }

    public KeyBind applyDefault(int keyCode) {
        this.key = keyCode;
        return this;
    }

    @Override
    public KeyBind collection(Collection collection) {
        collection.put(this);
        return this;
    }
}
