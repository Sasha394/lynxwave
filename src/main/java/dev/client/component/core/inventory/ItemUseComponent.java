package dev.client.component.core.inventory;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.entity.world.ItemUseHelper;
import dev.client.component.Component;

public class ItemUseComponent extends Component {

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        ItemUseHelper.handleUpdate();
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        ItemUseHelper.handlePacket(event);
    }
}
