package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixins extends Screen {

    protected GameMenuScreenMixins(Text title) {
        super(title);
    }

    @Inject(method = "initWidgets", at = @At("HEAD"))
    private void onInitWidgets(CallbackInfo ci) {
        String reconnect = "Reconnect";
        if (this.client.getLanguageManager().getLanguage().equals("ru_ru")) {
            reconnect = "Переподключиться";
        }

        int btnWidth = 204;
        int btnHeight = 20;
        int x = this.width / 2 - btnWidth / 2;
        int y = (int) (this.height * 0.25f) + 126;

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal(reconnect), btn -> {
                    if (ConnectionHelper.isPvP()) {
                        LynxWave.getInstance().getNotificationManager().add("Вы не можете выйти в PvP режиме!", 3, NotificationType.WARNING, RenderType.SCREEN);
                        return;
                    }

                    ServerInfo serverData = this.client.getCurrentServerEntry();
                    if (serverData == null) {
                        LynxWave.getInstance().getNotificationManager().add("Вы не подключены к серверу!", 3, NotificationType.INFO, RenderType.SCREEN);
                        return;
                    }

                    if (this.client.world == null) {
                        LynxWave.getInstance().getNotificationManager().add("Мир не загружен. Попробуйте ещё раз.", 3, NotificationType.INFO, RenderType.SCREEN);
                        return;
                    }

                    this.client.world.disconnect(Text.translatable("menu.disconnected"));
                    this.client.disconnect(Text.translatable("menu.disconnected"));

                    Screen parentScreen = new MultiplayerScreen(new TitleScreen());

                    String addr = serverData.address != null ? serverData.address.trim() : "";
                    if (!ServerAddress.isValid(addr)) {
                        LynxWave.getInstance().getNotificationManager().add("Некорректный адрес сервера.", 3, NotificationType.INFO, RenderType.SCREEN);
                        return;
                    }

                    ServerAddress serverAddress = ServerAddress.parse(addr);
                    ConnectScreen.connect(parentScreen, this.client, serverAddress, serverData, false, null);
                }).dimensions(x, y, btnWidth, btnHeight).build()
        );
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LynxWave.getInstance().getNotificationManager().notificationRenders.isEmpty()) {
            LynxWave.getInstance().getNotificationManager().draw(context, RenderType.SCREEN);
        }
    }

    @Redirect(
            method = "method_19836(Lnet/minecraft/client/gui/widget/ButtonWidget;)V",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/session/report/AbuseReportContext;tryShowDraftScreen(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/gui/screen/Screen;Ljava/lang/Runnable;Z)V"
            )
    )
    private void onExitButtonTryShowDraftScreen(AbuseReportContext context, MinecraftClient client, Screen screen, Runnable disconnect, boolean showDraftScreen) {
        if (client == null || !ConnectionHelper.isPvP()) {
            context.tryShowDraftScreen(client, screen, disconnect, showDraftScreen);
            return;
        }

        boolean ru = client.getLanguageManager().getLanguage().equals("ru_ru");

        Text body = Text.literal(ru
                ? "Вы находитесь в PvP режиме. Вы точно хотите выйти?"
                : "You are in PvP mode. Are you sure you want to disconnect?");
        Text yes  = Text.literal(ru ? "Подтвердить" : "Confirm");
        Text no   = Text.literal(ru ? "Назад" : "Back");

        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                context.tryShowDraftScreen(client, screen, disconnect, showDraftScreen);
            } else {
                client.setScreen(screen);
            }
        }, Text.empty(), body, yes, no));
    }
}
