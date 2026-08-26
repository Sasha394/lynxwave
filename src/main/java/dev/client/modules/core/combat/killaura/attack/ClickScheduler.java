package dev.client.modules.core.combat.killaura.attack;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import dev.client.modules.core.combat.KillAura;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClickScheduler implements ClientApi {
    private final int[] funTimeTicks = new int[]{10, 11, 10, 13}, spookyTicks = new int[]{11, 10, 13, 10, 12, 11, 12}, defaultTicks = new int[]{10, 11};
    long lastClickTime = System.currentTimeMillis();

    public boolean isCooldownComplete(boolean dynamicCooldown, int ticks) {
        boolean dynamicReady = !dynamicCooldown || hasTicksElapsedSinceLastClick(Math.max(0, tickCount() - ticks));

        float partialTicks;
        if (dynamicCooldown) {
            boolean syncWithTps = KillAura.INSTANCE != null && KillAura.INSTANCE.tpsSync.getEnabled();
            partialTicks = syncWithTps ? LynxWave.getInstance().getServerUtils().getAdjustTicks() : 0.5f;
            if (partialTicks <= 0.0f) {
                partialTicks = 0.5f;
            }
        } else {
            partialTicks = ticks;
        }

        return dynamicReady && mc.player.getAttackCooldownProgress(partialTicks) > 0.9F;
    }

    public boolean hasTicksElapsedSinceLastClick(int ticks) {
        float adjustTicks = LynxWave.getInstance().getServerUtils().getAdjustTicks();
        if (adjustTicks <= 0.0f) {
            adjustTicks = 20.0f;
        }
        return lastClickPassed() >= (ticks * 50L * (20F / adjustTicks));
    }

    public long lastClickPassed() {
        return System.currentTimeMillis() - lastClickTime;
    }

    public void recalculate() {
        lastClickTime = System.currentTimeMillis();
    }

    int tickCount() {
        int count = LynxWave.getInstance().getAttackPerpetrator().getAttackHandler().getCount();

        if (ConnectionHelper.isFT()) {
            return funTimeTicks[count % funTimeTicks.length];
        } else if (ConnectionHelper.isSpookyTime()) {
            return spookyTicks[count % spookyTicks.length];
        } else {
            return defaultTicks[count % defaultTicks.length];
        }
    }
}