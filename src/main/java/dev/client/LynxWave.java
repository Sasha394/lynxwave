package dev.client;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.ClientInfo;
import dev.client.api.nullcry.cmdHelper.ConsoleLogger;
import dev.client.api.nullcry.cmdHelper.MinecraftLogger;
import dev.client.api.nullcry.cmdHelper.MultiLogger;
import dev.client.api.nullcry.cmdHelper.interfaces.CommandDispatcher;
import dev.client.api.nullcry.cmdHelper.interfaces.Logger;
import dev.client.api.nullcry.cmdHelper.managers.configuration.ConfigurationManager;
import dev.client.api.nullcry.cmdHelper.managers.dragHandler.DraggableManager;
import dev.client.api.nullcry.cmdHelper.managers.friend.FriendManager;
import dev.client.api.nullcry.cmdHelper.managers.macro.MacroManager;
import dev.client.api.nullcry.cmdHelper.managers.staff.StaffManager;
import dev.client.api.nullcry.cmdHelper.managers.staffPrefix.StaffPrefixManager;
import dev.client.api.nullcry.events.PriorityEventBus;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.helper.client.irc.IRClient;
import dev.client.api.nullcry.helper.client.server.ServerUtils;
import dev.client.api.nullcry.helper.other.Console;
import dev.client.api.nullcry.modules.ModuleManager;
import dev.client.api.nullcry.modules.listener.ListenerRepository;
import dev.client.api.nullcry.uiClient.altManager.AltConfiguration;
import dev.client.api.nullcry.uiClient.altManager.AltScreenManager;
import dev.client.api.nullcry.uiClient.autobuy.settings.AutoBuyConfig;
import dev.client.api.nullcry.uiClient.clickGui.ClickGuiScreen;
import dev.client.api.nullcry.uiClient.notification.NotificationManager;
import dev.client.cmd.core.Cmd_Initializer;
import dev.client.component.ComponentManager;
import dev.client.modules.core.combat.killaura.attack.AttackPerpetrator;
import dev.client.modules.core.combat.killaura.attack.IdealHitUtil;
import dev.client.modules.core.combat.killaura.rotation.neuro.NeuroRotationManager;
import dev.other.DiscordManager;
import dev.other.customUser.ProfileType;
import dev.other.customUser.UserProfile;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.session.Session;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.Uuids;
import org.lwjgl.glfw.GLFW;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * я Anon1mysxd у тоесть ебать крутой тг мой Anon1mysxd
 */
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LynxWave implements ModInitializer, ClientApi {
    public final BuildType mode = BuildType.DEVELOPMENT;
    public static final List<String> customServer = List.of(
            "FunTime:mc.funtime.su",
            "HolyWorld:start.holyworld.ru",
            "ReallyWorld:mc.reallyworld.ru",
            "SpookyTime:spookytime.net",
            "MetaHVH:mc.metahvh.space"
    );

    public static ArrayList<Packet<?>> silentPackets = new ArrayList<>();

    // ДА Я ГЕНИЙ СУКААААА, СОСИТЕ mojang..
    @Getter public static float tickTimer = 1.0f;
    @Getter public static float jumpTicks = 0.02F;

    @Getter @Setter private volatile boolean isFocusedRender = false;

    public boolean customTitles() {
        return true;
    }

    public boolean cryptEnabled() {
        return mode == BuildType.BUILD;
    }

    public boolean development() {
        return mode == BuildType.DEVELOPMENT;
    }

    @Getter private static LynxWave instance;
    final PriorityEventBus eventBus = new PriorityEventBus();
    final Logger loggerCMD = new MultiLogger(List.of(new ConsoleLogger(), new MinecraftLogger()));

    final File clientDir = new File(mc.runDirectory + "\\LynxWave");
    final File filesDir = new File(clientDir, "files");

    ClientInfo clientInfo;
    UserProfile userProfile;
    DiscordManager discordManager;

    ConfigurationManager configurationManager;
    AltConfiguration altConfiguration;
    DraggableManager draggableManager;
    FriendManager friendManager;
    MacroManager macroManager;
    StaffManager staffManager;
    StaffPrefixManager staffPrefixManager;

    ModuleManager moduleManager;
    ComponentManager componentManager;

    CommandDispatcher commandDispatcher;
    Cmd_Initializer cmdInitializer;

    ClickGuiScreen clickGuiScreen;
    AltScreenManager altScreenManager;
    NotificationManager notificationManager;

    ListenerRepository listenerRepository;
    ServerUtils serverUtils;
    AttackPerpetrator attackPerpetrator;
    IdealHitUtil idealHitUtil;
    NeuroRotationManager neuroRotationManager;
    @Setter IRClient IRClient;

    public LynxWave()  {
     //   if (!HwidVerifier.check()) {
          //  mc.scheduleStop();
          //  return;
       // }
        instance = this;
        createDirectories();
        init();
        eventBus.register(this);
    }

    private void createDirectories() {
        if (!clientDir.exists()) clientDir.mkdirs();
        if (!filesDir.exists()) filesDir.mkdirs();
    }

    public void init() {
        try {
            initClientServices();
            registerManagers();
            initModules();
            initListeners();
            initScreens();
            initCMD();
            initManagers();
            this.clickGuiScreen = new ClickGuiScreen();
            idealHitUtil = new IdealHitUtil();
        } catch (Throwable t) {
            System.err.println("[LynxWave] Init failed:");
            t.printStackTrace();
            Throwable c = t.getCause();
            while (c != null) {
                System.err.println("Caused by:");
                c.printStackTrace();
                c = c.getCause();
            }
            throw t;
        }
    }
    private static List<String> find(String url) {
        ArrayList<String> uuids = new ArrayList<String>();
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));) {
                String line;
                while ((line = reader.readLine()) != null) {
                    uuids.add(line.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return uuids;
    }

    void initClientServices() {
        clientInfo = new ClientInfo("LynxWave", "1.6.8", "/assets/lynxwave/", mode == BuildType.BUILD ? ClientInfo.BuildType.Beta : ClientInfo.BuildType.Private);
        userProfile = ProfileType.Developer.createProfile();
        discordManager = new DiscordManager();
        discordManager.start();
    }

    void registerManagers() {
        configurationManager = new ConfigurationManager();
        altConfiguration = new AltConfiguration();
        draggableManager = new DraggableManager();
        friendManager = new FriendManager();
        macroManager = new MacroManager();
        staffManager = new StaffManager();
        staffPrefixManager = new StaffPrefixManager();

        attackPerpetrator = new AttackPerpetrator();
        serverUtils = new ServerUtils();
        neuroRotationManager = new NeuroRotationManager();

//        IRClient = null;
    }



    void initModules() {
        moduleManager = new ModuleManager();
        moduleManager.init();
        componentManager = new ComponentManager();
        componentManager.initComponents();
    }

    void initScreens() {
        altScreenManager = new AltScreenManager();
        notificationManager = new NotificationManager();
    }

    private void initCMD() {
        cmdInitializer = new Cmd_Initializer(mc, loggerCMD, macroManager, configurationManager);
        commandDispatcher = cmdInitializer.getCommandDispatcher();
    }


    private void initManagers() {
        try {
            configurationManager.init();
            altConfiguration.init();
            {
                String sel = AltConfiguration.getSelected();
                if (sel != null && !sel.isBlank()) {
                    String n = sel.trim();
                    mc.session = new Session(
                            n,
                            Uuids.getOfflinePlayerUuid(n),
                            "0",
                            Optional.empty(),
                            Optional.of("offline")
                    );
                }
            }

            friendManager.init();
            macroManager.init();
            staffManager.init();
            staffPrefixManager.init();
            draggableManager.init();
            draggableManager.load();
            AutoBuyConfig.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initListeners() {
        listenerRepository = new ListenerRepository();
        listenerRepository.setup();
    }

    final KeyBindEvent keyBindEvent = new KeyBindEvent(-1);

    public void keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == 0) return;
        keyBindEvent.setKey(key);
        eventBus.post(keyBindEvent);
        macroManager.onKey(key);
    }

    public static void openLink(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec("cmd /c start " + url);
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec("open " + url);
            } else {
                Runtime.getRuntime().exec("xdg-open " + url);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public enum BuildType {
        BUILD,
        DEVELOPMENT
    }

    public interface DelayHolder {
        double lumora$getThrowInventoryScreenDelay();
        void lumora$setThrowInventoryScreenDelay(double value);

        double lumora$getChestScreenDelay();
        void lumora$setChestScreenDelay(double v);
    }

    @Override
    public void onInitialize() {
        Console.log("Client was successfully initialized");
    }
}
