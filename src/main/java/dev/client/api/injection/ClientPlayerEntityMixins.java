package dev.client.api.injection;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.game.InventoryCloseEvent;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.events.core.input.MoveEvent;
import dev.client.api.nullcry.events.core.input.PostMotionEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.other.NoPushEvent;
import dev.client.api.nullcry.events.core.other.UsingItemEvent;
import dev.client.api.nullcry.events.types.EventType;
import dev.client.component.core.inventory.PlayerInventoryComponent;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.client.modules.core.combat.killaura.rotation.RaytracingUtil;
import dev.client.modules.core.misc.LockSlot;
import dev.client.modules.core.movement.NoSlow;
import dev.client.modules.core.movement.Sprint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixins extends AbstractClientPlayerEntity implements ClientApi {

    public ClientPlayerEntityMixins(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Shadow public abstract float getPitch(float tickDelta);
    @Shadow public abstract float getYaw(float tickDelta);
    @Shadow @Final protected MinecraftClient client;
    @Shadow public abstract void sendAbilitiesUpdate();
    @Shadow public Input input;

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        LockSlot lock = LockSlot.INSTANCE;
        if (lock == null || !lock.isEnabled()) return;

        int slot = mc.player.getInventory().getSelectedSlot();
        long handle = mc.getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean protectedNow = lock.isSlotProtected(slot);
        boolean ctrlBypass = ctrl && lock.pressedCtrl.getEnabled();

        if (protectedNow && !ctrlBypass) {
            ci.setReturnValue(false);
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        UpdateEvent tickEvent = new UpdateEvent();
        EventManager.call(tickEvent);
    }

    @Inject(method = "sendMovementPackets", at = @At(value = "HEAD"), cancellable = true)
    private void preMotion(CallbackInfo ci) {
        MotionEvent event = new MotionEvent(getX(), getY(), getZ(), getYaw(1), getPitch(1), isOnGround());
        EventManager.call(event);
        if (event.isCancelled()) ci.cancel();
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float hookPacketRotationYaw(float original) {
        return RotationController.INSTANCE.getRotation().getYaw();
    }

    @ModifyExpressionValue(method = {"sendMovementPackets", "tick"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float hookSilentRotationPitch(float original) {
        return RotationController.INSTANCE.getRotation().getPitch();
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float hookTickRotationYaw(float original) {
        return original;
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float hookTickRotationPitch(float original) {
        return original;
    }

    @Redirect(method = "getCrosshairTarget(Lnet/minecraft/entity/Entity;DDF)Lnet/minecraft/util/hit/HitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;"))
    private static HitResult hookCrosshairRaycast(Entity instance, double maxDistance, float tickDelta, boolean includeFluids) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || instance != mc.player) {
            return instance.raycast(maxDistance, tickDelta, includeFluids);
        }
        return RaytracingUtil.raycast(maxDistance, RotationController.INSTANCE.getRotation(), includeFluids);
    }

    @Redirect(method = "getCrosshairTarget(Lnet/minecraft/entity/Entity;DDF)Lnet/minecraft/util/hit/HitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getRotationVec(F)Lnet/minecraft/util/math/Vec3d;"))
    private static Vec3d hookCrosshairRotationVector(Entity instance, float tickDelta) {
        return RotationController.INSTANCE.getRotation().toVector();
    }

    @Inject(method = "isBlockedFromSprinting", at = @At("HEAD"), cancellable = true)
    public void isBlockedFromSprintingHook(CallbackInfoReturnable<Boolean> cir) {
        if (Sprint.getInstance().isEnabled() && NoSlow.getInstance().isEnabled()) cir.setReturnValue(false);
    }

    @ModifyExpressionValue(method = "applyMovementSpeedFactors(Lnet/minecraft/util/math/Vec2f;)Lnet/minecraft/util/math/Vec2f;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean usingItemHook(boolean original) {
        if (original) {
            UsingItemEvent event = new UsingItemEvent(EventType.ON);
            EventManager.call(event);
            if (event.isCancelled()) return false;
            Sprint.getInstance().tickStop = 1;
        }
        return original;
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    public void onPushOutOfBlocks(double x, double z, CallbackInfo callbackInfo) {
        NoPushEvent noPushEvent = new NoPushEvent(NoPushEvent.CollisionEnum.Blocks);
        EventManager.call(noPushEvent);

        if (noPushEvent.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "closeHandledScreen", at = @At("HEAD"), cancellable = true)
    public void onCloseHandlerScreen(CallbackInfo ci) {
        InventoryCloseEvent inventoryCloseEvent = new InventoryCloseEvent(MinecraftClient.getInstance().currentScreen, this.currentScreenHandler.syncId);
        EventManager.call(inventoryCloseEvent);
        if (inventoryCloseEvent.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void onMoveHook(MovementType movementType, Vec3d movement, @NotNull CallbackInfo ci) {
        MoveEvent event = new MoveEvent(movement);
        EventManager.call(event);

        if (!event.isCancelled() && event.getMovement().equals(movement)) {
            return;
        }

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        double d = this.getX();
        double e = this.getZ();
        super.move(movementType, event.getMovement());
        float f = (float) Math.sqrt(Math.pow(this.getX() - d, 2) + Math.pow(this.getZ() - e, 2));
        this.updateLimbs(f);
        ci.cancel();
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"), cancellable = true)
    private void postMotion(CallbackInfo ci) {
        PostMotionEvent postMotionEvent = new PostMotionEvent();
        EventManager.call(postMotionEvent);
        if (postMotionEvent.isCancelled()) ci.cancel();
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void postMovementPackets(CallbackInfo ci) {
        PlayerInventoryComponent.postMotion();
    }
}
