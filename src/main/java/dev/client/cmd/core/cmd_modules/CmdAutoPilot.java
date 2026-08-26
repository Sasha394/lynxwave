package dev.client.cmd.core.cmd_modules;

import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.Command;
import dev.client.api.nullcry.cmdHelper.interfaces.CommandWithAdvice;
import dev.client.api.nullcry.cmdHelper.interfaces.Logger;
import dev.client.api.nullcry.cmdHelper.interfaces.Parameters;
import dev.client.api.nullcry.cmdHelper.interfaces.Prefix;
import dev.client.modules.core.player.AutoPilot;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdAutoPilot implements Command, CommandWithAdvice {
    final MinecraftClient mc;
    final Prefix prefix;
    final Logger logger;

    @Override
    public void execute(Parameters parameters) {
        AutoPilot module = AutoPilot.INSTANCE;
        if (parameters.count() == 1 && "off".equalsIgnoreCase(parameters.asString(0).orElse(""))) {
            if (module != null && module.hasTarget()) {
                module.clearTarget();
                logger.log(Formatting.GRAY + "AutoPilot остановлен.");
            }
            return;
        }
        if (module == null || !module.isEnabled()) {
            throw new CommandException(Formatting.RED + "AutoPilot отключен.");
        }

        if (parameters.count() != 2) {
            throw new CommandException(Formatting.RED + "Использование: " + prefix.get() + "autopilot <x> <z> или off");
        }

        int x = parseInt(parameters.asString(0).orElse(""), "X");
        int z = parseInt(parameters.asString(1).orElse(""), "Z");

        module.setTarget(x, z);
        logger.log(Formatting.GRAY + "Цель: " + Formatting.WHITE + "[" + x + ", " + z + "]");
    }

    @Override
    public String name() {
        return "autopilot";
    }

    @Override
    public String description() {
        return "Полет к указанным координатам.";
    }

    @Override
    public List<String> adviceMessage() {
        if (AutoPilot.INSTANCE == null || !AutoPilot.INSTANCE.isEnabled()) {
            throw new CommandException(Formatting.RED + "AutoPilot отключен.");
        }
        return List.of(
                Formatting.GRAY + prefix.get() + "autopilot <x> <z>",
                Formatting.GRAY + prefix.get() + "autopilot off"
        );
    }

    @Override
    public List<String> parametersCommand() {
        if (AutoPilot.INSTANCE == null || !AutoPilot.INSTANCE.isEnabled()) {
            return List.of();
        }
        return List.of("x", "off");
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        if (AutoPilot.INSTANCE == null || !AutoPilot.INSTANCE.isEnabled()) {
            return List.of();
        }
        return List.of("z");
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        if (AutoPilot.INSTANCE == null || !AutoPilot.INSTANCE.isEnabled()) {
            return List.of();
        }
        if (step == 0) {
            return List.of("z");
        }
        return List.of();
    }

    private int parseInt(String value, String label) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new CommandException(Formatting.RED + label + " должен быть числом.");
        }
    }
}
