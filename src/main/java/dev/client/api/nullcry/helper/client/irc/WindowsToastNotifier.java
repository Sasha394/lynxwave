package dev.client.api.nullcry.helper.client.irc;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Native Windows 10/11 toast notifications via PowerShell + Windows.UI.Notifications.
 * No external dependencies, works on stock Windows.
 */
public final class WindowsToastNotifier {
    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "IRC-WinToast");
        t.setDaemon(true);
        return t;
    });

    private static final String APP_ID = "LynxWave.IRC";

    private static final boolean SUPPORTED = isWindows();

    private WindowsToastNotifier() {}

    public static boolean isSupported() {
        return SUPPORTED;
    }

    public static void show(String title, String body) {
        if (!SUPPORTED) return;
        if (!shouldShow()) return;

        EXEC.submit(() -> sendToast(safe(title), safe(body)));
    }

    private static boolean shouldShow() {
        IRCSettings s = IRCSettings.get();
        if (!s.isNotificationsEnabled()) return false;
        if (s.isNotifyOnlyWhenMinimized() && isGameFocused()) return false;
        return true;
    }

    private static boolean isGameFocused() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return false;
            long handle = mc.getWindow().getHandle();
            return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sendToast(String title, String body) {
        // Build PowerShell script that uses WinRT Toast XML.
        String script =
                "$ErrorActionPreference = 'SilentlyContinue';"
                        + "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType=WindowsRuntime] | Out-Null;"
                        + "$xml = New-Object Windows.Data.Xml.Dom.XmlDocument;"
                        + "$xml.LoadXml('<toast><visual><binding template=\"ToastGeneric\">"
                        + "<text>" + title + "</text>"
                        + "<text>" + body + "</text>"
                        + "</binding></visual></toast>');"
                        + "$toast = [Windows.UI.Notifications.ToastNotification]::new($xml);"
                        + "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('" + APP_ID + "').Show($toast);";

        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass",
                "-Command", script
        );
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            // Don't block forever — kill if it hangs
            EXEC.schedule(() -> {
                if (p.isAlive()) p.destroyForcibly();
            }, 6, TimeUnit.SECONDS);
        } catch (IOException ignored) {
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        // XML-escape + strip Minecraft formatting codes
        return s
                .replaceAll("§.", "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;")
                .replace("\"", "&quot;");
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }
}
