package dev.client.api.nullcry.helper.other;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TimerUtil {

    public TimerUtil() {
        this.lastMS = System.currentTimeMillis();
    }

    long lastMS;

    public static TimerUtil create() {
        return new TimerUtil();
    }

    public void resetCounter() {
        lastMS = System.currentTimeMillis();
    }

    public boolean isReached(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public boolean isFinished(final double delay) {
        return System.currentTimeMillis() - delay >= lastMS;
    }

    public void setTime(long time) {
        lastMS = time;
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMS;
    }

    public int elapsedTime() {
        return Math.toIntExact(System.currentTimeMillis() - this.lastMS);
    }

    public void reset() {
        lastMS = System.currentTimeMillis();
    }

    public boolean hasTimeElapsed(long time, boolean reset) {
        if (System.currentTimeMillis() - lastMS > time) {
            if (reset) reset();
            return true;
        }

        return false;
    }
}
