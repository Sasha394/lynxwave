package dev.client.api.nullcry.helper.player;

import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.Vec2f;

@UtilityClass
public class Simulations implements ClientApi {

    public boolean hasPlayerMovement() {
        Vec2f movementInput = mc.player.input.getMovementInput();
        return movementInput.y != 0f || movementInput.x != 0f;
    }

    public static double[] forward(final double d) {
        Vec2f movementInput = mc.player.input.getMovementInput();
        float f = movementInput.y;
        float f2 = movementInput.x;
        float f3 = RotationController.INSTANCE.getRotation().getYaw();
        if (f != 0.0f) {
            if (f2 > 0.0f) {
                f3 += ((f > 0.0f) ? -45 : 45);
            } else if (f2 < 0.0f) {
                f3 += ((f > 0.0f) ? 45 : -45);
            }
            f2 = 0.0f;
            if (f > 0.0f) {
                f = 1.0f;
            } else if (f < 0.0f) {
                f = -1.0f;
            }
        }
        final double d2 = Math.sin(Math.toRadians(f3 + 90.0f));
        final double d3 = Math.cos(Math.toRadians(f3 + 90.0f));
        final double d4 = f * d * d3 + f2 * d * d2;
        final double d5 = f * d * d2 - f2 * d * d3;
        return new double[]{d4, d5};
    }
}