package dev.client.modules.core.movement;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import net.minecraft.util.math.Vec3d;

public class ElytraBooster extends Module {
    public static ElytraBooster INSTANCE;

    public ElytraBooster() {
        super("ElytraBooster", ModuleCategory.Movement, "Ускоряет полёт на элитре с фейерверком");
        INSTANCE = this;
    }

    public final ModeElement mode = new ModeElement("Режим", () -> true)
            .set("ReallyWorld", "BravoHvH", "Свой")
            .defaultValue("ReallyWorld")
            .register(this);

    public final CheckBox maxSpeed = new CheckBox("Скорость от угла наклона", () -> mode.isSelected("Свой"))
            .defaultValue(false)
            .register(this);

    public final Slider speedXZ = new Slider("X и Z", () -> mode.isSelected("Свой") && !maxSpeed.getEnabled())
            .set(1.5f, 2.5f, 0.01f).defaultValue(1.5f).register(this);
    public final Slider speedY = new Slider("Y", () -> mode.isSelected("Свой") && !maxSpeed.getEnabled())
            .set(1.5f, 2.5f, 0.01f).defaultValue(1.5f).register(this);

    public final Slider speed5XZ = mkXZ("X и Z от 0 до 5 градусов");
    public final Slider speed10XZ = mkXZ("X и Z от 5 до 10 градусов");
    public final Slider speed15XZ = mkXZ("X и Z от 10 до 15 градусов");
    public final Slider speed20XZ = mkXZ("X и Z от 15 до 20 градусов");
    public final Slider speed25XZ = mkXZ("X и Z от 20 до 25 градусов");
    public final Slider speed30XZ = mkXZ("X и Z от 25 до 30 градусов");
    public final Slider speed35XZ = mkXZ("X и Z от 30 до 35 градусов");
    public final Slider speed40XZ = mkXZ("X и Z от 35 до 40 градусов");
    public final Slider speed45XZ = mkXZ("X и Z от 40 до 45 градусов");

    public final Slider speed5Y = mkXZ("Y от 0 до 5 градусов");
    public final Slider speed10Y = mkXZ("Y от 5 до 10 градусов");
    public final Slider speed15Y = mkXZ("Y от 10 до 15 градусов");
    public final Slider speed20Y = mkXZ("Y от 15 до 20 градусов");
    public final Slider speed25Y = mkXZ("Y от 20 до 25 градусов");
    public final Slider speed30Y = mkXZ("Y от 25 до 30 градусов");
    public final Slider speed35Y = mkXZ("Y от 30 до 35 градусов");
    public final Slider speed40Y = mkXZ("Y от 35 до 40 градусов");
    public final Slider speed45Y = mkXZ("Y от 40 до 45 градусов");

    private Slider mkXZ(String name) {
        return new Slider(name, () -> mode.isSelected("Свой") && maxSpeed.getEnabled())
                .set(1.5f, 2.5f, 0.01f).defaultValue(1.5f).register(this);
    }

    public Vec3d boost(Vec3d currentVelocity) {
        Angle angle = RotationController.INSTANCE.getMoveRotation();

        float normalizedYaw = normalizeAngle(angle.getYaw());
        float normalizedPitch = normalizeAngle(angle.getPitch());

        float finalSpeedXZ = 1.5f;
        float finalSpeedY = 1.5f;

        if (mode.isSelected("Свой")) {
            if (maxSpeed.getEnabled()) {
                finalSpeedXZ = getSpeedXZCustom(normalizedYaw, normalizedPitch);
                finalSpeedY = getSpeedYCustom(normalizedPitch);
            } else {
                finalSpeedXZ = speedXZ.getValue();
                finalSpeedY = speedY.getValue();
            }
        } else if (mode.isSelected("ReallyWorld")) {
            finalSpeedXZ = getSpeedXZForPitchReallyWorld(normalizedYaw);
            finalSpeedY = getSpeedYForPitchReallyWorld(normalizedPitch);
        } else if (mode.isSelected("BravoHvH")) {
            finalSpeedXZ = getSpeedXZForPitchBravoHvH(normalizedYaw);
            finalSpeedY = getSpeedYForPitchBravoHvH(normalizedPitch);
        }

        Vec3d lookVec = angle.toVector();

        return currentVelocity.add(
                lookVec.x * 0.1 + (lookVec.x * finalSpeedXZ - currentVelocity.x) * 0.5,
                lookVec.y * 0.1 + (lookVec.y * finalSpeedY - currentVelocity.y) * 0.5,
                lookVec.z * 0.1 + (lookVec.z * finalSpeedXZ - currentVelocity.z) * 0.5
        );
    }

    private float normalizeAngle(float angle) {
        float normalized = angle % 180;
        if (normalized > 90) normalized -= 180;
        else if (normalized < -90) normalized += 180;
        return normalized;
    }

