package dev.client.api.injection.accessor;

import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClickableWidget.class)
public interface IClickableWidgetAccessor {
    @Accessor("width") void setWidth(int w);
}