package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.entity.ITrailEntity;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.*;
import dev.client.api.nullcry.render.ColorUtils;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;

public class Trails extends Module {
    public static Trails INSTANCE;

    public Trails() {
        super("Trails", ModuleCategory.Render, "Красивая линия за вами");
    }

    private final SelectElements filter = new SelectElements("Фильтр на", () -> true)
            .set("Игроков", "Мобов", "Животных", "Жителей", "Друзей", "Невидимых", "Голых", "Себя", "Предметы")
            .defaultValue("Игроков", "Голых", "Друзей", "Себя")
            .register(this);

    private final CheckBox showFirstPerson = new CheckBox("Отображать от первого лица", () -> true)
            .defaultValue(false)
            .register(this);

    private final Slider width = new Slider("Длина", () -> true)
            .set(1.0f, 10.0f, 1f)
            .defaultValue(5.0f)
            .register(this);

    private final ModeElement color = new ModeElement("Цвет", () -> true)
            .set("Клиент", "Свой")
            .defaultValue("Клиент")
            .register(this);

    private final ColorPicker customColor = new ColorPicker("Выбор цвета", () -> color.isSelected("Свой"))
            .set(-1)
            .defaultValue(-1)
            .register(this);

    final double minDistance = 0.01;

    private long getLifetimeMs() {
        return (long) (width.getValue().doubleValue() * 100.0);
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.world == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        long lifetimeMs = getLifetimeMs();

        for (Entity entity : mc.world.getEntities()) {
            if (!shouldRenderEntity(entity)) continue;
            List<Trail> trails = ((ITrailEntity) entity).lumora$getTrails();
            trails.removeIf(t -> (now - t.time) > lifetimeMs);
        }
    }

    @Subscribe
    public void onRender3D(RenderEvent.Draw3D renderEvent) {
        if (mc.world == null || mc.player == null) return;

        float tickDelta = renderEvent.getTickCounter().getTickProgress(true);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        long now = System.currentTimeMillis();

        for (Entity entity : mc.world.getEntities()) {
            if (!shouldRenderEntity(entity)) continue;

            Vec3d interp = interpolateEntityPosition(entity, tickDelta);
            List<Trail> trails = ((ITrailEntity) entity).lumora$getTrails();
            long lifetimeMs = getLifetimeMs();

            if (trails.isEmpty()) {
                trails.add(new Trail(interp, getTrailColor(entity), now, lifetimeMs));
            } else {
                Trail last = trails.get(trails.size() - 1);
                if (last.pos.distanceTo(interp) >= minDistance) {
                    trails.add(new Trail(interp, getTrailColor(entity), now, lifetimeMs));
                }
            }

            render(renderEvent, entity, cameraPos, now);
        }
    }

    private boolean shouldRenderEntity(Entity entity) {
        if (entity instanceof AbstractClientPlayerEntity) return shouldRenderPlayer((AbstractClientPlayerEntity) entity);
        if (entity instanceof MobEntity) return filter.isSelected("Мобов");
        if (entity instanceof AnimalEntity) return filter.isSelected("Животных");
        if (entity instanceof VillagerEntity) return filter.isSelected("Жителей");
        return entity instanceof ItemEntity && filter.isSelected("Предметы");
    }

    private boolean shouldRenderPlayer(AbstractClientPlayerEntity player) {
        if (player == mc.player && mc.options.getPerspective() == Perspective.FIRST_PERSON && !showFirstPerson.getEnabled()) return false;
        if (player == mc.player && !filter.isSelected("Себя")) return false;

        if (filter.isSelected("Друзей") && handlerClient().getFriendManager().isFriend(player.getName().getString())) return true;
        if (!filter.isSelected("Игроков")) return false;

        boolean hasArmor = false;
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (!slot.isArmorSlot()) continue;
            ItemStack armorPiece = player.getEquippedStack(slot);
            if (!armorPiece.isEmpty()) {
                hasArmor = true;
                break;
            }
        }

        boolean isInvisible = player.isInvisible();

        if (isInvisible && !filter.isSelected("Невидимых")) return false;
        if (!hasArmor && !filter.isSelected("Голых")) return false;

        if (isInvisible && !hasArmor) {
            return filter.isSelected("Невидимых") && filter.isSelected("Голых");
        }

        return true;
    }

    private Vec3d interpolateEntityPosition(Entity entity, float tickDelta) {
        double ix = entity.lastX + (entity.getX() - entity.lastX) * tickDelta;
        double iy = entity.lastY + (entity.getY() - entity.lastY) * tickDelta;
        double iz = entity.lastZ + (entity.getZ() - entity.lastZ) * tickDelta;
        return new Vec3d(ix, iy, iz);
    }

    private void render(RenderEvent.Draw3D event, Entity entity, Vec3d cameraPos, long now) {
        List<Trail> trails = ((ITrailEntity) entity).lumora$getTrails();
        if (trails.isEmpty()) return;

        float entityHeight = entity.getHeight();
        event.getMatrices().push();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        int added = 0;
        for (Trail trail : trails) {
            if (trail.isExpired(now)) continue;
            float ageFrac = (float) (now - trail.time) / (float) trail.lifetimeMs;
            float alpha = 1f - Math.min(1f, ageFrac);
            alpha = Math.max(0.01f, alpha);

            int color = ColorUtils.setAlpha(trail.color, (int) (alpha * 255));
            Vec3d posRel = trail.pos.subtract(cameraPos);

            buffer.vertex(event.getMatrices().peek().getPositionMatrix(), (float) posRel.x, (float) (posRel.y + entityHeight), (float) posRel.z).color(color);
            buffer.vertex(event.getMatrices().peek().getPositionMatrix(), (float) posRel.x, (float) posRel.y, (float) posRel.z).color(color);
            added += 2;
        }

        if (added > 0) {
            BuiltBuffer builtBuffer = buffer.endNullable();
            if (builtBuffer != null) {
                RenderCompat.draw(builtBuffer);
            }
        } else {
            buffer.endNullable();
        }

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        event.getMatrices().pop();
    }

    private int getTrailColor(Entity entity) {
        if (entity instanceof PlayerEntity player && handlerClient().getFriendManager().isFriend(player.getName().getString())) {
            return new Color(0, 255, 0).getRGB();
        }
        return ColorUtils.resolveModeColor(color, customColor, Interface.INSTANCE.getMainColor());
    }

    public static class Trail {
        public final Vec3d pos;
        public final int color;
        public final long time;
        private final long lifetimeMs;

        public Trail(Vec3d pos, int color, long time, long lifetimeMs) {
            this.pos = pos;
            this.color = color;
            this.time = time;
            this.lifetimeMs = lifetimeMs;
        }

        public boolean isExpired(long now) {
            return (now - time) > lifetimeMs;
        }
    }
}
