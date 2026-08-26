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
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class AutoExplosion extends Module {
    public static AutoExplosion INSTANCE;

    public AutoExplosion() {
        super("AutoExplosion", ModuleCategory.Combat, "Автоматически ставит и взрывает кристалл");
    }

    CheckBox safeYourSelf = new CheckBox("Не взрывать себя", () -> true).defaultValue(true).register(this);
    CheckBox safeFriend = new CheckBox("Не взрывать друзей", () -> true).defaultValue(true).register(this);
    CheckBox onlyPvP = new CheckBox("Только в пвп", () -> true).defaultValue(false).register(this);
    Slider radiusFriend = new Slider("Радиус", () -> safeFriend.getEnabled()).set(1, 10, 0.1f).defaultValue(5).register(this);

    BlockPos targetPosition;
    int actionCounter = 0;
    int crystalSlot = -1;

    @Subscribe
    public void onBlockPlacement(BlockPlaceEvent event) {
        if (!isPvPActive()) return;

        if (event.getStack().getItem() != Item.fromBlock(Blocks.OBSIDIAN)) return;
        if (targetPosition != null) return;
        if (mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) return;

        targetPosition = event.getPosition();
        crystalSlot = findCrystalSlot();
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
                sendPacket(new UpdateSelectedSlotC2SPacket(crystalSlot));
                interactWithBlock(targetPosition);
            }
            case 1 -> {
                attackNearestCrystal(targetPosition);
                sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
            }
            case 2 -> {
            }
        }

        if (actionCounter >= 2) {
            targetPosition = null;
        }
        actionCounter++;
    }

    private void interactWithBlock(BlockPos position) {
        Vec3d hitVec = new Vec3d(position.getX() + 0.5, position.getY() + 1, position.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, position, false);
        sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void attackNearestCrystal(BlockPos position) {
        Box searchBox = new Box(position.getX() - 2, position.getY(), position.getZ() - 2, position.getX() + 2, position.getY() + 4, position.getZ() + 2);
        List<Entity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, searchBox, entity -> true).stream().map(e -> (Entity) e).toList();
        if (!crystals.isEmpty()) {
            Entity crystal = crystals.get(0);
            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private int findCrystalSlot() {
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == Items.END_CRYSTAL) {
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
