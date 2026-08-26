package dev.client.api.nullcry.events;

import lombok.Getter;
import lombok.Setter;

@Setter
public class Event {
    @Getter
    protected boolean isCancelled = false;

    public void cancelled() {
        this.isCancelled = true;
    }

    public void open() {
        isCancelled = false;
    }

}
