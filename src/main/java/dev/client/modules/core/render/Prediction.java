package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.projection.ProjectionUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.msdf.core.MsdfFont;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Prediction extends Module implements ClientApi {
    public static Prediction INSTANCE;

    private final List<Predicted> predicted = new ArrayList<>();
    private final List<Landed> landed = new ArrayList<>();

    private final SelectElements entities = new SelectElements("Сущности", () -> true)
            .set("Эндер-жемчуг", "Трезубцы", "Снежки", "Стрелы", "Зелья", "Предметы")
            .defaultValue("Эндер-жемчуг", "Трезубцы", "Снежки", "Стрелы", "Зелья")
            .register(this);

    private final ModeElement renderMode = new ModeElement("Режим рендера", () -> true)
            .set("Обычный", "Свечение")
            .applyDefault("Свечение")
            .register(this);

    private final CheckBox inHand = new CheckBox("В руке", () -> true).defaultValue(true).register(this);
    private final CheckBox walls = new CheckBox("Через стены", () -> true).defaultValue(true).register(this);
    private final CheckBox hud = new CheckBox("HUD информация", () -> true).defaultValue(false).register(this);

    public Prediction() {
        super("Prediction", ModuleCategory.Render, "Отображает траекторию полёта снарядов");
        INSTANCE = this;
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.world == null || mc.player == null) return;

        predicted.clear();
        landed.clear();

        List<ProjectileEntity> projectiles = new ArrayList<>();

        if (inHand.getEnabled()) {
            ItemStack handStack = mc.player.getMainHandStack();
            Entity inHandEntity = null;

            if (handStack.getItem() instanceof EnderPearlItem) {
                inHandEntity = new EnderPearlEntity(mc.world, mc.player, handStack);
            } else if (handStack.getItem() instanceof TridentItem && mc.player.isUsingItem()) {
                inHandEntity = new TridentEntity(mc.world, mc.player, handStack);
            } else if (handStack.getItem() instanceof BowItem && mc.player.isUsingItem()) {
                ItemStack arrowStack = new ItemStack(Items.ARROW);
                inHandEntity = new ArrowEntity(mc.world, mc.player, arrowStack, handStack);
            } else if (handStack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(handStack)) {
                boolean hasMultishot = false;
                try {
                    var enchantments = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(handStack);
                    for (var entry : enchantments.getEnchantmentEntries()) {
                        if (entry.getKey().matchesId(net.minecraft.enchantment.Enchantments.MULTISHOT.getValue())) {
                            hasMultishot = entry.getIntValue() > 0;
                            break;
                        }
                    }
                } catch (Exception ignored) {}

                ItemStack arrowStack = new ItemStack(Items.ARROW);
                if (hasMultishot) {
                    for (int i = 0; i < 3; i++) {
                        ArrowEntity arrow = new ArrowEntity(mc.world, mc.player, arrowStack, handStack);
                        projectiles.add(arrow);
                    }
                } else {
                    inHandEntity = new ArrowEntity(mc.world, mc.player, arrowStack, handStack);
                }
            }

            if (inHandEntity instanceof ProjectileEntity projectile) {
                float speed = 1.5F;
                if (inHandEntity instanceof TridentEntity) {
                    speed = 2.5F;
                } else if (inHandEntity instanceof ArrowEntity) {
                    speed = 3.0F;
                }

                setVelocity(projectile, mc.player, mc.player.getPitch(), mc.player.getYaw(), 0.0F, speed, 1.0F);
                predict(projectile, true);
            }
        }

        if (!projectiles.isEmpty()) {
            float speed = 3.15F;
            float spreadAngle = 10.0F;

            for (int i = 0; i < projectiles.size(); i++) {
                ProjectileEntity projectile = projectiles.get(i);
                float yawOffset = 0.0F;
                if (i == 0) {
                    yawOffset = -spreadAngle;
                } else if (i == 2) {
                    yawOffset = spreadAngle;
                }

                setVelocity(projectile, mc.player, mc.player.getPitch(), mc.player.getYaw() + yawOffset, 0.0F, speed, 1.0F);
                predict(projectile, true);
            }
        }

        for (Entity entity : mc.world.getEntities()) {
            predict(entity, false);
        }
    }

    @Subscribe
    public void onDraw3D(RenderEvent.Draw3D event) {
        if (mc.world == null || mc.player == null) return;

        float tickDelta = event.getTickCounter().getTickProgress(true);
        MatrixStack ms = event.getMatrices();
        Vec3d cameraPos = mc.getEntityRenderDispatcher().camera.getCameraPos();

        ms.push();
        ms.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        if (walls.getEnabled()) {
            RenderSystem.disableDepthTest();
        }

        int mainColor = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : 0xFFFFFFFF;

        if (renderMode.isSelected("Обычный")) {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder builder = tessellator.begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            Matrix4f matrix = ms.peek().getPositionMatrix();

            for (Predicted predict : predicted) {
                Vec3d prevPos = predict.vectors.getFirst();
                Vec3d entityPos = getInterpolatedPos(predict.entity, tickDelta);
                drawLine(builder, matrix, entityPos, prevPos, mainColor);

                for (Vec3d pos : predict.vectors) {
                    drawLine(builder, matrix, prevPos, pos, mainColor);
                    prevPos = pos;
                }
            }

            var built = builder.endNullable();
            if (built != null) RenderCompat.draw(built);
        } else {
            // Glow mode
            Identifier bloomId = Identifier.of("lynxwave", "textures/bloom.png");
            var bloomTexture = ClientTexture.of("textures/bloom.png");

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            Matrix4f matrix = ms.peek().getPositionMatrix();

            for (Predicted predict : predicted) {
                Vec3d prevPos = predict.vectors.getFirst();
                Vec3d entityPos = getInterpolatedPos(predict.entity, tickDelta);
                if (entityPos.distanceTo(mc.player.getEyePos()) > 2.0) {
                    for (int i = 0; i < 10; i++) {
                        float t = i / 10.0F;
                        Vec3d interpolatedPos = entityPos.add(prevPos.subtract(entityPos).multiply(t));
                        drawGlow(ms, interpolatedPos, buffer, (float) prevPos.distanceTo(entityPos) / 3.0F, 1.0F, mainColor);
                        drawGlow(ms, interpolatedPos, buffer, (float) prevPos.distanceTo(entityPos) * 2.0F, 0.05F, mainColor);
                    }
                }

                for (Vec3d pos : predict.vectors) {
                    if (pos.distanceTo(mc.player.getEyePos()) > 2.0) {
                        for (int i = 0; i < 10; i++) {
                            float t = i / 10.0F;
                            Vec3d interpolatedPos = prevPos.add(pos.subtract(prevPos).multiply(t));
                            drawGlow(ms, interpolatedPos, buffer, (float) pos.distanceTo(prevPos) / 3.0F, 1.0F, mainColor);
                            drawGlow(ms, interpolatedPos, buffer, (float) pos.distanceTo(prevPos) * 2.0F, 0.05F, mainColor);
                        }
                    }

                    prevPos = pos;
                }

                float potionSize = 9.0F;
                if (predict.entity instanceof PotionEntity) {
                    ms.push();
                    ms.translate(predict.vectors.getLast().x, predict.vectors.getLast().y, predict.vectors.getLast().z);
                    ms.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-90.0F));
                    drawImage(ms, buffer, (-potionSize / 2.0F), (-potionSize / 2.0F), 0.0F, potionSize, potionSize, mainColor);
                    ms.pop();
                }
            }

            var built = buffer.endNullable();
            if (built != null) RenderCompat.drawTextured(built, bloomTexture);
            RenderSystem.defaultBlendFunc();
        }

        // Landed hit markers
        {
            float hitSize = 1.0F;
            var hitTexture = ClientTexture.of("textures/hit.png");

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            for (Landed land : landed) {
                if (land.collidedEntity == null && land.hitResult != null) {
                    ms.push();
                    Vec3d hitPos = land.hitResult.getPos();
                    ms.translate(hitPos.x, hitPos.y, hitPos.z);
                    ms.multiply(land.hitResult.getSide().getRotationQuaternion());
                    ms.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-90.0F));
                    drawImage(ms, buffer, (-hitSize / 2.0F), (-hitSize / 2.0F), 0.0F, hitSize, hitSize, mainColor);
                    ms.pop();
                }
            }

            var built = buffer.endNullable();
            if (built != null) RenderCompat.drawTextured(built, hitTexture);
        }

        // Landed entity collision boxes
        {
            RenderSystem.enableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            Tessellator tessellator = Tessellator.getInstance();
            Matrix4f matrix = ms.peek().getPositionMatrix();

            BufferBuilder quadsBuffer = tessellator.begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Landed land : landed) {
                if (land.collidedEntity != null) {
                    renderFilledBox(quadsBuffer, matrix, land.collidedEntity.getBoundingBox(), ColorUtils.multAlpha(mainColor, 0.5f));
                }
            }
            var builtQuads = quadsBuffer.endNullable();
            if (builtQuads != null) RenderCompat.draw(builtQuads);

            BufferBuilder linesBuffer = tessellator.begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (Landed land : landed) {
                if (land.collidedEntity != null) {
                    renderOutlinedBox(linesBuffer, matrix, land.collidedEntity.getBoundingBox(), mainColor);
                }
            }
            var builtLines = linesBuffer.endNullable();
            if (builtLines != null) RenderCompat.draw(builtLines);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        ms.pop();
    }

    @Subscribe
    public void onDraw2D(RenderEvent.Draw2D event) {
        if (mc.world == null || mc.player == null) return;

        DrawContext context = event.getContext();
        var ms = context.getMatrices();
        MsdfFont font = Builder.INTER.get();
        float fontSize = 7.0F;
        int mainColor = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : 0xFFFFFFFF;

        for (Predicted predict : predicted) {
            Vec3d screenPos3d = ProjectionUtil.projectCoordinates(predict.vectors.getLast());
            if (screenPos3d.z < 0 || screenPos3d.z > 1) continue;

            float x = (float) screenPos3d.x;
            float y = (float) screenPos3d.y;

            String name = predict.entity.getName().getString()
                    .replace("Брошенный эндер-жемчуг", "Эндер-жемчуг");

            if (predict.entity instanceof PotionEntity potion) {
                name = potion.getStack().getFormattedName().getString();
            }

            name = name.replace("] ", "").replace("[", "")
                    + String.format(" (%s сек)", formatNumber(predict.ticks / 20.0F));

            ItemStack stack = switch (predict.entity) {
                case ThrownItemEntity item -> item.getStack();
                case PersistentProjectileEntity itemx -> itemx.getItemStack();
                case ItemEntity itemxx -> itemxx.getStack();
                default -> Items.ARROW.getDefaultStack();
            };

            float distance = (float) predict.vectors.getLast().distanceTo(mc.player.getEyePos());
            float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
            float height = fontSize + 6.0F;
            float yOff = -height;

            ms.pushMatrix();
            ms.translate(x, y);
            ms.scale(scale, scale);

            float firstWidth = font.getWidth(name, fontSize) + 20.0F;

            // Background rect
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(firstWidth, height))
                    .color(new QuadColorState(ColorUtils.rgba(0, 0, 0, 100)))
                    .radius(new QuadRadiusState(2))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), -firstWidth / 2.0F, yOff);

            // Item icon
            DrawUtil.drawItemStack(context, stack, -firstWidth / 2.0F, yOff, 1.0F);

            // Name text
            Builder.TEXT_BUILDER
                    .font(font)
                    .text(name)
                    .size(fontSize)
                    .color(0xFFFFFFFF)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), -firstWidth / 2.0F + 17.0F, yOff + 3.0F);

            yOff += height;

            // Owner info
            if (predict.entity instanceof ProjectileEntity projectile
                    && projectile.getOwner() instanceof AbstractClientPlayerEntity player) {
                String owner = "От " + (projectile.getOwner() == mc.player ? "Вас" : projectile.getOwner().getName().getString());
                float secondWidth = font.getWidth(owner, fontSize) + 22.0F;

                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(secondWidth, height))
                        .color(new QuadColorState(ColorUtils.rgba(0, 0, 0, 100)))
                        .radius(new QuadRadiusState(2))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), -secondWidth / 2.0F, yOff);

                // Player head
                DrawUtil.renderHeadTexture(
                        context,
                        -secondWidth / 2.0F, yOff,
                        height, height,
                        0,
                        0.125f, 0.125f,
                        0.125f, 0.125f,
                        player.getSkin().body().texturePath(),
                        Color.WHITE,
                        1.0f
                );

                Builder.TEXT_BUILDER
                        .font(font)
                        .text(owner)
                        .size(fontSize)
                        .color(0xFFFFFFFF)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), -secondWidth / 2.0F + 19.0F, yOff + 3.0F);

                yOff += height;
            }

            // Potion effects
            if (predict.entity instanceof PotionEntity potion) {
                PotionContentsComponent contents = potion.getStack().get(DataComponentTypes.POTION_CONTENTS);
                if (contents != null) {
                    for (StatusEffectInstance effect : contents.getEffects()) {
                        String potionName = ((StatusEffect) effect.getEffectType().value()).getName().getString();
                        int amplifier = effect.getAmplifier();
                        int duration = effect.getDuration();
                        String potionLevel = amplifier > 0 ? " " + (amplifier + 1) : "";
                        String potionTime = formatDuration(duration);
                        String fullPotionText = potionName + potionLevel + " (" + potionTime + ")";
                        float potionWidth = font.getWidth(fullPotionText, fontSize) + 6.0F;

                        Builder.RECTANGLE_BUILDER
                                .size(new SizeState(potionWidth, height))
                                .color(new QuadColorState(ColorUtils.rgba(0, 0, 0, 100)))
                                .radius(new QuadRadiusState(2))
                                .build()
                                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), -potionWidth / 2.0F, yOff + 5.0F);

                        int effectColor = ((StatusEffect) effect.getEffectType().value()).getColor();

                        Builder.TEXT_BUILDER
                                .font(font)
                                .text(fullPotionText)
                                .size(fontSize)
                                .color(effectColor | 0xFF000000)
                                .build()
                                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), -potionWidth / 2.0F + 3.0F, yOff + 8.0F);

                        yOff += height;
                    }
                }
            }

            ms.popMatrix();
        }

        // HUD warning
        if (hud.getEnabled()) {
            float hudFontSize = 6.0F;
            float yOff = 0.0F;

            for (Predicted predict : predicted) {
                if (predict.collidedEntity == mc.player && !(predict.entity instanceof EnderPearlEntity)) {
                    String name = predict.entity.getName().getString()
                            .replace("Брошенный эндер-жемчуг", "Эндер-жемчуг")
                            + String.format(" (%s сек)", formatNumber(predict.ticks / 20.0F));
                    String warning = "В вас летит " + name;
                    float textWidth = font.getWidth(warning, hudFontSize);

                    Builder.TEXT_BUILDER
                            .font(font)
                            .text(warning)
                            .size(hudFontSize)
                            .color(0xFFFFFFFF)
                            .build()
                            .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context),
                                    mc.getWindow().getScaledWidth() / 2.0F - textWidth / 2.0F,
                                    mc.getWindow().getScaledHeight() / 2.0F + 20.0F + yOff);

                    yOff += hudFontSize + 3.0F;
                }
            }
        }
    }

    // === Prediction logic ===

    private void predict(Entity entity, boolean fromHand) {
        if (!isValid(entity)) return;

        if (entity instanceof ProjectileEntity pearl && pearl.getOwner() == null) {
            List<AbstractClientPlayerEntity> sortedPlayers = new ArrayList<>(mc.world.getPlayers());
            if (!sortedPlayers.isEmpty()) {
                Collections.sort(sortedPlayers, Comparator.comparingDouble(player -> player.distanceTo(pearl)));
                pearl.setOwner(sortedPlayers.getFirst());
            }
        }

        List<Vec3d> positions = new ArrayList<>();
        Vec3d lastPos = dev.client.api.compat.EntityCompat.pos(entity);
        Vec3d lastMotion = entity.getVelocity();
        Entity collidedEntity = null;
        int ticks = 0;
        BlockHitResult blockHitResult = null;

        for (int i = 0; i < 150; i++) {
            Vec3d motion = predictMotion(entity, lastMotion);
            Vec3d pos = lastPos.add(motion);
            ticks = i;
            blockHitResult = mc.world.raycast(new RaycastContext(lastPos, pos, ShapeType.COLLIDER, FluidHandling.NONE, entity));
            Entity collided = checkEntityCollision(entity, pos);
            if (collided != null) {
                positions.add(pos);
                collidedEntity = collided;
                break;
            }

            if (blockHitResult.getType() != Type.MISS) {
                positions.add(blockHitResult.getPos());
                break;
            }

            positions.add(pos);
            lastPos = pos;
            lastMotion = motion;
        }

        if (!positions.isEmpty()) {
            if (fromHand) {
                landed.add(new Landed(entity, positions.getLast(), ticks, collidedEntity, blockHitResult));
            } else {
                predicted.add(new Predicted(entity, positions, ticks, collidedEntity));
            }
        }
    }

    private boolean isValid(Entity entity) {
        boolean valid = false;

        if (entities.isSelected("Эндер-жемчуг") && entity instanceof EnderPearlEntity) valid = true;
        if (entities.isSelected("Трезубцы") && entity instanceof TridentEntity) valid = true;
        if (entities.isSelected("Снежки") && entity instanceof SnowballEntity) valid = true;
        if (entities.isSelected("Стрелы") && entity instanceof ArrowEntity) valid = true;
        if (entities.isSelected("Зелья") && entity instanceof PotionEntity) valid = true;
        if (entities.isSelected("Предметы") && entity instanceof ItemEntity) valid = true;

        if (entity instanceof TridentEntity trident && trident.returnTimer > 0) return false;

        return valid && (Math.abs(entity.getVelocity().x + entity.getVelocity().z) > 0.01F
                || Math.abs(entity.getVelocity().y) > 0.2F);
    }

    private Entity checkEntityCollision(Entity movingEntity, Vec3d predictedPos) {
        Vec3d currentPos = dev.client.api.compat.EntityCompat.pos(movingEntity);
        Vec3d direction = predictedPos.subtract(currentPos);
        if (direction.lengthSquared() == 0.0) {
            return null;
        } else {
            EntityHitResult hitResult = ProjectileUtil.raycast(
                    movingEntity,
                    currentPos,
                    predictedPos,
                    movingEntity.getBoundingBox().stretch(direction).expand(0.5),
                    entity -> mc.player != entity
                            && entity.isAlive()
                            && !(entity instanceof ItemEntity)
                            && !(entity instanceof ExperienceOrbEntity)
                            && entity != movingEntity,
                    direction.lengthSquared()
            );
            return hitResult != null ? hitResult.getEntity() : null;
        }
    }

    // === Velocity ===

    private void setVelocity(ProjectileEntity entity, double x, double y, double z, float power) {
        Vec3d vec3d = new Vec3d(x, y, z).normalize().multiply(power);
        entity.setVelocity(vec3d);
        entity.velocityDirty = true;
        double d = vec3d.horizontalLength();
        entity.setYaw((float) (MathHelper.atan2(vec3d.x, vec3d.z) * 180.0F / (float) Math.PI));
        entity.setPitch((float) (MathHelper.atan2(vec3d.y, d) * 180.0F / (float) Math.PI));
        entity.lastYaw = entity.getYaw();
        entity.lastPitch = entity.getPitch();
    }

    private void setVelocity(ProjectileEntity entity, Entity shooter, float pitch, float yaw, float roll, float speed, float divergence) {
        float f = -MathHelper.sin(yaw * (float) (Math.PI / 180.0)) * MathHelper.cos(pitch * (float) (Math.PI / 180.0));
        float g = -MathHelper.sin((pitch + roll) * (float) (Math.PI / 180.0));
        float h = MathHelper.cos(yaw * (float) (Math.PI / 180.0)) * MathHelper.cos(pitch * (float) (Math.PI / 180.0));
        setVelocity(entity, f, g, h, speed);
        Vec3d vec3d = shooter.getMovement();
        entity.setVelocity(entity.getVelocity().add(vec3d.x, shooter.isOnGround() ? 0.0 : vec3d.y, vec3d.z));
    }

    private Vec3d predictMotion(Entity entity, Vec3d motion) {
        return motion.multiply(0.99).add(0.0, -entity.getFinalGravity(), 0.0);
    }

    // === Render helpers ===

    private Vec3d getInterpolatedPos(Entity entity, float tickDelta) {
        return new Vec3d(
                MathHelper.lerp(tickDelta, entity.lastX, entity.getX()),
                MathHelper.lerp(tickDelta, entity.lastY, entity.getY()),
                MathHelper.lerp(tickDelta, entity.lastZ, entity.getZ())
        );
    }

    private void drawLine(BufferBuilder builder, Matrix4f matrix, Vec3d start, Vec3d end, int color) {
        float r = ColorUtils.redf(color);
        float g = ColorUtils.greenf(color);
        float b = ColorUtils.bluef(color);
        float a = ColorUtils.alphaf(color);
        builder.vertex(matrix, (float) start.x, (float) start.y, (float) start.z).color(r, g, b, a);
        builder.vertex(matrix, (float) end.x, (float) end.y, (float) end.z).color(r, g, b, a);
    }

    private void drawGlow(MatrixStack ms, Vec3d pos, BufferBuilder buffer, float size, float alpha, int color) {
        ms.push();
        ms.translate(pos.x, pos.y, pos.z);
        ms.multiply(mc.gameRenderer.getCamera().getRotation());

        int glowColor = ColorUtils.setAlpha(color, (int) (255.0F * alpha));
        drawImage(ms, buffer, (-size / 2.0F), (-size / 2.0F), 0.0F, size, size, glowColor);
        ms.pop();
    }

    private void drawImage(MatrixStack ms, BufferBuilder buffer, float x, float y, float z, float width, float height, int color) {
        Matrix4f matrix = ms.peek().getPositionMatrix();
        float r = ColorUtils.redf(color);
        float g = ColorUtils.greenf(color);
        float b = ColorUtils.bluef(color);
        float a = ColorUtils.alphaf(color);

        buffer.vertex(matrix, x, y + height, z).texture(0, 1).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y + height, z).texture(1, 1).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y, z).texture(1, 0).color(r, g, b, a);
        buffer.vertex(matrix, x, y, z).texture(0, 0).color(r, g, b, a);
    }

    private void renderFilledBox(BufferBuilder builder, Matrix4f matrix, net.minecraft.util.math.Box box, int color) {
        float r = ColorUtils.redf(color);
        float g = ColorUtils.greenf(color);
        float b = ColorUtils.bluef(color);
        float a = ColorUtils.alphaf(color);

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // bottom
        builder.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        builder.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        builder.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        // top
        builder.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        builder.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        builder.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        builder.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        // north
        builder.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        builder.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        builder.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        // south
        builder.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        builder.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        builder.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        builder.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        // west
        builder.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        builder.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        builder.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        builder.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        // east
        builder.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        builder.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        builder.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        builder.vertex(matrix, x2, y1, z2).color(r, g, b, a);
    }

    private void renderOutlinedBox(BufferBuilder builder, Matrix4f matrix, net.minecraft.util.math.Box box, int color) {
        float r = ColorUtils.redf(color);
        float g = ColorUtils.greenf(color);
        float b = ColorUtils.bluef(color);
        float a = ColorUtils.alphaf(color);

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // bottom edges
        line(builder, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(builder, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(builder, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(builder, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // vertical edges
        line(builder, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
        line(builder, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(builder, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(builder, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        // top edges
        line(builder, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(builder, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(builder, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(builder, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);
    }

    private void line(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        builder.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix, x2, y2, z2).color(r, g, b, a);
    }

    // === Formatting ===

    private String formatDuration(int ticks) {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return minutes > 0 ? String.format("%d:%02d", minutes, remainingSeconds) : String.format("0:%02d", remainingSeconds);
    }

    private String formatNumber(float value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        }
        return String.format("%.1f", value);
    }

    // === Records ===

    record Landed(Entity entity, Vec3d pos, int ticks, Entity collidedEntity, BlockHitResult hitResult) {
    }

    record Predicted(Entity entity, List<Vec3d> vectors, int ticks, Entity collidedEntity) {
    }
}
