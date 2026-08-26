package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.api.injection.accessor.IChatHudAccessor;
import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.misc.NameProtect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public class ChatHudMixins {
    @Unique private static final Pattern COORDS_PATTERN = Pattern.compile("\\[(\\-?\\d+) (\\-?\\d+) (\\-?\\d+)]");

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        Text modified = makeCoordinatesClickable(message);

        if (NameProtect.INSTANCE != null && NameProtect.INSTANCE.isEnabled()) {
            modified = NameProtect.INSTANCE.patchText(modified);
        }

        ChatHudLine line = new ChatHudLine(
                MinecraftClient.getInstance().inGameHud.getTicks(),
                modified,
                signature,
                indicator
        );

        IChatHudAccessor accessor = (IChatHudAccessor) this;
        accessor.invokeAddVisibleMessage(line);
        accessor.invokeAddMessage(line);
        ci.cancel();
    }


    @Unique
    private Text makeCoordinatesClickable(Text component) {
        if (component == null) return null;
        if (component instanceof MutableText) {
            MutableText copy = component.copy();
            copy.getSiblings().replaceAll(this::makeCoordinatesClickable);

            Matcher matcher = COORDS_PATTERN.matcher(copy.getString());
            if (matcher.find()) {
                String x = matcher.group(1);
                String y = matcher.group(2);
                String z = matcher.group(3);

                String prefix = LynxWave.getInstance()
                        .getCmdInitializer()
                        .getCommandDispatcher()
                        .getPrefix()
                        .get();

                String gpsCommand = String.format("%sgps add %s %s %s %s", prefix, "Ивент", x, y, z);

                copy.setStyle(
                        copy.getStyle()
                                .withClickEvent(new ClickEvent.SuggestCommand(gpsCommand))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("§7Нажмите, чтобы вставить GPS-команду")))
                );
            }

            return copy;
        }
        return component;
    }
}
