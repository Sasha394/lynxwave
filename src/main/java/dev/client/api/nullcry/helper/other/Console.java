package dev.client.api.nullcry.helper.other;

import dev.client.LynxWave;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

@UtilityClass
@Log4j2
public class Console {
    static {
        AnsiConsole.systemInstall();
    }

    public Ansi getConsoleReset() {
        return Ansi.ansi().reset();
    }

    public Ansi getConsoleBackground() {
        return Ansi.ansi().bgRgb(0, 25, 20);
    }

    public Ansi getConsoleText() {
        return Ansi.ansi().fgRgb(0, 255, 200);
    }

    public static void log(String str, Object... args) {
        log.info("{}{}{} -> {}{}",
                Console.getConsoleBackground(),
                Console.getConsoleText(),
                LynxWave.getInstance().getClientInfo().getName(),
                str.formatted(args),
                Console.getConsoleReset());
    }

    public static void logManager(String str, Object... args) {
        log.info("{}{}{} -> Manager: {}{}",
                Console.getConsoleBackground(),
                Console.getConsoleText(),
                LynxWave.getInstance().getClientInfo().getName(),
                str.formatted(args),
                Console.getConsoleReset());
    }
}