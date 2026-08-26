package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Input;
import dev.client.api.nullcry.modules.settings.ModeElement;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;

import java.util.Random;
import java.util.regex.Pattern;

public class AutoAuth extends Module {

    public AutoAuth() {
        super("AutoAuth", ModuleCategory.Misc, "Автоматически регистрирует/авторизирует на сервере");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("Custom", "Random").defaultValue("Custom").register(this);
    Input register = new Input("Пароль для регистрации", () -> mode.isSelected("Custom")).defaultValue("").register(this);
    Input login = new Input("Пароль для авторизации", () -> mode.isSelected("Custom")).defaultValue("").register(this);

    private String lastMessage = "";
    private String lastHandledKey = "";

    private static final Pattern REGISTER_HINT = Pattern.compile(
            "(?:^|\\s)(?:/reg(?:ister)?\\b|зарегистрируй(?:тесь|ся)?|регист|регистрация)(?:\\s|$)"
            , Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern LOGIN_HINT = Pattern.compile(
            "(?:^|\\s)(?:/l(?:ogin)?\\b|авторизуй(?:тесь|ся)?|логин|вход)(?:\\s|$)"
            , Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (!event.isReceive() || mc == null || mc.player == null) return;

        String chatMessage = null;

        if (event.getPacket() instanceof GameMessageS2CPacket gp) {
            Text t = gp.content();
            if (t != null) chatMessage = t.getString();
        }

        if (chatMessage == null && event.getPacket() instanceof ChatMessageS2CPacket cp) {
            Text unsigned = cp.unsignedContent();
            if (unsigned != null) {
                chatMessage = unsigned.getString();
            } else {
                chatMessage = cp.body().content();
            }
        }

        if (chatMessage == null || chatMessage.isEmpty()) return;

        chatMessage = chatMessage.toLowerCase().trim();
        lastMessage = chatMessage;

        if (containsRegisterMessage(chatMessage)) {
            String key = "reg|" + chatMessage;
            if (!key.equals(lastHandledKey)) {
                lastHandledKey = key;
                executeRegisterCommand();
            }
        } else if (containsLoginMessage(chatMessage)) {
            String key = "login|" + chatMessage;
            if (!key.equals(lastHandledKey)) {
                lastHandledKey = key;
                executeLoginCommand();
            }
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!mode.isSelected("Custom")) return;

        if (containsRegisterMessage(lastMessage) && (register.getValue() == null || register.getValue().isEmpty())) {
            printClient("Укажите пароль для регистрации.");
            this.setEnabled(false);
        }

        if (containsLoginMessage(lastMessage) && (login.getValue() == null || login.getValue().isEmpty())) {
            printClient("Укажите пароль для авторизации.");
            this.setEnabled(false);
        }
    }

    private boolean containsRegisterMessage(String message) {
        return REGISTER_HINT.matcher(message).find();
    }

    private boolean containsLoginMessage(String message) {
        return LOGIN_HINT.matcher(message).find();
    }

    private void executeRegisterCommand() {
        String pass;
        if (mode.isSelected("Custom")) {
            pass = register.getValue();
            if (pass == null || pass.isEmpty()) {
                printClient("Укажите пароль для регистрации.");
                return;
            }
        } else {
            pass = generateRandomPassword();
        }

        printChat("/reg " + pass + " " + pass);
        this.toggle();
    }

    private void executeLoginCommand() {
        String pass;
        if (mode.isSelected("Custom")) {
            pass = login.getValue();
            if (pass == null || pass.isEmpty()) {
                printClient("Укажите пароль для авторизации.");
                return;
            }
        } else {
            pass = generateRandomPassword();
        }

        printChat("/login " + pass);
        this.toggle();
    }

    private String generateRandomPassword() {
        int length = 12;
        String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder passwordBuilder = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            passwordBuilder.append(characters.charAt(random.nextInt(characters.length())));
        }
        return passwordBuilder.toString();
    }

    @Override
    public void onEnabled() {
        super.onEnabled();

        if (mode.isSelected("Custom")) {
            boolean emptyRegister = register.getValue() == null || register.getValue().isEmpty();
            boolean emptyLogin = login.getValue() == null || login.getValue().isEmpty();

            if (emptyRegister && emptyLogin) {
                printClient("Укажите пароль для регистрации и авторизации.");
                this.setEnabled(false);
            } else if (emptyRegister) {
                printClient("Укажите пароль для регистрации.");
                this.setEnabled(false);
            } else if (emptyLogin) {
                printClient("Укажите пароль для авторизации.");
                this.setEnabled(false);
            }
        }

        lastHandledKey = "";
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        lastHandledKey = "";
    }
}
