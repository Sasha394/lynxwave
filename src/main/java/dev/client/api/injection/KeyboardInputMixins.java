package dev.client.api.injection;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.input.InputEvent;
import dev.client.component.core.inventory.PlayerInventoryComponent;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.client.modules.core.combat.killaura.rotation.RotationPlan;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixins extends Input implements ClientApi {

    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/util/PlayerInput;"))
    private PlayerInput tickHook(PlayerInput original) {
        InputEvent event = new InputEvent(original);
        EventManager.call(event);
        PlayerInventoryComponent.input(event);
        return transformInput(event.getInput());
    }

    @Unique
    private PlayerInput transformInput(PlayerInput input) {
        RotationController rotationController = RotationController.INSTANCE;
        Angle angle = rotationController.getCurrentAngle();
        RotationPlan configurable = rotationController.getCurrentRotationPlan();

        if (mc.player == null || angle == null || configurable == null || !(configurable.isMoveCorrection() && configurable.isFreeCorrection())) {
            return input;
        }

        float deltaYaw = mc.player.getYaw() - angle.getYaw();
        float z = KeyboardInput.getMovementMultiplier(input.forward(), input.backward());
        float x = KeyboardInput.getMovementMultiplier(input.left(), input.right());
        float newX = x * MathHelper.cos(deltaYaw * 0.017453292f) - z * MathHelper.sin(deltaYaw * 0.017453292f);
        float newZ = z * MathHelper.cos(deltaYaw * 0.017453292f) + x * MathHelper.sin(deltaYaw * 0.017453292f);
        int movementSideways = Math.round(newX), movementForward = Math.round(newZ);

        return new PlayerInput(movementForward > 0F, movementForward < 0F, movementSideways > 0F, movementSideways < 0F, input.jump(), input.sneak(), input.sprint());
    }
}