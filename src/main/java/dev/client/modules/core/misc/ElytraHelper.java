package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import lombok.Getter;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class ElytraHelper extends Module {
    public static ElytraHelper INSTANCE;

    ModeElement mode = new ModeElement("Мод", () -> true).set("Vanilla", "Grim", "Polar").defaultValue("Vanilla").register(this);
    KeyBind swapKey = new KeyBind("Кнопка свапа", () -> true).set(-1).register(this);
    KeyBind fireworkKey = new KeyBind("Кнопка феерверка", () -> true).set(-1).register(this);
    ModeElement throwFireworkMode = new ModeElement("Мод пуска феера", () -> true).set("Обычный", "Легитный").defaultValue("Обычный").register(this);
    CheckBox autoTakeoff = new CheckBox("Автовзлёт", () -> true).defaultValue(true).register(this);
    @Getter
    CheckBox elytraBoost = new CheckBox("Буст элитры", () -> true).defaultValue(false).register(this);
    @Getter
    Slider elytraSpeed = new Slider("Скорость элитры", elytraBoost::getEnabled)
            .set(1.0f, 5.0f, 0.1f).defaultValue(1.0f).register(this);

    private boolean fireworkUsed;
    private boolean swapRequested;

    private Runnable polarPending;
    private long polarDeadline;
    private static final long POLAR_DURATION_MS = 100L;
    private static final long POLAR_REFLECTION_MS = 20L;

    public ElytraHelper() {
        super("ElytraHelper", ModuleCategory.Misc, "Свап на элитру и автовзлёт");
        INSTANCE = this;
    }

    @Subscribe
    public void onKey(KeyBindEvent e) {
        if (mc.player == null) return;
        if (swapKey.getKey() != null && swapKey.getKey() != -1 && e.getKey() == swapKey.getKey()) {
            swapRequested = true;
        }
        if (fireworkKey.getKey() != null && fireworkKey.getKey() != -1 && e.getKey() == fireworkKey.getKey() && mc.player.isGliding()) {
            fireworkUsed = true;
        }
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        updatePolarPending();

        if (swapRequested) {
            swapRequested = false;
            boolean hasElytraOn = mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
            swap(hasElytraOn);
        }

        if (autoTakeoff.getEnabled()) {
            ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            boolean hasElytra = chest.getItem() == Items.ELYTRA;
            boolean commonOk = hasElytra
                    && !mc.player.isInLava()
                    && !mc.player.isTouchingWater()
                    && !mc.player.hasVehicle()
                    && !mc.player.isGliding()
                    && !mc.player.isSpectator();

            if (commonOk && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                mc.player.jump();
            }

            if (commonOk && !mc.player.isOnGround()) {
                sendPacketSilent(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startGliding();
            }
        }

        if (fireworkUsed) {
            fireworkUsed = false;
            if (throwFireworkMode.isSelected("Легитный")) {
                swapAndUseLegit(Items.FIREWORK_ROCKET);
            } else {
                swapAndUseHvH(Items.FIREWORK_ROCKET);
            }
        }
    }

    private void swap(boolean chestplate) {
        int slot = chestplate ? findBestChestplateSlot() : findBestElytraSlot();
        if (slot == -1) {
            printClient(chestplate ? "Нагрудник не найден в инвентаре" : "Элитра не найдена в инвентаре");
            return;
        }

        Runnable action = () -> doSwap(slot);

        if (mode.isSelected("Grim")) {
            try {
                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                }
                action.run();
                sendPacketSilent(new CloseHandledScreenC2SPacket(0));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else if (mode.isSelected("Polar")) {
            polarPending = () -> {
                if (mc.player == null || mc.interactionManager == null) return;
                if (mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
                action.run();
                sendPacketSilent(new CloseHandledScreenC2SPacket(0));
            };
            polarDeadline = System.currentTimeMillis() + POLAR_DURATION_MS;
        } else {
            action.run();
        }
    }

    private void updatePolarPending() {
        if (polarPending == null) return;

        long now = System.currentTimeMillis();
        long remaining = polarDeadline - now;

        if (remaining > POLAR_REFLECTION_MS) {
            if (mc.options != null) {
                mc.options.forwardKey.setPressed(false);
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
                mc.options.backKey.setPressed(false);
                mc.options.jumpKey.setPressed(false);
                mc.options.sprintKey.setPressed(false);
            }
            return;
        }

        Runnable task = polarPending;
        polarPending = null;
        try {
            task.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void doSwap(int slot) {
        if (slot >= 0 && slot <= 8) {
            mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
        } else if (slot >= 9 && slot <= 45) {
            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
        }
    }

    private int searchItem(Item item, int from, int toExclusive) {
        for (int i = from; i < toExclusive; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (!st.isEmpty() && st.getItem() == item) return i;
        }
        return -1;
    }

    private void swapAndUseHvH(Item item) {
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(item))) return;

        int slot = searchItem(item, 9, 45);
        int slotHotbar = searchItem(item, 0, 9);
        int previousSlot = mc.player.getInventory().getSelectedSlot();

        if (mc.player.getMainHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return;
        }
        if (mc.player.getOffHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }

        if (slotHotbar != -1) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slotHotbar));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            return;
        }

        if (slot != -1) {
            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(8));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        }
    }

    private void swapAndUseLegit(Item item) {
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(item))) return;

        int slot = searchItem(item, 9, 45);
        int slotHotbar = searchItem(item, 0, 9);
        int previousSlot = mc.player.getInventory().getSelectedSlot();

        if (mc.player.getMainHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return;
        }
        if (mc.player.getOffHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }

        if (slotHotbar != -1) {
            mc.player.getInventory().setSelectedSlot(slotHotbar);
            mc.interactionManager.syncSelectedSlot();
            mc.interactionManager.sendSequencedPacket(mc.world,
                    (sequence) -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch()));
            mc.player.getInventory().setSelectedSlot(previousSlot);
            return;
        }

        if (slot != -1) {
            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
            mc.player.getInventory().setSelectedSlot(8);
            mc.interactionManager.syncSelectedSlot();
            mc.interactionManager.sendSequencedPacket(mc.world,
                    (sequence) -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch()));
            mc.player.getInventory().setSelectedSlot(previousSlot);
            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
        }
    }

    private int findBestElytraSlot() {
        int bestSlot = -1;
        double bestScore = -1.0;

        RegistryEntry<net.minecraft.enchantment.Enchantment> protection = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.PROTECTION.getValue()).orElseThrow();
        RegistryEntry<net.minecraft.enchantment.Enchantment> unbreaking = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.UNBREAKING.getValue()).orElseThrow();
        RegistryEntry<net.minecraft.enchantment.Enchantment> mending = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.MENDING.getValue()).orElseThrow();

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isOf(Items.ELYTRA)) {
                int protLevel = EnchantmentHelper.getLevel(protection, stack);
                int unbLevel = EnchantmentHelper.getLevel(unbreaking, stack);
                int mendLevel = EnchantmentHelper.getLevel(mending, stack);

                int maxDurability = stack.getMaxDamage();
                int currentDamage = stack.getDamage();
                double durabilityRatio = maxDurability > 0 ? (maxDurability - currentDamage) / (double) maxDurability : 0.0;

                double score = protLevel * 100 + unbLevel * 10 + (mendLevel > 0 ? 1 : 0) + durabilityRatio * 10;
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
        }
        return bestSlot;
    }

    private int findBestChestplateSlot() {
        int bestSlot = -1;
        double bestScore = -1.0;

        RegistryEntry<net.minecraft.enchantment.Enchantment> protection = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.PROTECTION.getValue()).orElseThrow();
        RegistryEntry<net.minecraft.enchantment.Enchantment> unbreaking = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.UNBREAKING.getValue()).orElseThrow();
        RegistryEntry<net.minecraft.enchantment.Enchantment> mending = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.MENDING.getValue()).orElseThrow();

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (PlayerUtil.isArmorForSlot(stack, EquipmentSlot.CHEST)) {
                double armorProtection = PlayerUtil.getArmorProtection(stack);
                double toughness = PlayerUtil.getArmorToughness(stack);

                int protLevel = EnchantmentHelper.getLevel(protection, stack);
                int unbLevel = EnchantmentHelper.getLevel(unbreaking, stack);
                int mendLevel = EnchantmentHelper.getLevel(mending, stack);

                int maxDurability = stack.getMaxDamage();
                int currentDamage = stack.getDamage();
                double durabilityRatio = maxDurability > 0 ? (maxDurability - currentDamage) / (double) maxDurability : 0.0;

                double score = armorProtection * 10000 + toughness * 1000 + protLevel * 100 + unbLevel * 10 + (mendLevel > 0 ? 1 : 0) + durabilityRatio * 10;
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
        }
        return bestSlot;
    }
}
