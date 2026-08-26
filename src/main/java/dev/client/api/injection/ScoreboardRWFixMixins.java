package dev.client.api.injection;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Scoreboard.class)
public abstract class ScoreboardRWFixMixins {
    @Shadow public abstract @Nullable Team getScoreHolderTeam(String scoreHolderName);

    @Inject(method = "removeScoreHolderFromTeam",at = @At(value = "HEAD"), cancellable = true)
    public void remove(String scoreHolderName, Team team, CallbackInfo ci){
        if (this.getScoreHolderTeam(scoreHolderName) != team) { // RW ебаная помойка в рот ее ебал что сук инвалиды не могут сами зафиксить
            ci.cancel();
        }
    }
}