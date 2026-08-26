package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.killaura.rotation.RaytracingUtil;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class Spider extends Module {

    public Spider() {
        super("Spider", ModuleCategory.Movement, "Позволяет залазить на стены как человек-паук");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("Default", "FunTime", "SpookyTime").defaultValue("Default").register(this);
    Slider spiderSpeed = new Slider("Скорость", () -> mode.isSelected("Default")).set(1.0f, 10.0f, 0.05f).defaultValue(2.0f).register(this);
    Slider jumpHeight = new Slider("Высота прыжка", () -> mode.isSelected("SpookyTime")).set(0.1f, 1.0f, 0.05f).defaultValue(0.30f).register(this);

    private final TimerUtil timerUtil = new TimerUtil();
    private final TimerUtil funTimeTimer = new TimerUtil();
    int slot = -1;

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (FreeCamera.INSTANCE.isEnabled()) return;

        if (mode.isSelected("Default")) {
            if (!mc.player.horizontalCollision) {
                return;
            }
            long speed = MathHelper.clamp(500 - (spiderSpeed.getValue().longValue() / 2 * 100), 0, 500);
            if (timerUtil.isReached(speed)) {
                event.setOnGround(true);
                mc.player.setOnGround(true);
                mc.player.verticalCollision = true;
                mc.player.horizontalCollision = true;
                mc.player.velocityDirty = true;
                mc.player.jump();
                timerUtil.reset();
            }
            return;
        }

        if (mode.isSelected("FunTime")) {
            if (!mc.player.horizontalCollision) {
                return;
            }

            if (funTimeTimer.isReached(350L)) {
                event.setOnGround(true);
                mc.player.setOnGround(true);
                mc.player.verticalCollision = true;
                mc.player.horizontalCollision = true;
                mc.player.velocityDirty = true;
                mc.player.jump();
                funTimeTimer.reset();

                int blockSlot = getBlockSlot(true);
                if (blockSlot != -1 && mc.player.fallDistance > 0 && mc.player.fallDistance < 1.5f) {
                    placeBlocks(event, blockSlot);
                }
            }
            return;
        }

        if (mode.isSelected("SpookyTime")) {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
            if (!mc.player.horizontalCollision) return;

            int hotbarSlot = findWaterBucketHotbarSlot();
            if (hotbarSlot == -1) {
                printClient("Положите ведро воды в хотбар");
                this.toggle();
                return;
            }

            if (mc.player.getInventory().getSelectedSlot() != hotbarSlot
                    || mc.player.getMainHandStack().getItem() != Items.WATER_BUCKET) {
                return;
            }

            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.setVelocity(mc.player.getVelocity().x, jumpHeight.getValue(), mc.player.getVelocity().z);
        }
    }

    private void placeBlocks(MotionEvent event, int slot) {
        int last = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        event.setPitch(80.0f);
        event.setYaw(mc.player.getYaw());

        HitResult result = rayTrace(4, event.getYaw(), event.getPitch(), mc.player);
        if (result instanceof BlockHitResult blockResult) {
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockResult);
        }

        mc.player.getInventory().setSelectedSlot(last);
        mc.player.fallDistance = 0;
    }

    private HitResult rayTrace(double distance, float yaw, float pitch, Entity entity) {
        Vec3d startVec = mc.player.getEyePos();
        Vec3d directionVec = RaytracingUtil.getVectorForRotation(pitch, yaw);
        Vec3d endVec = startVec.add(directionVec.x * distance, directionVec.y * distance, directionVec.z * distance);
        return mc.world.raycast(new RaycastContext(startVec, endVec, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
    }

    public int getBlockSlot(boolean inHotBar) {
        int firstSlot = inHotBar ? 0 : 9;
        int lastSlot = inHotBar ? 9 : mc.player.getInventory().size();

        for (int i = firstSlot; i < lastSlot; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.REPEATING_COMMAND_BLOCK) {
                return i;
            }
        }
        return -1;
    }

    private int getSlotForFence() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.OAK_FENCE || mc.player.getInventory().getStack(i).getItem() == Items.GRANITE_WALL || mc.player.getInventory().getStack(i).getItem() == Items.BIRCH_FENCE || mc.player.getInventory().getStack(i).getItem() == Items.SPRUCE_FENCE || mc.player.getInventory().getStack(i).getItem() == Items.ACACIA_FENCE || mc.player.getInventory().getStack(i).getItem() == Items.DARK_OAK_FENCE || mc.player.getInventory().getStack(i).getItem() == Items.JUNGLE_FENCE || mc.player.getInventory().getStack(i).getItem() == Items.CRIMSON_FENCE || mc.player.getInventory().getStack(i).getItem() == Items.WARPED_FENCE || mc.player.getInventory().getStack(i).getItem() == Items.BAMBOO || mc.player.getInventory().getStack(i).getItem() == Items.NETHER_BRICK_FENCE) {
                return i;
            }
        }
        return -1;
    }

    private int findWaterBucketHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.WATER_BUCKET) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        timerUtil.reset();
        funTimeTimer.reset();
        if (mode.isSelected("Default")) {
            slot = mc.player.getInventory().getSelectedSlot();
            int fenceSlot = getSlotForFence();
            if (fenceSlot != -1) {
                mc.player.getInventory().setSelectedSlot(fenceSlot);
            }
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        timerUtil.reset();
        funTimeTimer.reset();
        if (slot != -1) {
            mc.player.getInventory().setSelectedSlot(slot);
            slot = -1;
        }
    }
}
