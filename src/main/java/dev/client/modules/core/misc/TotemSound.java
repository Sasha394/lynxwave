package dev.client.modules.core.misc;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public class TotemSound extends Module {

    public static TotemSound INSTANCE;

    private static final String[] SOUND_IDS = new String[]{
            "1",
            "11",
            "222",
            "333",
            "444",
            "52_s2bby3v",
            "555",
            "ahah",
            "ahoh",
            "aibla",
            "am-am-am",
            "anime-ahh_1",
            "apple-pay",
            "araara",
            "bax",
            "bliaiaiat",
            "bone-crack",
            "burp",
            "kat",
            "chicken-on-tree-scr",
            "eqed132",
            "grob",
            "hog-rider",
            "kat",
            "mugi",
            "nanax",
            "ne-nado-diada",
            "oi-oi-oe-oi-a-eye-e",
            "pikmi",
            "posiobam",
            "pozovi",
            "sebalos-chudishche_buztiif",
            "ser-da-ser_o6s3ouc",
            "spongebob-fail",
            "stoniland",
            "suda",
            "tuco-get-out",
            "use_totem",
            "zxc",
            "zxcghoul",
            "ia-uzhe-krasnyi",
            "ia-uzhe-krasnyi",
            "lynxxxx"

    };

    ModeElement sound = new ModeElement("Звук", () -> true)
            .set(
                    "sound 1", "sound 2", "sound 3", "sound 4", "sound 5", "sound 6", "sound 7", "sound 8", "sound 9", "sound 10",
                    "sound 11", "sound 12", "sound 13", "sound 14", "sound 15", "sound 16", "sound 17", "sound 18", "sound 19", "sound 20",
                    "sound 21", "sound 22", "sound 23", "sound 24", "sound 25", "sound 26", "sound 27", "sound 28", "sound 29", "sound 30",
                    "sound 31", "sound 32", "sound 33", "sound 34", "sound 35", "sound 36", "sound 37", "sound 38", "sound 39", "sound 40",
                    "sound 41", "sound 42", "sound 43"
            )
            .register(this);

    Slider volume = new Slider("Громкость", () -> true)
            .set(0.0f, 2.0f, 0.1f)
            .applyDefault(1.0f)
            .register(this);

    Slider pitch = new Slider("Тон", () -> true)
            .set(0.5f, 2.0f, 0.1f)
            .applyDefault(1.0f)
            .register(this);

    public TotemSound() {
        super("TotemSound", ModuleCategory.Misc, "Замена звука тотема");
    }

    public boolean shouldReplace() {
        return isEnabled();
    }

    public SoundEvent replacementEvent() {
        if (!isEnabled()) return SoundEvents.ITEM_TOTEM_USE;

        String s = sound.getValue();
        if (s == null) return SoundEvents.ITEM_TOTEM_USE;

        int idx = parseSoundIndex(s);
        if (idx >= 0 && idx < SOUND_IDS.length) return ensure(Identifier.of("lynxwave", "totem_" + SOUND_IDS[idx]));

        return SoundEvents.ITEM_TOTEM_USE;
    }

    public float replacementVolume() {
        return volume.getValue();
    }

    public float replacementPitch() {
        return pitch.getValue();
    }

    private static int parseSoundIndex(String s) {
        String x = s.trim().toLowerCase();
        if (!x.startsWith("sound")) return -1;
        int i = 5;
        while (i < x.length() && x.charAt(i) == ' ') i++;
        if (i >= x.length()) return -1;
        int n = 0;
        boolean any = false;
        while (i < x.length()) {
            char c = x.charAt(i);
            if (c < '0' || c > '9') break;
            any = true;
            n = n * 10 + (c - '0');
            i++;
        }
        if (!any) return -1;
        return n - 1;
    }

    private static SoundEvent ensure(Identifier id) {
        try {
            if (Registries.SOUND_EVENT.containsId(id)) return Registries.SOUND_EVENT.get(id);
        } catch (Throwable ignored) {
        }
        SoundEvent e = SoundEvent.of(id);
        try {
            if (!Registries.SOUND_EVENT.containsId(id)) Registry.register(Registries.SOUND_EVENT, id, e);
        } catch (Throwable ignored) {
        }
        return e;
    }
}
