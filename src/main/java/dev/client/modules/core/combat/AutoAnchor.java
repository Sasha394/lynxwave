package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.BlockPlaceEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.List;

public class AutoAnchor extends Module {

    public AutoAnchor() {
        super("AutoAnchor", ModuleCategory.Combat, "Автоматически взрывает якорь");
    }

    CheckBox safeYourSelf = new CheckBox("Не взрывать себя", () -> true).defaultValue(true).register(this);
    CheckBox safeFriend = new CheckBox("Не взрывать друзей", () -> true).defaultValue(true).register(this);
    CheckBox onlyPvP = new CheckBox("Только в пвп", () -> true).defaultValue(false).register(this);
    Slider radiusFriend = new Slider("Радиус", () -> safeFriend.getEnabled()).set(1, 10, 0.1f).defaultValue(5).register(this);

    BlockPos targetPosition;
    int actionCounter = 0;
    int glowstoneSlot = -1;

    @Subscribe
    public void onBlockPlacement(BlockPlaceEvent event) {
        if (!isPvPActive()) return;

        if (event.getStack().getItem() != Item.fromBlock(Blocks.RESPAWN_ANCHOR)) return;
        if (targetPosition != null) return;
        if (mc.player.getOffHandStack().getItem() == Item.fromBlock(Blocks.GLOWSTONE)) return;

        targetPosition = event.getPosition();
        glowstoneSlot = findGlowstoneSlot();
        actionCounter = 0;
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!isPvPActive() || targetPosition == null) return;

        if (safeYourSelf.getEnabled() && mc.player.getY() > targetPosition.getY()) {
            targetPosition = null;
            return;
        }

        if (safeFriend.getEnabled() && isFriendNearby(targetPosition)) {
            targetPosition = null;
            return;
        }

        switch (actionCounter) {
            case 0 -> {
                sendPacket(new UpdateSelectedSlotC2SPacket(glowstoneSlot));
                interactWithBlock(targetPosition);
            }
            case 1 -> {
                sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
            }
            case 2 -> {
                interactWithBlock(targetPosition);
            }
        }

        if (actionCounter >= 2) targetPosition = null;

        actionCounter++;
    }

    private void interactWithBlock(BlockPos position) {
        BlockHitResult hitResult = new BlockHitResult(mc.crosshairTarget.getPos(), Direction.UP, position, true);
        sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findGlowstoneSlot() {
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == Item.fromBlock(Blocks.GLOWSTONE)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isFriendNearby(BlockPos position) {
        double explosionRadius = radiusFriend.getValue();

        Box area = new Box(
                position.getX() - explosionRadius, position.getY() - explosionRadius, position.getZ() - explosionRadius,
                position.getX() + explosionRadius, position.getY() + explosionRadius, position.getZ() + explosionRadius
        );

        List<PlayerEntity> nearbyEntities = mc.world.getEntitiesByClass(PlayerEntity.class, area, entity -> true);
        for (Entity entity : nearbyEntities) {
            if (entity instanceof PlayerEntity player) {

                if (handlerClient().getFriendManager().isFriend(player.getName().getString())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isPvPActive() {
        return !onlyPvP.getEnabled() || isPvP();
    }
}
