package dev.client.cmd.core.cmd_modules;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import dev.client.api.nullcry.helper.client.projection.ProjectionUtil;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.cmd.core.util.CommandTextUtil;
import dev.client.modules.core.render.Interface;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdGps implements Command, CommandWithAdvice {
    final MinecraftClient mc;
    final Prefix prefix;
    final Logger logger;

    private final Map<String, Vec3i> waypoints = new HashMap<>();
    private final Map<String, String> waypointNames = new HashMap<>();
    private int untitledCounter = 2;

    public CmdGps(MinecraftClient mc, Prefix prefix, Logger logger) {
        this.mc = MinecraftClient.getInstance();
        this.prefix = prefix;
        this.logger = logger;
        LynxWave.getInstance().getEventBus().register(this);
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("add", "delete", "clear", "list");
    }

    @Override
    public void execute(Parameters parameters) {
        String commandType = parameters.asString(0).orElse("");

        switch (commandType) {
            case "add" -> addGPS(parameters);
            case "delete" -> deleteGPS(parameters);
            case "clear" -> clearGPS();
            case "list" -> listGPS();
            default ->
                    throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "add, delete, list, clear");
        }
    }

    @Override
    public String name() {
        return "gps";
    }

    @Override
    public String description() {
        return "Позволяет устанавливать GPS координаты и удалять по имени";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "gps add <name?> <x> <y?> <z> - Добавить GPS точку (имя и Y необязательны)",
                prefix.get() + "gps delete <name> - Удалить GPS точку по имени",
                prefix.get() + "gps list - Показать список точек",
                prefix.get() + "gps clear - Удалить все точки для текущего сервера",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "gps add Home 100 64 200" + Formatting.RESET,
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "gps add 150 70 150" + Formatting.RESET + " (без имени)",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "gps add 150 150" + Formatting.RESET + " (без имени и Y — Y будет текущим положением игрока)",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "gps delete Home" + Formatting.RESET
        );
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        if ("add".equalsIgnoreCase(subCommand)) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("name");

            if (ConnectionHelper.isHW()) {
                suggestions.add("zamok");
            }
            return suggestions;
        }
        if ("delete".equalsIgnoreCase(subCommand)) {
            return waypoints.keySet().stream().toList();
        }
        return List.of();
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        if ("add".equalsIgnoreCase(subCommand)) {
            return switch (step) {
                case 0 -> {
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add("name");
                    suggestions.add("x");
                    if (ConnectionHelper.isHW()) {
                        suggestions.add("zamok");
                    }
                    yield suggestions;
                }
                case 1 -> List.of("x");
                case 2 -> List.of("y");
                case 3 -> List.of("z");
                default -> List.of();
            };
        }
        if ("delete".equalsIgnoreCase(subCommand)) {
            return step == 0 ? waypoints.keySet().stream().toList() : List.of();
        }
        return List.of();
    }

    @Subscribe
    private void onRender2D(RenderEvent.Draw2D draw2D) {
        String serverIP = ConnectionHelper.getServerIP();

        final float sw = mc.getWindow().getScaledWidth();
        final float sh = mc.getWindow().getScaledHeight();

        var camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        Vec3d camForward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
        Vec3d camPos = camera.getCameraPos();

        for (Map.Entry<String, Vec3i> entry : waypoints.entrySet()) {
            String name = entry.getKey();
            String server = waypointNames.getOrDefault(name, "");
            if (!server.equals(serverIP)) continue;

            Vec3i waypoint = entry.getValue();
            Vec3d world = new Vec3d(waypoint.getX() + 0.5, waypoint.getY() + 0.5, waypoint.getZ() + 0.5);

            Vec3d toPoint = world.subtract(camPos);
            if (toPoint.lengthSquared() <= 1e-6) continue;
            double dot = camForward.dotProduct(toPoint.normalize());
            if (dot <= 0) continue;

            Vec3d screenPos = ProjectionUtil.projectCoordinates(world);

            int distance = (int) dev.client.api.compat.EntityCompat.pos(MinecraftClient.getInstance().player).distanceTo(world);
            double time = System.currentTimeMillis() / 1000.0;
            float bounceOffset = (float) (Math.sin(time * 3) * 2);

            Builder.TEXTURE_BUILDER
                    .size(new SizeState(13, 13))
                    .texture(0, 0, 1, 1, ClientTexture.of("images/world/waypoint.png"))
                    .color(new QuadColorState(Interface.INSTANCE.getMainColor()))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(draw2D.getContext()),
                            (float) screenPos.x - 6.5f, (float) screenPos.y - 6.5f + bounceOffset);

            String label = name + " (" + distance + "m)";
            float labelWidth = Builder.INTER.get().getWidth(label, 7.5f);

            Builder.TEXT_BUILDER
                    .font(Builder.INTER.get())
                    .color(-1)
                    .text(label)
                    .size(7.5f)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(draw2D.getContext()),
                            (float) screenPos.x - labelWidth / 2f, (float) screenPos.y + 10);
        }
    }


    private void addGPS(Parameters param) {
        if (param.count() == 2 && param.asString(1).orElse("").equalsIgnoreCase("zamok")) {
            String name = "zamok";
            int x = 0, y = 0, z = 0;
            String serverIP = ConnectionHelper.getServerIP();
            waypoints.put(name, new Vec3i(x, y, z));
            waypointNames.put(name, serverIP);

            logger.log(
                    Formatting.GRAY + "GPS точка " +
                            Formatting.WHITE + "'" + name + "'" +
                            Formatting.GRAY + " добавлена: " +
                            Formatting.WHITE + "[0, 0, 0]" +
                            Formatting.GRAY + " для сервера " +
                            Formatting.YELLOW + serverIP +
                            Formatting.RESET
            );
            return;
        }

        String name = "";
        int x, y, z;

        try {
            List<String> args = new ArrayList<>();
            for (int i = 1; i < param.count(); i++) {
                args.add(param.asString(i).orElse(""));
            }

            int coordStart = -1;
            for (int i = 0; i < args.size(); i++) {
                if (isInteger(args.get(i))) {
                    coordStart = i;
                    break;
                }
            }

            if (coordStart == -1 || args.size() - coordStart < 2) {
                throw new CommandException(Formatting.RED + "Укажите как минимум X и Z координаты!" + Formatting.RESET);
            }

            name = String.join(" ", args.subList(0, coordStart)).trim();
            if (name.isEmpty()) name = generateUntitledName();

            if (args.size() - coordStart == 2) {
                x = Integer.parseInt(args.get(coordStart));
                y = (int) dev.client.api.compat.EntityCompat.pos(mc.player).getY();
                z = Integer.parseInt(args.get(coordStart + 1));
            } else if (args.size() - coordStart >= 3) {
                x = Integer.parseInt(args.get(coordStart));
                y = Integer.parseInt(args.get(coordStart + 1));
                z = Integer.parseInt(args.get(coordStart + 2));
            } else {
                throw new CommandException(Formatting.RED + "Неверный формат команды. Используйте:\n" +
                        Formatting.GRAY + "gps add <x> <z> (Y берется текущий)\n" +
                        "gps add <x> <y> <z>\n" +
                        "gps add <name> <x> <y> <z>\n" +
                        "gps add <name> <x> <z> (Y берется текущий)" +
                        Formatting.RESET);
            }

        } catch (NumberFormatException e) {
            throw new CommandException(Formatting.RED + "Координаты должны быть целыми числами." + Formatting.RESET);
        }

        String serverIP = ConnectionHelper.getServerIP();
        waypoints.put(name, new Vec3i(x, y, z));
        waypointNames.put(name, serverIP);

        logger.log(
                Formatting.GRAY + "GPS точка " +
                        Formatting.WHITE + "'" + name + "'" +
                        Formatting.GRAY + " добавлена: " +
                        Formatting.WHITE + "[" + x + ", " + y + ", " + z + "]" +
                        Formatting.GRAY + " для сервера " +
                        Formatting.YELLOW + serverIP +
                        Formatting.RESET
        );
    }

    private void deleteGPS(Parameters param) {
        String name = param.asString(1).orElseThrow(() ->
                new CommandException(Formatting.RED + "Укажите имя точки!" + Formatting.RESET));

        if (!waypoints.containsKey(name)) {
            logger.log(Formatting.RED + "GPS точка " + Formatting.YELLOW + "'" + name + "'" + Formatting.RED + " не найдена." + Formatting.RESET);
            return;
        }

        waypoints.remove(name);
        waypointNames.remove(name);

        logger.log(Formatting.GRAY + "GPS точка " + Formatting.WHITE + "'" + name + "'" + Formatting.GRAY + " была удалена." + Formatting.RESET);
    }

    private void listGPS() {
        if (waypoints.isEmpty()) {
            logger.log(Formatting.RED + "Нет сохранённых GPS точек." + Formatting.RESET);
            return;
        }

        logger.log(Text.literal("Список сохранённых GPS точек:").formatted(Formatting.GRAY));
        logger.log(Text.literal("Используйте кнопки для управления точками.").formatted(Formatting.DARK_GRAY));

        String prefixValue = prefix.get();
        int mainColor = Interface.INSTANCE.getMainColor();

        waypoints.forEach((name, coords) -> {
            String server = waypointNames.getOrDefault(name, "неизвестно");
            MutableText line = Text.literal("Название: ").formatted(Formatting.GRAY)
                    .append(Text.literal(name).formatted(Formatting.WHITE))
                    .append(Text.literal(" | Сервер: ").formatted(Formatting.GRAY))
                    .append(Text.literal(server).formatted(Formatting.YELLOW))
                    .append(Text.literal(" - Координаты: ").formatted(Formatting.GRAY));

            MutableText coordsText = Text.literal("[" + coords.getX() + ", " + coords.getY() + ", " + coords.getZ() + "]")
                    .styled(style -> style.withColor(TextColor.fromRgb(mainColor)));

            line.append(coordsText);
            line.append(CommandTextUtil.bracketedButton(
                    "Удалить",
                    Formatting.RED,
                    ClickEvent.Action.SUGGEST_COMMAND,
                    prefixValue + "gps delete " + name,
                    "§cУдалить точку"
            ));
            line.append(CommandTextUtil.bracketedButton(
                    "Скопировать",
                    Formatting.AQUA,
                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                    coords.getX() + " " + coords.getY() + " " + coords.getZ(),
                    "§7Скопировать координаты"
            ));

            logger.log(line);
        });
    }

    private void clearGPS() {
        String serverIP = ConnectionHelper.getServerIP();

        boolean hadAny = waypoints.entrySet().removeIf(entry -> serverIP.equals(waypointNames.get(entry.getKey())));
        waypointNames.entrySet().removeIf(entry -> serverIP.equals(entry.getValue()));

        if (hadAny) {
            logger.log(
                    Formatting.GRAY + "Все GPS точки для сервера " +
                            Formatting.YELLOW + serverIP +
                            Formatting.GRAY + " были " +
                            Formatting.WHITE + "удалены." +
                            Formatting.RESET
            );
        } else {
            logger.log(
                    Formatting.RED + "Нет GPS точек для сервера " +
                            Formatting.YELLOW + serverIP +
                            Formatting.RED + "." +
                            Formatting.RESET
            );
        }
    }

    public void addGpsClient(String name, int x, int y, int z) {
        String finalName = (name == null || name.isEmpty()) ? generateUntitledName() : name;
        String serverIP = ConnectionHelper.getServerIP();
        waypoints.put(finalName, new Vec3i(x, y, z));
        waypointNames.put(finalName, serverIP);
        logger.log(Formatting.WHITE + "GPS точка '" + finalName + "' добавлена: [" + x + ", " + y + ", " + z + "] для сервера " + serverIP + Formatting.RESET);
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String generateUntitledName() {
        String name = "Untitled";
        if (!waypoints.containsKey(name)) {
            return name;
        }

        while (waypoints.containsKey(name + untitledCounter)) {
            untitledCounter++;
        }

        return name + untitledCounter++;
    }
}
