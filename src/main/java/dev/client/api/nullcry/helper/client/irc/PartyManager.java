package dev.client.api.nullcry.helper.client.irc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side party storage with optional join-key support and dual (IRC / MC) identities.
 */
public final class PartyManager {
    private static final PartyManager INSTANCE = new PartyManager();
    private static final String KEY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int KEY_LENGTH = 6;

    /**
     * Immutable snapshot of a party member.
     */
    public static final class PartyMemberSnapshot {
        private final String ircName;
        private final String gameName;

        private PartyMemberSnapshot(String ircName, String gameName) {
            this.ircName = sanitize(ircName);
            this.gameName = sanitize(gameName);
        }

        public static PartyMemberSnapshot of(String ircName, String gameName) {
            String sanitizedIrc = sanitize(ircName);
            String sanitizedGame = sanitize(gameName);
            if (sanitizedIrc == null && sanitizedGame == null) {
                return null;
            }
            return new PartyMemberSnapshot(sanitizedIrc, sanitizedGame);
        }

        public String getIrcName() {
            return ircName;
        }

        public String getGameName() {
            return gameName;
        }

        public String getDisplayName() {
            return gameName != null ? gameName : ircName != null ? ircName : "unknown";
        }
    }

    public static final class PositionSnapshot {
        private final double x;
        private final double y;
        private final double z;
        private final long timestamp;

        private PositionSnapshot(double x, double y, double z, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isRecent(long maxAgeMs) {
            return System.currentTimeMillis() - timestamp <= maxAgeMs;
        }
    }

    public static final class PartyMarkerSnapshot {
        private final String ownerIrcName;
        private final String ownerGameName;
        private final double x;
        private final double y;
        private final double z;
        private final long createdAt;

        private PartyMarkerSnapshot(String ownerIrcName, String ownerGameName, double x, double y, double z, long createdAt) {
            this.ownerIrcName = sanitize(ownerIrcName);
            this.ownerGameName = sanitize(ownerGameName);
            this.x = x;
            this.y = y;
            this.z = z;
            this.createdAt = createdAt;
        }

        public String getOwnerIrcName() {
            return ownerIrcName;
        }

        public String getOwnerGameName() {
            return ownerGameName;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public String getDisplayName() {
            return ownerGameName != null ? ownerGameName : ownerIrcName != null ? ownerIrcName : "unknown";
        }
    }

    public static final class PartyMemberStatus {
        private final PartyMemberSnapshot member;
        private final PositionSnapshot position;
        private final Double health;
        private final Double maxHealth;
        private final Double distance;
        private final long positionTimestamp;
        private final boolean leader;

        private PartyMemberStatus(PartyMemberSnapshot member,
                                  PositionSnapshot position,
                                  Double health,
                                  Double maxHealth,
                                  Double distance,
                                  long positionTimestamp,
                                  boolean leader) {
            this.member = member;
            this.position = position;
            this.health = health;
            this.maxHealth = maxHealth;
            this.distance = distance;
            this.positionTimestamp = positionTimestamp;
            this.leader = leader;
        }

        public PartyMemberSnapshot getMember() {
            return member;
        }

        public Optional<PositionSnapshot> getPosition() {
            return Optional.ofNullable(position);
        }

        public Optional<Double> getHealth() {
            return Optional.ofNullable(health);
        }

        public Optional<Double> getMaxHealth() {
            return Optional.ofNullable(maxHealth);
        }

        public Optional<Double> getDistance() {
            return Optional.ofNullable(distance);
        }

        public long getPositionTimestamp() {
            return positionTimestamp;
        }

        public boolean isLeader() {
            return leader;
        }
    }

    private final List<PartyMemberSnapshot> members = new ArrayList<>();
    private final Map<String, PositionSnapshot> positionsByGame = new HashMap<>();
    private final Map<String, PositionSnapshot> positionsByIrc = new HashMap<>();
    private final Map<String, PartyMarkerSnapshot> markersByOwner = new HashMap<>();
    private String partyKey;
    private PartyMemberSnapshot leaderInfo;
    private boolean leader;
    private String pendingKey;

    private PartyManager() {
    }

    public static PartyManager getInstance() {
        return INSTANCE;
    }

