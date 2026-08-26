package dev.client.api.nullcry.helper.player;

import dev.client.api.nullcry.ClientApi;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

@UtilityClass
public class PotionUtil implements ClientApi {

    public boolean isBadEffect(StatusEffectInstance effect) {
        return effect.getEffectType() == StatusEffects.SLOWNESS
                || effect.getEffectType() == StatusEffects.BLINDNESS
                || effect.getEffectType() == StatusEffects.WEAKNESS
                || effect.getEffectType() == StatusEffects.WITHER
                || effect.getEffectType() == StatusEffects.POISON
                || effect.getEffectType() == StatusEffects.MINING_FATIGUE
                || effect.getEffectType() == StatusEffects.NAUSEA
                || effect.getEffectType() == StatusEffects.UNLUCK
                || effect.getEffectType() == StatusEffects.HUNGER;
    }
}
