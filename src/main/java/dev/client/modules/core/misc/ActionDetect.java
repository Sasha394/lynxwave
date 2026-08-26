package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import dev.client.api.nullcry.events.core.game.PickupItemEvent;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.game.UseItemEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.helper.player.ItemUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntityStatusEffectS2CPacket;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.*;

public class ActionDetect extends Module {
    private static final int NOTIF_ITEM_NAME_MAX = 28;
    private static final int NOTIF_EFFECT_NAME_MAX = 24;
    private static final int NOTIF_SOURCE_NAME_MAX = 28;
    private final Map<UUID, List<ItemStack>> collectedThisTick = new HashMap<>();

    public ActionDetect() {
        super("ActionDetect", ModuleCategory.Misc, "Детектит подобранные/использованные предметы");
    }

    SelectElements mode = new SelectElements("Показывать", () -> true).set("Поднятые предметы", "Скушанный предмет", "Использованный тотем", "Полученные эффекты").defaultValue("Поднятые предметы").register(this);

    CheckBox ignoreLocal = new CheckBox("Игнорировать себя", () -> !mode.isEmptySelected()).applyDefault(true).register(this);
    CheckBox notifications = new CheckBox("Присылать уведомление", () -> !mode.isEmptySelected()).applyDefault(true).register(this);
    CheckBox checkPickupDonatItem = new CheckBox("Показывать только донатные предметы", () -> mode.isSelected("Поднятые предметы")).applyDefault(true).register(this);
    ModeElement notificationsType = new ModeElement("Отправлять уведомление", () -> notifications.getEnabled() && !mode.isEmptySelected()).set("По одному", "Вместе").defaultValue("Вместе").register(this);

    private final Map<UUID, ItemStack> useTrackMap = new HashMap<>();
    private final Map<UUID, Map<StatusEffect, EffectSnapshot>> knownEffects = new HashMap<>();
    private final Map<UUID, List<EffectNotification>> receivedEffectsThisTick = new HashMap<>();
    private final Map<UUID, RecentSource> recentSources = new HashMap<>();
    private final Map<UUID, SplashSource> recentSplashSources = new HashMap<>();

    @Subscribe
    public void onPickupItem(PickupItemEvent event) {
        if (!mode.isSelected("Поднятые предметы")) return;
        if (!(event.getLivingEntity() instanceof PlayerEntity player)) return;
        if (isIgnored(player)) return;

        ItemStack stack = event.getItemStack();
        boolean isRare = ItemUtil.isRare(stack);

        if (checkPickupDonatItem.getEnabled() && !isRare) return;
        collectedThisTick.computeIfAbsent(player.getUuid(), k -> new java.util.ArrayList<>()).add(stack.copy());
    }

    @Subscribe
    public void onUseItem(UseItemEvent event) {
        if (!(event.getEntity() instanceof PlayerEntity player)) return;

        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) return;

        UseAction useAction = itemStack.getUseAction();

        if (mode.isSelected("Полученные эффекты") && useAction != UseAction.NONE) {
            long now = System.currentTimeMillis();
            recentSources.put(player.getUuid(), new RecentSource(itemStack.copy(), useAction, now + 2500L, now));
        }

        if (!mode.isSelected("Скушанный предмет")) return;
        if (isIgnored(player)) return;

        if (useAction == UseAction.NONE) return;

        String playerName = player.getName().getString();
        String actionWord = switch (useAction) {
            case EAT -> " скушал ";
            case DRINK -> " запил ";
            default -> " использовал ";
        };

        Text itemName = itemStack.getName().copy();
        if (itemName.getString().isEmpty()) return;