    private static String sanitize(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalize(String name) {
        String sanitized = sanitize(name);
        return sanitized == null ? null : sanitized.toLowerCase(Locale.ROOT);
    }

    private static boolean equalsNormalized(String a, String b) {
        return Objects.equals(normalize(a), normalize(b));
    }

    private static String normalizeKey(String key) {
        return key == null ? null : key.trim().toUpperCase(Locale.ROOT);
    }

    private String markerKey(String ircName, String gameName) {
        String normalizedGame = normalize(gameName);
        if (normalizedGame != null) {
            return "game:" + normalizedGame;
        }
        String normalizedIrc = normalize(ircName);
        if (normalizedIrc != null) {
            return "irc:" + normalizedIrc;
        }
        return null;
    }

    private String generateKey() {
        StringBuilder builder = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            int index = ThreadLocalRandom.current().nextInt(KEY_CHARS.length());
            builder.append(KEY_CHARS.charAt(index));
        }
        return builder.toString();
    }

    public synchronized boolean hasParty() {
        return partyKey != null;
    }

    public synchronized String getPartyKey() {
        return partyKey;
    }

    public synchronized boolean isLeader() {
        return leader;
    }

    public synchronized boolean isLeader(String name) {
        if (!hasParty() || leaderInfo == null) {
            return false;
        }
        return equalsNormalized(leaderInfo.getIrcName(), name) || equalsNormalized(leaderInfo.getGameName(), name);
    }

