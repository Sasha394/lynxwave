package dev.client.modules.core.combat.killaura.rotation.neuro;

import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.other.Console;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class NeuroRotationManager implements ClientApi {
    private static final String PROFILE_EXTENSION = ".json";
    private static final String STATE_FILE_NAME = "_state.dat";
    private static final Pattern INVALID_PROFILE_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final List<String> YAW_MODEL_CANDIDATES = List.of("delta_yaw_model.cbm", "yaw_model.cbm", "neuro_yaw_model.cbm");
    private static final List<String> PITCH_MODEL_CANDIDATES = List.of("delta_pitch_model.cbm", "pitch_model.cbm", "neuro_pitch_model.cbm");
    private static final String BUNDLED_MODEL_RESOURCE_PREFIX = "/assets/lynxwave/neuro/";
    private static final String BUNDLED_YAW_MODEL = "delta_yaw_model.cbm";
    private static final String BUNDLED_PITCH_MODEL = "delta_pitch_model.cbm";
    private static final float MODEL_BLEND_MIN = 0.05f;
    private static final float MODEL_BLEND_MAX = 0.28f;
    private static final long MAX_FRAME_TIME_MS = 200L;
    private static final long TARGET_SWITCH_WINDOW_MS = 220L;
    private static final long SWING_PHASE_MS = 85L;
    private static final long RECOVER_PHASE_MS = 280L;
    private static final int SITUATION_SAMPLE_LIMIT = 1200;

    private final Gson gson = new Gson();
    private final Type sampleListType = new TypeToken<List<RotationSample>>() { }.getType();
    private final File profilesDirectory = new File(LynxWave.getInstance().getFilesDir(), "neuro");
    private final Path statePath = profilesDirectory.toPath().resolve(STATE_FILE_NAME);
    private final Map<String, NeuroProfile> profilesByKey = new LinkedHashMap<>();
    private final List<RotationSample> recordingSamples = new ArrayList<>();

    private List<RotationSample> playbackSamples = List.of();
    private String playbackProfileName;
    private String recordingProfileName;
    private String preferredPlaybackProfileName;
    private boolean recording;
    private boolean playing;
    private boolean snapOnlyOnAttack;
    private boolean recordingPausedByKillAura;
    private int playbackIndex;
    private long playbackLastUpdateMs;
    private long playbackSampleElapsedMs;
    private float lastYaw;
    private float lastPitch;
    private float lastBodyYaw;
    private float lastHeadYaw;
    private long lastSampleTimeMs;
    private boolean hasPreviousRotation;

    private Object yawModel;
    private Object pitchModel;
    private boolean modelLoaded;
    private String modelStatus = "missing";
    private boolean loggedPredictionError;

    private long lastSwingTimeMs;
    private float prevModelYaw;
    private float prevModelPitch;
    private float prevTargetYaw;
    private float prevTargetPitch;
    private float prevDistance = -1.0f;
    private float prevPlayerSpeed;
    private float prevTargetSpeed;
    private AimPhase currentPhase = AimPhase.IDLE;
    private int lastTargetEntityId = Integer.MIN_VALUE;
    private long lastTargetSwitchTimeMs;

    public NeuroRotationManager() {
        ensureDirectory();
        loadState();
        reloadProfiles();
        restorePlaybackAfterStartup();
        LynxWave.getInstance().getEventBus().register(this);
    }

    @Subscribe
    public void onPlayerTick(TickEvent event) {
        capturePlayerRotationSample();
    }

    @Subscribe
    public void onAttack(PlayerAttackEvent event) {
        if (event.getEntity() != null) {
            lastSwingTimeMs = System.currentTimeMillis();
        }
    }

    public enum AimPhase {
        IDLE,
        ACQUIRE,
        TRACK,
        SWING,
        RECOVER
    }

    public synchronized void startRecording(String profileName) {
        String validatedName = validateProfileName(profileName);
        if (recording) {
            throw new IllegalStateException("Neuro recording is already running.");
        }

        if (playing) {
            stopPlaybackInternal();
        }

        recording = true;
        recordingPausedByKillAura = false;
        recordingProfileName = validatedName;
        recordingSamples.clear();
        recordingSamples.add(new RotationSample(0.0f, 0.0f));
        currentPhase = AimPhase.IDLE;
        lastTargetEntityId = Integer.MIN_VALUE;
        lastTargetSwitchTimeMs = 0L;

        ClientPlayerEntity player = mc.player;
        if (player != null) {
            lastYaw = player.getYaw();
            lastPitch = player.getPitch();
            lastBodyYaw = player.bodyYaw;
            lastHeadYaw = player.headYaw;
            lastSampleTimeMs = System.currentTimeMillis();
            hasPreviousRotation = true;
        } else {
            hasPreviousRotation = false;
        }
    }

    public synchronized int stopRecording() throws IOException {
        if (!recording) {
            throw new IllegalStateException("Neuro recording is not running.");
        }

        recording = false;
        recordingPausedByKillAura = false;
        hasPreviousRotation = false;
        lastSampleTimeMs = 0L;

        String profileName = recordingProfileName;
        List<RotationSample> samplesToSave = new ArrayList<>(recordingSamples);
        recordingSamples.clear();
        recordingProfileName = null;

        if (samplesToSave.isEmpty()) {
            samplesToSave.add(new RotationSample(0.0f, 0.0f));
        }

        saveProfile(profileName, samplesToSave);
        profilesByKey.put(profileName.toLowerCase(Locale.ROOT), new NeuroProfile(profileName, samplesToSave));
        preferredPlaybackProfileName = profileName;
        saveStateQuietly();
        return samplesToSave.size();
    }

    public synchronized int startPlayback(String profileName) {
        if (recording) {
            throw new IllegalStateException("Stop recording before starting playback.");
        }

        NeuroProfile profile = getProfile(profileName);
        if (profile == null) {
            throw new IllegalArgumentException("Neuro profile '" + profileName + "' was not found.");
        }

        startPlaybackInternal(profile, true);
        return playbackSamples.size();
    }

    public synchronized boolean stopPlayback() {
        if (!playing) {
            return false;
        }
        stopPlaybackInternal();
        return true;
    }

    public synchronized RotationSample nextNeuroSample(Angle currentAngle, Angle targetAngle, Entity targetEntity) {
        onTargetTracked(targetEntity);

        RotationSample fromProfile = nextPlaybackSampleInternal();
        if (fromProfile == null) {
            return null;
        }

        RotationSample situationSample = shouldUseSituationSample()
                ? selectSituationSample(currentAngle, targetAngle, targetEntity)
                : null;
        if (situationSample != null) {
            fromProfile = situationSample;
        }

        AimPhase phase = resolvePhase(currentAngle, targetAngle, targetEntity);
        currentPhase = phase;
        RotationSample profileSample = adaptProfileSample(fromProfile, phase, currentAngle, targetAngle);

        if (!modelLoaded || currentAngle == null || targetAngle == null || targetEntity == null || mc.player == null) {
            return profileSample;
        }

        RotationSample fromModel = predictFromModel(currentAngle, targetAngle, targetEntity);
        if (fromModel == null) {
            return profileSample;
        }

        return blendSamples(profileSample, fromModel, currentAngle, targetAngle, phase);
    }

    public synchronized int reloadProfiles() {
        profilesByKey.clear();
        ensureDirectory();
        reloadModels();

        if (!profilesDirectory.exists()) {
            return 0;
        }

        Path directory = profilesDirectory.toPath();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(PROFILE_EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(this::loadProfileFromPath);
        } catch (IOException exception) {
            Console.log("Failed to reload neuro profiles", exception);
        }

        if (playing && playbackProfileName != null) {
            NeuroProfile current = getProfile(playbackProfileName);
            if (current == null) {
                NeuroProfile fallback = resolveAutoplayProfile();
                if (fallback == null) {
                    stopPlaybackInternal();
                } else {
                    startPlaybackInternal(fallback, true);
                }
            } else {
                playbackSamples = current.samples();
                playbackIndex = Math.min(playbackIndex, Math.max(playbackSamples.size() - 1, 0));
            }
        }

        return profilesByKey.size();
    }

    public synchronized List<String> getProfileNames() {
        return profilesByKey.values().stream()
                .map(NeuroProfile::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public synchronized boolean hasProfile(String profileName) {
        return getProfile(profileName) != null;
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    public synchronized boolean isPlaying() {
        return playing;
    }

    public synchronized boolean isModelLoaded() {
        return modelLoaded;
    }

    public synchronized boolean hasUsableSource() {
        return recording || playing;
    }

    public synchronized boolean isPlaybackActive() {
        return playing && !playbackSamples.isEmpty();
    }

    public synchronized boolean isSnapOnlyOnAttack() {
        return snapOnlyOnAttack;
    }

    public synchronized void setSnapOnlyOnAttack(boolean enabled) {
        if (snapOnlyOnAttack == enabled) {
            return;
        }
        snapOnlyOnAttack = enabled;
        saveStateQuietly();
    }

    public synchronized boolean toggleSnapOnlyOnAttack() {
        snapOnlyOnAttack = !snapOnlyOnAttack;
        saveStateQuietly();
        return snapOnlyOnAttack;
    }

    public synchronized String getModelStatus() {
        return modelStatus;
    }

    public synchronized String getRecordingProfileName() {
        return recordingProfileName;
    }

    public synchronized String getPlaybackProfileName() {
        return playbackProfileName;
    }

    public synchronized AimPhase getCurrentPhase() {
        return currentPhase;
    }

    public synchronized void onTargetTracked(Entity targetEntity) {
        if (targetEntity == null) {
            onNoTarget();
            return;
        }

        int entityId = targetEntity.getId();
        if (lastTargetEntityId != entityId) {
            lastTargetEntityId = entityId;
            lastTargetSwitchTimeMs = System.currentTimeMillis();
            currentPhase = AimPhase.ACQUIRE;
            resetPredictionState();
        }
    }

    public synchronized void onNoTarget() {
        lastTargetEntityId = Integer.MIN_VALUE;
        lastTargetSwitchTimeMs = 0L;
        currentPhase = AimPhase.IDLE;
        resetPredictionState();
        playbackSampleElapsedMs = 0L;
        playbackLastUpdateMs = System.currentTimeMillis();
    }

    public synchronized File getProfilesDirectory() {
        return profilesDirectory;
    }

    public synchronized void openProfilesDirectory() throws IOException {
        ensureDirectory();
        Runtime.getRuntime().exec(new String[]{"explorer", profilesDirectory.getAbsolutePath()});
    }

    public synchronized boolean deleteProfile(String profileName) throws IOException {
        NeuroProfile profile = getProfile(profileName);
        if (profile == null) {
            return false;
        }

        Path outputPath = profilesDirectory.toPath().resolve(profile.name() + PROFILE_EXTENSION);
        Files.deleteIfExists(outputPath);
        profilesByKey.remove(profile.name().toLowerCase(Locale.ROOT));
        boolean preferredDeleted = preferredPlaybackProfileName != null
                && preferredPlaybackProfileName.equalsIgnoreCase(profile.name());
        if (preferredDeleted) {
            preferredPlaybackProfileName = null;
        }

        if (playing && playbackProfileName != null && playbackProfileName.equalsIgnoreCase(profile.name())) {
            NeuroProfile fallback = resolveAutoplayProfile();
            if (fallback == null) {
                stopPlaybackInternal();
            } else {
                startPlaybackInternal(fallback, true);
            }
        } else if (preferredDeleted) {
            saveStateQuietly();
        }

        return true;
    }

    public synchronized void resetPredictionState() {
        prevModelYaw = 0.0f;
        prevModelPitch = 0.0f;
        prevTargetYaw = 0.0f;
        prevTargetPitch = 0.0f;
        prevDistance = -1.0f;
        prevPlayerSpeed = 0.0f;
        prevTargetSpeed = 0.0f;
    }

    private synchronized void capturePlayerRotationSample() {
        if (!recording) {
            return;
        }

        KillAura killAura = KillAura.INSTANCE;
        if (killAura == null || !killAura.isEnabled()) {
            recordingPausedByKillAura = true;
            hasPreviousRotation = false;
            lastSampleTimeMs = 0L;
            return;
        }
        LivingEntity target = killAura.getTarget();
        if (target == null || !target.isAlive()) {
            hasPreviousRotation = false;
            lastSampleTimeMs = 0L;
            return;
        }
        if (recordingPausedByKillAura) {
            recordingPausedByKillAura = false;
        }

        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return;
        }

        float yaw = player.getYaw();
        float pitch = player.getPitch();
        float bodyYaw = player.bodyYaw;
        float headYaw = player.headYaw;
        if (!hasPreviousRotation) {
            lastYaw = yaw;
            lastPitch = pitch;
            lastBodyYaw = bodyYaw;
            lastHeadYaw = headYaw;
            lastSampleTimeMs = System.currentTimeMillis();
            hasPreviousRotation = true;
            return;
        }

        long now = System.currentTimeMillis();
        long dtMs = lastSampleTimeMs > 0L ? Math.max(1L, now - lastSampleTimeMs) : 50L;
        lastSampleTimeMs = now;
        float deltaYaw = MathHelper.wrapDegrees(yaw - lastYaw);
        float deltaPitch = MathHelper.wrapDegrees(pitch - lastPitch);
        float deltaBodyYaw = MathHelper.wrapDegrees(bodyYaw - lastBodyYaw);
        float deltaHeadYaw = MathHelper.wrapDegrees(headYaw - lastHeadYaw);
        double gcd = Math.max(MathUtil.computeGcd(), 1.0E-4);
        float yawSpeed = Math.abs(deltaYaw) * 50.0f / dtMs;
        float pitchSpeed = Math.abs(deltaPitch) * 50.0f / dtMs;
        float screenSpeed = (float) Math.hypot(yawSpeed, pitchSpeed);
        float gcdStepAverage = (float) ((Math.abs(deltaYaw) / gcd + Math.abs(deltaPitch) / gcd) * 0.5);
        double velocityX = player.getVelocity().x;
        double velocityZ = player.getVelocity().z;
        float horizontalSpeed = (float) Math.hypot(velocityX, velocityZ);
        float targetDistance = (float) player.getEyePos().distanceTo(target.getEyePos());
        float targetSpeed = (float) Math.hypot(target.getVelocity().x, target.getVelocity().z);
        boolean critical = player.fallDistance > 0.0f && !player.isOnGround();
        int targetType = resolveTargetType(target);
        boolean onGround = player.isOnGround();
        boolean sprinting = player.isSprinting();
        recordingSamples.add(new RotationSample(
                deltaYaw,
                deltaPitch,
                dtMs,
                horizontalSpeed,
                onGround,
                sprinting,
                deltaBodyYaw,
                deltaHeadYaw,
                targetDistance,
                targetSpeed,
                critical,
                targetType,
                yawSpeed,
                pitchSpeed,
                screenSpeed,
                gcdStepAverage
        ));
        lastYaw = yaw;
        lastPitch = pitch;
        lastBodyYaw = bodyYaw;
        lastHeadYaw = headYaw;
    }

    private RotationSample predictFromModel(Angle currentAngle, Angle targetAngle, Entity targetEntity) {
        float targetDeltaYaw = normalizeAngle(targetAngle.getYaw() - currentAngle.getYaw());
        float targetDeltaPitch = normalizeAngle(targetAngle.getPitch() - currentAngle.getPitch());
        float distance = (float) mc.player.distanceTo(targetEntity);
        float sinceLastHit = (float) Math.min(500L, Math.max(0L, System.currentTimeMillis() - lastSwingTimeMs));
        float diffY = (float) (mc.player.getY() - targetEntity.getY());
        float playerSpeed = (float) Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        float targetSpeed = (float) Math.hypot(targetEntity.getVelocity().x, targetEntity.getVelocity().z);
        float bodyHeadGap = normalizeAngle(mc.player.headYaw - mc.player.bodyYaw);
        float playerOnGround = mc.player.isOnGround() ? 1.0f : 0.0f;
        float playerSprinting = mc.player.isSprinting() ? 1.0f : 0.0f;
        float targetOnGround = targetEntity.isOnGround() ? 1.0f : 0.0f;
        float targetSprinting = targetEntity instanceof LivingEntity living && living.isSprinting() ? 1.0f : 0.0f;

        float[][] features = new float[][]{{
                targetDeltaYaw,
                targetDeltaPitch,
                distance,
                sinceLastHit,
                (float) mc.player.fallDistance,
                diffY,
                prevTargetYaw,
                prevTargetPitch,
                prevModelYaw,
                prevModelPitch,
                prevDistance < 0.0f ? distance : prevDistance,
                playerSpeed,
                targetSpeed,
                prevPlayerSpeed,
                prevTargetSpeed,
                bodyHeadGap,
                playerOnGround,
                playerSprinting,
                targetOnGround,
                targetSprinting
        }};

        try {
            String[][] catFeatures = new String[features.length][0];
            Object predictedYaw = predictCatBoost(yawModel, features, catFeatures);
            Object predictedPitch = predictCatBoost(pitchModel, features, catFeatures);

            float yawStep = (float) getPredictionValue(predictedYaw, 0, 0);
            float pitchStep = (float) getPredictionValue(predictedPitch, 0, 0);

            prevTargetYaw = targetDeltaYaw;
            prevTargetPitch = targetDeltaPitch;
            prevModelYaw = yawStep;
            prevModelPitch = pitchStep;
            prevDistance = distance;
            prevPlayerSpeed = playerSpeed;
            prevTargetSpeed = targetSpeed;

            return new RotationSample(yawStep, pitchStep);
        } catch (Throwable throwable) {
            if (!loggedPredictionError) {
                Console.log("Neuro model prediction failed", throwable);
                loggedPredictionError = true;
            }
            modelLoaded = false;
            yawModel = null;
            pitchModel = null;
            modelStatus = "error";
            return null;
        }
    }

    private Object predictCatBoost(Object model, float[][] features, String[][] catFeatures) throws Exception {
        Method predict = model.getClass().getMethod("predict", float[][].class, String[][].class);
        return predict.invoke(model, features, catFeatures);
    }

    private double getPredictionValue(Object predictions, int row, int column) throws Exception {
        Method get = predictions.getClass().getMethod("get", int.class, int.class);
        return ((Number) get.invoke(predictions, row, column)).doubleValue();
    }

    private void reloadModels() {
        closeLoadedModels();
        modelLoaded = false;
        loggedPredictionError = false;
        resetPredictionState();
        if (loadModelsFromAssets()) {
            modelLoaded = true;
            modelStatus = "loaded_assets";
            return;
        }

        Path yawPath = findFirstModel(YAW_MODEL_CANDIDATES);
        Path pitchPath = findFirstModel(PITCH_MODEL_CANDIDATES);

        if (yawPath == null || pitchPath == null) {
            yawModel = null;
            pitchModel = null;
            modelStatus = "missing";
            return;
        }

        try {
            yawModel = loadCatBoostModel(yawPath.toString());
            pitchModel = loadCatBoostModel(pitchPath.toString());
            modelLoaded = true;
            modelStatus = "loaded_files";
        } catch (Throwable error) {
            yawModel = null;
            pitchModel = null;
            modelStatus = "error";
            Console.log("Failed to load neuro CatBoost models", error);
        }
    }

    private boolean loadModelsFromAssets() {
        try (
                InputStream yawStream = openBundledModel(BUNDLED_YAW_MODEL);
                InputStream pitchStream = openBundledModel(BUNDLED_PITCH_MODEL)
        ) {
            if (yawStream == null || pitchStream == null) {
                return false;
            }

            yawModel = loadCatBoostModel(yawStream);
            pitchModel = loadCatBoostModel(pitchStream);
            return true;
        } catch (Throwable throwable) {
            Console.log("Failed to load bundled neuro CatBoost models from assets", throwable);
            yawModel = null;
            pitchModel = null;
            modelStatus = "error";
            return false;
        }
    }

    private Object loadCatBoostModel(String path) throws Exception {
        Class<?> modelClass = Class.forName("ai.catboost.CatBoostModel");
        Method loadModel = modelClass.getMethod("loadModel", String.class);
        return loadModel.invoke(null, path);
    }

    private Object loadCatBoostModel(InputStream stream) throws Exception {
        Class<?> modelClass = Class.forName("ai.catboost.CatBoostModel");
        Method loadModel = modelClass.getMethod("loadModel", InputStream.class);
        return loadModel.invoke(null, stream);
    }

    private InputStream openBundledModel(String fileName) {
        return NeuroRotationManager.class.getResourceAsStream(BUNDLED_MODEL_RESOURCE_PREFIX + fileName);
    }

    private Path findFirstModel(List<String> candidates) {
        for (String fileName : candidates) {
            Path path = profilesDirectory.toPath().resolve(fileName);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private void closeLoadedModels() {
        if (yawModel != null) {
            try {
                if (yawModel instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } catch (Throwable ignored) {
            }
        }
        if (pitchModel != null) {
            try {
                if (pitchModel instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } catch (Throwable ignored) {
            }
        }
        yawModel = null;
        pitchModel = null;
    }

    private RotationSample nextPlaybackSampleInternal() {
        if (!playing || playbackSamples.isEmpty()) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (playbackLastUpdateMs == 0L) {
            playbackLastUpdateMs = now;
        }
        long elapsedMs = Math.max(0L, now - playbackLastUpdateMs);
        playbackLastUpdateMs = now;
        if (elapsedMs == 0L) {
            return new RotationSample(0.0f, 0.0f);
        }

        elapsedMs = Math.min(elapsedMs, MAX_FRAME_TIME_MS);

        long remaining = elapsedMs;
        long consumedMs = 0L;
        long onGroundMs = 0L;
        long sprintingMs = 0L;
        float accumulatedYaw = 0.0f;
        float accumulatedPitch = 0.0f;
        float accumulatedBodyYaw = 0.0f;
        float accumulatedHeadYaw = 0.0f;
        float accumulatedSpeed = 0.0f;
        float accumulatedTargetDistance = 0.0f;
        float accumulatedTargetSpeed = 0.0f;
        float accumulatedTargetDistanceWeight = 0.0f;
        float accumulatedTargetSpeedWeight = 0.0f;
        float accumulatedYawSpeed = 0.0f;
        float accumulatedPitchSpeed = 0.0f;
        float accumulatedScreenSpeed = 0.0f;
        float accumulatedGcdStepAverage = 0.0f;
        int targetType = 0;
        int criticalVotes = 0;
        int criticalTrue = 0;

        while (remaining > 0L && !playbackSamples.isEmpty()) {
            RotationSample sample = playbackSamples.get(playbackIndex);
            long sampleDt = Math.max(1L, sample.getDtMs());
            long sampleRemaining = Math.max(1L, sampleDt - playbackSampleElapsedMs);
            long used = Math.min(remaining, sampleRemaining);
            float factor = used / (float) sampleDt;

            accumulatedYaw += sample.getYaw() * factor;
            accumulatedPitch += sample.getPitch() * factor;
            accumulatedBodyYaw += sample.getBodyYawDelta() * factor;
            accumulatedHeadYaw += sample.getHeadYawDelta() * factor;
            accumulatedSpeed += sample.getHorizontalSpeed() * used;
            accumulatedYawSpeed += sample.getYawSpeed() * factor;
            accumulatedPitchSpeed += sample.getPitchSpeed() * factor;
            accumulatedScreenSpeed += sample.getScreenSpeed() * factor;
            accumulatedGcdStepAverage += sample.getGcdStepAverage() * factor;
            if (sample.getTargetDistance() > 0.0f) {
                accumulatedTargetDistance += sample.getTargetDistance() * factor;
                accumulatedTargetDistanceWeight += factor;
            }
            if (sample.getTargetSpeed() > 0.0f) {
                accumulatedTargetSpeed += sample.getTargetSpeed() * factor;
                accumulatedTargetSpeedWeight += factor;
            }
            if (targetType == 0 && sample.getTargetType() != 0) {
                targetType = sample.getTargetType();
            }
            criticalVotes++;
            if (sample.isCritical()) {
                criticalTrue++;
            }

            if (sample.isOnGround()) {
                onGroundMs += used;
            }
            if (sample.isSprinting()) {
                sprintingMs += used;
            }

            consumedMs += used;
            remaining -= used;
            playbackSampleElapsedMs += used;

            if (playbackSampleElapsedMs >= sampleDt) {
                playbackSampleElapsedMs = 0L;
                playbackIndex++;
                if (playbackIndex >= playbackSamples.size()) {
                    playbackIndex = 0;
                }
            }
        }

        float avgSpeed = consumedMs > 0L ? accumulatedSpeed / consumedMs : 0.0f;
        boolean onGround = consumedMs > 0L && onGroundMs * 2L >= consumedMs;
        boolean sprinting = consumedMs > 0L && sprintingMs * 2L >= consumedMs;
        float avgTargetDistance = accumulatedTargetDistanceWeight > 0.0f ? accumulatedTargetDistance / accumulatedTargetDistanceWeight : 0.0f;
        float avgTargetSpeed = accumulatedTargetSpeedWeight > 0.0f ? accumulatedTargetSpeed / accumulatedTargetSpeedWeight : 0.0f;
        boolean critical = criticalVotes > 0 && criticalTrue * 2 >= criticalVotes;
        return new RotationSample(
                accumulatedYaw,
                accumulatedPitch,
                consumedMs == 0L ? 1L : consumedMs,
                avgSpeed,
                onGround,
                sprinting,
                accumulatedBodyYaw,
                accumulatedHeadYaw,
                avgTargetDistance,
                avgTargetSpeed,
                critical,
                targetType,
                accumulatedYawSpeed,
                accumulatedPitchSpeed,
                accumulatedScreenSpeed,
                accumulatedGcdStepAverage
        );
    }

    private AimPhase resolvePhase(Angle currentAngle, Angle targetAngle, Entity targetEntity) {
        if (currentAngle == null || targetAngle == null || targetEntity == null || mc.player == null) {
            return AimPhase.IDLE;
        }

        long now = System.currentTimeMillis();
        long sinceLastHit = Math.max(0L, now - lastSwingTimeMs);
        if (sinceLastHit <= SWING_PHASE_MS) {
            return AimPhase.SWING;
        }
        if (sinceLastHit <= RECOVER_PHASE_MS) {
            return AimPhase.RECOVER;
        }

        float deltaYaw = Math.abs(normalizeAngle(targetAngle.getYaw() - currentAngle.getYaw()));
        float deltaPitch = Math.abs(normalizeAngle(targetAngle.getPitch() - currentAngle.getPitch()));
        float deltaMagnitude = (float) Math.hypot(deltaYaw, deltaPitch);

        float targetSpeed = (float) Math.hypot(targetEntity.getVelocity().x, targetEntity.getVelocity().z);
        float playerSpeed = (float) Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        boolean targetSwitchedRecently = lastTargetSwitchTimeMs > 0L && now - lastTargetSwitchTimeMs <= TARGET_SWITCH_WINDOW_MS;
        if (targetSwitchedRecently || deltaMagnitude > 34.0f || (deltaMagnitude > 20.0f && targetSpeed > 0.19f)) {
            return AimPhase.ACQUIRE;
        }
        if (deltaMagnitude <= 12.0f && playerSpeed <= 0.11f && targetSpeed <= 0.11f) {
            return AimPhase.TRACK;
        }
        return AimPhase.TRACK;
    }

    private RotationSample adaptProfileSample(RotationSample sample, AimPhase phase, Angle currentAngle, Angle targetAngle) {
        if (sample == null || currentAngle == null || targetAngle == null) {
            return sample;
        }

        float deltaYaw = normalizeAngle(targetAngle.getYaw() - currentAngle.getYaw());
        float deltaPitch = normalizeAngle(targetAngle.getPitch() - currentAngle.getPitch());

        float yaw = sample.getYaw();
        float pitch = sample.getPitch();

        switch (phase) {
            case ACQUIRE -> {
                yaw = MathHelper.clamp(yaw + deltaYaw * 0.28f, -35.0f, 35.0f);
                pitch = MathHelper.clamp(pitch + deltaPitch * 0.24f, -25.0f, 25.0f);
            }
            case SWING -> {
                yaw = MathHelper.clamp(yaw + deltaYaw * 0.35f, -35.0f, 35.0f);
                pitch = MathHelper.clamp(pitch + deltaPitch * 0.31f, -25.0f, 25.0f);
            }
            case RECOVER -> {
                yaw *= 0.78f;
                pitch *= 0.72f;
            }
            case TRACK, IDLE -> {
            }
        }

        return new RotationSample(
                yaw,
                pitch,
                sample.getDtMs(),
                sample.getHorizontalSpeed(),
                sample.isOnGround(),
                sample.isSprinting(),
                sample.getBodyYawDelta(),
                sample.getHeadYawDelta(),
                sample.getTargetDistance(),
                sample.getTargetSpeed(),
                sample.isCritical(),
                sample.getTargetType(),
                sample.getYawSpeed(),
                sample.getPitchSpeed(),
                sample.getScreenSpeed(),
                sample.getGcdStepAverage()
        );
    }

    private RotationSample blendSamples(RotationSample profile, RotationSample model, Angle currentAngle, Angle targetAngle, AimPhase phase) {
        float deltaYaw = Math.abs(normalizeAngle(targetAngle.getYaw() - currentAngle.getYaw()));
        float deltaPitch = Math.abs(normalizeAngle(targetAngle.getPitch() - currentAngle.getPitch()));
        float deltaMagnitude = (float) Math.hypot(deltaYaw, deltaPitch);
        float speedFactor = mc.player == null ? 0.0f : (float) Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        float phaseBias = switch (phase) {
            case ACQUIRE -> 0.07f;
            case SWING -> 0.09f;
            case RECOVER -> -0.06f;
            case TRACK -> -0.02f;
            case IDLE -> -0.08f;
        };
        float modelWeight = MathHelper.clamp(0.05f + deltaMagnitude / 280.0f + speedFactor * 0.22f + phaseBias, MODEL_BLEND_MIN, MODEL_BLEND_MAX);
        float profileWeight = 1.0f - modelWeight;

        float yaw = profile.getYaw() * profileWeight + model.getYaw() * modelWeight;
        float pitch = profile.getPitch() * profileWeight + model.getPitch() * modelWeight;
        float yawSpeed = profile.getYawSpeed() * profileWeight + model.getYawSpeed() * modelWeight;
        float pitchSpeed = profile.getPitchSpeed() * profileWeight + model.getPitchSpeed() * modelWeight;
        float screenSpeed = profile.getScreenSpeed() * profileWeight + model.getScreenSpeed() * modelWeight;
        float gcdStepAverage = profile.getGcdStepAverage() * profileWeight + model.getGcdStepAverage() * modelWeight;
        return new RotationSample(
                yaw,
                pitch,
                profile.getDtMs(),
                profile.getHorizontalSpeed(),
                profile.isOnGround(),
                profile.isSprinting(),
                profile.getBodyYawDelta(),
                profile.getHeadYawDelta(),
                profile.getTargetDistance(),
                profile.getTargetSpeed(),
                profile.isCritical(),
                profile.getTargetType(),
                yawSpeed,
                pitchSpeed,
                screenSpeed,
                gcdStepAverage
        );
    }

    private NeuroProfile getProfile(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return null;
        }
        return profilesByKey.get(profileName.trim().toLowerCase(Locale.ROOT));
    }

    private void loadProfileFromPath(Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            List<RotationSample> loadedSamples = gson.fromJson(json, sampleListType);
            if (loadedSamples == null || loadedSamples.isEmpty()) {
                return;
            }

            List<RotationSample> sanitizedSamples = new ArrayList<>();
            for (RotationSample sample : loadedSamples) {
                if (sample == null) {
                    continue;
                }
                sanitizedSamples.add(new RotationSample(
                        sample.getYaw(),
                        sample.getPitch(),
                        sample.getDtMs(),
                        sample.getHorizontalSpeed(),
                        sample.isOnGround(),
                        sample.isSprinting(),
                        sample.getBodyYawDelta(),
                        sample.getHeadYawDelta(),
                        sample.getTargetDistance(),
                        sample.getTargetSpeed(),
                        sample.isCritical(),
                        sample.getTargetType(),
                        sample.getYawSpeed(),
                        sample.getPitchSpeed(),
                        sample.getScreenSpeed(),
                        sample.getGcdStepAverage()
                ));
            }

            if (sanitizedSamples.isEmpty()) {
                return;
            }

            String fileName = path.getFileName().toString();
            String profileName = fileName.substring(0, fileName.length() - PROFILE_EXTENSION.length());
            profilesByKey.put(profileName.toLowerCase(Locale.ROOT), new NeuroProfile(profileName, sanitizedSamples));
        } catch (Exception exception) {
            Console.log("Failed to parse neuro profile {}", path.getFileName(), exception);
        }
    }

    private void saveProfile(String profileName, List<RotationSample> samples) throws IOException {
        ensureDirectory();
        Path outputPath = profilesDirectory.toPath().resolve(profileName + PROFILE_EXTENSION);
        String serialized = gson.toJson(samples, sampleListType);
        Files.writeString(
                outputPath,
                serialized,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private void stopPlaybackInternal() {
        playing = false;
        playbackProfileName = null;
        playbackSamples = List.of();
        playbackIndex = 0;
        playbackSampleElapsedMs = 0L;
        playbackLastUpdateMs = 0L;
        currentPhase = AimPhase.IDLE;
        lastTargetEntityId = Integer.MIN_VALUE;
        lastTargetSwitchTimeMs = 0L;
    }

    private void startPlaybackInternal(NeuroProfile profile, boolean persistState) {
        playbackSamples = profile.samples();
        playbackProfileName = profile.name();
        playbackIndex = 0;
        playbackSampleElapsedMs = 0L;
        playbackLastUpdateMs = System.currentTimeMillis();
        playing = true;
        currentPhase = AimPhase.IDLE;
        lastTargetEntityId = Integer.MIN_VALUE;
        lastTargetSwitchTimeMs = 0L;
        resetPredictionState();
        if (persistState) {
            preferredPlaybackProfileName = profile.name();
            saveStateQuietly();
        }
    }

    private void restorePlaybackAfterStartup() {
        if (recording || playing || profilesByKey.isEmpty()) {
            return;
        }
        NeuroProfile profile = resolveAutoplayProfile();
        if (profile == null) {
            return;
        }
        startPlaybackInternal(profile, true);
    }

    private NeuroProfile resolveAutoplayProfile() {
        NeuroProfile preferred = getProfile(preferredPlaybackProfileName);
        if (preferred != null) {
            return preferred;
        }
        if (profilesByKey.isEmpty()) {
            if (preferredPlaybackProfileName != null) {
                preferredPlaybackProfileName = null;
                saveStateQuietly();
            }
            return null;
        }
        return profilesByKey.values().iterator().next();
    }

    private void loadState() {
        if (!Files.exists(statePath)) {
            return;
        }
        try {
            String json = Files.readString(statePath, StandardCharsets.UTF_8);
            NeuroState state = gson.fromJson(json, NeuroState.class);
            if (state == null) {
                return;
            }
            if (state.preferredPlaybackProfileName != null && !state.preferredPlaybackProfileName.isBlank()) {
                preferredPlaybackProfileName = state.preferredPlaybackProfileName.trim();
            }
            snapOnlyOnAttack = state.snapOnlyOnAttack;
        } catch (Exception exception) {
            Console.log("Failed to load neuro state {}", statePath.toAbsolutePath(), exception);
        }
    }

    private void saveStateQuietly() {
        try {
            ensureDirectory();
            NeuroState state = new NeuroState(preferredPlaybackProfileName, snapOnlyOnAttack);
            Files.writeString(
                    statePath,
                    gson.toJson(state),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (Exception exception) {
            Console.log("Failed to save neuro state {}", statePath.toAbsolutePath(), exception);
        }
    }

    private void ensureDirectory() {
        if (!profilesDirectory.exists() && !profilesDirectory.mkdirs()) {
            Console.log("Failed to create neuro directory {}", profilesDirectory.getAbsolutePath());
        }
    }

    private String validateProfileName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("Profile name cannot be empty.");
        }

        String normalized = profileName.trim();
        if (".".equals(normalized) || "..".equals(normalized)) {
            throw new IllegalArgumentException("Invalid profile name.");
        }

        if (INVALID_PROFILE_CHARS.matcher(normalized).find()) {
            throw new IllegalArgumentException("Profile name contains invalid characters.");
        }

        return normalized;
    }

    private float normalizeAngle(float angle) {
        if ((angle %= 360.0f) > 180.0f) {
            angle -= 360.0f;
        }
        if (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }

    private int resolveTargetType(Entity targetEntity) {
        if (targetEntity instanceof PlayerEntity) {
            return 1;
        }
        if (targetEntity instanceof VillagerEntity) {
            return 4;
        }
        if (targetEntity instanceof AnimalEntity) {
            return 3;
        }
        if (targetEntity instanceof MobEntity) {
            return 2;
        }
        return 0;
    }

    private RotationSample selectSituationSample(Angle currentAngle, Angle targetAngle, Entity targetEntity) {
        if (currentAngle == null || targetAngle == null || targetEntity == null || mc.player == null) {
            return null;
        }
        if (playbackSamples.isEmpty()) {
            return null;
        }

        float targetDistance = (float) mc.player.getEyePos().distanceTo(targetEntity.getEyePos());
        float targetSpeed = (float) Math.hypot(targetEntity.getVelocity().x, targetEntity.getVelocity().z);
        boolean critical = mc.player.fallDistance > 0.0f && !mc.player.isOnGround();
        boolean onGround = mc.player.isOnGround();
        boolean sprinting = mc.player.isSprinting();
        int targetType = resolveTargetType(targetEntity);

        float deltaYaw = Math.abs(normalizeAngle(targetAngle.getYaw() - currentAngle.getYaw()));
        float deltaPitch = Math.abs(normalizeAngle(targetAngle.getPitch() - currentAngle.getPitch()));
        float deltaMagnitude = (float) Math.hypot(deltaYaw, deltaPitch);

        int size = playbackSamples.size();
        int start = Math.max(0, size - SITUATION_SAMPLE_LIMIT);
        float totalWeight = 0.0f;
        float sumYaw = 0.0f;
        float sumPitch = 0.0f;

        for (int i = start; i < size; i++) {
            RotationSample sample = playbackSamples.get(i);
            if (sample == null) {
                continue;
            }

            float weight = 1.0f;
            if (sample.getTargetType() != 0 && targetType != 0 && sample.getTargetType() != targetType) {
                weight *= 0.5f;
            }
            if (sample.getTargetDistance() > 0.0f) {
                float diff = Math.abs(sample.getTargetDistance() - targetDistance);
                weight *= MathHelper.clamp(1.0f - diff / 6.0f, 0.0f, 1.0f);
            }
            if (sample.getTargetSpeed() > 0.0f) {
                float diff = Math.abs(sample.getTargetSpeed() - targetSpeed);
                weight *= MathHelper.clamp(1.0f - diff / 0.8f, 0.0f, 1.0f);
            }
            if (sample.isCritical() != critical) {
                weight *= 0.7f;
            }
            if (sample.isOnGround() != onGround) {
                weight *= 0.7f;
            }
            if (sample.isSprinting() != sprinting) {
                weight *= 0.8f;
            }

            float sampleMagnitude = (float) Math.hypot(Math.abs(sample.getYaw()), Math.abs(sample.getPitch()));
            if (sampleMagnitude > 0.0f) {
                float diff = Math.abs(sampleMagnitude - deltaMagnitude);
                weight *= MathHelper.clamp(1.0f - diff / 90.0f, 0.2f, 1.0f);
            }

            float ageWeight = 0.5f + 0.5f * (float) (i - start) / Math.max(1, size - start);
            weight *= ageWeight;

            if (weight <= 0.001f) {
                continue;
            }

            sumYaw += sample.getYaw() * weight;
            sumPitch += sample.getPitch() * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0f) {
            return null;
        }

        return new RotationSample(sumYaw / totalWeight, sumPitch / totalWeight);
    }

    private boolean shouldUseSituationSample() {
        return false;
    }

    private record NeuroProfile(String name, List<RotationSample> samples) {
    }

    private record NeuroState(String preferredPlaybackProfileName, boolean snapOnlyOnAttack) {
    }

    public static class RotationSample {
        private float yaw;
        private float pitch;
        private long dtMs;
        private float horizontalSpeed;
        private boolean onGround;
        private boolean sprinting;
        private float bodyYawDelta;
        private float headYawDelta;
        private float targetDistance;
        private float targetSpeed;
        private boolean critical;
        private int targetType;
        private float yawSpeed;
        private float pitchSpeed;
        private float screenSpeed;
        private float gcdStepAverage;

        public RotationSample() {
        }

        public RotationSample(float yaw, float pitch) {
            this(yaw, pitch, 50L, 0.0f, false, false, 0.0f, 0.0f, 0.0f, 0.0f, false, 0, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        public RotationSample(float yaw, float pitch, long dtMs, float horizontalSpeed, boolean onGround, boolean sprinting, float bodyYawDelta, float headYawDelta) {
            this(yaw, pitch, dtMs, horizontalSpeed, onGround, sprinting, bodyYawDelta, headYawDelta, 0.0f, 0.0f, false, 0, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        public RotationSample(float yaw, float pitch, long dtMs, float horizontalSpeed, boolean onGround, boolean sprinting, float bodyYawDelta, float headYawDelta, float targetDistance, float targetSpeed, boolean critical, int targetType) {
            this(yaw, pitch, dtMs, horizontalSpeed, onGround, sprinting, bodyYawDelta, headYawDelta, targetDistance, targetSpeed, critical, targetType, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        public RotationSample(float yaw, float pitch, long dtMs, float horizontalSpeed, boolean onGround, boolean sprinting, float bodyYawDelta, float headYawDelta, float targetDistance, float targetSpeed, boolean critical, int targetType, float yawSpeed, float pitchSpeed, float screenSpeed, float gcdStepAverage) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.dtMs = dtMs;
            this.horizontalSpeed = horizontalSpeed;
            this.onGround = onGround;
            this.sprinting = sprinting;
            this.bodyYawDelta = bodyYawDelta;
            this.headYawDelta = headYawDelta;
            this.targetDistance = targetDistance;
            this.targetSpeed = targetSpeed;
            this.critical = critical;
            this.targetType = targetType;
            this.yawSpeed = yawSpeed;
            this.pitchSpeed = pitchSpeed;
            this.screenSpeed = screenSpeed;
            this.gcdStepAverage = gcdStepAverage;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public long getDtMs() {
            return dtMs;
        }

        public float getHorizontalSpeed() {
            return horizontalSpeed;
        }

        public boolean isOnGround() {
            return onGround;
        }

        public boolean isSprinting() {
            return sprinting;
        }

        public float getBodyYawDelta() {
            return bodyYawDelta;
        }

        public float getHeadYawDelta() {
            return headYawDelta;
        }

        public float getTargetDistance() {
            return targetDistance;
        }

        public float getTargetSpeed() {
            return targetSpeed;
        }

        public boolean isCritical() {
            return critical;
        }

        public int getTargetType() {
            return targetType;
        }

        public float getYawSpeed() {
            return yawSpeed;
        }

        public float getPitchSpeed() {
            return pitchSpeed;
        }

        public float getScreenSpeed() {
            return screenSpeed;
        }

        public float getGcdStepAverage() {
            return gcdStepAverage;
        }
    }
}
