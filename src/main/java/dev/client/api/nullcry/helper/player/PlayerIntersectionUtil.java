package dev.client.api.nullcry.helper.player;

import dev.client.api.nullcry.ClientApi;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@UtilityClass
public class PlayerIntersectionUtil implements ClientApi {
    public List<BlockPos> getCube(BlockPos center, float radius) {
        return getCube(center, radius, radius, true);
    }

    public List<BlockPos> getCube(BlockPos center, float radiusXZ, float radiusY) {
        return getCube(center, radiusXZ, radiusY, true);
    }

    public List<BlockPos> getCube(BlockPos center, float radiusXZ, float radiusY, boolean down) {
        List<BlockPos> positions = new ArrayList<>();
        if (center == null) return positions;

        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();
        int startY = down ? centerY - (int) radiusY : centerY;

        for (int x = centerX - (int) radiusXZ; x <= centerX + radiusXZ; x++) {
            for (int z = centerZ - (int) radiusXZ; z <= centerZ + radiusXZ; z++) {
                for (int y = startY; y <= centerY + radiusY; y++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }

        return positions;
    }

    public List<BlockPos> getCube(BlockPos start, BlockPos end) {
        List<BlockPos> positions = new ArrayList<>();
        if (start == null || end == null) return positions;

        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int z = start.getZ(); z <= end.getZ(); z++) {
                for (int y = start.getY(); y <= end.getY(); y++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }

        return positions;
    }

    public Stream<Entity> streamEntities() {
        if (mc.world == null) return Stream.empty();
        return StreamSupport.stream(mc.world.getEntities().spliterator(), false);
    }

    public boolean canChangeIntoPose(EntityPose pose, Vec3d pos) {
        return mc.player != null && mc.world.isSpaceEmpty(mc.player, mc.player.getDimensions(pose).getBoxAt(pos).contract(1.0E-7));
    }

    public boolean isPlayerInBlock(Block block) {
        return mc.player != null && isBoxInBlock(mc.player.getBoundingBox().expand(-1e-3), block);
    }

    public boolean isBoxInBlock(Box box, Block block) {
        return isBox(box, pos -> mc.world != null && mc.world.getBlockState(pos).getBlock().equals(block));
    }

    public boolean isBoxInBlocks(Box box, List<Block> blocks) {
        return isBox(box, pos -> mc.world != null && blocks.contains(mc.world.getBlockState(pos).getBlock()));
    }

    public boolean isBox(Box box, Predicate<BlockPos> predicate) {
        if (box == null) return false;
        return BlockPos.stream(box).anyMatch(predicate);
    }

    public boolean isAir(BlockPos pos) {
        if (mc.world == null || pos == null) return true;
        return isAir(mc.world.getBlockState(pos));
    }

    public boolean isAir(BlockState state) {
        if (state == null) return true;
        return state.isAir() || state.isOf(Blocks.CAVE_AIR) || state.isOf(Blocks.VOID_AIR);
    }

    public boolean nullCheck() {
        return mc.player == null || mc.world == null;
    }
}