    public synchronized Optional<String> getLeader() {
        if (!hasParty() || leaderInfo == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(leaderInfo.getDisplayName());
    }

    public synchronized String createParty(String leaderIrcName, String leaderGameName) {
        PartyMemberSnapshot leaderSnapshot = PartyMemberSnapshot.of(leaderIrcName, leaderGameName);
        if (leaderSnapshot == null) {
            return null;
        }
        reset();
        this.leader = true;
        this.leaderInfo = leaderSnapshot;
        this.partyKey = generateKey();
        members.add(leaderSnapshot);
        return partyKey;
    }

    public synchronized boolean disband() {
        if (!hasParty()) {
            return false;
        }
        reset();
        return true;
    }

    public synchronized void applyRemoteState(String leaderIrcName, String leaderGameName, String key,
                                              List<PartyMemberSnapshot> newMembers, boolean isLeaderSelf) {
        reset();
        String normalizedKey = normalizeKey(key);
        if (normalizedKey == null) {
            return;
        }
        this.partyKey = normalizedKey;
        this.leader = isLeaderSelf;

        if (newMembers != null) {
            for (PartyMemberSnapshot snapshot : newMembers) {
                if (snapshot != null) {
                    addInternal(snapshot);
                }
            }
        }

        PartyMemberSnapshot explicitLeader = PartyMemberSnapshot.of(leaderIrcName, leaderGameName);
        if (explicitLeader != null) {
            PartyMemberSnapshot existing = findMember(explicitLeader.getIrcName(), explicitLeader.getGameName());
            if (existing != null) {
                this.leaderInfo = existing;
            } else {
                this.leaderInfo = explicitLeader;
                addInternal(explicitLeader);
            }
        } else if (!members.isEmpty()) {
            this.leaderInfo = members.get(0);
        }
    }

    public synchronized void clear() {
        reset();
    }

    private void reset() {
        members.clear();
        positionsByGame.clear();
        positionsByIrc.clear();
        markersByOwner.clear();
        partyKey = null;
        leaderInfo = null;
        leader = false;
        pendingKey = null;
    }

    public synchronized PartyMemberSnapshot addOrUpdateMember(String ircName, String gameName) {
        if (!hasParty()) {
            return null;
        }
        PartyMemberSnapshot snapshot = PartyMemberSnapshot.of(ircName, gameName);
        if (snapshot == null) {
            return null;
        }
        PartyMemberSnapshot existing = findMember(snapshot.getIrcName(), snapshot.getGameName());
        boolean wasLeader = existing != null && existing == leaderInfo;
        if (existing != null) {
            members.remove(existing);
        }
        members.add(snapshot);
        if (leaderInfo != null) {
            if (wasLeader || matches(leaderInfo, snapshot)) {
                leaderInfo = snapshot;
            }
        }
        return snapshot;
    }

    public synchronized void updateMarker(String ircName, String gameName, double x, double y, double z, long timestamp) {
        if (!hasParty()) {
            return;
        }
        String key = markerKey(ircName, gameName);
        if (key == null) {
            return;
        }
        PartyMarkerSnapshot snapshot = new PartyMarkerSnapshot(ircName, gameName, x, y, z, timestamp);
        markersByOwner.put(key, snapshot);
    }

    public synchronized List<PartyMarkerSnapshot> getMarkersSnapshot(long maxAgeMs) {
        if (markersByOwner.isEmpty()) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        long cutoff = maxAgeMs > 0 ? now - maxAgeMs : Long.MIN_VALUE;
        List<PartyMarkerSnapshot> result = new ArrayList<>(markersByOwner.size());
        markersByOwner.entrySet().removeIf(entry -> {
            PartyMarkerSnapshot marker = entry.getValue();
            if (marker == null) {
                return true;
            }
            if (marker.getCreatedAt() < cutoff) {
                return true;
            }
            result.add(marker);
            return false;
        });
        return result;
    }

    public synchronized boolean removeMember(String ircName, String gameName) {
        if (!hasParty()) {
            return false;
        }
        PartyMemberSnapshot existing = findMember(ircName, gameName);
        if (existing == null) {
            return false;
        }
        members.remove(existing);
        removeMarkers(existing);
        if (existing == leaderInfo) {
            reset();
        } else {
            removePositions(existing);
            pruneMarkers();
        }
        return true;
    }

    public synchronized boolean removeMember(String identifier) {
        return removeMember(identifier, identifier);
    }

    public synchronized void replaceMembers(List<PartyMemberSnapshot> snapshots) {
        if (!hasParty()) {
            return;
        }
        members.clear();
        if (snapshots != null) {
            for (PartyMemberSnapshot snapshot : snapshots) {
                if (snapshot != null) {
                    addInternal(snapshot);
                }
            }
        }
        prunePositions();
        if (leaderInfo != null) {
            PartyMemberSnapshot refreshed = findMember(leaderInfo.getIrcName(), leaderInfo.getGameName());
            if (refreshed != null) {
                leaderInfo = refreshed;
            }
        }
    }

    public synchronized Optional<PositionSnapshot> getPosition(PartyMemberSnapshot snapshot) {
        if (snapshot == null) {
            return Optional.empty();
        }
        PositionSnapshot result = null;
        if (snapshot.getGameName() != null) {
            result = positionsByGame.get(normalize(snapshot.getGameName()));
        }
        if (result == null && snapshot.getIrcName() != null) {
            result = positionsByIrc.get(normalize(snapshot.getIrcName()));
        }
        return Optional.ofNullable(result);
    }

    public synchronized Optional<PositionSnapshot> getPosition(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        String normalized = normalize(identifier);
        if (normalized == null) {
            return Optional.empty();
        }
        PositionSnapshot result = positionsByGame.get(normalized);
        if (result == null) {
            result = positionsByIrc.get(normalized);
        }
        return Optional.ofNullable(result);
    }

    public synchronized void updatePosition(String ircName, String gameName, double x, double y, double z, long timestamp) {
        if (!hasParty()) {
            return;
        }
        PositionSnapshot snapshot = new PositionSnapshot(x, y, z, timestamp);
        String normalizedGame = normalize(gameName);
        if (normalizedGame != null) {
            positionsByGame.put(normalizedGame, snapshot);
        }
        String normalizedIrc = normalize(ircName);
        if (normalizedIrc != null) {
            positionsByIrc.put(normalizedIrc, snapshot);
        }
    }

    public synchronized boolean isMember(String name) {
        if (!hasParty() || name == null) {
            return false;
        }
        return findMember(name, name) != null;
    }

    public synchronized boolean isMemberGame(String gameName) {
        if (!hasParty() || gameName == null) {
            return false;
        }
        return findMember(null, gameName) != null;
    }

    public synchronized Optional<PartyMemberSnapshot> getMemberByGameName(String gameName) {
        if (!hasParty() || gameName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(findMember(null, gameName));
    }

    public synchronized Optional<PositionSnapshot> getLatestPosition(PartyMemberSnapshot snapshot) {
        if (!hasParty() || snapshot == null) {
            return Optional.empty();
        }
        PositionSnapshot result = null;
        String gameName = snapshot.getGameName();
        if (gameName != null) {
            result = positionsByGame.get(normalize(gameName));
        }
        if (result == null) {
            String ircName = snapshot.getIrcName();
            if (ircName != null) {
                result = positionsByIrc.get(normalize(ircName));
            }
        }
        return Optional.ofNullable(result);
    }

    public List<PartyMemberStatus> collectStatuses(MinecraftClient client) {
        MinecraftClient minecraft = client != null ? client : MinecraftClient.getInstance();
        Vec3d selfPos = null;
        List<AbstractClientPlayerEntity> worldPlayers = Collections.emptyList();
        if (minecraft != null) {
            if (minecraft.player != null) {
                selfPos = dev.client.api.compat.EntityCompat.pos(minecraft.player);
            }
            if (minecraft.world != null) {
                worldPlayers = new ArrayList<>(minecraft.world.getPlayers());
            }
        }

        synchronized (this) {
            if (!hasParty()) {
                return Collections.emptyList();
            }
            List<PartyMemberStatus> result = new ArrayList<>(members.size());
            for (PartyMemberSnapshot member : members) {
                PositionSnapshot position = findPositionLocked(member);
                AbstractClientPlayerEntity worldMatch = findWorldPlayer(worldPlayers, member);
                Double health = worldMatch != null ? (double) worldMatch.getHealth() : null;
                Double maxHealth = worldMatch != null ? (double) worldMatch.getMaxHealth() : null;
                Vec3d targetPos = resolveTargetPosition(position, worldMatch);
                Double distance = computeDistance(selfPos, targetPos);
                long timestamp = position != null ? position.getTimestamp() : -1L;
                boolean leaderFlag = matches(leaderInfo, member);
                result.add(new PartyMemberStatus(member, position, health, maxHealth, distance, timestamp, leaderFlag));
            }
            return result;
        }
    }

    public synchronized List<PartyMemberSnapshot> getMembersSnapshot() {
        if (!hasParty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(members);
    }

    public synchronized Optional<PartyMemberSnapshot> getLeaderSnapshot() {
        return Optional.ofNullable(leaderInfo);
    }

    public synchronized boolean isLeader(PartyMemberSnapshot snapshot) {
        return matches(leaderInfo, snapshot);
    }

    public synchronized int getMemberCount() {
        return members.size();
    }

    public synchronized boolean matchesKey(String key) {
        if (!hasParty() || key == null) {
            return false;
        }
        return partyKey.equalsIgnoreCase(key);
    }

    public synchronized void setPendingKey(String key) {
        this.pendingKey = normalizeKey(key);
    }

    public synchronized boolean hasPendingKey() {
        return pendingKey != null;
    }

    public synchronized boolean isPendingKey(String key) {
        if (pendingKey == null || key == null) {
            return false;
        }
        return pendingKey.equalsIgnoreCase(key);
    }

    public synchronized void clearPendingKey() {
        this.pendingKey = null;
    }

    private void addInternal(PartyMemberSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        PartyMemberSnapshot existing = findMember(snapshot.getIrcName(), snapshot.getGameName());
        if (existing != null) {
            members.remove(existing);
            if (existing == leaderInfo) {
                leaderInfo = snapshot;
            }
        }
        members.add(snapshot);
        prunePositions();
    }

    private PartyMemberSnapshot findMember(String ircName, String gameName) {
        String normalizedIrc = normalize(ircName);
        String normalizedGame = normalize(gameName);
        for (PartyMemberSnapshot member : members) {
            if (member == null) {
                continue;
            }
            if (normalizedIrc != null && equalsNormalized(member.getIrcName(), normalizedIrc)) {
                return member;
            }
            if (normalizedGame != null && equalsNormalized(member.getGameName(), normalizedGame)) {
                return member;
            }
        }
        return null;
    }

    public synchronized Optional<PartyMemberSnapshot> findMemberByGame(String gameName) {
        return Optional.ofNullable(findMember(null, gameName));
    }

    public synchronized Optional<PartyMemberSnapshot> findMemberByIrc(String ircName) {
        return Optional.ofNullable(findMember(ircName, null));
    }

    private boolean matches(PartyMemberSnapshot left, PartyMemberSnapshot right) {
        if (left == null || right == null) {
            return false;
        }
        return (left.getIrcName() != null && equalsNormalized(left.getIrcName(), right.getIrcName()))
                || (left.getGameName() != null && equalsNormalized(left.getGameName(), right.getGameName()));
    }

    private PositionSnapshot findPositionLocked(PartyMemberSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot.getGameName() != null) {
            PositionSnapshot byGame = positionsByGame.get(normalize(snapshot.getGameName()));
            if (byGame != null) {
                return byGame;
            }
        }
        if (snapshot.getIrcName() != null) {
            return positionsByIrc.get(normalize(snapshot.getIrcName()));
        }
        return null;
    }

    private static AbstractClientPlayerEntity findWorldPlayer(List<AbstractClientPlayerEntity> worldPlayers, PartyMemberSnapshot snapshot) {
        if (worldPlayers == null || worldPlayers.isEmpty() || snapshot == null) {
            return null;
        }
        String gameName = snapshot.getGameName();
        if (gameName == null) {
            return null;
        }
        for (AbstractClientPlayerEntity player : worldPlayers) {
            if (player == null || player.getGameProfile() == null) {
                continue;
            }
            String name = player.getGameProfile().name();
            if (name != null && name.equalsIgnoreCase(gameName)) {
                return player;
            }
        }
        return null;
    }

    private static Vec3d resolveTargetPosition(PositionSnapshot snapshot, AbstractClientPlayerEntity entity) {
        if (entity != null) {
            return dev.client.api.compat.EntityCompat.pos(entity);
        }
        if (snapshot == null) {
            return null;
        }
        return new Vec3d(snapshot.getX(), snapshot.getY(), snapshot.getZ());
    }

    private static Double computeDistance(Vec3d selfPos, Vec3d target) {
        if (selfPos == null || target == null) {
            return null;
        }
        return selfPos.distanceTo(target);
    }

    private void prunePositions() {
        if (positionsByGame.isEmpty() && positionsByIrc.isEmpty()) {
            return;
        }
        Set<String> validGameKeys = new HashSet<>();
        Set<String> validIrcKeys = new HashSet<>();
        for (PartyMemberSnapshot member : members) {
            if (member == null) continue;
            String normalizedGame = normalize(member.getGameName());
            if (normalizedGame != null) {
                validGameKeys.add(normalizedGame);
            }
            String normalizedIrc = normalize(member.getIrcName());
            if (normalizedIrc != null) {
                validIrcKeys.add(normalizedIrc);
            }
        }
        positionsByGame.keySet().removeIf(key -> !validGameKeys.contains(key));
        positionsByIrc.keySet().removeIf(key -> !validIrcKeys.contains(key));
        pruneMarkers();
    }

    private void removePositions(PartyMemberSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        String normalizedGame = normalize(snapshot.getGameName());
        if (normalizedGame != null) {
            positionsByGame.remove(normalizedGame);
        }
        String normalizedIrc = normalize(snapshot.getIrcName());
        if (normalizedIrc != null) {
            positionsByIrc.remove(normalizedIrc);
        }
    }

    private void removeMarkers(PartyMemberSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        String gameKey = markerKey(null, snapshot.getGameName());
        if (gameKey != null) {
            markersByOwner.remove(gameKey);
        }
        String ircKey = markerKey(snapshot.getIrcName(), null);
        if (ircKey != null) {
            markersByOwner.remove(ircKey);
        }
    }

    private void pruneMarkers() {
        if (markersByOwner.isEmpty()) {
            return;
        }
        Set<String> validKeys = new HashSet<>();
        for (PartyMemberSnapshot member : members) {
            if (member == null) {
                continue;
            }
            String gameKey = markerKey(null, member.getGameName());
            if (gameKey != null) {
                validKeys.add(gameKey);
            }
            String ircKey = markerKey(member.getIrcName(), null);
            if (ircKey != null) {
                validKeys.add(ircKey);
            }
        }
        markersByOwner.keySet().removeIf(key -> !validKeys.contains(key));
    }
}