        Text component = Text.literal("")
                .append(Text.literal(playerName).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)))
                .append(Text.literal(actionWord).setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                .append(itemName);

        if (notifications.getEnabled()) {
            handlerClient().getNotificationManager().add(component, 2, NotificationType.INFO, RenderType.WORLD, itemStack);
        }

        printClient(component);
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (mode.isSelected("Полученные эффекты")) {
            if (event.getPacket() instanceof EntityStatusEffectS2CPacket effectPacket) {
                handleStatusEffect(effectPacket);
            } else if (event.getPacket() instanceof RemoveEntityStatusEffectS2CPacket removePacket) {
                handleRemoveEffect(removePacket);
            }
        }

        if (mode.isSelected("Использованный тотем") && event.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35) {
                Entity entity = packet.getEntity(mc.world);
                if (!(entity instanceof PlayerEntity player)) return;
                if (isIgnored(player)) return;

                String playerName = player.getName().getString();
                ItemStack itemStack = getActiveDeathProtector(player);
                Text displayName = itemStack.getName();
                String itemDescription = displayName.getString();

                Text component = Text.literal("")
                        .append(Text.literal(playerName).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)))
                        .append(Text.literal(" использовал ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                        .append(Text.literal(itemDescription));

                if (notifications.getEnabled()) {
                    handlerClient().getNotificationManager().add(
                            component,
                            2,
                            NotificationType.INFO,
                            RenderType.WORLD,
                            itemStack
                    );
                }

                printClient(component);
            }
        }
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (!recentSources.isEmpty()) {
            Iterator<Map.Entry<UUID, RecentSource>> iterator = recentSources.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, RecentSource> entry = iterator.next();
                if (entry.getValue().expiresAt() < now) {
                    iterator.remove();
                }
            }
        }

        if (!recentSplashSources.isEmpty()) {
            Iterator<Map.Entry<UUID, SplashSource>> splashIterator = recentSplashSources.entrySet().iterator();
            while (splashIterator.hasNext()) {
                Map.Entry<UUID, SplashSource> entry = splashIterator.next();
                if (entry.getValue().expiresAt() < now) {
                    splashIterator.remove();
                }
            }
        }

        boolean checkEffects = mode.isSelected("Полученные эффекты");

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (isIgnored(player)) continue;
            GameProfile profile = player.getGameProfile();
            UUID uuid = profile.id();

            if (player.isUsingItem()) {
                ItemStack current = player.getActiveItem();

                UseAction action = current.getUseAction();
                if (action != UseAction.EAT && action != UseAction.DRINK) continue;

                ItemStack last = useTrackMap.get(uuid);
                if (last == null || !ItemStack.areItemsEqual(current, last)) {
                    useTrackMap.put(uuid, current.copy());
                }
            } else {
                ItemStack removed = useTrackMap.remove(uuid);
                if (removed != null && removed.getUseAction() != UseAction.NONE && checkEffects) {
                    recentSources.put(uuid, new RecentSource(removed.copy(), removed.getUseAction(), now + 2500L, now));
                }
            }

            if (checkEffects) {
                syncEffectsFromClientState(player, now);
            }
        }

        for (Map.Entry<UUID, List<ItemStack>> entry : collectedThisTick.entrySet()) {
            PlayerEntity player = mc.world.getPlayerByUuid(entry.getKey());
            if (player == null) continue;
            if (isIgnored(player)) continue;

            List<ItemStack> items = entry.getValue();
            if (items.isEmpty()) continue;
            String playerName = player.getName().getString();

            MutableText component = Text.literal("")
                    .append(Text.literal(playerName).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)))
                    .append(Text.literal(" поднял:").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));

            for (ItemStack item : items) {
                boolean isRare = ItemUtil.isRare(item);
                int count = item.getCount();

                String label = isRare ? "Донатный предмет " : "";
                String countSuffix = count > 1 ? " x" + count : "";

                component.append(Text.literal("\n - " + label).setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                        .append(item.getName())
                        .append(Text.literal(countSuffix).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)));
            }

            if (notifications.getEnabled()) {
                if (notificationsType.isSelected("По одному")) {
                    for (ItemStack item : items) {
                        boolean isRare = ItemUtil.isRare(item);
                        int count = item.getCount();
                        String countSuffix = count > 1 ? " x" + count : "";

                        MutableText singleNotif = Text.literal("")
                                .append(Text.literal(playerName).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)));

                        if (isRare) {
                            singleNotif.append(Text.literal(" поднял Донатный предмет").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
                        } else {
                            singleNotif.append(Text.literal(" поднял").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
                        }

                        singleNotif.append(Text.literal(" ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                                .append(item.getName())
                                .append(Text.literal(countSuffix).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)));

                        handlerClient().getNotificationManager().add(singleNotif, 3, NotificationType.INFO, RenderType.WORLD, item);
                    }
                } else {
                    boolean hasRare = false;
                    boolean hasNonRare = false;

                    for (ItemStack item : items) {
                        if (ItemUtil.isRare(item)) hasRare = true;
                        else hasNonRare = true;
                    }

                    int totalRare = (int) items.stream().filter(ItemUtil::isRare).count();

                    MutableText notif = Text.literal("")
                            .append(Text.literal(playerName).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)));

                    if (hasRare && !hasNonRare) {
                        if (totalRare == 1) {
                            notif.append(Text.literal(" поднял Донатный предмет").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
                        } else {
                            notif.append(Text.literal(" поднял Донатные предметы").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
                        }
                    } else {
                        notif.append(Text.literal(" поднял").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
                    }

                    int shown = 0;
                    int maxItems = 6;

                    for (ItemStack item : items) {
                        if (shown >= maxItems) break;

                        int count = item.getCount();
                        String countSuffix = count > 1 ? " x" + count : "";

                        if (shown > 0)
                            notif.append(Text.literal(",").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));

                        notif.append(Text.literal(" ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                                .append(abbreviateText(item.getName(), NOTIF_ITEM_NAME_MAX))
                                .append(Text.literal(countSuffix).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)));

                        shown++;
                    }

                    int hidden = items.size() - shown;
                    if (hidden > 0) {
                        notif.append(Text.literal(" и ещё " + hidden + "...").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
                    }

                    handlerClient().getNotificationManager().add(notif, 3, NotificationType.INFO, RenderType.WORLD, items.get(0));
                }
            }

            printClient(component);
        }

        collectedThisTick.clear();

        if (!receivedEffectsThisTick.isEmpty()) {
            Map<UUID, List<EffectNotification>> effectsSnapshot = new HashMap<>();
            for (Map.Entry<UUID, List<EffectNotification>> entry : receivedEffectsThisTick.entrySet()) {
                effectsSnapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            receivedEffectsThisTick.clear();

            for (Map.Entry<UUID, List<EffectNotification>> entry : effectsSnapshot.entrySet()) {
                PlayerEntity player = mc.world.getPlayerByUuid(entry.getKey());
                if (player == null) continue;
                if (isIgnored(player)) continue;

                List<EffectNotification> effects = entry.getValue();
                if (effects.isEmpty()) continue;

                String playerName = player.getName().getString();

                Map<String, SourceGroup> grouped = new LinkedHashMap<>();
                for (EffectNotification notification : effects) {
                    SourceInfo source = notification.source();
                    grouped.computeIfAbsent(source.key(), key -> new SourceGroup(source)).add(notification.effect());
                }

                for (SourceGroup group : grouped.values()) {
                    SourceInfo source = group.source();
                    MutableText message = buildEffectMessage(playerName, group);
                    ItemStack icon = source.icon().copy();
                    if (notifications.getEnabled()) {
                        if (notificationsType.isSelected("По одному")) {
                            for (StatusEffectInstance effectInstance : group.effects()) {
                                MutableText notif = source.buildHeader(playerName)
                                        .append(Text.literal(": ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                                        .append(buildEffectDescription(effectInstance));

                                handlerClient().getNotificationManager().add(
                                        notif,
                                        3,
                                        NotificationType.INFO,
                                        RenderType.WORLD,
                                        icon.copy()
                                );
                            }
                        } else {
                            MutableText notif = source.buildHeader(playerName, NOTIF_SOURCE_NAME_MAX)
                                    .append(Text.literal(": ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));

                            boolean first = true;
                            for (StatusEffectInstance effectInstance : group.effects()) {
                                if (first) {
                                    notif.append(buildShortEffectDescription(effectInstance));
                                    first = false;
                                } else {
                                    notif.append(buildEffectLineForNotifShort(effectInstance));
                                }
                            }

                            handlerClient().getNotificationManager().add(
                                    notif,
                                    3,
                                    NotificationType.INFO,
                                    RenderType.WORLD,
                                    icon.copy()
                            );
                        }
                    }

                    printClient(message);
                }
            }

            effectsSnapshot.clear();
        }
    }

    private void syncEffectsFromClientState(PlayerEntity player, long now) {
        if (player == mc.player) {
            return;
        }

        UUID uuid = player.getUuid();
        Map<StatusEffect, EffectSnapshot> effectMap = knownEffects.computeIfAbsent(uuid, k -> new HashMap<>());
        Set<StatusEffect> seenTypes = new HashSet<>();

        for (StatusEffectInstance activeEffect : player.getStatusEffects()) {
            StatusEffectInstance effectCopy = new StatusEffectInstance(activeEffect);
            StatusEffect effectType = effectCopy.getEffectType().value();
            seenTypes.add(effectType);

            EffectSnapshot previous = effectMap.get(effectType);
            EffectSnapshot snapshot = new EffectSnapshot(
                    effectCopy.getAmplifier(),
                    effectCopy.getDuration(),
                    effectCopy.isAmbient(),
                    effectCopy.shouldShowParticles(),
                    effectCopy.shouldShowIcon(),
                    previous != null ? previous.keepFading() : null,
                    now
            );

            if (shouldNotify(previous, snapshot)) {
                SourceInfo source = determineSource(player, effectCopy);
                receivedEffectsThisTick.computeIfAbsent(uuid, k -> new ArrayList<>())
                        .add(new EffectNotification(effectCopy, source));
            }

            effectMap.put(effectType, snapshot);
        }

        if (!effectMap.isEmpty()) {
            effectMap.keySet().removeIf(type -> !seenTypes.contains(type));
            if (effectMap.isEmpty()) {
                knownEffects.remove(uuid);
            }
        }
    }

    private MutableText buildEffectMessage(String playerName, SourceGroup group) {
        SourceInfo source = group.source();
        MutableText message = source.buildHeader(playerName);
        message.append(Text.literal(":").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));

        for (StatusEffectInstance effect : group.effects()) {
            message.append(buildEffectLine(effect));
        }

        return message;
    }

    private MutableText buildEffectLine(StatusEffectInstance effect) {
        return Text.literal("\n - ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY))
                .append(buildEffectDescription(effect));
    }

    private MutableText buildEffectLineForNotif(StatusEffectInstance effect) {
        return Text.literal(", ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY))
                .append(buildEffectDescription(effect));
    }

    private MutableText buildEffectLineForNotifShort(StatusEffectInstance effect) {
        return Text.literal(", ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY))
                .append(buildShortEffectDescription(effect));
    }

    private MutableText buildShortEffectDescription(StatusEffectInstance effect) {
        MutableText description = Text.literal("");

        MutableText effectName = effect.getEffectType().value().getName().copy();
        int amplifier = effect.getAmplifier();
        if (amplifier > 0) {
            effectName = Text.translatable("potion.withAmplifier",
                    effectName,
                    Text.translatable("potion.potency." + amplifier));
        }

        effectName = effectName.setStyle(Style.EMPTY.withFormatting(Formatting.WHITE));
        description = description.append(abbreviateText(effectName, NOTIF_EFFECT_NAME_MAX));

        return description;
    }

    private void handleStatusEffect(EntityStatusEffectS2CPacket packet) {
        if (mc.world == null) return;

        Entity entity = mc.world.getEntityById(packet.getEntityId());
        if (!(entity instanceof PlayerEntity player)) return;
        if (isIgnored(player)) return;

        StatusEffectInstance effectInstance = new StatusEffectInstance(
                packet.getEffectId(),
                packet.getDuration(),
                packet.getAmplifier(),
                packet.isAmbient(),
                packet.shouldShowParticles(),
                packet.shouldShowIcon(),
                null
        );

        if (!packet.keepFading()) {
            effectInstance.skipFading();
        }

        UUID uuid = player.getUuid();
        Map<StatusEffect, EffectSnapshot> effectMap = knownEffects.computeIfAbsent(uuid, k -> new HashMap<>());
        StatusEffect effectType = effectInstance.getEffectType().value();
        long now = System.currentTimeMillis();

        EffectSnapshot snapshot = new EffectSnapshot(
                effectInstance.getAmplifier(),
                effectInstance.getDuration(),
                effectInstance.isAmbient(),
                effectInstance.shouldShowParticles(),
                effectInstance.shouldShowIcon(),
                packet.keepFading(),
                now
        );

        EffectSnapshot previous = effectMap.put(effectType, snapshot);
        if (!shouldNotify(previous, snapshot)) {
            return;
        }

        SourceInfo source = determineSource(player, effectInstance);
        receivedEffectsThisTick.computeIfAbsent(uuid, k -> new ArrayList<>())
                .add(new EffectNotification(effectInstance, source));
    }

    private boolean shouldNotify(EffectSnapshot previous, EffectSnapshot current) {
        if (previous == null) {
            return true;
        }

        if (previous.amplifier() != current.amplifier()
                || previous.ambient() != current.ambient()
                || previous.showParticles() != current.showParticles()
                || previous.showIcon() != current.showIcon()
                || !Objects.equals(previous.keepFading(), current.keepFading())) {
            return true;
        }

        return current.duration() > previous.duration();
    }

    private void handleRemoveEffect(RemoveEntityStatusEffectS2CPacket packet) {
        if (mc.world == null) return;

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity player)) return;

        Map<StatusEffect, EffectSnapshot> effectMap = knownEffects.get(player.getUuid());
        if (effectMap == null) return;

        StatusEffect effectType = packet.effect().value();
        effectMap.remove(effectType);
        if (effectMap.isEmpty()) {
            knownEffects.remove(player.getUuid());
        }
    }

    private SourceInfo determineSource(PlayerEntity player, StatusEffectInstance effectInstance) {
        UUID uuid = player.getUuid();
        long now = System.currentTimeMillis();
        RecentSource recent = recentSources.get(uuid);
        if (recent != null) {
            if (recent.expiresAt() < now) {
                recentSources.remove(uuid);
            } else {
                return buildRecentSourceInfo(recent);
            }
        }

        return buildSplashSourceInfo(uuid, effectInstance, now);
    }

    private SourceInfo buildRecentSourceInfo(RecentSource recent) {
        ItemStack stack = recent.stack().copy();

        MutableText verb = switch (recent.action()) {
            case DRINK -> Text.literal(" запил ");
            case EAT -> Text.literal(" скушал ");
            default -> Text.literal(" использовал ");
        };
        verb = verb.setStyle(Style.EMPTY.withFormatting(Formatting.GRAY));

        MutableText subject = stack.getName().copy();

        return new SourceInfo("recent:" + recent.createdAt(), verb, subject, stack, null);
    }

    private SourceInfo buildSplashSourceInfo(UUID uuid, StatusEffectInstance effectInstance, long now) {
        PotionMatch match = findMatchingSplashPotion(effectInstance);
        ItemStack stack = match.stack();
        ItemStack stackCopy = stack.copy();
        MutableText potionName = stackCopy.getName().copy();
        if (!match.hasSpecificName()) {
            potionName = potionName.append(Text.literal(" (").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                    .append(effectInstance.getEffectType().value().getName().copy().setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)))
                    .append(Text.literal(")").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
        }

        int baseDuration = match.baseDuration();
        int percent = baseDuration > 0 ? MathHelper.clamp(Math.round((effectInstance.getDuration() / (float) baseDuration) * 100.0F), 0, 100) : 100;

        SplashSource existing = recentSplashSources.get(uuid);
        if (existing != null) {
            if (existing.expiresAt() < now || !ItemStack.areItemsEqual(existing.stack(), stackCopy)) {
                recentSplashSources.remove(uuid);
            } else {
                int mergedPercent = mergeSplashPercent(existing.percent(), percent);
                SplashSource updated = existing.withUpdatedPercent(mergedPercent, now + 500L);
                recentSplashSources.put(uuid, updated);
                return updated.toSourceInfo();
            }
        }

        String key = "splash:" + now;
        SplashSource created = new SplashSource(key, stackCopy, potionName.copy(), percent, now + 500L);
        recentSplashSources.put(uuid, created);
        return created.toSourceInfo();
    }

    private int mergeSplashPercent(int previous, int current) {
        if (previous == current) {
            return previous;
        }

        return MathHelper.clamp(Math.round((previous + current) / 2.0F), 0, 100);
    }

    private PotionMatch findMatchingSplashPotion(StatusEffectInstance effect) {
        Registry<Potion> registry = Registries.POTION;
        RegistryEntry<Potion> bestEntry = null;
        int bestDifference = Integer.MAX_VALUE;
        int bestDuration = -1;

        for (Map.Entry<RegistryKey<Potion>, Potion> entry : registry.getEntrySet()) {
            Potion potion = entry.getValue();
            for (StatusEffectInstance potionEffect : potion.getEffects()) {
                if (potionEffect.getEffectType().equals(effect.getEffectType())
                        && potionEffect.getAmplifier() == effect.getAmplifier()) {
                    int difference = Math.abs(potionEffect.getDuration() - effect.getDuration());
                    if (difference < bestDifference) {
                        bestDifference = difference;
                        bestEntry = registry.getEntry(potion);
                        bestDuration = potionEffect.getDuration();
                    }
                }
            }
        }

        if (bestEntry != null) {
            return new PotionMatch(PotionContentsComponent.createStack(Items.SPLASH_POTION, bestEntry), true, bestDuration);
        }

        return new PotionMatch(new ItemStack(Items.SPLASH_POTION), false, effect.getDuration());
    }

    private MutableText buildEffectDescription(StatusEffectInstance effect) {
        MutableText description = Text.literal("");

        MutableText effectName = effect.getEffectType().value().getName().copy();
        int amplifier = effect.getAmplifier();
        if (amplifier > 0) {
            effectName = Text.translatable("potion.withAmplifier",
                    effectName,
                    Text.translatable("potion.potency." + amplifier));
        }

        description = description.append(effectName.setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)));

        Text durationText = StatusEffectUtil.getDurationText(effect, 1.0F, 20F);
        if (!durationText.getString().isEmpty()) {
            description = description.append(Text.literal(" ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                    .append(Text.literal("(").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                    .append(durationText.copy().setStyle(Style.EMPTY.withFormatting(Formatting.DARK_GRAY)))
                    .append(Text.literal(")").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
        }

        return description;
    }

    private record EffectSnapshot(int amplifier, int duration, boolean ambient, boolean showParticles,
                                  boolean showIcon, Boolean keepFading, long receivedAt) {
    }

    private record EffectNotification(StatusEffectInstance effect, SourceInfo source) {
    }

    private static class SourceGroup {
        private final SourceInfo source;
        private final List<StatusEffectInstance> effects = new ArrayList<>();

        private SourceGroup(SourceInfo source) {
            this.source = source;
        }

        private void add(StatusEffectInstance effect) {
            effects.add(effect);
        }

        private SourceInfo source() {
            return source;
        }

        private List<StatusEffectInstance> effects() {
            return effects;
        }
    }

    private record SourceInfo(String key, Text verb, Text subject, ItemStack icon, Integer potencyPercent) {
        private MutableText buildHeader(String playerName) {
            MutableText header = Text.literal("")
                    .append(Text.literal(playerName).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)))
                    .append(verb.copy());
            header.append(subject.copy());
            if (potencyPercent != null) {
                header.append(Text.literal(" (").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                        .append(Text.literal(potencyPercent + "%").setStyle(Style.EMPTY.withFormatting(Formatting.GREEN)))
                        .append(Text.literal(")").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
            }
            return header;
        }

        private MutableText buildHeader(String playerName, int maxSubjectLen) {
            MutableText header = Text.literal("")
                    .append(Text.literal(playerName).setStyle(Style.EMPTY.withFormatting(Formatting.WHITE)))
                    .append(verb.copy());
            header.append(ActionDetect.abbreviateText(subject, maxSubjectLen));
            if (potencyPercent != null) {
                header.append(Text.literal(" (").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
                        .append(Text.literal(potencyPercent + "%").setStyle(Style.EMPTY.withFormatting(Formatting.GREEN)))
                        .append(Text.literal(")").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
            }
            return header;
        }
    }

    private record RecentSource(ItemStack stack, UseAction action, long expiresAt, long createdAt) {
    }

    private record SplashSource(String key, ItemStack stack, Text potionName, int percent, long expiresAt) {
        private SplashSource withUpdatedPercent(int percent, long expiresAt) {
            return new SplashSource(key, stack, potionName, percent, expiresAt);
        }

        private SourceInfo toSourceInfo() {
            MutableText verb = Text.literal(" получил ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY));
            MutableText subject = potionName.copy();
            return new SourceInfo(key, verb, subject, stack.copy(), percent);
        }
    }

    private record PotionMatch(ItemStack stack, boolean hasSpecificName, int baseDuration) {
    }

    private static ItemStack getActiveDeathProtector(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack itemStack = player.getStackInHand(hand);
            if (itemStack.contains(DataComponentTypes.DEATH_PROTECTION)) {
                return itemStack;
            }
        }

        return new ItemStack(Items.TOTEM_OF_UNDYING);
    }

    private boolean isIgnored(PlayerEntity player) {
        return ignoreLocal.getEnabled() && mc != null && mc.player != null && player.getUuid().equals(mc.player.getUuid());
    }

    private static Text abbreviateText(Text original, int maxLen) {
        String value = original.getString();
        if (value.length() <= maxLen) {
            return original.copy();
        }

        int cut = Math.max(0, maxLen - 3);
        String shortened = value.substring(0, cut) + "...";
        return Text.literal(shortened).setStyle(original.getStyle());
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        collectedThisTick.clear();
        useTrackMap.clear();
        knownEffects.clear();
        receivedEffectsThisTick.clear();
        recentSources.clear();
        recentSplashSources.clear();
    }
}
