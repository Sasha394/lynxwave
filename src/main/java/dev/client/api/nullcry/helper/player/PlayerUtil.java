package dev.client.api.nullcry.helper.player;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.math.MathUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

@UtilityClass
public class PlayerUtil implements ClientApi {

    public Map<ItemStack, Double> getCooldownItems() {
        if (mc.player == null) return Collections.emptyMap();

        Map<Identifier, ItemStack> uniqueItems = new LinkedHashMap<>();
        Map<Identifier, Double> remainingTimes = new LinkedHashMap<>();
        ItemCooldownManager cooldownTracker = mc.player.getItemCooldownManager();

        float tickDelta = mc.getRenderTickCounter() != null
                ? mc.getRenderTickCounter().getTickProgress(false)
                : 0f;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Identifier group = cooldownTracker.getGroup(stack);
            ItemCooldownManager.Entry entry = cooldownTracker.entries.get(group);
            if (entry == null) continue;

            int totalTicks = entry.endTick() - entry.startTick();
            if (totalTicks <= 0) continue;

            float progress = cooldownTracker.getCooldownProgress(stack, tickDelta);
            if (progress <= 0f) continue;

            double remainingSeconds = (totalTicks * progress) / 20.0;

            if (!remainingTimes.containsKey(group) || remainingTimes.get(group) < remainingSeconds) {
                remainingTimes.put(group, remainingSeconds);
                uniqueItems.put(group, stack.copy());
            }
        }

