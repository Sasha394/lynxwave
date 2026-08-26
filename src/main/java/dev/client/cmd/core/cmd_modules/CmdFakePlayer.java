package dev.client.cmd.core.cmd_modules;

import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdFakePlayer implements Command, CommandWithAdvice, ClientApi {

    /** UUID фейкового игрока (используется AntiBot, чтобы не считать его ботом). */
    public static final java.util.UUID FAKE_PLAYER_UUID = UUID.fromString("66123666-6666-6666-6666-666666666600");

    final Prefix prefix;
    final Logger logger;

    OtherClientPlayerEntity fakePlayer;

    public CmdFakePlayer(Prefix prefix, Logger logger) {
        this.prefix = prefix;
        this.logger = logger;
        EventManager.register(this);
    }

    @Subscribe
    public void onAttack(PlayerAttackEvent event) {
        if (fakePlayer != null && event.getEntity() == fakePlayer && fakePlayer.hurtTime == 0) {
            mc.world.playSound(
                mc.player,
                fakePlayer.getX(),
                fakePlayer.getY(),
                fakePlayer.getZ(),
                SoundEvents.ENTITY_PLAYER_HURT,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F
            );
            if (mc.player.fallDistance > 0.0F) {
                mc.world.playSound(
                    mc.player,
                    fakePlayer.getX(),
                    fakePlayer.getY(),
                    fakePlayer.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
                    SoundCategory.PLAYERS,
                    1.0F,
                    1.0F
                );
            } else {
                mc.world.playSound(
                    mc.player,
                    fakePlayer.getX(),
                    fakePlayer.getY(),
                    fakePlayer.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                    SoundCategory.PLAYERS,
                    1.0F,
                    1.0F
                );
            }

            fakePlayer.onDamaged(mc.world.getDamageSources().generic());
            fakePlayer.setHealth(fakePlayer.getHealth() + fakePlayer.getAbsorptionAmount() - 1.0F);
            if (fakePlayer.isDead()) {
                fakePlayer.setHealth(10.0F);
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().onEntityStatus(new EntityStatusS2CPacket(fakePlayer, (byte) 35));
                }
            }
        }
    }

    @Override
    public void execute(Parameters parameters) {
        String action = parameters.asString(0).orElseThrow(() ->
            new CommandException(Formatting.RED + "Укажите действие: add, del" + Formatting.RESET)
        ).toLowerCase();

        switch (action) {
            case "add" -> add();
            case "del" -> del();
            default -> throw new CommandException(Formatting.RED + "Неизвестное действие. Используйте: add, del" + Formatting.RESET);
        }
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("add", "del");
    }

    @Override
    public String name() {
        return "fakeplayer";
    }

    @Override
    public String description() {
        return "Создаёт или удаляет фейкового игрока для тренировки";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
            prefix.get() + "fakeplayer add" + Formatting.GRAY + " - Создать фейкового игрока",
            prefix.get() + "fakeplayer del" + Formatting.GRAY + " - Удалить фейкового игрока"
        );
    }

    private void add() {
        if (mc.player == null || mc.world == null) {
            logger.log(Formatting.RED + "Вы должны быть в игре.");
            return;
        }

        if (fakePlayer != null) {
            fakePlayer.discard();
            fakePlayer = null;
        }

        fakePlayer = new OtherClientPlayerEntity(mc.world, new GameProfile(FAKE_PLAYER_UUID, "FakePlayer"));
        fakePlayer.copyPositionAndRotation(mc.player);
        fakePlayer.setStackInHand(Hand.MAIN_HAND, mc.player.getMainHandStack().copy());
        fakePlayer.setStackInHand(Hand.OFF_HAND, mc.player.getOffHandStack().copy());
        fakePlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 9999, 2));
        fakePlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 9999, 4));
        fakePlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 9999, 1));
        mc.world.addEntity(fakePlayer);

        LynxWave.getInstance().getNotificationManager().add("Фейковый игрок добавлен", 3, NotificationType.WARNING, RenderType.WORLD);
    }

    private void del() {
        if (fakePlayer == null) {
            LynxWave.getInstance().getNotificationManager().add("Фейковый игрок не существует", 3, NotificationType.ERROR, RenderType.WORLD);
        } else {
            fakePlayer.discard();
            fakePlayer = null;
            LynxWave.getInstance().getNotificationManager().add("Фейковый игрок удалён", 3, NotificationType.WARNING, RenderType.WORLD);
        }
    }
}
