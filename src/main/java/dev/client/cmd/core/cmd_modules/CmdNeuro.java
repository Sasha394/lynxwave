package dev.client.cmd.core.cmd_modules;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.Command;
import dev.client.api.nullcry.cmdHelper.interfaces.CommandWithAdvice;
import dev.client.api.nullcry.cmdHelper.interfaces.Logger;
import dev.client.api.nullcry.cmdHelper.interfaces.Parameters;
import dev.client.api.nullcry.cmdHelper.interfaces.Prefix;
import dev.client.modules.core.combat.killaura.rotation.neuro.NeuroRotationManager;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CmdNeuro implements Command, CommandWithAdvice {
    Prefix prefix;
    Logger logger;

    @Override
    public void execute(Parameters parameters) {
        String commandType = parameters.asString(0).orElse("");
        NeuroRotationManager manager = getManager();

        switch (commandType.toLowerCase()) {
            case "record" -> startRecord(parameters, manager);
            case "stop" -> stopRecordOrPlayback(manager);
            case "play" -> playProfile(parameters, manager);
            case "delete" -> deleteProfile(parameters, manager);
            case "list" -> printProfiles(manager);
            case "dir" -> openDirectory(manager);
            case "reload" -> reloadProfiles(manager);
            default -> throw new CommandException(Formatting.GRAY + "Используй: " + Formatting.WHITE + "record, stop, play, delete, list, dir, reload");
        }
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("record", "stop", "play", "delete", "list", "dir", "reload");
    }

    @Override
    public String name() {
        return "neuro";
    }

    @Override
    public String description() {
        return "Управление Neuro профилями ротации";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "neuro record <name> - записать профиль",
                prefix.get() + "neuro stop - остановить запись или проигрывание",
                prefix.get() + "neuro play <name> - включить профиль",
                prefix.get() + "neuro delete <name> - удалить профиль",
                prefix.get() + "neuro list - список профилей",
                prefix.get() + "neuro dir - открыть папку профилей",
                prefix.get() + "neuro reload - перезагрузить профили"
        );
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        if ("record".equalsIgnoreCase(subCommand)) {
            return List.of("name");
        }
        if ("play".equalsIgnoreCase(subCommand) || "delete".equalsIgnoreCase(subCommand)) {
            return getManager().getProfileNames();
        }
        return List.of();
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        return step == 0 ? firstArguments(subCommand) : List.of();
    }

    private void startRecord(Parameters parameters, NeuroRotationManager manager) {
        String profileName = parameters.asString(1)
                .orElseThrow(() -> new CommandException(Formatting.RED + "Укажи имя профиля: " + Formatting.GRAY + prefix.get() + "neuro record <name>"));

        try {
            boolean overwrite = manager.hasProfile(profileName);
            manager.startRecording(profileName);
            logger.log(Formatting.GREEN + "Neuro запись начата: " + Formatting.WHITE + profileName);
            if (overwrite) {
                logger.log(Formatting.YELLOW + "Профиль будет перезаписан после " + Formatting.WHITE + prefix.get() + "neuro stop");
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new CommandException(Formatting.RED + exception.getMessage());
        }
    }

    private void stopRecordOrPlayback(NeuroRotationManager manager) {
        if (manager.isRecording()) {
            try {
                String profileName = manager.getRecordingProfileName();
                int samples = manager.stopRecording();
                logger.log(Formatting.GREEN + "Neuro профиль сохранён: " + Formatting.WHITE + profileName + Formatting.GRAY + " (" + samples + " samples)");
            } catch (IOException exception) {
                throw new CommandException(Formatting.RED + "Не удалось сохранить профиль: " + exception.getMessage());
            }
            return;
        }

        if (manager.isPlaying()) {
            String profileName = manager.getPlaybackProfileName();
            manager.stopPlayback();
            logger.log(Formatting.GREEN + "Neuro проигрывание остановлено: " + Formatting.WHITE + profileName);
            return;
        }

        logger.log(Formatting.RED + "Нет активной Neuro записи или проигрывания.");
    }

    private void playProfile(Parameters parameters, NeuroRotationManager manager) {
        String profileName = parameters.asString(1)
                .orElseThrow(() -> new CommandException(Formatting.RED + "Укажи профиль: " + Formatting.GRAY + prefix.get() + "neuro play <name>"));

        try {
            int samples = manager.startPlayback(profileName);
            logger.log(Formatting.GREEN + "Neuro профиль включён: " + Formatting.WHITE + manager.getPlaybackProfileName() + Formatting.GRAY + " (" + samples + " samples)");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new CommandException(Formatting.RED + exception.getMessage());
        }
    }

    private void deleteProfile(Parameters parameters, NeuroRotationManager manager) {
        String profileName = parameters.asString(1)
                .orElseThrow(() -> new CommandException(Formatting.RED + "Укажи профиль: " + Formatting.GRAY + prefix.get() + "neuro delete <name>"));

        try {
            if (!manager.deleteProfile(profileName)) {
                throw new CommandException(Formatting.RED + "Профиль не найден: " + profileName);
            }
            logger.log(Formatting.GREEN + "Neuro профиль удалён: " + Formatting.WHITE + profileName);
        } catch (IOException exception) {
            throw new CommandException(Formatting.RED + "Не удалось удалить профиль: " + exception.getMessage());
        }
    }

    private void printProfiles(NeuroRotationManager manager) {
        List<String> profiles = manager.getProfileNames();
        logger.log(Formatting.GRAY + "Neuro профили: " + Formatting.WHITE + profiles.size());
        if (profiles.isEmpty()) {
            logger.log(Formatting.RED + "Профилей пока нет.");
        } else {
            String activeProfile = manager.getPlaybackProfileName();
            for (String profile : profiles) {
                boolean active = activeProfile != null && activeProfile.equalsIgnoreCase(profile) && manager.isPlaying();
                logger.log((active ? Formatting.GREEN + "* " : Formatting.GRAY + "- ") + Formatting.WHITE + profile);
            }
        }
        logger.log(Formatting.GRAY + "Модель: " + Formatting.WHITE + manager.getModelStatus());
    }

    private void openDirectory(NeuroRotationManager manager) {
        try {
            manager.openProfilesDirectory();
            logger.log(Formatting.GREEN + "Папка Neuro открыта.");
        } catch (IOException exception) {
            throw new CommandException(Formatting.RED + "Не удалось открыть папку: " + exception.getMessage());
        }
    }

    private void reloadProfiles(NeuroRotationManager manager) {
        int count = manager.reloadProfiles();
        logger.log(Formatting.GREEN + "Neuro профили перезагружены: " + Formatting.WHITE + count);
        logger.log(Formatting.GRAY + "Модель: " + Formatting.WHITE + manager.getModelStatus());
    }

    private NeuroRotationManager getManager() {
        NeuroRotationManager manager = LynxWave.getInstance().getNeuroRotationManager();
        if (manager == null) {
            throw new CommandException(Formatting.RED + "Neuro manager недоступен.");
        }
        return manager;
    }
}
