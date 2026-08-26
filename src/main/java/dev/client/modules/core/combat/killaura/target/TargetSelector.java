package dev.client.modules.core.combat.killaura.target;

import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import dev.client.modules.core.combat.killaura.rotation.PointFinder;
import dev.client.modules.core.combat.killaura.rotation.RaytracingUtil;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.client.modules.core.combat.killaura.rotation.angle.LinearSmoothMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TargetSelector implements ClientApi {
    final PointFinder pointFinder = new PointFinder();
    LivingEntity currentTarget;
    List<LivingEntity> potentialTargets = new ArrayList<>();

    public TargetSelector() {
        this.currentTarget = null;
    }

    public void releaseTarget() {
        this.currentTarget = null;
    }

    public void searchTargets(Iterable<Entity> entities, float maxDistance, float maxFov, boolean ignoreWalls, Consumer<List<LivingEntity>> sorter) {
        if (currentTarget != null && (!pointFinder.hasValidPoint(currentTarget, maxDistance, ignoreWalls) || getFov(currentTarget, maxDistance, ignoreWalls) > maxFov)) {
            releaseTarget();
        }

        this.potentialTargets = createStreamFromEntities(entities, maxDistance, maxFov, ignoreWalls);
        if (sorter != null) {
            sorter.accept(this.potentialTargets);
        }
    }

    private double getFov(LivingEntity entity, float maxDistance, boolean ignoreWalls) {
        Vec3d attackVector = pointFinder.computeVector(entity, maxDistance, RotationController.INSTANCE.getRotation(), new LinearSmoothMode().randomValue(), ignoreWalls).getLeft();
        return RaytracingUtil.rayTrace(maxDistance, entity.getBoundingBox()) ? 0 : RotationController.computeRotationDifference(AngleUtil.cameraAngle(), AngleUtil.calculateAngle(attackVector));
    }

    private List<LivingEntity> createStreamFromEntities(Iterable<Entity> entities, float maxDistance, float maxFov, boolean ignoreWalls) {
        return StreamSupport.stream(entities.spliterator(), false)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(entity -> pointFinder.hasValidPoint(entity, maxDistance, ignoreWalls) && getFov(entity, maxDistance, ignoreWalls) < maxFov)
                .sorted(Comparator.comparingDouble(entity -> entity.distanceTo(mc.player)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Optional<LivingEntity> findFirstMatch(Predicate<LivingEntity> predicate) {
        return this.potentialTargets.stream().filter(predicate).findFirst();
    }

    public void validateTarget(Predicate<LivingEntity> predicate, boolean singleMode) {
        if (!singleMode) {
            this.currentTarget = findFirstMatch(predicate).orElse(null);
            return;
        }

        if (this.currentTarget == null) {
            this.currentTarget = findFirstMatch(predicate).orElse(null);
            return;
        }

        if (!predicate.test(this.currentTarget)) {
            this.currentTarget = findFirstMatch(predicate).orElse(null);
        }
    }
}