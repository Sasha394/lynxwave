package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.helper.client.projection.ProjectionUtil;
import dev.client.api.nullcry.helper.math.MathVector;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ColorUtilsExcellent;
import dev.client.api.nullcry.render.core.Draw3DUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4d;

public class EntityESP extends Module {
    public static EntityESP INSTANCE;

    public EntityESP() {
        super("EntityESP", ModuleCategory.Render, "Отображает красивый бокс на цели");
    }

    SelectElements filter = new SelectElements("Фильтр на", () -> true).set("Игроков", "Мобов", "Животных", "Жителей", "Друзей", "Невидимых", "Голых", "Себя", "Предметы").defaultValue("Игроков", "Голых", "Друзей", "Себя").register(this);
    SelectElements elements = new SelectElements("Отображать", () -> true).set("Боксы","Полоску хп").defaultValue("Боксы", "Полоску хп").register(this);
    ModeElement boxMode = new ModeElement("Режим боксов",() -> elements.isSelected("Боксы")).set("Бокс","Углы").defaultValue("Бокс").register(this);
    CheckBox box3D = new CheckBox("3D Бокс", () -> true).defaultValue(false).register(this);
    ModeElement elementsColor = new ModeElement("Цвет полоски",() -> elements.isSelected("Полоску хп")).set("Клиентский","Состояние здоровья").defaultValue("Клиентский").register(this);
    ModeElement color3DBox = new ModeElement("Цвет 3D бокса", () -> box3D.getEnabled()).set("Клиент", "Свой").defaultValue("Клиент").register(this);
    ColorPicker customColor3DBox = new ColorPicker("Выбор цвета", () -> color3DBox.isSelected("Свой")).set(-1).defaultValue(-1).register(this);

