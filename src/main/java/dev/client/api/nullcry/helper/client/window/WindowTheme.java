package dev.client.api.nullcry.helper.client.window;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFWNativeWin32;

/**
 * Тёмный заголовок окна на Windows — та полоса сверху, где крестик, квадрат и минус.
 * <p>
 * Красится через DWM: у окна выставляется атрибут «immersive dark mode». Номер атрибута
 * менялся между сборками Windows 10, поэтому пробуем оба — начиная с современного.
 */
public final class WindowTheme {

    /** Windows 10 build 18985+ и Windows 11. */
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    /** Windows 10 build 17763..18984. */
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;

    /** SetWindowPos: не двигать, не менять размер, не менять Z, но перерисовать рамку. */
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;

    private static boolean applied;

    private WindowTheme() {
    }

    /** Вызывается каждый тик, но реально срабатывает один раз за запуск. */
    public static void applyOnce() {
        if (applied) return;
        applied = true;

        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                applied = false;
                return;
            }

            long hwnd = GLFWNativeWin32.glfwGetWin32Window(client.getWindow().getHandle());
            if (hwnd == 0L) {
                applied = false;
                return;
            }

            Pointer window = new Pointer(hwnd);
            IntByReference enabled = new IntByReference(1);

            boolean ok = Dwmapi.INSTANCE.DwmSetWindowAttribute(
                    window, DWMWA_USE_IMMERSIVE_DARK_MODE, enabled, 4) == 0;
            if (!ok) {
                ok = Dwmapi.INSTANCE.DwmSetWindowAttribute(
                        window, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, enabled, 4) == 0;
            }

            // Заголовок перерисовывается не сразу — просим Windows обновить рамку.
            if (ok) {
                User32.INSTANCE.SetWindowPos(window, null, 0, 0, 0, 0,
                        SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
            }
        } catch (Throwable error) {
            System.err.println("[LynxWave] Не удалось затемнить заголовок окна: " + error);
        }
    }

    private interface Dwmapi extends Library {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);

        int DwmSetWindowAttribute(Pointer hwnd, int attribute, IntByReference value, int size);
    }

    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean SetWindowPos(Pointer hwnd, Pointer insertAfter, int x, int y, int cx, int cy, int flags);
    }
}
