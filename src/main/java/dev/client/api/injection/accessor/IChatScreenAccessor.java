package dev.client.api.injection.accessor;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface IChatScreenAccessor {
    @Accessor("chatField")
    TextFieldWidget getChatField();

    @Accessor("chatInputSuggestor")
    ChatInputSuggestor getChatInputSuggestor();
}

