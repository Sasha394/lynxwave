package dev.client.api.injection.accessor;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerListHud.class)
public interface IPlayerListHudAccessor {
    @Accessor("header")
    Text getHeader();
    @Accessor("footer") Text getFooter();
}