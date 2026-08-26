package dev.client.api.nullcry.helper.other;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@UtilityClass
public class PushUtils implements ClientApi {

    private static final String APP_ID = LynxWave.getInstance().getClientInfo().getName() + ".Client";
    private static final String APP_DISPLAY_NAME = LynxWave.getInstance().getClientInfo().getName() + " Client";
    private static final File PS_SCRIPT_FILE = new File(System.getProperty("java.io.tmpdir"), "lynxwave_notification.ps1");

    public boolean sendPush(String title, String message) {
        try {
            if (mc.isWindowFocused()) return false;
            ensureScriptFileExists();

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", PS_SCRIPT_FILE.getAbsolutePath(),
                    title,
                    message
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return true;
            } else {
                String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                Console.log("§7[§bPush§7] §cОшибка отправки уведомления: " + error.trim());
                return false;
            }

        } catch (Exception e) {
            Console.log("§7[§bPush§7] §cОшибка: " + e.getMessage());
            return false;
        }
    }

    private void ensureScriptFileExists() throws IOException {
        if (PS_SCRIPT_FILE.exists()) return;

        String script = String.format(
                "param($Title, $Message)\n" +
                        "$AppId = '%s'\n" +
                        "$regPath = 'HKCU:\\\\SOFTWARE\\\\Classes\\\\AppUserModelId\\\\' + $AppId\n" +
                        "if (!(Test-Path $regPath)) {\n" +
                        "    New-Item -Path $regPath -Force | Out-Null\n" +
                        "    New-ItemProperty -Path $regPath -Name 'DisplayName' -Value '%s' -PropertyType String -Force | Out-Null\n" +
                        "    New-ItemProperty -Path $regPath -Name 'ShowInSettings' -Value 1 -PropertyType DWord -Force | Out-Null\n" +
                        "}\n" +
                        "$XmlText = @\"\n" +
                        "<toast scenario='reminder'>\n" +
                        "  <visual>\n" +
                        "    <binding template='ToastGeneric'>\n" +
                        "      <text>$Title</text>\n" +
                        "      <text>$Message</text>\n" +
                        "    </binding>\n" +
                        "  </visual>\n" +
                        "  <audio src='ms-winsoundevent:Notification.Default'/>\n" +
                        "</toast>\n" +
                        "\"@\n" +
                        "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null\n" +
                        "[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null\n" +
                        "$XmlDoc = [Windows.Data.Xml.Dom.XmlDocument]::new()\n" +
                        "$XmlDoc.LoadXml($XmlText)\n" +
                        "$Toast = [Windows.UI.Notifications.ToastNotification]::new($XmlDoc)\n" +
                        "$Toast.Tag = '%s'\n" +
                        "$Toast.Group = '%s'\n" +
                        "try {\n" +
                        "  $Notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier($AppId)\n" +
                        "  $Notifier.Show($Toast)\n" +
                        "  exit 0\n" +
                        "} catch {\n" +
                        "  Write-Error $_.Exception.Message\n" +
                        "  exit 1\n" +
                        "}\n",
                APP_ID, APP_DISPLAY_NAME, APP_DISPLAY_NAME, APP_DISPLAY_NAME
        );

        Files.write(PS_SCRIPT_FILE.toPath(), script.getBytes(StandardCharsets.UTF_8));
    }
}