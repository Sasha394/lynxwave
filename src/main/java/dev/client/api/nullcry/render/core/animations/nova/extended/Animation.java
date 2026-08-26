package dev.client.api.nullcry.render.core.animations.nova.extended;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.other.TimerUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.MathHelper;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class Animation implements ClientApi {
    public TimerUtil timerUtil = new TimerUtil();
    protected int duration;
    protected double endPoint;
    protected Direction direction;

    public Animation(int ms, double endPoint) {
        this(ms, endPoint, Direction.FORWARDS);
    }

    public Animation(int duration, double endPoint, Direction direction) {
        this.duration = duration;
        this.endPoint = endPoint;
        this.direction = direction;
    }

    public static double deltaTime() {
        return mc.getCurrentFps() > 0 ? (1.0000 / mc.getCurrentFps()) : 1;
    }

    public static float fast(float end, float start, float multiple) {
        return (1 - MathHelper.clamp((float) (deltaTime() * multiple), 0, 1)) * end + MathHelper.clamp((float) (deltaTime() * multiple), 0, 1) * start;
    }

    public Animation setDirection(Direction direction) {
        if (this.direction != direction) {
            this.direction = direction;
            timerUtil.setTime(System.currentTimeMillis() - (duration - Math.min(duration, timerUtil.getTime())));
        }
        return this;
    }

    public Animation setDirection(boolean forwards) {
        Direction direction = forwards ? Direction.FORWARDS : Direction.BACKWARDS;
        if (this.direction != direction) {
            this.direction = direction;
            timerUtil.setTime(System.currentTimeMillis() - (duration - Math.min(duration, timerUtil.getTime())));
        }
        return this;
    }

    public Animation setDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public Number getOutput() {
        if (direction.forwards()) {
            if (isDone())
                return endPoint;
            return getEquation(timerUtil.getTime() / (double) duration) * endPoint;
        } else {
            if (isDone()) {
                return 0.0;
            }
            if (correctOutput()) {
                double revTime = Math.min(duration, Math.max(0, duration - timerUtil.getTime()));
                return getEquation(revTime / (double) duration) * endPoint;
            }
            return (1 - getEquation(timerUtil.getTime() / (double) duration)) * endPoint;
        }
    }

    public double getOutputs() {
        double time = timerUtil.getTime() / (double) duration;

        if (direction == Direction.FORWARDS) {
            if (isDone()) {
                return endPoint;
            }
            return getEquation(time) * endPoint;
        } else {
            if (isDone()) {
                return 0.0;
            }
            if (correctOutput()) {
                double revTime = Math.min(duration, Math.max(0, duration - timerUtil.getTime()));
                return getEquation(revTime / (double) duration) * endPoint;
            } else {
                return (1 - getEquation(time)) * endPoint;
            }
        }
    }

    protected boolean correctOutput() {
        return false;
    }

    public boolean finished(Direction direction) {
        return isDone() && this.direction.equals(direction);
    }

    public Animation reset() {
        timerUtil.resetCounter();
        return this;
    }

    public void setEndPoint(double endPoint) {
        this.endPoint = endPoint;
    }

    public boolean isDone() {
        return timerUtil.isReached(duration);
    }

    protected abstract double getEquation(double x);

}
