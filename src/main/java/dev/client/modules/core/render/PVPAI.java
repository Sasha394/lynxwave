package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PVPAI extends Module implements ClientApi {

    private final Map<PlayerEntity, Float> playerWinChances = new HashMap<>();
    private float ourWinChance = 50.0f;
    private final CheckBox damageIndicator = new CheckBox("Показывать урон", () -> true).defaultValue(true).register(this);
    private final Slider damageSize = new Slider("Размер урона", () -> damageIndicator.getEnabled()).set(0.5f, 2.0f, 0.1f).applyDefault(1.0f).register(this);
    private final ColorPicker damageColor = new ColorPicker("Цвет урона", () -> damageIndicator.getEnabled()).set(new java.awt.Color(255, 85, 85).getRGB()).defaultValue(new java.awt.Color(255, 85, 85).getRGB()).register(this);
    private final List<DamageNumber> damageNumbers = new ArrayList<>();
    private final Map<LivingEntity, Float> entityHealthMap = new HashMap<>();

    private static final long DAMAGE_NUMBER_DURATION_MS = 2000L;

    public PVPAI() {
        super("PVP AI", ModuleCategory.Render, "Оценка шанса победы и индикатор урона");
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (!isEnabled() || !damageIndicator.getEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || living.isRemoved()) continue;
            float currentHealth = living.getHealth() + living.getAbsorptionAmount();
            if (entityHealthMap.containsKey(living)) {
                float previousHealth = entityHealthMap.get(living);
                float damage = previousHealth - currentHealth;
                if (damage > 0.01f) {
                    Vec3d pos = dev.client.api.compat.EntityCompat.pos(living).add(0.0, living.getHeight() + 0.3, 0.0);
                    damageNumbers.add(new DamageNumber(pos, damage));
                }
            }
            entityHealthMap.put(living, currentHealth);
        }
        entityHealthMap.entrySet().removeIf(entry -> entry.getKey().isRemoved());
    }

    @Subscribe
    public void onDraw2D(RenderEvent.Draw2D event) {
        if (mc.player == null || mc.world == null) return;

        float ourPower = calculatePlayerPower(mc.player);
        float totalEnemyPower = 0f;
        int visibleEnemies = 0;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isInvisible() || player.isDead() || mc.player.distanceTo(player) > 50f) continue;
            float enemyPower = calculatePlayerPower(player);
            playerWinChances.put(player, calculateWinChance(ourPower, enemyPower));
            totalEnemyPower += enemyPower;
            visibleEnemies++;
        }

        if (visibleEnemies > 0) {
            ourWinChance = calculateWinChance(ourPower, totalEnemyPower / visibleEnemies);
        } else {
            ourWinChance = 50f;
        }

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();
        String ourText = "Ваш процент: " + (int) ourWinChance + "%";
        int textWidth = mc.textRenderer.getWidth(ourText);
        int textX = (screenWidth - textWidth) / 2;
        int textY = screenHeight - 70;
        event.getContext().drawText(mc.textRenderer, ourText, textX, textY, 0xFFFFFF, true);
    }

    @Subscribe
    public void onDraw3D(RenderEvent.Draw3D event) {
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = event.getMatrices();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();

        if (damageIndicator.getEnabled() && !damageNumbers.isEmpty()) {
            Iterator<DamageNumber> iterator = damageNumbers.iterator();
            while (iterator.hasNext()) {
                DamageNumber dmg = iterator.next();
                dmg.update();
                if (dmg.shouldRemove()) {
                    iterator.remove();
                } else {
                    Vec3d renderPos = dmg.getPosition();
                    double x = renderPos.x - cameraPos.x;
                    double y = renderPos.y - cameraPos.y;
                    double z = renderPos.z - cameraPos.z;
                    matrices.push();
                    matrices.translate(x, y, z);
                    matrices.multiply(camera.getRotation());
                    matrices.scale(-0.025f, -0.025f, 0.025f);
                    String damageText = formatDamage(dmg.damage);
                    float textWidth = mc.textRenderer.getWidth(damageText);
                    float alpha = dmg.getAlpha();
                    float scale = dmg.getScale() * damageSize.getValue();
                    matrices.scale(scale, scale, 1f);
                    int color = ColorUtils.setAlpha(damageColor.getColorRGBA(), (int) (alpha * 255));
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
                    mc.textRenderer.draw(
                            damageText,
                            -textWidth / 2f,
                            0f,
                            color,
                            true,
                            matrices.peek().getPositionMatrix(),
                            immediate,
                            net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH,
                            0,
                            15728880
                    );
                    immediate.draw();
                    RenderSystem.disableBlend();
                    matrices.pop();
                }
            }
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isInvisible() || player.isDead() || !playerWinChances.containsKey(player)) continue;
            if (mc.player.distanceTo(player) > 32) continue;

            float enemyWinChance = 100f - ourWinChance;
            Vec3d playerPos = dev.client.api.compat.EntityCompat.pos(player);
            double x = playerPos.x - cameraPos.x;
            double y = playerPos.y + player.getHeight() + 0.6 - cameraPos.y;
            double z = playerPos.z - cameraPos.z;
            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(camera.getRotation());
            matrices.scale(0.025f, -0.025f, 0.025f);
            String text = (int) enemyWinChance + "%";
            int textWidth = mc.textRenderer.getWidth(text);
            int bgWidth = textWidth + 4;
            int bgHeight = 10;
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, -bgWidth / 2f - 1f, -1f, 0f).color(0, 0, 0, 64);
            buffer.vertex(matrix, -bgWidth / 2f - 1f, bgHeight - 1, 0f).color(0, 0, 0, 64);
            buffer.vertex(matrix, bgWidth / 2f + 1f, bgHeight - 1, 0f).color(0, 0, 0, 64);
            buffer.vertex(matrix, bgWidth / 2f + 1f, -1f, 0f).color(0, 0, 0, 64);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderCompat.draw(buffer.end());
            int color = enemyWinChance > 50f ? 16733525 : 5635925;
            VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
            mc.textRenderer.draw(
                    text,
                    -textWidth / 2f,
                    0f,
                    color,
                    true,
                    matrices.peek().getPositionMatrix(),
                    immediate,
                    net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                    0,
                    15728880
            );
            immediate.draw();
            RenderSystem.disableBlend();
            matrices.pop();
        }
    }

    private float calculatePlayerPower(PlayerEntity player) {
        float power = 0f;
        float healthPercent = player.getHealth() / player.getMaxHealth();
        power += healthPercent * 20f;
        power += player.getHungerManager().getFoodLevel() / 2f;

        float totalArmorValue = 0f;
        float armorDurabilityFactor = 0f;
        int armorCount = 0;
        for (ItemStack armor : dev.client.api.compat.InventoryCompat.armorItems(player)) {
            if (armor.isEmpty()) continue;
            totalArmorValue += getArmorValue(armor);
            totalArmorValue += getEnchantmentPower(armor);
            if (armor.isDamageable()) {
                int maxDamage = armor.getMaxDamage();
                int damage = armor.getDamage();
                float durabilityPercent = 1f - (float) damage / maxDamage;
                armorDurabilityFactor += durabilityPercent;
                armorCount++;
            }
        }
        if (armorCount > 0) {
            power += totalArmorValue * (armorDurabilityFactor / armorCount);
        } else {
            power += totalArmorValue;
        }

        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isEmpty()) {
            power += getWeaponDamage(mainHand);
            power += getEnchantmentPower(mainHand);
        }

        if (player.hasStatusEffect(StatusEffects.STRENGTH)) {
            int amp = player.getStatusEffect(StatusEffects.STRENGTH).getAmplifier() + 1;
            power += amp * 5;
        }
        if (player.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amp = player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1;
            power += amp * 5;
        }
        if (player.hasStatusEffect(StatusEffects.SPEED)) {
            int amp = player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1;
            power += amp * 3;
        }
        if (player.hasStatusEffect(StatusEffects.REGENERATION)) {
            int amp = player.getStatusEffect(StatusEffects.REGENERATION).getAmplifier() + 1;
            power += amp * 4;
        }
        if (player.hasStatusEffect(StatusEffects.WEAKNESS)) {
            int amp = player.getStatusEffect(StatusEffects.WEAKNESS).getAmplifier() + 1;
            power -= amp * 3;
        }
        if (player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            int amp = player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() + 1;
            power -= amp * 2;
        }

        return Math.max(1f, power);
    }

    private float getArmorValue(ItemStack armor) {
        String name = armor.getItem().toString().toLowerCase();
        if (name.contains("diamond")) return 3f;
        if (name.contains("netherite")) return 4f;
        if (name.contains("iron")) return 2f;
        if (name.contains("gold") || name.contains("chain")) return 1.5f;
        if (name.contains("leather")) return 1f;
        return 0.5f;
    }

    private float getWeaponDamage(ItemStack weapon) {
        String name = weapon.getItem().toString().toLowerCase();
        if (name.contains("netherite_sword")) return 10f;
        if (name.contains("diamond_sword")) return 9f;
        if (name.contains("iron_sword")) return 7f;
        if (name.contains("gold_sword")) return 5f;
        if (name.contains("stone_sword")) return 6f;
        if (name.contains("wood_sword")) return 5f;
        if (name.contains("netherite_axe")) return 12f;
        if (name.contains("diamond_axe")) return 11f;
        if (name.contains("iron_axe")) return 9f;
        if (name.contains("trident")) return 10f;
        return 2f;
    }

    private float getEnchantmentPower(ItemStack item) {
        ItemEnchantmentsComponent enchantments = item.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) return 0f;
        float power = 0f;
        for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
            int level = enchantments.getLevel(entry);
            if (entry.matchesKey(Enchantments.PROTECTION)) power += level * 2;
            else if (entry.matchesKey(Enchantments.SHARPNESS)) power += level * 2.5f;
            else if (entry.matchesKey(Enchantments.FIRE_ASPECT)) power += level * 1.5f;
            else if (entry.matchesKey(Enchantments.KNOCKBACK)) power += level * 1f;
            else if (entry.matchesKey(Enchantments.THORNS)) power += level * 1.5f;
            else if (entry.matchesKey(Enchantments.BLAST_PROTECTION)) power += level * 1.5f;
            else if (entry.matchesKey(Enchantments.PROJECTILE_PROTECTION)) power += level * 1.5f;
            else if (entry.matchesKey(Enchantments.FIRE_PROTECTION)) power += level * 1.5f;
            else if (entry.matchesKey(Enchantments.FEATHER_FALLING)) power += level * 0.5f;
            else if (entry.matchesKey(Enchantments.UNBREAKING)) power += level * 0.5f;
            else power += level * 0.5f;
        }
        return power;
    }

    private float calculateWinChance(float ourPower, float enemyPower) {
        float total = ourPower + enemyPower;
        if (total == 0f) return 50f;
        return MathHelper.clamp(ourPower / total * 100f, 5f, 95f);
    }

    private String formatDamage(float damage) {
        int whole = (int) damage;
        int decimal = Math.round((damage - whole) * 10f);
        if (decimal == 10) {
            whole++;
            decimal = 0;
        }
        return whole + "." + decimal;
    }

    private static class DamageNumber {
        final Vec3d startPos;
        final float damage;
        final long startTime;
        float progress = 0f;

        DamageNumber(Vec3d pos, float damage) {
            this.startPos = pos;
            this.damage = damage;
            this.startTime = System.currentTimeMillis();
        }

        void update() {
            long elapsed = System.currentTimeMillis() - startTime;
            progress = Math.min(1f, (float) elapsed / DAMAGE_NUMBER_DURATION_MS);
        }

        Vec3d getPosition() {
            double yOffset = progress * 1.5;
            return startPos.add(0, yOffset, 0);
        }

        float getAlpha() {
            return progress > 0.7f ? 1f - (progress - 0.7f) / 0.3f : 1f;
        }

        float getScale() {
            return progress < 0.2f ? 0.8f + progress / 0.2f * 0.4f : 1.2f;
        }

        boolean shouldRemove() {
            return System.currentTimeMillis() - startTime >= DAMAGE_NUMBER_DURATION_MS;
        }
    }
}
