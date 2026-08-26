package dev.client.cmd.core.cmd_modules;

import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdHClip implements Command, CommandWithAdvice {
    final MinecraftClient mc;
    final Prefix prefix;
    final Logger logger;

    @Override
    public List<String> parametersCommand() {
        return List.of("");
    }

    @Override
    public String name() {
        return "hclip";
    }

    @Override
    public String description() {
        return "Телепортирует вперёд/назад по горизонтали";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                Formatting.GRAY + prefix.get() + "hclip <distance> - Телепортация на указанное расстояние",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "hclip 1"
        );
    }

    @Override
    public void execute(Parameters parameters) throws CommandException {
        String distanceStr = parameters.asString(0).orElseThrow(() -> new CommandException(Formatting.GRAY + "Необходимо указать расстояние для перемещения."));

        if (!NumberUtils.isCreatable(distanceStr)) {
            logger.log(Formatting.RED + "Введите числовое значение для расстояния.");
            return;
        }

        double distance = Double.parseDouble(distanceStr);
        Vec3d lookVector = mc.player.getRotationVector().normalize();

        double totalOffsetX = lookVector.x * distance;
        double totalOffsetZ = lookVector.z * distance;

        int steps = (int) Math.ceil(Math.abs(distance) / 0.5);
        double stepOffsetX = totalOffsetX / steps;
        double stepOffsetZ = totalOffsetZ / steps;
        double currentX = mc.player.getX();
        double currentY = mc.player.getY();
        double currentZ = mc.player.getZ();

        for (int i = 0; i < steps; i++) {
            currentX += stepOffsetX;
            currentZ += stepOffsetZ;
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(currentX, currentY, currentZ, mc.player.isOnGround(), mc.player.horizontalCollision));
            mc.player.setPosition(currentX, currentY, currentZ);
        }

        String blockUnit = Math.abs(distance) > 1 ? "блоков" : "блок";
        logger.log(Formatting.GRAY + String.format("Вы переместились на %.1f %s по горизонтали.", Math.abs(distance), blockUnit));
    }
}