    private float getSpeedXZCustom(float yaw, float pitch) {
        float absP = Math.abs(pitch);
        float absY = Math.abs(yaw);

        if ((absY >= 40 && absY <= 45) || (absP >= 40 && absP <= 45)) return speed45XZ.getValue();
        if ((absY >= 40 && absY <= 50) || (absP >= 40 && absP <= 50)) return speed40XZ.getValue();
        if ((absY >= 35 && absY <= 55) || (absP >= 35 && absP <= 55)) return speed35XZ.getValue();
        if ((absY >= 30 && absY <= 60) || (absP >= 30 && absP <= 60)) return speed30XZ.getValue();
        if ((absY >= 25 && absY <= 65) || (absP >= 25 && absP <= 65)) return speed25XZ.getValue();
        if ((absY >= 20 && absY <= 70) || (absP >= 20 && absP <= 70)) return speed20XZ.getValue();
        if ((absY >= 15 && absY <= 75) || (absP >= 15 && absP <= 75)) return speed15XZ.getValue();
        if ((absY >= 10 && absY <= 80) || (absP >= 10 && absP <= 80)) return speed10XZ.getValue();
        return speed5XZ.getValue();
    }

    private float getSpeedYCustom(float pitch) {
        float absP = Math.abs(pitch);

        if (absP >= 40 && absP <= 45) return speed45Y.getValue();
        if (absP >= 40 && absP <= 50) return speed40Y.getValue();
        if (absP >= 35 && absP <= 55) return speed35Y.getValue();
        if (absP >= 30 && absP <= 60) return speed30Y.getValue();
        if (absP >= 25 && absP <= 65) return speed25Y.getValue();
        if (absP >= 20 && absP <= 70) return speed20Y.getValue();
        if (absP >= 15 && absP <= 75) return speed15Y.getValue();
        if (absP >= 10 && absP <= 80) return speed10Y.getValue();
        return speed5Y.getValue();
    }

    private float getSpeedXZForPitchReallyWorld(float yaw) {
        float absY = Math.abs(yaw);

        if (absY >= 0 && absY <= 5) return 1.6f;
        if (absY > 5 && absY <= 10) return 1.63f;
        if (absY > 10 && absY <= 15) return 1.66f;
        if (absY > 15 && absY <= 20) return 1.71f;
        if (absY > 20 && absY <= 25) return 1.73f;
        if (absY > 25 && absY <= 30) return 1.81f;
        if (absY > 30 && absY <= 35) return 1.81f;
        if (absY > 35 && absY <= 40) return 1.81f;
        if (absY > 40 && absY <= 45) return 1.81f;
        return 1.6f;
    }

    private float getSpeedYForPitchReallyWorld(float pitch) {
        float absP = Math.abs(pitch);

        if (absP >= 0 && absP <= 5) return 1.6f;
        if (absP > 5 && absP <= 10) return 1.62f;
        if (absP > 10 && absP <= 15) return 1.6f;
        if (absP > 15 && absP <= 20) return 1.62f;
        if (absP > 20 && absP <= 25) return 1.64f;
        if (absP > 25 && absP <= 30) return 1.68f;
        if (absP > 30 && absP <= 35) return 1.75f;
        if (absP > 35 && absP <= 40) return 1.9f;
        if (absP > 40 && absP <= 45) return 2.16f;
        return 1.6f;
    }

    private float getSpeedYForPitchBravoHvH(float pitch) {
        float absP = Math.abs(pitch);

        if (absP >= 0 && absP <= 5) return 1.59f;
        if (absP > 5 && absP <= 10) return 1.59f;
        if (absP > 10 && absP <= 15) return 1.59f;
        if (absP > 15 && absP <= 20) return 1.59f;
        if (absP > 20 && absP <= 25) return 1.59f;
        if (absP > 25 && absP <= 30) return 1.59f;
        if (absP > 30 && absP <= 35) return 1.59f;
        if (absP > 35 && absP <= 37) return 1.59f;
        if (absP > 37 && absP <= 38) return 2.02f;
        if (absP > 38 && absP <= 40) return 1.99f;
        if (absP > 40 && absP <= 45) return 1.99f;
        if (absP > 45 && absP <= 50) return 1.97f;
        if (absP > 50 && absP <= 60) return 1.96f;
        if (absP > 60 && absP <= 61) return 1.87f;
        if (absP > 61 && absP <= 65) return 1.69f;
        return 1.59f;
    }

    private float getSpeedXZForPitchBravoHvH(float pitch) {
        float absP = Math.abs(pitch);

        if (absP >= 0 && absP <= 5) return 1.62f;
        if (absP > 5 && absP <= 12) return 1.6205f;
        if (absP > 12 && absP <= 15) return 1.625f;
        if (absP > 15 && absP <= 22) return 1.62f;
        if (absP > 22 && absP <= 28) return 1.86f;
        if (absP > 28 && absP <= 32) return 1.93f;
        if (absP > 32 && absP <= 34) return 1.94f;
        if (absP > 34 && absP <= 35) return 1.963f;
        if (absP > 35 && absP <= 38) return 1.963f;
        if (absP > 38 && absP <= 40) return 1.9645f;
        if (absP > 40 && absP <= 42) return 1.9645f;
        if (absP > 42 && absP <= 48) return 2.0f;
        if (absP > 48 && absP <= 52) return 2.0f;
        if (absP > 52 && absP <= 54) return 1.98f;
        if (absP > 54 && absP <= 58) return 1.96f;
        if (absP > 58 && absP <= 62) return 1.95f;
        return 1.62f;
    }
}
