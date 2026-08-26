package dev.client.component.core.sync;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.world.WorldLoadEvent;
import dev.client.component.Component;
import net.minecraft.client.option.KeyBinding;

public class SyncFixComponent extends Component {

    @Subscribe
    public void onWorldLoad(WorldLoadEvent event) {
        KeyBinding.unpressAll();
        for (KeyBinding binding : mc.options.allKeys) {
            binding.setPressed(false);
        }
    }
}
