package dev.other;

import de.jcm.discordgamesdk.Core;
import de.jcm.discordgamesdk.CreateParams;
import de.jcm.discordgamesdk.activity.Activity;
import de.jcm.discordgamesdk.activity.ActivityType;
import de.jcm.discordgamesdk.user.DiscordUser;
import dev.client.LynxWave;
import dev.client.api.nullcry.helper.client.ConnectionHelper;

public final class DiscordManager {
    private static final long APP_ID = 1511138735805435934L;
    private static final String LARGE_IMAGE_KEY = "https://i.postimg.cc/7P90rS12/ezgif-52c50fea6229c3.gif";

    private static final String SMALL_NEXUS = "https://i.postimg.cc/RZjggMQs/photo-2026-01-25-14-46-22.jpg";
    private static final String SMALL_FUNTIME = "https://i.imgur.com/eKb1Vbk.png";
    private static final String SMALL_SPOOKY = "https://i.postimg.cc/tCy2HZ4T/FYr-QDq-67zo.jpg";
    private static final String SMALL_HOLYWORLD = "https://i.imgur.com/FqEcDAY.png";
    private static final String SMALL_REALLYWORLD = "https://i.imgur.com/GL7Lnzu.png";
    private static final String SMALL_SKYTIME = "https://i.imgur.com/VFgPGPG.png";
    private static final String SMALL_FUNSKY = "https://i.imgur.com/gUv2pA1.png";
    private static final String SMALL_METAHVH = "https://i.imgur.com/LzPB0Pj.png";

    private static final boolean SUPPORTED =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private Core core;
    private Thread callbacks;
    private Thread updater;

    private String username;
    private int uid;
    private String build;
    private String releaseType;

    private volatile String lastSmallKey = "";
    private volatile boolean lastPlaying = false;
    private volatile String lastBottom = "";
    private volatile String lastLargeHover = "";
    private volatile String lastSmallHover = "";

    private long startedAt = 0L;

    public void initRpc() {
        if (!SUPPORTED) return;
        this.username = LynxWave.getInstance().getUserProfile().getUser();
        this.uid = Integer.parseInt(String.valueOf(LynxWave.getInstance().getUserProfile().getUid()));
        this.build = LynxWave.getInstance().getClientInfo().getVersion();
        this.releaseType = String.valueOf(LynxWave.getInstance().getClientInfo().getType());

        start();
    }
    public boolean isDiscordAvailable() {
        if (!SUPPORTED) return false;
        try {
            return core != null && core.isOpen() && core.isDiscordRunning();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public DiscordUser getCurrentUserSafe() {
        if (!SUPPORTED) return null;
        if (!isDiscordAvailable()) return null;
        try {
            return core.userManager().getCurrentUser();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void start() {
        if (!SUPPORTED) {
            core = null;
            return;
        }

        try (CreateParams params = new CreateParams()) {
            params.setClientID(APP_ID);
            params.setFlags(CreateParams.Flags.NO_REQUIRE_DISCORD);
            core = new Core(params);
        } catch (Throwable ignored) {
            core = null;
            return;
        }

        if (startedAt == 0L) {
            startedAt = System.currentTimeMillis() / 1000L;
        }

        pushActivity();

        callbacks = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (core != null) core.runCallbacks();
                } catch (Throwable ignored) {
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Discord-RPC-Callbacks");
        callbacks.setDaemon(true);
        callbacks.start();

        updater = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    pushActivity();
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable ignored) {
                }
            }
        }, "Discord-RPC-Updater");
        updater.setDaemon(true);
        updater.start();
    }

    private void pushActivity() {
        if (core == null) return;

        boolean playing = isServer();
        String bottom = footerLine();
        String smallKey = isServerSmallIcon(playing);
        String largeHover = isHoveredBigIcon(playing);
        String smallHover = isHoveredSmallIcon(playing);

        if (playing == lastPlaying
                && smallKey.equals(lastSmallKey)
                && bottom.equals(lastBottom)
                && largeHover.equals(lastLargeHover)
                && smallHover.equals(lastSmallHover)) {
            return;
        }


        lastPlaying = playing;
        lastSmallKey = smallKey;
        lastBottom = bottom;
        lastLargeHover = largeHover;
        lastSmallHover = smallHover;

        try (Activity a = new Activity()) {
            a.setType(ActivityType.PLAYING);
            a.setState(bottom);

            a.timestamps().setStart(java.time.Instant.ofEpochSecond(startedAt));

            a.assets().setLargeImage(LARGE_IMAGE_KEY);
            a.assets().setLargeText(largeHover);
            a.assets().setSmallImage(smallKey);
            a.assets().setSmallText(smallHover);

            core.activityManager().updateActivity(a);
        } catch (Throwable ignored) {
        }
    }

    private boolean isServer() {
        String ip = ConnectionHelper.getServerIP();
        return ip != null && !ip.isEmpty() && !ip.equals("mainmenu") && !ip.equals("local");
    }

    private String footerLine() {
        return "User: " + username + " • UID: " + uid + " • Build: " + build;
    }

    private String serverName() {
        String ip = ConnectionHelper.getServerIP();
        if (ConnectionHelper.isNM()) return "NexusMine";
        if (ConnectionHelper.isFT()) return "FunTime";
        if (ConnectionHelper.isSpookyTime()) return "SpookyTime";
        if (ConnectionHelper.isHW()) return "HolyWorld";
        if (ConnectionHelper.isRW()) return "ReallyWorld";
        if (ConnectionHelper.isFunSky()) return "FunSky";
        if (ConnectionHelper.isConnectedToServer("skytime")) return "SkyTime";
        if (ConnectionHelper.isConnectedToServer("metahvh")) return "MetaHvH";
        return (ip == null || ip.isEmpty()) ? "Unknown" : ip.toLowerCase();
    }

    private String isHoveredBigIcon(boolean playing) {
        if (!playing) {
            return "Release: " + releaseType;
        }
        return "LynxWave Boost";
    }

    private String isHoveredSmallIcon(boolean playing) {
        if (!playing) {
            return "LynxWave Boost";
        }
        return "Playing " + serverName();
    }

    private String isServerSmallIcon(boolean playing) {
        if (!playing) {
            return LARGE_IMAGE_KEY;
        }
        if (ConnectionHelper.isFT()) return SMALL_FUNTIME;
        if (ConnectionHelper.isNM()) return SMALL_NEXUS;
        if (ConnectionHelper.isSpookyTime()) return SMALL_SPOOKY;
        if (ConnectionHelper.isHW()) return SMALL_HOLYWORLD;
        if (ConnectionHelper.isRW()) return SMALL_REALLYWORLD;
        if (ConnectionHelper.isFunSky()) return SMALL_FUNSKY;
        if (ConnectionHelper.isConnectedToServer("skytime")) return SMALL_SKYTIME;
        if (ConnectionHelper.isConnectedToServer("metahvh")) return SMALL_METAHVH;
        return LARGE_IMAGE_KEY;
    }

    public void stop() {
        if (!SUPPORTED) return;
        if (updater != null) updater.interrupt();
        if (callbacks != null) callbacks.interrupt();
        if (core != null) {
            try { core.close(); } catch (Throwable ignored) {}
            core = null;
        }
    }
}
