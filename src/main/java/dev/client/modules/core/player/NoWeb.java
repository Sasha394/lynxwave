package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.other.CollisionEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.block.Blocks;
import net.minecraft.block.CobwebBlock;
import net.minecraft.util.math.Vec3d;

public class NoWeb extends Module {
    public static NoWeb INSTANCE;

    public NoWeb() {
        super("NoWeb", ModuleCategory.Movement, "Убирает замедление в паутине");
    }

    public ModeElement mode = new ModeElement("Режим", () -> true).set("Timer", "Solid", "Ignore", "Fly", "ReallyWorld").defaultValue("Solid").register(this);
    public CheckBox grim = new CheckBox("Grim", () -> mode.isSelected("Ignore")).defaultValue(false).register(this);
    Slider timer = new Slider("Скорость таймера", () -> mode.isSelected("Timer")).set(1, 50f, 1).defaultValue(20f).register(this);
    Slider speed = new Slider("Скорость", () -> mode.isSelected("Fly")).set(0.0f, 10.0f, 0.1f).defaultValue(0.3f).register(this);

    private boolean timerEnabled = false;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (PlayerUtil.isInWeb()) {
            if (mode.isSelected("Timer")) {
                if (mc.player.isOnGround()) {
                    LynxWave.tickTimer = 1f;
                } else {
                    LynxWave.tickTimer = timer.getValue();
                    timerEnabled = true;
                }
            }
            if (mode.isSelected("Fly")) {
                final double[] dir = MovingUtil.forward(speed.getValue());
                mc.player.setVelocity(dir[0], 0, dir[1]);
                if (mc.options.jumpKey.isPressed())
                    mc.player.setVelocity(mc.player.getVelocity().add(0, speed.getValue(), 0));
                if (mc.options.sneakKey.isPressed())
                    mc.player.setVelocity(mc.player.getVelocity().add(0, -speed.getValue(), 0));
            }
            if (mode.isSelected("ReallyWorld")) {
                applyReallyWorld();
            }
        } else if (timerEnabled) {
            timerEnabled = false;
            LynxWave.tickTimer = 1f;
        }
    }

    @Subscribe
    public void onCollision(CollisionEvent event) {
        if (event.getState().getBlock() instanceof CobwebBlock && mode.isSelected("Solid"))
            event.setState(Blocks.DIRT.getDefaultState());
    }

    private void applyReallyWorld() {
        Vec3d velocity = mc.player.getVelocity();
        float yaw = mc.player.getYaw();
        double forward = 0.0D;
        double strafe = 0.0D;
        if (mc.player.input.playerInput.forward()) ++forward;
        if (mc.player.input.playerInput.backward()) --forward;
        if (mc.player.input.playerInput.left()) ++strafe;
        if (mc.player.input.playerInput.right()) --strafe;

        if (forward != 0.0D || strafe != 0.0D) {
            if (forward != 0.0D) {
                if (strafe > 0.0D) {
                    yaw += (float) (forward > 0.0D ? -45 : 45);
                } else if (strafe < 0.0D) {
                    yaw += (float) (forward > 0.0D ? 45 : -45);
                }
                strafe = 0.0D;
                forward = forward > 0.0D ? 1.0D : -1.0D;
            }
            double movementYaw = Math.toDegrees(Math.atan2(strafe, forward)) + (double) yaw;
            yaw = (float) ((movementYaw % 360.0D + 360.0D) % 360.0D);
        }

        float result = 0.63F;
        if ((!(yaw >= 313.0F) || !(yaw <= 317.0F)) && (!(yaw >= 223.0F) || !(yaw <= 227.0F)) && (!(yaw >= 133.0F) || !(yaw <= 137.0F)) && (!(yaw >= 43.0F) || !(yaw <= 47.0F))) {
            if ((!(yaw >= 311.0F) || !(yaw <= 319.0F)) && (!(yaw >= 221.0F) || !(yaw <= 229.0F)) && (!(yaw >= 131.0F) || !(yaw <= 139.0F)) && (!(yaw >= 41.0F) || !(yaw <= 49.0F))) {
                if ((!(yaw >= 310.8F) || !(yaw <= 320.8F)) && (!(yaw >= 220.8F) || !(yaw <= 230.8F)) && (!(yaw >= 130.8F) || !(yaw <= 140.8F)) && (!(yaw >= 40.8F) || !(yaw <= 50.8F))) {
                    if ((!(yaw >= 308.7F) || !(yaw <= 322.7F)) && (!(yaw >= 218.7F) || !(yaw <= 232.7F)) && (!(yaw >= 128.7F) || !(yaw <= 142.7F)) && (!(yaw >= 38.7F) || !(yaw <= 52.7F))) {
                        if ((!(yaw >= 306.5F) || !(yaw <= 324.5F)) && (!(yaw >= 216.5F) || !(yaw <= 234.5F)) && (!(yaw >= 126.5F) || !(yaw <= 144.5F)) && (!(yaw >= 36.5F) || !(yaw <= 54.5F))) {
                            if (yaw >= 304.0F && yaw <= 327.0F || yaw >= 214.0F && yaw <= 237.0F || yaw >= 124.0F && yaw <= 147.0F || yaw >= 34.0F && yaw <= 57.0F) {
                                result = 0.75F;
                            }
                        } else {
                            result = 0.79F;
                        }
                    } else {
                        result = 0.81F;
                    }
                } else {
                    result = 0.83F;
                }
            } else {
                result = 0.85F;
            }
        } else {
            result = 0.88F;
        }

        if (!mc.options.jumpKey.isPressed()) {
            if (mc.options.sneakKey.isPressed()) {
                mc.player.setVelocity(velocity.x, -3.6D, velocity.z);
            } else {
                mc.player.setVelocity(velocity.x, 0.0D, velocity.z);
            }
        } else {
            mc.player.setVelocity(velocity.x, forward == 0.0D && strafe == 0.0D ? 1.4D : 1.2D, velocity.z);
        }
        MovingUtil.setVelocity(result);
    }
}
