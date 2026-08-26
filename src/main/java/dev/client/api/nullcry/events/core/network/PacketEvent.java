package dev.client.api.nullcry.events.core.network;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.packet.Packet;

@Getter
@Setter
@AllArgsConstructor
public class PacketEvent extends Event {
    Packet<?> packet;
    Type type;

    public boolean isSend() {
        return type == Type.SEND;
    }

    public boolean isReceive() {
        return type == Type.RECEIVE;
    }

    public enum Type {
        SEND, RECEIVE
    }
}
