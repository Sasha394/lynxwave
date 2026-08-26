package dev.client.api.injection.accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatHud.class)
public interface IChatHudAccessor {
    @Invoker("addMessage") void invokeAddMessage(ChatHudLine message);
    @Invoker("addVisibleMessage") void invokeAddVisibleMessage(ChatHudLine message);

    @Accessor("messages")
    List<ChatHudLine> getMessages();

    @Accessor("scrolledLines")
    int getScrolledLines();

    @Accessor("scrolledLines")
    void setScrolledLines(int value);
}