    @Subscribe
    public void onDraw3D(RenderEvent.Draw3D event) {
        if (mc.world == null || mc.player == null) return;

        if (box3D.getEnabled()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == null || player.isRemoved() || player.isSpectator()) continue;
                if (player == mc.player && mc.options.getPerspective().isFirstPerson()) continue;

                Box box = getInterpolatedBox(player);
                if (!ProjectionUtil.canSee(box)) continue;

                float hurtProgress = MathHelper.clamp((float) Math.sin(player.hurtTime * (18F * Math.PI / 180F)), 0.0f, 1.0f);
                boolean isFriend = handlerClient().getFriendManager().isFriend(player.getName().getString());

                int themeColor = ColorUtils.resolveModeColor(color3DBox, customColor3DBox, Interface.INSTANCE.getMainColor());

                int friendColor = ColorUtilsExcellent.GREEN;
                int baseColor = isFriend ? friendColor : themeColor;
                int finalColor = ColorUtilsExcellent.overCol(baseColor, ColorUtilsExcellent.RED, hurtProgress);

                Draw3DUtil.drawBox(box, finalColor, 1.5f, true, false, false);
            }
        }
    }

    @Subscribe
    public void draw2D(RenderEvent.Draw2D renderEvent) {
        DrawContext context = renderEvent.getContext();

        if (!elements.isSelected("Боксы")) return;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        mc.world.getEntities().forEach(entity -> {
            if (entity == mc.player && mc.options.getPerspective().isFirstPerson()) return;
            if (!shouldRenderEntity(entity)) return;

            Vec3d projected = ProjectionUtil.projectCoordinates(MathVector.lerpPosition(entity).add(0, entity.getHeight() + .5f, 0));

            if (projected.z <= 0 || projected.z >= 1) return;

            drawBox(entity, context);
        });

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public void drawBox(Entity entity, DrawContext context) {
        Vec3d[] vectors = getVectors(entity);

        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();

        Vector4d position = null;
        int visible = 0;

        for (Vec3d v : vectors) {
            Vec3d p = ProjectionUtil.projectCoordinates(new Vec3d(v.x, v.y, v.z));
            boolean inDepth = p.z > 0 && p.z < 1;
            boolean onScreen = p.x >= 0 && p.x <= sw && p.y >= 0 && p.y <= sh;

            if (inDepth && onScreen) {
                if (position == null) position = new Vector4d(p.x, p.y, p.x, p.y);
                position.x = Math.min(p.x, position.x);
                position.y = Math.min(p.y, position.y);
                position.z = Math.max(p.x, position.z);
                position.w = Math.max(p.y, position.w);
                visible++;
            }
        }

        if (position == null || visible < 2) return;

        double posX = position.x;
        double posY = position.y;
        double endPosX = position.z;
        double endPosY = position.w;

        if (boxMode.isSelected("Углы")) {
            drawCornerBox(context, posX, posY, endPosX, endPosY, Interface.INSTANCE.getMainColor());
        } else {
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            setRectPoints(builder, dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), (float) (posX - 0.5f), (float) posY, (float) (posX + 0.5 - 0.5), (float) endPosY, Interface.INSTANCE.getMainColor());
            setRectPoints(builder, dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), (float) posX, (float) (endPosY - 0.5f), (float) endPosX, (float) endPosY, Interface.INSTANCE.getMainColor());
            setRectPoints(builder, dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), (float) (posX - 0.5), (float) posY, (float) endPosX, (float) (posY + 0.5), Interface.INSTANCE.getMainColor());
            setRectPoints(builder, dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), (float) (endPosX - 0.5), (float) posY, (float) endPosX, (float) endPosY, Interface.INSTANCE.getMainColor());
            RenderCompat.draw(builder.end());
        }

        if (entity instanceof LivingEntity living && elements.isSelected("Полоску хп")) {
            renderHealthBar(context, living, posX, posY, endPosY);
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
        if (player == mc.player && mc.options.getPerspective().isFirstPerson()) return false;
        if (player == mc.player && !filter.isSelected("Себя")) return false;

        if (filter.isSelected("Друзей") && handlerClient().getFriendManager().isFriend(player.getName().getString())) return true;
        if (!filter.isSelected("Игроков")) return false;

        boolean hasArmor = false;
        for (ItemStack armorPiece : dev.client.api.compat.InventoryCompat.armorItems(player)) {
            if (!armorPiece.isEmpty()) { hasArmor = true; break; }
        }

        boolean isInvisible = player.isInvisible();

        if (isInvisible && !filter.isSelected("Невидимых")) return false;
        if (!hasArmor && !filter.isSelected("Голых")) return false;

        if (isInvisible && !hasArmor) {
            return filter.isSelected("Невидимых") && filter.isSelected("Голых");
        }

        return true;
    }

    private void renderHealthBar(DrawContext context, LivingEntity entity, double posX, double posY, double endPosY) {
        float gap = 2.0f;
        float barWidth = 2f;
        float x = (float) (posX - gap - barWidth);
        float yTop = (float) posY;
        float yBottom = (float) endPosY;
        float height = yBottom - yTop;

        if (height <= 0) return;

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(barWidth, height))
                .color(new QuadColorState(ColorUtils.rgba(0, 0, 0, 150)))
                .radius(new QuadRadiusState(1))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, yTop);

        float hp = Math.max(0f, entity.getHealth());
        float maxHp = Math.max(1f, entity.getMaxHealth());
        float pct = Math.max(0f, Math.min(1f, hp / maxHp));

        float fillHeight = height * pct;
        float fillY = yBottom - fillHeight;

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(barWidth, fillHeight))
                .color(new QuadColorState(getHealthColorEntity(entity)))
                .radius(new QuadRadiusState(1))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, fillY);
    }

    private static Vec3d[] getVectors(Entity ent) {
        Vec3d lerp = MathVector.lerpPosition(ent);

        Box axisAlignedBB2 = ent.getBoundingBox();
        Box axisAlignedBB = new Box(axisAlignedBB2.minX - ent.getX() + lerp.x - 0.05, axisAlignedBB2.minY - ent.getY() + lerp.y, axisAlignedBB2.minZ - ent.getZ() + lerp.z - 0.05, axisAlignedBB2.maxX - ent.getX() + lerp.x + 0.05, axisAlignedBB2.maxY - ent.getY() + lerp.y + 0.15, axisAlignedBB2.maxZ - ent.getZ() + lerp.z + 0.05);
        return new Vec3d[]{new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)};
    }

    public static void setRectPoints(BufferBuilder bufferBuilder, Matrix4f matrix, float x, float y, float x1, float y1, int color) {
        bufferBuilder.vertex(matrix, x, y1, 0.0F).color(color);
        bufferBuilder.vertex(matrix, x1, y1, 0.0F).color(color);
        bufferBuilder.vertex(matrix, x1, y, 0.0F).color(color);
        bufferBuilder.vertex(matrix, x, y, 0.0F).color(color);
    }

    private void drawCornerBox(DrawContext context, double posX, double posY, double endPosX, double endPosY, int color) {
        float t = 0.5f;
        double w = endPosX - posX;
        double h = endPosY - posY;
        float cx = (float) (w * 0.22);
        float cy = (float) (h * 0.18);

        Matrix4f m = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
        BufferBuilder builder = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float x0 = (float) posX;
        float y0 = (float) posY;
        float x1 = (float) endPosX;
        float y1 = (float) endPosY;

        // └
        setRectPoints(builder, m, x0 - 0.5f, y0, x0 + cx, y0 + t, color);
        setRectPoints(builder, m, x0 - 0.5f, y0, x0 + t - 0.5f, y0 + cy, color);

        // ┘
        setRectPoints(builder, m, x1 - cx, y0, x1 + 0.5f, y0 + t, color);
        setRectPoints(builder, m, x1 - t + 0.5f, y0, x1 + 0.5f, y0 + cy, color);

        // ┌
        setRectPoints(builder, m, x0 - 0.5f, y1 - t, x0 + cx, y1, color);
        setRectPoints(builder, m, x0 - 0.5f, y1 - cy, x0 + t - 0.5f, y1, color);

        // ┐
        setRectPoints(builder, m, x1 - cx, y1 - t, x1 + 0.5f, y1, color);
        setRectPoints(builder, m, x1 - t + 0.5f, y1 - cy, x1 + 0.5f, y1, color);

        RenderCompat.draw(builder.end());
    }

    private Box getInterpolatedBox(PlayerEntity player) {
        Vec3d interpolated = MathVector.lerpPosition(player);
        Vec3d delta = interpolated.subtract(dev.client.api.compat.EntityCompat.pos(player));
        return player.getBoundingBox().offset(delta);
    }

    private int getHealthColorEntity(LivingEntity entity) {
        if (elementsColor.isSelected("Состояние здоровья")) {
            return PlayerHelper.getHealthColorHSV(entity);
        } else if (elementsColor.isSelected("Клиентский")) {
            return Interface.INSTANCE.getMainColor(4);
        } else {
            return ColorUtils.rgb(255, 255, 255);
        }
    }
}
