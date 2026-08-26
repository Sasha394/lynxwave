package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.modules.core.render.Interface;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.*;

public class UseTracker extends Module {

    private final CheckBox trackTotem = new CheckBox("Снос тотема", () -> true)
            .applyDefault(true)
            .register(this);

    private final CheckBox trackPotions = new CheckBox("Полученные зелья", () -> true)
            .applyDefault(true)
            .register(this);

    private final CheckBox trackConsume = new CheckBox("Съеденный предмет", () -> true)
            .applyDefault(true)
            .register(this);

    private final Slider radius = new Slider("Радиус зелий", trackPotions::getEnabled)
            .set(10f, 100f, 1f)
            .defaultValue(100f)
            .register(this);

    private static class PotionData {
        ItemStack stack;
        double lastX, lastY, lastZ;

        PotionData(ItemStack stack, double x, double y, double z) {
            this.stack = stack;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
        }
    }

    private final Map<Integer, PotionData> trackedPotions = new HashMap<>();
    private final Map<UUID, ItemStack> activeUseItem = new HashMap<>();
    private final Map<UUID, Integer> useStartTick = new HashMap<>();

    public UseTracker() {
        super("UseTracker", ModuleCategory.Misc, "Трекает тотемы, зелья и еду игроков");
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        trackedPotions.clear();
        activeUseItem.clear();
        useStartTick.clear();
    }

    private int getClientColor() {
        return Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : 0x47A3FF;
    }

