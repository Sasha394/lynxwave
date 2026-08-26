package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.render.Interface;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class AutoLeave extends Module {

    public AutoLeave() {
        super("AutoLeave", ModuleCategory.Misc, "Автоматически выполняет заданное действие при появлении игрока");
    }

    ModeElement action = new ModeElement("Действие", () -> true).set("Kick", "/hub", "/spawn", "/home", "Staff").defaultValue("Kick").register(this);
    Slider radius = new Slider("Радиус", () -> !action.isSelected("Staff")).set(1f, 100f, 0.5f).defaultValue(50f).register(this);
    CheckBox ignoreFriends = new CheckBox("Игнорить друзей", () -> true).defaultValue(true).register(this);

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (action.isSelected("Staff")) {
            handleStaffAction();
            return;
        }

        mc.world.getPlayers().stream()
                .filter(this::isEligiblePlayer)
                .findFirst()
                .ifPresent(this::executeAction);
    }

    private void handleStaffAction() {
        if (mc.player == null || mc.world == null || mc.player.networkHandler == null) {
            return;
        }

        var interfaceModule = Interface.INSTANCE;
        if (interfaceModule == null) {
            return;
        }

        var staffList = interfaceModule.getStaffList();
        if (staffList == null) {
            return;
        }

        var detectedStaffs = staffList.getDetectedStaffs();
        if (!detectedStaffs.isEmpty()) {
            mc.player.networkHandler.sendChatCommand("hub");
            MutableText message = Text.literal("Обнаружен стафф ");
            for (int i = 0; i < detectedStaffs.size(); i++) {
                var staff = detectedStaffs.get(i);
                if (i > 0) {
                    message.append(Text.literal(", "));
                }
                message.append(staff.getDisplay().copy());
            }
            printClient(message);
            toggle();
        }
    }

    private boolean isEligiblePlayer(PlayerEntity player) {
        if (ignoreFriends.getEnabled() && handlerClient().getFriendManager().isFriend(player.getGameProfile().name())) {
            return false;
        }

        return player.isAlive()
                && player.getHealth() > 0.0f
                && player.distanceTo(mc.player) <= radius.getValue()
                && player != mc.player
                && PlayerHelper.isNameValid(player.getName().getString());
    }

    private void executeAction(PlayerEntity player) {
        if (mc.player == null || mc.player.networkHandler == null) {
            return;
        }

        float distance = mc.player.distanceTo(player);
        String distanceInfo = String.format(" (%.1f блоков)", distance);

        if (!action.isSelected("Kick")) {
            String command = action.getValue();
            printChat(command);

            MutableText message = Text.literal("Ливнул")
                    .append(Text.literal(" от "))
                    .append(player.getDisplayName().copy())
                    .append(Text.literal(distanceInfo));
            printClient(message);
        } else {
            MutableText reason = Text.literal("Причина лива: 1488 \n").append(player.getDisplayName().copy());
            mc.getNetworkHandler().getConnection().disconnect(reason);

            MutableText message = Text.literal("Ливнул от ")
                    .append(player.getDisplayName().copy())
                    .append(Text.literal(distanceInfo));
            printClient(message);
        }
        toggle();
    }
}