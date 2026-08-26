package dev.client.modules.core.render.littlepet;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class GhostWolfEntity extends WolfEntity {
    public GhostWolfEntity(EntityType<? extends WolfEntity> entityType, World world) {
        super(entityType, world);
        keepVisualOnly();
    }

    public void keepVisualOnly() {
        this.noClip = true;
        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);
    }

    @Override
    protected EntityDimensions getBaseDimensions(EntityPose pose) {
        return EntityDimensions.fixed(0.0F, 0.0F);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    public boolean isCollidable(Entity other) {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean collidesWithStateAtPos(BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public boolean collidesWithFluid(FluidState state, BlockPos pos, Vec3d currentPos, Vec3d nextPos) {
        return false;
    }

    @Override
    public void pushAwayFrom(Entity entity) {
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public void tickMovement() {
        keepVisualOnly();
    }
}
