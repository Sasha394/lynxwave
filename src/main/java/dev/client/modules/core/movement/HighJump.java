package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

public class HighJump extends Module {
    public static HighJump INSTANCE;

    public HighJump() {
        super("HighJump", ModuleCategory.Movement, "Дает возможность высоко прыгать");
    }

    ModeElement mode = new ModeElement("Режим", () -> true)
            .set("Default", "Grim", "Элитры", "FunTime")
            .defaultValue("Default")
            .register(this);

    Slider velocityY = new Slider("Высота прыжка", () -> mode.isSelected("Default")).set(0.2f, 0.6f, 0.1f).defaultValue(0.5f).register(this);

    Slider grimHeight = new Slider("Высота подъема", () -> mode.isSelected("Grim")).set(20.0f, 1000.0f, 1.0f).defaultValue(35.0f).register(this);

    Slider height = new Slider("Высота элитры", () -> mode.isSelected("Элитры")).set(0.1f, 1f, 0.1f).defaultValue(0.6f).register(this);

    Slider funtimeSnowJumpY = new Slider("FuntimeSnow JumpY", () -> mode.isSelected("FunTime")).set(0.1f, 1.0f, 0.01f).defaultValue(0.42f).register(this);

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (mode.isSelected("Default")) {
            handleDefaultMode();
        } else if (mode.isSelected("Grim")) {
            handleGrimMode();
        } else if (mode.isSelected("Элитры")) {
            handleElytraMode();
        } else if (mode.isSelected("FunTime")) {
            handleFuntimeSnow();
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (mode.isSelected("Элитры")) {
            swapToElytra();
        }
    }

    @Subscribe
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;

        if (mode.isSelected("Элитры") && mc.player.isGliding()) {
            float targetYaw = mc.player.getYaw();
            float targetPitch = 0.0f;

            e.setYaw(targetYaw);
            e.setPitch(targetPitch);

            mc.player.setYaw(targetYaw);
            mc.player.setPitch(targetPitch);
            mc.player.headYaw = targetYaw;
            mc.player.bodyYaw = targetYaw;
        }
    }

    private void handleElytraMode() {
        if (mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        boolean hasElytra = mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;

        if (!mc.player.getAbilities().flying && mc.player.isOnGround() && !mc.player.isTouchingWater()
                && !mc.player.isInLava() && hasElytra && !mc.options.jumpKey.isPressed()) {
            mc.player.jump();
        }

        if (!mc.player.getAbilities().flying && !mc.player.isOnGround() && !mc.player.isTouchingWater()
                && !mc.player.isGliding() && hasElytra) {
            mc.player.startGliding();
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        if (hasElytra && !mc.player.isGliding()) {
            mc.options.jumpKey.setPressed(false);
            mc.player.jumping = false;
        }

        if (hasElytra) {

            if (mc.player.isGliding()) {
                mc.player.addVelocity(0, height.getValue() / 10.0, 0);
            }
        } else {
            swapToElytra();
        }
    }

    private void swapToElytra() {
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) return;

        int slot = findElytraSlot();
        if (slot == -1) return;

        // SWAP найденного слота в слот брони (нагрудник = action button 6)
        if (slot >= 0 && slot <= 8) {
            mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
        }
        sendPacket(new CloseHandledScreenC2SPacket(0));
    }

    private int findElytraSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.ELYTRA) return i;
        }
        return -1;
    }

    private void handleDefaultMode() {
        if (mc.player.isOnGround()) {
            LynxWave.jumpTicks = Math.min(LynxWave.jumpTicks + 0.0025f, 0.035f);
            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, mc.player.getBlockPos().up(), Direction.UP));
            mc.player.setVelocity(mc.player.getVelocity().x, velocityY.getValue(), mc.player.getVelocity().z);
        } else {
            resetJumpMovementFactor();
        }
    }

    private void handleFuntimeSnow() {
        if (mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, funtimeSnowJumpY.getValue(), mc.player.getVelocity().z);
        }
    }

    private void handleGrimMode() {
        resetJumpMovementFactor();

        if (mc.world == null) return;

        ClientWorld world = mc.world;
        ChunkPos playerChunkPos = new ChunkPos(mc.player.getBlockPos());

        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(playerChunkPos.x + chunkX, playerChunkPos.z + chunkZ);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : getBlockEntitiesSafe(chunk, world)) {
                    if (!(blockEntity instanceof ShulkerBoxBlockEntity shulker)) continue;

                    BlockPos tilePos = shulker.getPos();

                    double dx = mc.player.getX() - (tilePos.getX() + 0.5);
                    double dz = mc.player.getZ() - (tilePos.getZ() + 0.5);
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    double yDiff = Math.abs(mc.player.getY() - (tilePos.getY() + 0.5));

                    double maxYDiff = mc.player.getVelocity().y > 1.0 ? 30.0 : 2.0;

                    if (distance <= 1.0 && yDiff <= maxYDiff && mc.player.fallDistance == 0.0f) {
                        float progress = shulker.getAnimationProgress(1.0f);
                        if (progress > 0.0f && progress < 1.0f) {
                            double velocity = computeVelocityForHeight(grimHeight.getValue());
                            mc.player.setVelocity(mc.player.getVelocity().x, velocity, mc.player.getVelocity().z);
                            return;
                        }
                    }
                }
            }
        }
    }

    private Iterable<BlockEntity> getBlockEntitiesSafe(WorldChunk chunk, ClientWorld world) {
        try {
            return chunk.getBlockEntities().values();
        } catch (Throwable ignored) {
            java.util.List<BlockEntity> list = new java.util.ArrayList<>();
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity != null) list.add(entity);
            }
            return list;
        }
    }

    private double computeVelocityForHeight(float height) {
        float baseHeight = 35.0f;
        double velocity = height / baseHeight;
        return Math.max(0.5f, velocity);
    }

    public void resetJumpMovementFactor() {
        LynxWave.jumpTicks = 0.02F;
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        resetJumpMovementFactor();
    }
}