        Map<ItemStack, Double> cooldownItems = new LinkedHashMap<>();
        for (Map.Entry<Identifier, ItemStack> e : uniqueItems.entrySet()) {
            cooldownItems.put(e.getValue(), remainingTimes.get(e.getKey()));
        }
        return cooldownItems;
    }

    public double getItemCooldown(Item item) {
        if (mc.player == null) return 0;

        ItemCooldownManager cooldownTracker = mc.player.getItemCooldownManager();

        ItemStack stack = findStackForItem(item);
        if (stack.isEmpty()) stack = new ItemStack(item);

        Identifier group = cooldownTracker.getGroup(stack);
        if (group == null) return 0;

        ItemCooldownManager.Entry cooldown = cooldownTracker.entries.get(group);
        if (cooldown == null) return 0;

        int remainingTicks = cooldown.endTick() - cooldownTracker.tick;
        if (remainingTicks <= 0) return 0;

        return remainingTicks / 20.0;
    }

    private ItemStack findStackForItem(Item item) {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    public boolean isInWeb() {
        Box pBox = mc.player.getBoundingBox();
        BlockPos pBlockPos = BlockPos.ofFloored(dev.client.api.compat.EntityCompat.pos(mc.player));

        for (int x = pBlockPos.getX() - 2; x <= pBlockPos.getX() + 2; x++) {
            for (int y = pBlockPos.getY() - 1; y <= pBlockPos.getY() + 4; y++) {
                for (int z = pBlockPos.getZ() - 2; z <= pBlockPos.getZ() + 2; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (pBox.intersects(new Box(bp)) && mc.world.getBlockState(bp).getBlock() == Blocks.COBWEB)
                        return true;
                }
            }
        }

        return false;
    }

    public List<BlockPos> getSphere(final BlockPos center, final float radius, final float height, final boolean hollow, final boolean fromBottom, final int yOffset, boolean cube) {
        List<BlockPos> positions = new ArrayList<>();
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();

        for (int x = centerX - (int) radius; x <= centerX + radius; x++) {
            for (int z = centerZ - (int) radius; z <= centerZ + radius; z++) {
                int yStart = fromBottom ? (centerY - (int) radius) : centerY;
                int yEnd = fromBottom ? (centerY + (int) radius) : (centerY + (int) height);

                for (int y = yStart; y < yEnd; y++) {
                    if (isPositionWithinSphere(centerX, centerY, centerZ, x, y, z, radius, hollow) || cube) {
                        positions.add(new BlockPos(x, y + yOffset, z));
                    }
                }
            }
        }

        return positions;
    }

    public List<BlockPos> getCube(final BlockPos center, final float radiusXZ, final float radiusY) {
        if (center == null) return Collections.emptyList();

        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : PlayerIntersectionUtil.getCube(center, radiusXZ, radiusY, true)) {
            if (pos.getY() < center.getY() + radiusY) {
                positions.add(pos);
            }
        }
        return positions;
    }

    public boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isIn(ItemTags.SWORDS);
    }

    public boolean isArmor(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.isIn(ItemTags.FOOT_ARMOR)
                || stack.isIn(ItemTags.LEG_ARMOR)
                || stack.isIn(ItemTags.CHEST_ARMOR)
                || stack.isIn(ItemTags.HEAD_ARMOR));
    }

    public boolean isArmorForSlot(ItemStack stack, EquipmentSlot slot) {
        if (!isArmor(stack) || slot == null || !slot.isArmorSlot()) return false;

        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null) {
            return equippable.slot() == slot;
        }

        return switch (slot) {
            case FEET -> stack.isIn(ItemTags.FOOT_ARMOR);
            case LEGS -> stack.isIn(ItemTags.LEG_ARMOR);
            case CHEST -> stack.isIn(ItemTags.CHEST_ARMOR);
            case HEAD -> stack.isIn(ItemTags.HEAD_ARMOR);
            default -> false;
        };
    }

    public EquipmentSlot getArmorEquipmentSlot(ItemStack stack) {
        if (!isArmor(stack)) return null;

        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null && equippable.slot().isArmorSlot()) {
            return equippable.slot();
        }

        if (stack.isIn(ItemTags.FOOT_ARMOR)) return EquipmentSlot.FEET;
        if (stack.isIn(ItemTags.LEG_ARMOR)) return EquipmentSlot.LEGS;
        if (stack.isIn(ItemTags.CHEST_ARMOR)) return EquipmentSlot.CHEST;
        if (stack.isIn(ItemTags.HEAD_ARMOR)) return EquipmentSlot.HEAD;
        return null;
    }

    public int getArmorInventoryIndex(ItemStack stack) {
        return getArmorInventoryIndex(getArmorEquipmentSlot(stack));
    }

    public int getArmorInventoryIndex(EquipmentSlot slot) {
        if (slot == null) return -1;
        return switch (slot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
    }

    public double getArmorProtection(ItemStack stack) {
        return getArmorAttribute(stack, EntityAttributes.ARMOR);
    }

    public double getArmorToughness(ItemStack stack) {
        return getArmorAttribute(stack, EntityAttributes.ARMOR_TOUGHNESS);
    }

    private double getArmorAttribute(ItemStack stack, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute) {
        EquipmentSlot slot = getArmorEquipmentSlot(stack);
        if (slot == null) return 0.0;

        AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        return modifiers.applyOperations(attribute, 0.0, slot);
    }

    public int findItem(final int endSlot, final Item ofType) {
        int slot = -1;

        for (int i = 0; i < endSlot; i++) {
            if (mc.player.getInventory().getStack(i).getItem() != ofType) continue;
            slot = i == 40 ? 45 : i < 9 ? 36 + i : i;
        }

        return slot;
    }

    public boolean collideWith(LivingEntity entity, float grow) {
        Box box = mc.player.getBoundingBox();
        Box targetbox = entity.getBoundingBox().expand(grow, 0, grow);

        return box.maxX > targetbox.minX
                && box.maxY > targetbox.minY
                && box.maxZ > targetbox.minZ
                && box.minX < targetbox.maxX
                && box.minY < targetbox.maxY
                && box.minZ < targetbox.maxZ;
    }

    public BlockPos getPlayerPositionLocal() {
        if (mc.player == null) return new BlockPos(0, 0, 0);
        return BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    public Block getBlock(double x, double y, double z) {
        if (mc.world == null || mc.player == null) {
            return Blocks.AIR;
        }

        BlockPos pos = BlockPos.ofFloored(mc.player.getX() + x, mc.player.getY() + y, mc.player.getZ() + z);
        return mc.world.getBlockState(pos).getBlock();
    }

    public BlockPos getBlock(BlockPos centerPos, float distance, Block block) {
        if (mc.world == null) return null;
        return getCube(centerPos, distance, distance).stream().filter(pos -> mc.world.getBlockState(pos).equals(block)).findFirst().orElse(null);
    }

    public BlockPos getBlock(float distance, Block block) {
        return getSphere(getPlayerPositionLocal(), distance, 6, false, true, 0, false).stream().filter(position -> mc.world.getBlockState(position).getBlock() == block).min(Comparator.comparing(blockPos -> getDistanceOfEntityToBlock(mc.player, blockPos))).orElse(null);
    }

    public Block getBlock() {
        return getBlock(0, 0, 0);
    }

    public Block block(final BlockPos pos) {
        if (mc.world == null) return null;
        return mc.world.getBlockState(pos).getBlock();
    }

    public boolean isPlayerInBlock(Block block) {
        return PlayerIntersectionUtil.isPlayerInBlock(block);
    }

    public boolean isBoxInBlock(Box box, Block block) {
        return PlayerIntersectionUtil.isBoxInBlock(box, block);
    }

    public boolean isBoxInBlocks(Box box, List<Block> blocks) {
        return PlayerIntersectionUtil.isBoxInBlocks(box, blocks);
    }

    public boolean canChangeIntoPose(EntityPose pose, Vec3d pos) {
        return PlayerIntersectionUtil.canChangeIntoPose(pose, pos);
    }

    public boolean isPositionWithinSphere(int centerX, int centerY, int centerZ, int x, int y, int z, float radius, boolean hollow) {
        double distanceSq = Math.pow(centerX - x, 2) + Math.pow(centerZ - z, 2) + Math.pow(centerY - y, 2);
        return distanceSq < Math.pow(radius, 2) && (!hollow || distanceSq >= Math.pow(radius - 1.0f, 2));
    }
    public double getDistanceOfEntityToBlock(final Entity entity, final BlockPos blockPos) {
        return MathUtil.getDistance(entity.getX(), entity.getY(), entity.getZ(), blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }
    public boolean nullCheck() {return mc.player == null || mc.world == null;}
}
