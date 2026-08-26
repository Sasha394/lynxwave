package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.cmd.core.cmd_modules.CmdFakePlayer;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class AntiBot extends Module {
    public static AntiBot INSTANCE;

    public AntiBot() {
        super("AntiBot", ModuleCategory.Combat, "Удаляет ботов, созданных анти-читом, из мира");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("Matrix", "RW").defaultValue("Matrix").register(this);

    public static final List<Entity> isBot = new ArrayList<>();

    private final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private final Map<UUID, Vec3d> lastVelocities = new HashMap<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Integer> previousPings = new HashMap<>();

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            isBot.clear();
            return;
        }

        isBot.removeIf(entity -> entity == null || entity.isRemoved() || entity.getEntityWorld() != mc.world);

        if (this.mode.isSelected("Matrix")) {
            this.handleMatrix();
        } else if (this.mode.isSelected("RW")) {
            this.handleRW();
            clearTrackingData();
        }
    }

    private void handleRW() {
        for (PlayerEntity entity : mc.world.getPlayers()) {
            if (entity == mc.player) continue;
            if (entity.getUuid().equals(CmdFakePlayer.FAKE_PLAYER_UUID)) continue;

            boolean uuidMismatch = !entity.getUuid().equals(Uuids.getOfflinePlayerUuid(entity.getName().getString()));

            ItemStack boots = entity.getEquippedStack(EquipmentSlot.FEET);
            ItemStack leggings = entity.getEquippedStack(EquipmentSlot.LEGS);
            ItemStack chestplate = entity.getEquippedStack(EquipmentSlot.CHEST);
            ItemStack helmet = entity.getEquippedStack(EquipmentSlot.HEAD);

            boolean armorComplete = !boots.isEmpty()
                    && !leggings.isEmpty()
                    && !chestplate.isEmpty()
                    && !helmet.isEmpty();

            boolean enchantableArmor = boots.isEnchantable()
                    && leggings.isEnchantable()
                    && chestplate.isEnchantable()
                    && helmet.isEnchantable();

            boolean leatherOrIronSet = boots.getItem() == Items.LEATHER_BOOTS
                    || leggings.getItem() == Items.LEATHER_LEGGINGS
                    || chestplate.getItem() == Items.LEATHER_CHESTPLATE
                    || helmet.getItem() == Items.LEATHER_HELMET
                    || boots.getItem() == Items.IRON_BOOTS
                    || leggings.getItem() == Items.IRON_LEGGINGS
                    || chestplate.getItem() == Items.IRON_CHESTPLATE
                    || helmet.getItem() == Items.IRON_HELMET;

            boolean undamagedArmor = !boots.isDamaged()
                    && !leggings.isDamaged()
                    && !chestplate.isDamaged()
                    && !helmet.isDamaged();

            boolean matchingMatrixPattern = mc.player != entity
                    && armorComplete
                    && enchantableArmor
                    && entity.getOffHandStack().getItem() == Items.AIR
                    && leatherOrIronSet
                    && entity.getMainHandStack().getItem() != Items.AIR
                    && undamagedArmor
                    && entity.getHungerManager().getFoodLevel() == 20;

            boolean shouldFlag = uuidMismatch || matchingMatrixPattern;

            if (shouldFlag) {
                if (!isBot.contains(entity)) {
                    isBot.add(entity);
                }
            } else {
                isBot.remove(entity);
            }
        }
    }

    private void handleMatrix() {
        Set<UUID> seenPlayers = new HashSet<>();

        for (PlayerEntity entity : mc.world.getPlayers()) {
            if (entity == mc.player) continue;
            if (entity.getUuid().equals(CmdFakePlayer.FAKE_PLAYER_UUID)) continue;

            UUID uuid = entity.getUuid();
            seenPlayers.add(uuid);

            Vec3d currentPos = dev.client.api.compat.EntityCompat.pos(entity);
            Vec3d lastPos = lastPositions.get(uuid);
            Vec3d velocity = entity.getVelocity();
            Vec3d lastVelocity = lastVelocities.get(uuid);

            double horizontalDistance = 0.0D;
            if (lastPos != null) {
                double dx = currentPos.x - lastPos.x;
                double dz = currentPos.z - lastPos.z;
                horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            }

            double horizontalVelocity = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

            int airTick = airTicks.getOrDefault(uuid, 0);
            if (entity.isOnGround() || entity.isTouchingWater()) {
                airTick = 0;
            } else {
                airTick++;
            }
            airTicks.put(uuid, airTick);

            boolean speedSpike = horizontalDistance > 1.1D || horizontalVelocity > 1.1D;
            boolean hovering = airTick > 8 && Math.abs(velocity.y) < 0.05D && horizontalVelocity > 0.35D;
            boolean verticalBoost = velocity.y > 0.6D && !entity.isOnGround();

            boolean directionCorrection = false;
            if (lastVelocity != null) {
                Vec3d normalizedLast = lastVelocity.normalize();
                Vec3d normalizedCurrent = velocity.normalize();
                if (normalizedLast.lengthSquared() > 0.0D && normalizedCurrent.lengthSquared() > 0.0D) {
                    double dot = normalizedLast.dotProduct(normalizedCurrent);
                    directionCorrection = dot < -0.5D && velocity.lengthSquared() > 0.25D;
                }

                if (!directionCorrection) {
                    directionCorrection = velocity.subtract(lastVelocity).lengthSquared() > 1.2D;
                }
            }

            boolean unnaturalMovement = speedSpike || hovering || verticalBoost || directionCorrection;

            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(uuid);
            boolean notInTab = info == null;

            boolean weirdPing = false;
            if (info != null) {
                int ping = info.getLatency();
                Integer previousPing = previousPings.put(uuid, ping);
                if (ping <= 0 || ping >= 1000) {
                    weirdPing = true;
                } else if (previousPing != null && Math.abs(ping - previousPing) > 200) {
                    weirdPing = true;
                }
            } else {
                previousPings.remove(uuid);
            }

            boolean shouldFlag = unnaturalMovement && (notInTab || weirdPing || directionCorrection || hovering);

            if (shouldFlag) {
                if (!isBot.contains(entity)) {
                    isBot.add(entity);
                }
            } else {
                isBot.remove(entity);
            }

            lastPositions.put(uuid, currentPos);
            lastVelocities.put(uuid, velocity);
        }

        cleanup(seenPlayers);
    }

    private void cleanup(Set<UUID> seenPlayers) {
        lastPositions.keySet().removeIf(uuid -> !seenPlayers.contains(uuid));
        lastVelocities.keySet().removeIf(uuid -> !seenPlayers.contains(uuid));
        airTicks.keySet().removeIf(uuid -> !seenPlayers.contains(uuid));
        previousPings.keySet().removeIf(uuid -> !seenPlayers.contains(uuid));
        isBot.removeIf(entity -> {
            if (!(entity instanceof PlayerEntity)) {
                return true;
            }

            PlayerEntity player = (PlayerEntity) entity;
            return !seenPlayers.contains(player.getUuid());
        });
    }

    private void clearTrackingData() {
        lastPositions.clear();
        lastVelocities.clear();
        airTicks.clear();
        previousPings.clear();
    }

    public static boolean isBot(Entity entity) {
        return entity instanceof PlayerEntity && isBot.contains(entity);
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        isBot.clear();
        clearTrackingData();
    }
}
