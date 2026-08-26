package dev.client.modules.core.combat;

/**
 * Воспроизводит звук при ударе по живой цели.
 * Нужны WAV-файлы в resources/assets/lynxwave/sounds/:
 * hit_moan1.wav, hit_moan2.wav, hit_moan3.wav, hit_moan4.wav (Moan),
 * hit_metallic.wav (Metallic), hit_crime.wav (Crime).
 */
import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.helper.other.SoundUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

public class HitSound extends Module {

    public static HitSound INSTANCE;

    private final ModeElement soundType = new ModeElement("Тип звука", () -> true)
            .set("Moan", "Metallic", "Crime")
            .applyDefault("Moan")
            .register(this);

    private final Slider volume = new Slider("Громкость", () -> true)
            .set(0.1f, 1.0f, 0.05f)
            .applyDefault(1.0f)
            .register(this);

    public HitSound() {
        super("HitSound", ModuleCategory.Combat, "Воспроизводит звук при ударе по цели");
    }

    @Subscribe
    public void onAttack(PlayerAttackEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        playSelectedSound();
    }

    private void playSelectedSound() {
        float vol = volume.getValue() != null ? volume.getValue() : 1.0f;
        vol = Math.max(0.1f, Math.min(1.0f, vol));

        if (soundType.isSelected("Moan")) {
            playRandomMoan(vol);
        } else if (soundType.isSelected("Metallic")) {
            new SoundUtil("hit_metallic", vol).play();
        } else if (soundType.isSelected("Crime")) {
            new SoundUtil("hit_crime", vol).play();
        }
    }

    private void playRandomMoan(float vol) {
        int random = ThreadLocalRandom.current().nextInt(4);
        String name = "hit_moan" + (random + 1);
        new SoundUtil(name, vol).play();
    }
}
