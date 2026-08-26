package dev.client.component.core.client;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import dev.client.api.nullcry.helper.other.PushUtils;
import dev.client.api.nullcry.uiClient.notification.NotificationDragging;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.component.Component;
import dev.client.modules.core.render.Interface;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ClientComponent extends Component {
    String lastAnCommand = null;
    boolean confirmedHubExit = false;
    final Set<RegistryEntry<StatusEffect>> effects = new HashSet<>();
    final Set<Integer> armorSlots = new HashSet<>();

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        boolean effectNotifEnabled = Interface.INSTANCE.isEnabled() && NotificationDragging.INSTANCE.getElementsNotifications().isSelected("Заканчивается эффект");
        boolean armorNotifEnabled = Interface.INSTANCE.isEnabled() && NotificationDragging.INSTANCE.getElementsNotifications().isSelected("Мало прочности брони");

        for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
            RegistryEntry<StatusEffect> effectKey = effect.getEffectType();
            StatusEffect statusEffect = effectKey.value();

            String effectName = statusEffect.getName().getString().toLowerCase(Locale.ROOT);
            if (!(statusEffect == StatusEffects.STRENGTH || statusEffect == StatusEffects.SPEED || effectName.contains("киллер"))) {
                continue;
            }

            if (effect.isInfinite()) {
                effects.remove(effectKey);
                continue;
            }

            int secondsLeft = effect.getDuration() / 20;
            if (secondsLeft <= 10) {
                if (!effects.contains(effectKey)) {
                    if (effectNotifEnabled) {
                        String text = String.format(Formatting.GRAY + "Эффект" + Formatting.WHITE + " %s " + Formatting.GRAY + "закончится через " + Formatting.RED + "%d секунд", statusEffect.getName().getString(), secondsLeft);
                        LynxWave.getInstance().getNotificationManager().add(text, 2, NotificationType.WARNING, RenderType.WORLD);
                        printClient(text);
                    }
                    effects.add(effectKey);
                }
            } else {
                effects.remove(effectKey);
            }
        }

        for (int slot = 0; slot < 4; slot++) {
            ItemStack piece = dev.client.api.compat.InventoryCompat.getArmorStack(mc.player.getInventory(), slot);
            if (piece.isEmpty() || !piece.isDamageable()) {
                armorSlots.remove(slot);
                continue;
            }

            int leftPercent = getDurabilityLeftPercent(piece);

            if (leftPercent <= 10) {
                if (!armorSlots.contains(slot)) {
                    if (armorNotifEnabled) {
                        var itemName = piece.getName().copy();
                        String armorName = armorSlotName(slot);
                        Text msg = Text.empty()
                                .append(itemName)
                                .append(Text.literal(" (" + armorName + ")").formatted(Formatting.GRAY))
                                .append(Text.literal(" осталось ").formatted(Formatting.GRAY))
                                .append(Text.literal(leftPercent + "%").formatted(Formatting.RED))
                                .append(Text.literal(" прочности").formatted(Formatting.WHITE));

                        LynxWave.getInstance().getNotificationManager().add(msg, 2, NotificationType.WARNING, RenderType.WORLD);
                        printClient(msg);
                    }
                    armorSlots.add(slot);
                }
            } else {
                armorSlots.remove(slot);
            }
        }

        effects.removeIf(effect -> mc.player.getStatusEffect(effect) == null);
    }


    @Subscribe
    public void onPacket(PacketEvent event) {
        if (mc.player == null || !event.isSend()) return;

        if (event.getPacket() instanceof ChatMessageC2SPacket p) {
            handleChat(p.chatMessage(), event);
        }
        if (event.getPacket() instanceof ChatCommandSignedC2SPacket p) {
            handleChat("/" + p.command(), event);
        }
        if (event.getPacket() instanceof CommandExecutionC2SPacket(String command)) {
            handleChat("/" + command, event);
        }
    }

    private void handleChat(String messageRaw, PacketEvent event) {
        String normalized = messageRaw.trim();
        String message = normalized.toLowerCase();

        if (message.equals("/hub") && ConnectionHelper.isPvP()) {
            if (!confirmedHubExit) {
                PushUtils.sendPush("Предупреждение", "Вы точно уверены? Напишите /hub еще 1 раз");
                printClient("Вы точно уверены? Напишите /hub еще 1 раз");
                confirmedHubExit = true;
                event.cancelled();
                return;
            } else {
                confirmedHubExit = false;
            }
        } else {
            confirmedHubExit = false;
        }

        if (message.matches("^/an\\d{3}$")) {
            int anarchyId = Integer.parseInt(message.substring(3));
            if (isValidAnarchyFunTime(anarchyId) && ConnectionHelper.isPvP()) {
                if (!message.equalsIgnoreCase(lastAnCommand)) {
                    PushUtils.sendPush("Предупреждение", "Вы точно уверены? Напишите " + message + " ещё 1 раз");
                    printClient("Вы точно уверены? Напишите " + message + " ещё 1 раз");
                    lastAnCommand = message;
                    event.cancelled();
                    return;
                } else {
                    lastAnCommand = null;
                }
            } else {
                lastAnCommand = null;
            }
        } else if (!message.startsWith("/an")) {
            lastAnCommand = null;
        }

        Set<String> ahCommands = Set.of("/ah me", "/ah my");
        if (ahCommands.contains(message)) {
            String playerName = (mc.player != null) ? mc.player.getName().getString() : mc.session.getUsername();
            event.cancelled();
            mc.player.networkHandler.sendChatCommand("ah " + playerName);
        }
    }

    private static String armorSlotName(int slot) {
        return switch (slot) {
            case 3 -> "Шлем";
            case 2 -> "Нагрудник";
            case 1 -> "Поножи";
            case 0 -> "Ботинки";
            default -> "Броня";
        };
    }

    private static int getDurabilityLeftPercent(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) return 100;
        int max = stack.getMaxDamage();
        int dmg = stack.getDamage();
        int left = Math.max(0, max - dmg);
        return (int) Math.round((left * 100.0) / Math.max(1, max));
    }

    private boolean isValidAnarchyFunTime(int id) {
        return (id >= 101 && id <= 110)
                || (id >= 201 && id <= 228)
                || (id >= 301 && id <= 318)
                || (id >= 501 && id <= 511)
                || (id >= 601 && id <= 606);
    }
}