    private MutableText prefix() {
        int color = getClientColor();
        return Text.empty()
                .append(Text.literal("UseTracker").styled(s -> s.withBold(true).withColor(color)))
                .append(Text.literal(" -> ").styled(s -> s.withColor(Formatting.GRAY)));
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (trackConsume.getEnabled()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player.getUuid().equals(mc.player.getUuid())) continue;
                UUID id = player.getUuid();

                if (player.isUsingItem()) {
                    if (!activeUseItem.containsKey(id)) {
                        activeUseItem.put(id, player.getActiveItem().copy());
                        useStartTick.put(id, player.age);
                    }
                } else {
                    ItemStack used = activeUseItem.remove(id);
                    Integer startTick = useStartTick.remove(id);
                    if (used == null || used.isEmpty() || startTick == null) continue;
                    if (player.age - startTick < 31) continue;

                    UseAction action = used.getUseAction();
                    String verb = switch (action) {
                        case DRINK -> "выпил";
                        case EAT -> "съел";
                        default -> null;
                    };
                    if (verb == null) continue;

                    MutableText msg = prefix()
                            .append(Text.literal(player.getName().getString()).styled(s -> s.withColor(Formatting.WHITE)))
                            .append(Text.literal(" " + verb + " ").styled(s -> s.withColor(Formatting.GRAY)))
                            .append(used.getName());

                    MutableText notifMsg = Text.empty()
                            .append(Text.literal(player.getName().getString()).styled(s -> s.withColor(Formatting.WHITE)))
                            .append(Text.literal(" " + verb + " ").styled(s -> s.withColor(Formatting.GRAY)))
                            .append(used.getName());

                    mc.player.sendMessage(msg, false);
                    handlerClient().getNotificationManager().add(notifMsg, 3, NotificationType.WARNING, RenderType.WORLD, used.copy());
                }
            }
        }

        if (trackPotions.getEnabled()) {
            Set<Integer> current = new HashSet<>();
            float r = radius.getValue();

            for (var entity : mc.world.getEntities()) {
                if (!(entity instanceof PotionEntity potion)) continue;
                double dist = mc.player.distanceTo(potion);
                if (dist > r) continue;

                int eid = potion.getId();
                current.add(eid);
                if (!trackedPotions.containsKey(eid)) {
                    ItemStack stack = potion.getStack();
                    trackedPotions.put(eid, new PotionData(stack.copy(), potion.getX(), potion.getY(), potion.getZ()));
                } else {
                    PotionData d = trackedPotions.get(eid);
                    d.lastX = potion.getX();
                    d.lastY = potion.getY();
                    d.lastZ = potion.getZ();
                }
            }

            Set<Integer> removed = new HashSet<>(trackedPotions.keySet());
            removed.removeAll(current);

            for (int eid : removed) {
                PotionData data = trackedPotions.remove(eid);
                if (data == null) continue;

                Box hitBox = new Box(
                        data.lastX - 4, data.lastY - 2, data.lastZ - 4,
                        data.lastX + 4, data.lastY + 2, data.lastZ + 4
                );

                for (LivingEntity hit : mc.world.getEntitiesByClass(LivingEntity.class, hitBox, e -> true)) {
                    if (!(hit instanceof PlayerEntity player)) continue;
                    double dx = player.getX() - data.lastX;
                    double dz = player.getZ() - data.lastZ;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > 4.0) continue;

                    Text potionNameText = data.stack.getName();
                    String playerName = player.getName().getString();
                    double hitChance = Math.max(0, 1.0 - dist / 4.0) * 100.0;

                    Formatting hitColor = hitChance >= 65 ? Formatting.GREEN
                            : hitChance >= 35 ? Formatting.YELLOW
                            : Formatting.RED;

                    MutableText potionMsg = prefix()
                            .append(Text.literal(playerName).styled(s -> s.withColor(Formatting.WHITE)))
                            .append(Text.literal(" получил ").styled(s -> s.withColor(Formatting.GRAY)))
                            .append(potionNameText)
                            .append(Text.literal(" (").styled(s -> s.withColor(Formatting.GRAY)))
                            .append(Text.literal(String.format("%.0f%%", hitChance)).styled(s -> s.withColor(hitColor)))
                            .append(Text.literal(")").styled(s -> s.withColor(Formatting.GRAY)));

                    MutableText potionNotif = Text.empty()
                            .append(Text.literal(playerName).styled(s -> s.withColor(Formatting.WHITE)))
                            .append(Text.literal(" получил ").styled(s -> s.withColor(Formatting.GRAY)))
                            .append(potionNameText)
                            .append(Text.literal(" (").styled(s -> s.withColor(Formatting.GRAY)))
                            .append(Text.literal(String.format("%.0f%%", hitChance)).styled(s -> s.withColor(hitColor)))
                            .append(Text.literal(")").styled(s -> s.withColor(Formatting.GRAY)));

                    mc.player.sendMessage(potionMsg, false);
                    handlerClient().getNotificationManager().add(potionNotif, 3, NotificationType.WARNING, RenderType.WORLD, data.stack.copy());
                }
            }
        }
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (!event.isReceive() || mc.world == null || mc.player == null) return;

        if (trackTotem.getEnabled() && event.getPacket() instanceof EntityStatusS2CPacket pkt && pkt.getStatus() == 35) {
            var entity = pkt.getEntity(mc.world);
            if (!(entity instanceof PlayerEntity player)) return;
            if (player.getUuid().equals(mc.player.getUuid())) return;

            ItemStack totemStack = player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                    ? player.getOffHandStack()
                    : player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
                    ? player.getMainHandStack()
                    : player.getOffHandStack();

            Text totemNameText = totemStack.isEmpty()
                    ? Text.literal("Тотем бессмертия")
                    : totemStack.getName();

            boolean isEnchanted = EnchantmentHelper.hasEnchantments(player.getOffHandStack())
                    || EnchantmentHelper.hasEnchantments(player.getMainHandStack());

            MutableText enchantPart = Text.literal(isEnchanted ? "зачарованный" : "незачарованный")
                    .styled(s -> s.withColor(isEnchanted ? Formatting.GREEN : Formatting.RED));

            MutableText text = prefix()
                    .append(Text.literal(player.getName().getString()).styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal(" потерял ").styled(s -> s.withColor(Formatting.GRAY)))
                    .append(totemNameText)
                    .append(Text.literal(" (").styled(s -> s.withColor(Formatting.GRAY)))
                    .append(enchantPart)
                    .append(Text.literal(")").styled(s -> s.withColor(Formatting.GRAY)));

            MutableText totemNotif = Text.empty()
                    .append(Text.literal(player.getName().getString()).styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal(" потерял ").styled(s -> s.withColor(Formatting.GRAY)))
                    .append(totemNameText)
                    .append(Text.literal(" (").styled(s -> s.withColor(Formatting.GRAY)))
                    .append(enchantPart)
                    .append(Text.literal(")").styled(s -> s.withColor(Formatting.GRAY)));

            ItemStack totemIcon = totemStack.isEmpty() ? new ItemStack(Items.TOTEM_OF_UNDYING) : totemStack.copy();

            mc.player.sendMessage(text, false);
            handlerClient().getNotificationManager().add(totemNotif, 3, NotificationType.WARNING, RenderType.WORLD, totemIcon);
        }
    }
}
