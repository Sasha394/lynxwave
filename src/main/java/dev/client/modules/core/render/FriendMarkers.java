package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.render.core.Draw3DUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Friend Markers — выделяет друзей маркерами над головами (режим «Маркеры») или опцией «Головы».
 */
public class FriendMarkers extends Module implements ClientApi {

    public static FriendMarkers INSTANCE;

    private static final int FRIEND_COLOR = 0xFF34C759; // 52, 199, 89

    private final ModeElement mode = new ModeElement("Режим", () -> true).set("Маркеры", "Головы").defaultValue("Маркеры").register(this);

    public FriendMarkers() {
        super("Friend Markers", ModuleCategory.Render, "Выделяет друзей маркерами над головами");
    }

    @Subscribe
    public void onDraw3D(RenderEvent.Draw3D event) {
        if (!mode.isSelected("Маркеры")) return;
        if (mc.world == null || mc.player == null) return;

        float tickDelta = event.getTickCounter().getTickProgress(true);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!handlerClient().getFriendManager().isFriend(player.getName().getString())) continue;

            double x = player.lastX + (player.getX() - player.lastX) * tickDelta;
            double y = player.lastY + (player.getY() - player.lastY) * tickDelta;
            double z = player.lastZ + (player.getZ() - player.lastZ) * tickDelta;

            float size = 0.1f;
            double yOffset = player.getHeight() + 0.4;
            Box box = new Box(
                    x - size, y + yOffset - size, z - size,
                    x + size, y + yOffset + size, z + size
            );
            Draw3DUtil.drawBox(box, FRIEND_COLOR, 2f, true, true, true);
        }
    }
}
