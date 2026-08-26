package dev.client.modules.core.misc;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Input;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;


public class NameProtect extends Module implements ClientApi {

    public static NameProtect INSTANCE;

    public final Input fakeName = new Input("Ник", () -> true, "Player", false).register(this);

    public NameProtect() {
        super("NameProtect", ModuleCategory.Misc, "Визуально скрывает ник игрока");
    }

    public String patchName(String text) {
        if (text == null) return null;
        if (mc == null || mc.getSession() == null) return text;

        String repl = getFakeName();
        String clientUsername = mc.getSession().getUsername();
        if (clientUsername != null && !clientUsername.isEmpty()) {
            text = text.replace(clientUsername, repl);
        }

        if (mc.player != null) {
            String display = mc.player.getDisplayName().getString();
            if (display != null && !display.isEmpty()) {
                text = text.replace(display, repl);
            }
        }

        return text;
    }

    public Text patchText(Text in) {
        if (in == null) return Text.empty();
        if (!isEnabled()) return in;

        MutableText out;
        if (in.getContent() instanceof PlainTextContent plain) {
            String patched = patchName(plain.string());
            out = Text.literal(patched != null ? patched : "");
            out.setStyle(in.getStyle());
        } else {
            out = in.copy();
        }

        out.getSiblings().clear();
        for (Text sib : in.getSiblings()) {
            out.append(patchText(sib));
        }
        return out;
    }

    public String getFakeName() {
        String v = fakeName != null ? fakeName.getValue() : null;
        return (v == null || v.isBlank()) ? "Player" : v;
    }
}

