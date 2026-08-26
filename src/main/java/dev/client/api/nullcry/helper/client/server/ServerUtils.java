package dev.client.api.nullcry.helper.client.server;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import lombok.Getter;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;

@Getter
public class ServerUtils {
    protected final float[] ticks = new float[20];
    private int serverSlot;
    protected int index;
    protected long lastPacketTime;
    private long timestamp;

    private float TPS = 20;
    private float adjustTicks = 0;

    public ServerUtils() {
        this.index = 0;
        this.lastPacketTime = -1L;
        Arrays.fill(ticks, 0.0F);
        LynxWave.getInstance().getEventBus().register(this);
    }

    private void update() {
        if (this.lastPacketTime != -1L) {
            float timeElapsed = (float) (System.currentTimeMillis() - this.lastPacketTime) / 1000.0F;
            ticks[this.index % ticks.length] = MathHelper.clamp(20.0F / timeElapsed, 0.0F, 20.0F);
            this.index++;
        }
        this.lastPacketTime = System.currentTimeMillis();
    }

    @Subscribe
    public void onPacket(PacketEvent packetEvent) {
        update();
        if (packetEvent.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            updateTPS();
        }

        if (packetEvent.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) {
            serverSlot = packet.getSelectedSlot();
        }
    }

    private void updateTPS() {
        long delay = System.nanoTime() - timestamp;
        if (delay == 0) return;

        float maxTPS = 20.0F;
        float rawTPS = maxTPS * (1e9f / delay);

        float boundedTPS = MathHelper.clamp(rawTPS, 0.0F, maxTPS);

        TPS = (float) round(boundedTPS);
        adjustTicks = boundedTPS - maxTPS;

        timestamp = System.nanoTime();
    }

    public double round(final double input) {
        return Math.round(input * 100.0) / 100.0;
    }
}
