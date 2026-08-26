package dev.client.api.nullcry.helper.other;

import dev.client.api.nullcry.ClientApi;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.function.DoubleSupplier;

public class SoundUtil implements ClientApi {
    private final String name;
    private Clip clip;
    private Thread creating;
    private volatile DoubleSupplier volumeProvider;

    public SoundUtil(String name) {
        this(name, 1.0);
    }

    public SoundUtil(String name, double initialVolume) {
        this.name = name;
        this.volumeProvider = () -> initialVolume;
        init();
    }

    public SoundUtil(String name, DoubleSupplier volumeProvider) {
        this.name = name;
        this.volumeProvider = volumeProvider != null ? volumeProvider : () -> 1.0;
        init();
    }

    public void setVolumeProvider(DoubleSupplier volumeProvider) {
        this.volumeProvider = volumeProvider != null ? volumeProvider : () -> 1.0;
        applyVolumeFromProvider();
    }

    private void init() {
        try {
            this.clip = AudioSystem.getClip();
        } catch (LineUnavailableException e) {
            System.out.println("Debug error: " + e);
        }

        this.creating = ThreadManager.run(this::create);
    }

    public void play(int count) {
        ThreadManager.run(() -> {
            try {
                this.creating.join();
            } catch (InterruptedException e) {
                System.out.println("Debug error: " + e);
                return;
            }
            if (clip == null) return;

            applyVolumeFromProvider();

            if (clip.isRunning()) clip.stop();
            clip.setFramePosition(0);

            if (count <= 0) {
                clip.start();
            } else {
                clip.loop(count);
            }
        });
    }

    public void play() {
        this.play(0);
    }

    public void stop() {
        try {
            this.creating.join();
        } catch (InterruptedException e) {
            System.out.println("Debug error: " + e);
        }
        if (clip != null) clip.stop();
    }

    private void create() {
        try (InputStream resourceStream = SoundUtil.class.getResourceAsStream(getSoundResource(name))) {
            if (resourceStream == null) {
                System.out.println("Resource not found: " + getSoundResource(name));
                return;
            }

            try (BufferedInputStream bufferedIn = new BufferedInputStream(resourceStream);
                 AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn)) {

                clip.open(audioStream);
                applyVolumeFromProvider();

            } catch (Exception e) {
                System.out.println("Debug error: " + e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void applyVolumeFromProvider() {
        if (clip == null || volumeProvider == null) return;

        double raw = 1.0;
        try {
            raw = volumeProvider.getAsDouble();
        } catch (Exception ignored) { }

        float v = (float) (raw > 1.0 ? raw / 100.0 : raw);
        v = Math.max(0.0f, Math.min(1.0f, v));

        try {
            FloatControl vc = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float min = vc.getMinimum();
            float max = vc.getMaximum();
            float gainDb = (v <= 0.0001f) ? min : (float) (20.0 * Math.log10(v));
            gainDb = Math.max(min, Math.min(gainDb, max));
            vc.setValue(gainDb);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public String getSoundResource(String name) {
        return "/assets/lynxwave/sounds/" + name.toLowerCase(Locale.ROOT) + ".wav";
    }

    public static class ThreadManager {
        public static Thread run(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.start();
            return thread;
        }
    }
}
