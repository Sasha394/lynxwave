package dev.client.api.nullcry.modules;

import dev.client.LynxWave;
import dev.client.api.injection.accessor.IClientWorldAccessor;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.IModule;
import dev.client.api.nullcry.IScreen;
import dev.client.api.nullcry.helper.other.SoundUtil;
import dev.client.api.nullcry.render.core.animations.client.Animation;
import dev.client.api.nullcry.render.core.animations.client.Direction;
import dev.client.api.nullcry.render.core.animations.client.implement.DecelerateAnimation;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.notification.NotificationDragging;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.modules.core.render.Interface;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class Module implements ClientApi, IModule, IScreen, SettingProvider {
    String name;
    String description;
    ModuleCategory moduleCategory;

    Integer key = GLFW.GLFW_KEY_UNKNOWN;
    boolean enabled, binding = false;
    boolean feedbackFromKeybind = false;

    private static final ConcurrentMap<Class<? extends Module>, Module> INSTANCE_CACHE = new ConcurrentHashMap<>();

    final Set<IModule.ServerType> allowedServers = new HashSet<>();
    final List<Setting> settings = new ArrayList<>();
    final Animation animation = new DecelerateAnimation().setMs(250).setValue(1);

    public Module(String name, ModuleCategory moduleCategory, String description) {
        this.name = name;
        this.moduleCategory = moduleCategory;
        this.description = description;
        this.animation.setDirection(enabled ? Direction.FORWARDS : Direction.BACKWARDS);

        INSTANCE_CACHE.put(getClass(), this);
        bindStaticInstance();
    }

    public void setModuleServer(IModule.ServerType... servers) {
        allowedServers.clear();
        allowedServers.addAll(Arrays.asList(servers));
    }

    public final void setEnabled(boolean shouldEnable) {
        if (enabled == shouldEnable) return;

        if (shouldEnable && !isAllowedServer()) {
            String serverList = allowedServers.isEmpty() ? "всех серверах" : String.join(", ", allowedServers.stream().map(ServerType::name).toList());
            feedbackFromKeybind = false;
            printClient("Модуль \"" + name + "\" работает только на " + serverList);
            return;
        }

        enabled = shouldEnable;
        this.animation.setDirection(enabled ? Direction.FORWARDS : Direction.BACKWARDS);

        try {
            if (enabled) onEnabled();
            else onDisabled();

            if (feedbackFromKeybind && key != 0 && key != -1) {
                handlerClient().getNotificationManager().add(
                        name + (enabled ? " "  + "enabled" : " " + "disabled"),
                        2,
                        enabled ? NotificationType.SUCCESS : NotificationType.ERROR,
                        RenderType.WORLD
                );

                if (Interface.INSTANCE.isEnabled() && Interface.INSTANCE.elements.isSelected("Notifications")) {
                    playToggleSound();
                }
            }

        } catch (Exception e) {
            reportError(e);
        } finally {
            feedbackFromKeybind = false;
        }
    }

    private void playToggleSound() {
        Float vol = NotificationDragging.INSTANCE.getVolumeSetting().getValue();
        double volume = vol != null ? vol : 1.0;
        new SoundUtil(enabled ? "module_enable" : "module_disable", volume).play();
    }

    public boolean isAllowedServer() {
        return allowedServers.isEmpty() || allowedServers.stream().anyMatch(ServerType::isConnected);
    }

    private void reportError(Exception ex) {
        if (enabled) {
            setEnabled(false);
            printClient("Модуль \"" + name + "\" был автоматически отключён из-за ошибки. Передайте это разработчику.");
        }

        String prefix = "[" + name + "]";
        String baseMsg = "Произошла неизвестная ошибка в модуле.";
        String advice = "Пожалуйста, сообщите об этом разработчику.";

        if (mc.player != null) {
            printClient(prefix + " " + Formatting.RED + baseMsg + Formatting.WHITE + " " + advice);
            ex.printStackTrace();
        } else {
            System.err.println(prefix + " " + baseMsg + " " + advice);
            ex.printStackTrace();
        }
    }

    public void toggle() {
        feedbackFromKeybind = true;
        setEnabled(!enabled);
    }

    public void onEnabled() {
        LynxWave.getInstance().getEventBus().register(this);
    }

    public void onDisabled() {
        LynxWave.getInstance().getEventBus().unregister(this);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T instance(Class<T> clazz) {
        return (T) INSTANCE_CACHE.computeIfAbsent(clazz, Module::resolveModule);
    }

    protected static <T extends Module> T link(final Class<T> clazz) {
        return instance(clazz);
    }

    private static Module resolveModule(Class<? extends Module> clazz) {
        Module module = LynxWave.getInstance().getModuleManager().get(clazz);
        if (module == null) {
            throw new IllegalStateException("Module \"" + clazz.getSimpleName() + "\" is not registered yet");
        }
        return module;
    }

    private void bindStaticInstance() {
        try {
            Field instanceField = getClass().getDeclaredField("INSTANCE");
            if (Modifier.isStatic(instanceField.getModifiers()) && instanceField.getType().isAssignableFrom(getClass())) {
                if (!instanceField.canAccess(null)) {
                    instanceField.setAccessible(true);
                }
                instanceField.set(null, this);
            }
        } catch (NoSuchFieldException ignored) {
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to bind module instance for " + getClass().getSimpleName(), e);
        }
    }

    public static void sendPacket(Packet<?> packet) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(packet);
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        try (PendingUpdateManager pendingUpdateManager = ((IClientWorldAccessor) mc.world).getPendingUpdateManager().incrementSequence();) {
            int i = pendingUpdateManager.getSequence();
            mc.getNetworkHandler().sendPacket(packetCreator.predict(i));
        }
    }

    public static void sendPacketSilent(Packet<?> packet) {
        if (mc.getNetworkHandler() == null) return;
        LynxWave.silentPackets.add(packet);
        mc.getNetworkHandler().sendPacket(packet);
    }
}
