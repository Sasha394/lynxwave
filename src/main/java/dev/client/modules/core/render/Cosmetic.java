package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.opengl.GlStateManager;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.math.MathVector;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.Input;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.animations.nova.extended.Animation;
import dev.client.api.nullcry.render.core.animations.nova.extended.Direction;
import dev.client.api.nullcry.render.core.animations.nova.extended.core.EaseBackIn;
import dev.client.modules.core.render.littlepet.GhostWolfEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.Color;
import java.util.Random;

public class Cosmetic extends Module {
    public static Cosmetic INSTANCE;

    private static final String C_WINGS = "Крылья";
    private static final String C_HAT = "Шляпа";
    private static final String C_PET = "Питомец";
    private static final String C_PENIS = "Пенис";
    private static final String C_MODELS = "Модельки";

    public static final String MODEL_RABBIT = "Crazy Rabbit";
    public static final String MODEL_FREDDY = "Freddy Bear";
    public static final String MODEL_TUNG = "Тунг Тунг Сахур";
    public static final String MODEL_INK_DEMON = "Ink Demon";

    public final SelectElements cosmetics = new SelectElements("Косметика", () -> true)
            .set(C_WINGS, C_HAT, C_PET, C_PENIS, C_MODELS)
            .register(this);

    private static final String WINGS_TYPE_1 = "Тип 1";
    private static final String WINGS_TYPE_2 = "Тип 2";
    private static final String WINGS_TYPE_3 = "Тип 3";
    private static final String WINGS_TYPE_4 = "Тип 4";

    public final ModeElement wingsMode = new ModeElement("Тип крыльев", () -> cosmetics.isSelected(C_WINGS))
            .set(WINGS_TYPE_1, WINGS_TYPE_2, WINGS_TYPE_3, WINGS_TYPE_4)
            .defaultValue(WINGS_TYPE_1)
            .register(this);

    public final CheckBox wingsOnPlayers = new CheckBox("Крылья на игроках", () -> cosmetics.isSelected(C_WINGS) && wingsMode.isSelected(WINGS_TYPE_4))
            .defaultValue(false).register(this);
    public final dev.client.api.nullcry.modules.settings.Slider wingsSize = new dev.client.api.nullcry.modules.settings.Slider("Размер крыльев", () -> cosmetics.isSelected(C_WINGS) && wingsMode.isSelected(WINGS_TYPE_4))
            .set(0.75f, 1.35f, 0.05f).defaultValue(1.0f).register(this);

    private float wingsSelfBodyYaw;
    private boolean wingsSelfBodyYawInitialized;

    private static final float WINGS4_DEFAULT_SPREAD = 8.0f;
    private static final int WINGS4_DEFAULT_ALPHA = 220;

    private static final float[][] WINGS4_SHAPE = {
            { 0.08f,  0.10f,  0.88f}, { 0.28f,  0.34f,  0.78f}, { 0.56f,  0.82f,  0.62f},
            { 0.86f,  0.30f,  0.52f}, { 1.14f,  0.46f,  0.40f}, { 1.24f,  0.04f,  0.30f},
            { 1.02f, -0.18f,  0.28f}, { 1.18f, -0.64f,  0.22f}, { 0.86f, -0.46f,  0.20f},
            { 0.80f, -0.98f,  0.14f}, { 0.54f, -0.74f,  0.16f}, { 0.30f, -1.16f,  0.12f},
            { 0.10f, -0.54f,  0.18f}
    };
    private static final int[] WINGS4_RIB_INDICES = {2, 4, 7, 9, 11};

    public static final Animation animationWings = new EaseBackIn(550, 0.5, 0.001F);

    public final CheckBox hatFriend = new CheckBox("Шляпа у друзей", () -> cosmetics.isSelected(C_HAT)).defaultValue(true).register(this);
    public final CheckBox hatFollowHead = new CheckBox("Шляпа за головой", () -> cosmetics.isSelected(C_HAT)).defaultValue(true).register(this);
    public final ModeElement hatColor = new ModeElement("Цвет шляпы", () -> cosmetics.isSelected(C_HAT)).set("Клиент", "Свой").defaultValue("Клиент").register(this);
    public final ColorPicker hatCustomColor = new ColorPicker("Цвет шляпы (свой)", () -> cosmetics.isSelected(C_HAT) && hatColor.isSelected("Свой")).set(-1).register(this);

    private static final int FRIEND_FILL = (128 << 24) | (Color.GREEN.getRGB() & 0xFFFFFF);
    private static final int FRIEND_LINE = 0xFF00FF00;

    public final CheckBox petFollowTarget = new CheckBox("Питомец к цели при атаке", () -> cosmetics.isSelected(C_PET)).defaultValue(false).register(this);
    public final CheckBox petShowName = new CheckBox("Дать имя питомцу", () -> cosmetics.isSelected(C_PET)).defaultValue(false).register(this);
    public final Input petNameInput = new Input("Имя питомца", () -> cosmetics.isSelected(C_PET) && petShowName.getEnabled()).defaultValue("").register(this);

    public final ModeElement customModel = new ModeElement("Моделька", () -> cosmetics.isSelected(C_MODELS))
            .set(MODEL_RABBIT, MODEL_FREDDY, MODEL_TUNG, MODEL_INK_DEMON)
            .defaultValue(MODEL_RABBIT)
            .register(this);
    public final CheckBox customModelFriends = new CheckBox("Модельки у друзей", () -> cosmetics.isSelected(C_MODELS))
            .defaultValue(true)
            .register(this);

    private LivingEntity fakeEntity;
    private World lastWorld;
    private int ticksAway;
    private int stuckTicks;
    private LivingEntity currentTarget;

    public final ModeElement penisColorMode = new ModeElement("Цвет пениса", () -> cosmetics.isSelected(C_PENIS))
            .set("Клиент", "Свой", "Радуга", "Случайный")
            .defaultValue("Клиент")
            .register(this);
    public final ColorPicker penisCustomColor = new ColorPicker("Цвет пениса (свой)", () -> cosmetics.isSelected(C_PENIS) && penisColorMode.isSelected("Свой"))
            .set(ColorUtils.rgb(100, 200, 255))
            .register(this);

    private final Random random = new Random();

    public Cosmetic() {
        super("Cosmetic", ModuleCategory.Render, "Косметика: крылья, шляпа, питомец, пенис, модельки");
        INSTANCE = this;
    }

    public boolean isWingsActive() {
        return isEnabled() && cosmetics.isSelected(C_WINGS) && !wingsMode.isSelected(WINGS_TYPE_4);
    }

    public boolean isWingsType4() {
        return isEnabled() && cosmetics.isSelected(C_WINGS) && wingsMode.isSelected(WINGS_TYPE_4);
    }

    public boolean isCustomModelActive() {
        return isEnabled() && cosmetics.isSelected(C_MODELS);
    }

    public String getCustomModelName() {
        return customModel.getValue();
    }

    public boolean shouldApplyCustomModelTo(PlayerLikeEntity player) {
        if (!isCustomModelActive() || player == null || player.isSpectator()) {
            return false;
        }

        boolean localPlayer = mc != null && mc.player != null && player == mc.player;
        if (localPlayer) {
            return true;
        }

        return customModelFriends.getEnabled()
                && handlerClient().getFriendManager().isFriend(player.getName().getString());
    }

    public Identifier getWingsTexture() {
        String key;
        if (wingsMode.isSelected(WINGS_TYPE_2)) key = "type2";
        else if (wingsMode.isSelected(WINGS_TYPE_3)) key = "type3";
        else key = "type1";
        return Identifier.of("lynxwave", "images/wings/" + key + ".png");
    }

    public boolean hatFollowHeadEnabled() {
        return hatFollowHead.getEnabled();
    }


    @Subscribe
    public void onTick(TickEvent event) {
        if (cosmetics.isSelected(C_WINGS)) {
            if (animationWings.finished(Direction.FORWARDS)) {
                animationWings.setDirection(Direction.BACKWARDS);
            } else if (animationWings.finished(Direction.BACKWARDS)) {
                animationWings.setDirection(Direction.FORWARDS);
            }
        }

        if (cosmetics.isSelected(C_PET)) {
            tickPet();
        } else if (fakeEntity != null) {
            removePet();
        }
    }

    @Subscribe
    public void onPlayerAttack(PlayerAttackEvent event) {
        if (!cosmetics.isSelected(C_PET) || !petFollowTarget.getEnabled()) return;
        if (event.getEntity() instanceof LivingEntity living && living != mc.player) {
            currentTarget = living;
        }
    }


    @Subscribe
    public void onRender3D(RenderEvent.Draw3D event) {
        if (cosmetics.isSelected(C_HAT)) {
            renderHat(event, mc.player, false);
            if (hatFriend.getEnabled() && mc.world != null) {
                for (PlayerEntity player : mc.world.getPlayers()) {
                    if (player != mc.player && handlerClient().getFriendManager().isFriend(player.getName().getString())) {
                        renderHat(event, player, true);
                    }
                }
            }
        }

        if (cosmetics.isSelected(C_PENIS)) {
            renderPenisEsp(event);
        }

        if (isWingsType4()) {
            renderWingsType4(event);
        }
    }

    private void renderWingsType4(RenderEvent.Draw3D event) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;

        MatrixStack stack = event.getMatrices();
        float tickDelta = (float) MathUtil.getTickDelta();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        stack.push();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        if (!mc.options.getPerspective().isFirstPerson()
                && mc.player.isAlive()
                && !hasElytraEquipped(mc.player)) {
            try { renderWings4(stack, mc.player, tickDelta, camera, false); } catch (Exception ignored) {}
        }

        // Друзья — зелёные крылья (вне зависимости от wingsOnPlayers)
        if (mc.world != null) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (!player.isAlive() || hasElytraEquipped(player)) continue;
                boolean isFriend = handlerClient().getFriendManager().isFriend(player.getName().getString());
                if (!isFriend && !wingsOnPlayers.getEnabled()) continue;
                try { renderWings4(stack, player, tickDelta, camera, isFriend); } catch (Exception ignored) {}
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(
                RenderSystem.SRC_ALPHA, RenderSystem.ONE_MINUS_SRC_ALPHA,
                RenderSystem.ONE, RenderSystem.ZERO);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        stack.pop();
    }

    private boolean hasElytraEquipped(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void renderWings4(MatrixStack stack, PlayerEntity player, float tickDelta, Vec3d camera, boolean isFriend) {
        double x = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()) - camera.z;

        float bodyYaw = resolveWingsBodyYaw(player, tickDelta);
        float move = MathHelper.clamp(player.limbAnimator.getSpeed(), 0f, 1f);

        Wings4Pose pose = resolveWings4Pose(player, tickDelta);
        if (pose == null) return;

        float flap = (float) Math.sin((player.age + tickDelta) * pose.flapSpeed) * pose.flapAmplitude;
        float open = (WINGS4_DEFAULT_SPREAD + flap + move * pose.motionSpreadBoost) * pose.openMultiplier;
        float wingScale = wingsSize.getValue() * pose.scaleMultiplier;

        int baseColor = isFriend ? (0xFF000000 | Color.GREEN.getRGB() & 0xFFFFFF)
                                 : (Interface.INSTANCE.getMainColor() | 0xFF000000);
        int glowColor = blendColor(baseColor, 0xFFFFFFFF, 0.28f);
        int coreColor = blendColor(baseColor, 0xFFFFFFFF, 0.55f);
        int outlineColor = baseColor;

        stack.push();
        stack.translate(x, y, z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        if (pose.preTranslateY != 0f || pose.preTranslateZ != 0f)
            stack.translate(0f, pose.preTranslateY, pose.preTranslateZ);
        if (pose.pitchRotation != 0f)
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.pitchRotation));
        if (pose.rollRotation != 0f)
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.rollRotation));
        stack.translate(0f, pose.anchorY, pose.anchorZ);
        stack.scale(wingScale, wingScale, wingScale);

        renderWings4Side(stack, -1f, open, baseColor, glowColor, coreColor, outlineColor, pose);
        renderWings4Side(stack,  1f, open, baseColor, glowColor, coreColor, outlineColor, pose);
        stack.pop();
    }

    private void renderWings4Side(MatrixStack stack, float side, float open,
                                  int baseColor, int glowColor, int coreColor, int outlineColor,
                                  Wings4Pose pose) {
        stack.push();
        stack.translate(side * pose.sideOffset, pose.sideYOffset, pose.sideZOffset);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * open));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * pose.sideRoll));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.sidePitch));

        drawWings4Layer(stack, side, 1.22f, withAlpha(glowColor, (int)(WINGS4_DEFAULT_ALPHA * 0.22f)), withAlpha(glowColor, 0), true);
        drawWings4Layer(stack, side, 0.84f, withAlpha(coreColor, (int)(WINGS4_DEFAULT_ALPHA * 0.26f)), withAlpha(coreColor, 0), true);

        drawWings4Layer(stack, side, 1.0f, withAlpha(baseColor, WINGS4_DEFAULT_ALPHA), withAlpha(baseColor, 10), false);

        drawWings4Outline(stack, side, 1.0f, withAlpha(outlineColor, (int)(WINGS4_DEFAULT_ALPHA * 0.62f)), true);
        drawWings4Ribs(stack, side, 0.96f, withAlpha(glowColor, (int)(WINGS4_DEFAULT_ALPHA * 0.20f)), true);

        stack.pop();
    }

    private void drawWings4Layer(MatrixStack stack, float side, float scale, int rootColor, int edgeColor, boolean additive) {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < WINGS4_SHAPE.length; i++) {
            float[] cur  = WINGS4_SHAPE[i];
            float[] next = WINGS4_SHAPE[(i + 1) % WINGS4_SHAPE.length];
            wings4Vertex(buffer, matrix, 0f, 0f, 0f, rootColor);
            wings4Vertex(buffer, matrix, side * cur[0]  * scale, cur[1]  * scale, 0f, applyAlphaMul(edgeColor, cur[2]));
            wings4Vertex(buffer, matrix, side * next[0] * scale, next[1] * scale, 0f, applyAlphaMul(edgeColor, next[2]));
        }
        if (additive) {
            RenderCompat.drawPositionColorAdditiveNoDepthWrite(buffer.end());
        } else {
            RenderCompat.drawPositionColorNoDepthWrite(buffer.end());
        }
    }

    private void drawWings4Outline(MatrixStack stack, float side, float scale, int color, boolean additive) {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        RenderSystem.lineWidth(1.0f);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (float[] point : WINGS4_SHAPE)
            wings4Vertex(buffer, matrix, side * point[0] * scale, point[1] * scale, 0f, color);
        wings4Vertex(buffer, matrix, side * WINGS4_SHAPE[0][0] * scale, WINGS4_SHAPE[0][1] * scale, 0f, color);
        if (additive) {
            RenderCompat.drawPositionColorAdditiveNoDepthWrite(buffer.end());
        } else {
            RenderCompat.drawPositionColorNoDepthWrite(buffer.end());
        }
    }

    private void drawWings4Ribs(MatrixStack stack, float side, float scale, int color, boolean additive) {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        RenderSystem.lineWidth(1.0f);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (int idx : WINGS4_RIB_INDICES) {
            float[] point = WINGS4_SHAPE[idx];
            wings4Vertex(buffer, matrix, 0f, 0f, 0f, withAlpha(color, Math.max(8, (int)(((color >> 24) & 0xFF) * 0.75f))));
            wings4Vertex(buffer, matrix, side * point[0] * scale, point[1] * scale, 0f, applyAlphaMul(color, point[2]));
        }
        if (additive) {
            RenderCompat.drawPositionColorAdditiveNoDepthWrite(buffer.end());
        } else {
            RenderCompat.drawPositionColorNoDepthWrite(buffer.end());
        }
    }

    private static int withAlpha(int color, int a) {
        return (MathHelper.clamp(a, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int applyAlphaMul(int color, float mul) {
        int a = (color >> 24) & 0xFF;
        return withAlpha(color, Math.max(0, Math.min(255, (int)(a * mul))));
    }

    private static int blendColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        int ra = Math.round(aa + (ba - aa) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private void wings4Vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f;
        buffer.vertex(matrix, x, y, z).color(r, g, b, a);
    }

    private float resolveWingsBodyYaw(PlayerEntity player, float tickDelta) {
        float target = MathHelper.lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);
        if (player != mc.player) return target;
        if (!wingsSelfBodyYawInitialized || player.age < 2) {
            wingsSelfBodyYaw = target;
            wingsSelfBodyYawInitialized = true;
            return wingsSelfBodyYaw;
        }
        float delta = MathHelper.wrapDegrees(target - wingsSelfBodyYaw);
        delta = MathHelper.clamp(delta, -14f, 14f);
        wingsSelfBodyYaw += delta;
        return wingsSelfBodyYaw;
    }

    private Wings4Pose resolveWings4Pose(PlayerEntity player, float tickDelta) {
        float pitch = MathHelper.lerp(tickDelta, player.lastPitch, player.getPitch());

        if (player.isGliding()) {
            float flightTicks = (float) player.getGlidingTicks() + tickDelta;
            float flightProgress = MathHelper.clamp(flightTicks * flightTicks / 100f, 0f, 1f);
            float pitchRotation = flightProgress * (-90f - pitch);
            return new Wings4Pose(0.34f, 0.46f, 0f, 0f, pitchRotation, 0f,
                    0.76f, 0.92f, 0.10f, 0.58f, 0.05f, 0f, 0.06f, -5f, -2f, 0.13f);
        }

        if (player.isTouchingWater()) return null;

        if (player.isSneaking()) {
            return new Wings4Pose(0f, 0f, 0.96f, 0.10f, 18f, 0f,
                    1f, 1f, 0.18f, 4.5f, 0.06f, 0f, 0.02f, -11f, -4f, 0.12f);
        }

        return new Wings4Pose(0f, 0f, 1.38f, 0.10f, 0f, 0f,
                1f, 1f, 0.18f, 4.5f, 0.06f, 0f, 0.02f, -11f, -4f, 0.12f);
    }

    private static final class Wings4Pose {
        final float preTranslateY, preTranslateZ;
        final float anchorY, anchorZ;
        final float pitchRotation, rollRotation;
        final float openMultiplier, scaleMultiplier;
        final float motionSpreadBoost, flapAmplitude;
        final float sideOffset, sideYOffset, sideZOffset;
        final float sideRoll, sidePitch, flapSpeed;

        Wings4Pose(float preTranslateY, float preTranslateZ, float anchorY, float anchorZ,
                   float pitchRotation, float rollRotation, float openMultiplier, float scaleMultiplier,
                   float motionSpreadBoost, float flapAmplitude, float sideOffset, float sideYOffset,
                   float sideZOffset, float sideRoll, float sidePitch, float flapSpeed) {
            this.preTranslateY = preTranslateY;
            this.preTranslateZ = preTranslateZ;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.pitchRotation = pitchRotation;
            this.rollRotation = rollRotation;
            this.openMultiplier = openMultiplier;
            this.scaleMultiplier = scaleMultiplier;
            this.motionSpreadBoost = motionSpreadBoost;
            this.flapAmplitude = flapAmplitude;
            this.sideOffset = sideOffset;
            this.sideYOffset = sideYOffset;
            this.sideZOffset = sideZOffset;
            this.sideRoll = sideRoll;
            this.sidePitch = sidePitch;
            this.flapSpeed = flapSpeed;
        }
    }


    private void renderHat(RenderEvent.Draw3D event, PlayerEntity player, boolean isFriend) {
        if (player == null) return;
        if (mc.options.getPerspective().isFirstPerson() && player == mc.player) return;

        MatrixStack matrices = hatStack(event, player);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        renderDefaultHat(matrices, isFriend);

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.lineWidth(1f);
        matrices.pop();
    }

    private void renderDefaultHat(MatrixStack matrices, boolean isFriend) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int baseFill = resolveHatBaseColor(isFriend);
        int baseLine = resolveHatBaseColor(isFriend) | 0xFF000000;

        int tipFill;
        if (isFriend) tipFill = meltedColor(baseFill, 0f, 128);
        else if (hatColor.isSelected("Свой")) tipFill = hatCustomColor.getColorRGBA();
        else tipFill = meltedColor(baseFill, 0f, 128);
        buffer.vertex(matrix, 0, 0.3f, 0).color(tipFill);

        for (int i = 0; i <= 360; i += 10) {
            int hatCol;
            if (isFriend) hatCol = meltedColor(baseFill, i / 360f, 128);
            else if (hatColor.isSelected("Свой")) hatCol = hatCustomColor.getColorRGBA();
            else hatCol = meltedColor(baseFill, i / 360f, 128);

            float xOffset = MathHelper.sin((float) Math.toRadians(i)) * 0.65F;
            float zOffset = -MathHelper.cos((float) Math.toRadians(i)) * 0.65F;
            buffer.vertex(matrix, xOffset, 0, zOffset).color(hatCol);
        }
        RenderCompat.draw(buffer.end());

        RenderSystem.lineWidth(3f);
        BufferBuilder buffer1 = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 360; i += 10) {
            int lineColor;
            if (isFriend) lineColor = meltedColor(baseLine, i / 360f, 255);
            else if (hatColor.isSelected("Свой")) lineColor = hatCustomColor.getColorRGBA();
            else lineColor = meltedColor(baseLine, i / 360f, 255);

            float xOffset = MathHelper.sin(i * MathHelper.RADIANS_PER_DEGREE) * 0.65F;
            float zOffset = -MathHelper.cos(i * MathHelper.RADIANS_PER_DEGREE) * 0.65F;
            buffer1.vertex(matrix, xOffset, 0, zOffset).color(lineColor);
        }
        RenderCompat.draw(buffer1.end());
    }

    private int resolveHatBaseColor(boolean isFriend) {
        if (isFriend) return Color.GREEN.getRGB();
        if (hatColor.isSelected("Свой")) return hatCustomColor.getColorRGBA();
        return Interface.INSTANCE.getFirstColor();
    }

    private static int meltedColor(int base, float pos, int alpha) {
        int dark = darkenRgb(base, 0.30f);
        float phase = (System.currentTimeMillis() % 2000L) / 2000f;
        float t = (float) (Math.sin((pos + phase) * Math.PI * 2.0) * 0.5 + 0.5);
        int rgb = ColorUtils.interpolate(dark, base, t) & 0x00FFFFFF;
        return ((alpha & 0xFF) << 24) | rgb;
    }

    private static int darkenRgb(int color, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        int r = Math.round(((color >>> 16) & 0xFF) * f);
        int g = Math.round(((color >>> 8) & 0xFF) * f);
        int b = Math.round((color & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    private MatrixStack hatStack(RenderEvent.Draw3D event, PlayerEntity player) {
        MatrixStack matrices = event.getMatrices();
        matrices.push();
        float tickDelta = (float) MathUtil.getTickDelta();
        double x = MathVector.interpolate(player.lastRenderX, player.getX(), tickDelta);
        double y = MathVector.interpolate(player.lastRenderY, player.getY(), tickDelta);
        double z = MathVector.interpolate(player.lastRenderZ, player.getZ(), tickDelta);
        matrices.translate(
                x - mc.gameRenderer.getCamera().getCameraPos().x,
                y + player.getHeight() - mc.gameRenderer.getCamera().getCameraPos().y,
                z - mc.gameRenderer.getCamera().getCameraPos().z
        );

        if (hatFollowHead.getEnabled()) {
            float yaw = MathHelper.lerpAngleDegrees(tickDelta, player.lastHeadYaw, player.headYaw);
            float pitch = MathHelper.lerp(tickDelta, player.lastPitch, player.getPitch());
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrices.translate(0f, -0.3f, 0f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            matrices.translate(0f, 0.3f, 0f);
        }
        return matrices;
    }


    private void renderPenisEsp(RenderEvent.Draw3D event) {
        if (mc.world == null || mc.player == null) return;

        MatrixStack ms = event.getMatrices();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        ms.push();
        ms.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (PlayerEntity entity : mc.world.getPlayers()) {
            if (!entity.isAlive()) continue;
            if (entity.distanceTo(mc.player) > 12.0f) continue;

            int color = getPenisColor();
            Vec3d pos = MathVector.lerpPosition(entity);
            double ex = pos.x;
            double ey = pos.y + entity.getHeight() * 0.32;
            double ez = pos.z;

            ms.push();
            ms.translate(ex, ey, ez);
            ms.multiply(new Quaternionf().rotateY((float) Math.toRadians(-entity.getYaw())));

            float length = entity == mc.player ? 0.35f : 0.2f;
            renderPenis(buffer, ms, length, color);
            ms.pop();
        }

        var built = buffer.endNullable();
        if (built != null) RenderCompat.draw(built);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ms.pop();
    }

    private int getPenisColor() {
        if (penisColorMode.isSelected("Радуга")) {
            float hue = (System.currentTimeMillis() % 3000) / 3000f;
            return Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;
        }
        if (penisColorMode.isSelected("Случайный")) {
            return Color.getHSBColor(random.nextFloat(), 1f, 1f).getRGB();
        }
        if (penisColorMode.isSelected("Свой")) {
            return penisCustomColor.getColorRGBA();
        }
        return Interface.INSTANCE.getMainColor();
    }

    private void renderPenis(BufferBuilder buffer, MatrixStack ms, float length, int color) {
        float shaftRadius = 0.11f;
        float ballRadius = 0.16f;

        ms.push();
        ms.translate(0, 0.15, 0.2);
        ms.multiply(new Quaternionf().rotateX((float) Math.toRadians(90)));
        renderWireCylinder(buffer, ms.peek().getPositionMatrix(), shaftRadius, length, 20, color);
        ms.pop();

        ms.push();
        ms.translate(-0.14, 0.15, 0.2);
        ms.multiply(new Quaternionf().rotateX((float) Math.toRadians(90)));
        renderWireSphere(buffer, ms.peek().getPositionMatrix(), ballRadius, 10, 16, color);
        ms.pop();

        ms.push();
        ms.translate(0.14, 0.15, 0.2);
        ms.multiply(new Quaternionf().rotateX((float) Math.toRadians(90)));
        renderWireSphere(buffer, ms.peek().getPositionMatrix(), ballRadius, 10, 16, color);
        ms.pop();

        ms.push();
        ms.translate(0, 0.15, length + 0.03f + 0.2);
        ms.multiply(new Quaternionf().rotateX((float) Math.toRadians(90)));
        renderWireSphere(buffer, ms.peek().getPositionMatrix(), 0.12f, 10, 16, color);
        ms.pop();
    }

    private void renderWireCylinder(BufferBuilder buffer, Matrix4f matrix, float radius, float height, int segments, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        for (int i = 0; i < segments; i++) {
            double theta1 = 2 * Math.PI * i / segments;
            double theta2 = 2 * Math.PI * (i + 1) / segments;
            float x1 = (float) (radius * Math.cos(theta1));
            float z1 = (float) (radius * Math.sin(theta1));
            float x2 = (float) (radius * Math.cos(theta2));
            float z2 = (float) (radius * Math.sin(theta2));
            buffer.vertex(matrix, x1, 0, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, 0, z2).color(r, g, b, a);
            buffer.vertex(matrix, x1, height, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, height, z2).color(r, g, b, a);
            buffer.vertex(matrix, x1, 0, z1).color(r, g, b, a);
            buffer.vertex(matrix, x1, height, z1).color(r, g, b, a);
        }
    }

    private void renderWireSphere(BufferBuilder buffer, Matrix4f matrix, float radius, int latSegments, int lonSegments, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        for (int i = 0; i <= latSegments; i++) {
            double phi = Math.PI * i / latSegments;
            float y = (float) (radius * Math.cos(phi));
            float rCircle = (float) (radius * Math.sin(phi));
            for (int j = 0; j < lonSegments; j++) {
                double theta1 = 2 * Math.PI * j / lonSegments;
                double theta2 = 2 * Math.PI * (j + 1) / lonSegments;
                float x1 = (float) (rCircle * Math.cos(theta1));
                float z1 = (float) (rCircle * Math.sin(theta1));
                float x2 = (float) (rCircle * Math.cos(theta2));
                float z2 = (float) (rCircle * Math.sin(theta2));
                buffer.vertex(matrix, x1, y, z1).color(r, g, b, a);
                buffer.vertex(matrix, x2, y, z2).color(r, g, b, a);
            }
        }
        for (int j = 0; j < lonSegments; j++) {
            double theta = 2 * Math.PI * j / lonSegments;
            for (int i = 0; i < latSegments; i++) {
                double phi1 = Math.PI * i / latSegments;
                double phi2 = Math.PI * (i + 1) / latSegments;
                float x1 = (float) (radius * Math.sin(phi1) * Math.cos(theta));
                float y1 = (float) (radius * Math.cos(phi1));
                float z1 = (float) (radius * Math.sin(phi1) * Math.sin(theta));
                float x2 = (float) (radius * Math.sin(phi2) * Math.cos(theta));
                float y2 = (float) (radius * Math.cos(phi2));
                float z2 = (float) (radius * Math.sin(phi2) * Math.sin(theta));
                buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
                buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
            }
        }
    }



    private void tickPet() {
        if (mc.player == null || mc.world == null) return;

        if (fakeEntity == null) {
            fakeEntity = createPet();
            if (fakeEntity != null) {
                mc.world.addEntity(fakeEntity);
                keepPetVisualOnly();
                applyPetName();
                lastWorld = mc.world;
            }
            return;
        }

        keepPetVisualOnly();

        if (mc.world != lastWorld) {
            mc.world.removeEntity(fakeEntity.getId(), Entity.RemovalReason.DISCARDED);
            fakeEntity = createPet();
            if (fakeEntity == null) return;
            mc.world.addEntity(fakeEntity);
            keepPetVisualOnly();
            applyPetName();
            lastWorld = mc.world;
        }

        Vec3d playerPos = dev.client.api.compat.EntityCompat.pos(mc.player);
        Vec3d entityPos = dev.client.api.compat.EntityCompat.pos(fakeEntity);
        double distToPlayer = playerPos.distanceTo(entityPos);

        if (distToPlayer > 10.0 || stuckTicks > 40) {
            ticksAway++;
            if (ticksAway >= 60 || stuckTicks > 40) {
                double targetX = playerPos.x;
                double targetY = playerPos.y;
                double targetZ = playerPos.z;

                if (mc.player.isGliding() || !mc.player.isOnGround()) {
                    BlockPos.Mutable posBelow = new BlockPos.Mutable(
                            (int) Math.floor(playerPos.x),
                            (int) Math.floor(playerPos.y),
                            (int) Math.floor(playerPos.z)
                    );
                    for (int yOffset = 0; yOffset < 64; yOffset++) {
                        if (isSolid(posBelow)) {
                            targetY = posBelow.getY() + 1.0;
                            break;
                        }
                        posBelow.move(0, -1, 0);
                    }
                }

                fakeEntity.refreshPositionAndAngles(targetX, targetY, targetZ, fakeEntity.getYaw(), fakeEntity.getPitch());
                fakeEntity.setVelocity(Vec3d.ZERO);
                ticksAway = 0;
                stuckTicks = 0;
            }
        } else {
            ticksAway = 0;
        }

        if (currentTarget != null && (currentTarget.isRemoved() || currentTarget.isDead())) {
            currentTarget = null;
        }

        Vec3d moveTo = playerPos;
        Entity lookTarget = mc.player;

        if (petFollowTarget.getEnabled() && currentTarget != null) {
            double distToTarget = dev.client.api.compat.EntityCompat.pos(currentTarget).distanceTo(entityPos);
            if (distToTarget <= 12) {
                moveTo = dev.client.api.compat.EntityCompat.pos(currentTarget);
                lookTarget = currentTarget;
            } else {
                currentTarget = null;
            }
        }

        movePetTowards(moveTo);
        fakeEntity.tickMovement();
        keepPetVisualOnly();
        updatePetRotation(lookTarget);
        applyPetName();
    }

    private LivingEntity createPet() {
        if (mc.world == null || mc.player == null) return null;
        var entity = new GhostWolfEntity(EntityType.WOLF, mc.world);
        entity.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch());
        entity.setInvisible(false);
        entity.setCustomNameVisible(false);
        entity.setHealth(20.0F);
        entity.keepVisualOnly();
        if (entity instanceof TameableEntity tameable) {
            tameable.setTamed(true, false);
            tameable.setOwner(mc.player);
        }
        return entity;
    }

    private void removePet() {
        if (mc.world != null && fakeEntity != null) {
            mc.world.removeEntity(fakeEntity.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakeEntity = null;
        ticksAway = 0;
        stuckTicks = 0;
        currentTarget = null;
        lastWorld = null;
    }

    private void applyPetName() {
        if (fakeEntity == null) return;
        if (petShowName.getEnabled()) {
            String name = petNameInput.getValue();
            if (name == null || name.isEmpty()) {
                fakeEntity.setCustomName(null);
                fakeEntity.setCustomNameVisible(false);
                return;
            }
            fakeEntity.setCustomName(Text.of(name));
            fakeEntity.setCustomNameVisible(true);
            return;
        }
        fakeEntity.setCustomName(null);
        fakeEntity.setCustomNameVisible(false);
    }

    private void movePetTowards(Vec3d targetPos) {
        if (fakeEntity == null) return;
        Vec3d entityPos = dev.client.api.compat.EntityCompat.pos(fakeEntity);
        double dx = targetPos.x - entityPos.x;
        double dz = targetPos.z - entityPos.z;
        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        double dy = targetPos.y - entityPos.y;

        boolean isMoving = false;
        boolean isJumping = false;

        BlockPos entityBlockPos = new BlockPos(
                (int) Math.floor(entityPos.x),
                (int) Math.floor(entityPos.y - 0.1),
                (int) Math.floor(entityPos.z)
        );
        boolean onGround = isSolid(entityBlockPos);

        if (Math.abs(dy) >= 12 || distanceXZ > 20) {
            fakeEntity.refreshPositionAndAngles(targetPos.x, targetPos.y, targetPos.z, fakeEntity.getYaw(), fakeEntity.getPitch());
            fakeEntity.setVelocity(Vec3d.ZERO);
            stuckTicks = 0;
            return;
        }

        if (distanceXZ > 1.2) {
            double speed = 0.21;
            double normX = dx / distanceXZ;
            double normZ = dz / distanceXZ;

            BlockPos frontPos = new BlockPos(
                    (int) Math.floor(entityPos.x + normX * 0.6),
                    (int) Math.floor(entityPos.y),
                    (int) Math.floor(entityPos.z + normZ * 0.6)
            );
            BlockPos aboveFrontPos = frontPos.up();
            BlockPos blockBelowFront = frontPos.down();
            BlockPos blockBelow = entityBlockPos.down();

            boolean frontSolid = isSolid(frontPos);
            boolean aboveClear = !isSolid(aboveFrontPos);
            boolean belowSolid = isSolid(blockBelow);
            boolean belowFrontSolid = isSolid(blockBelowFront);

            if (frontSolid && aboveClear && onGround) {
                int blockHeight = frontPos.getY() - entityBlockPos.getY();
                if (blockHeight <= 1) {
                    Vec3d vel = fakeEntity.getVelocity();
                    fakeEntity.setVelocity(vel.x, 0.5, vel.z);
                    isJumping = true;
                    onGround = false;
                } else {
                    stuckTicks++;
                    return;
                }
            }

            if (!frontSolid && !belowFrontSolid) {
                boolean foundGround = false;
                for (int drop = 1; drop <= 3; drop++) {
                    BlockPos check = frontPos.down(drop);
                    if (isSolid(check)) {
                        Vec3d vel = fakeEntity.getVelocity();
                        fakeEntity.setVelocity(vel.x, -0.1, vel.z);
                        foundGround = true;
                        break;
                    }
                }
                if (!foundGround) {
                    stuckTicks++;
                    return;
                }
            }

            if (!isJumping && (belowSolid || onGround)) {
                movePetBy(new Vec3d(normX * speed, 0, normZ * speed));
                isMoving = true;
                stuckTicks = 0;
            } else if (!isJumping) {
                stuckTicks++;
            }

            if (isMoving) {
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                fakeEntity.setYaw(yaw);
                fakeEntity.setBodyYaw(yaw);
                if (fakeEntity instanceof MobEntity mob) mob.setHeadYaw(yaw);
            }
        }

        if (!onGround) {
            Vec3d velocity = fakeEntity.getVelocity();
            fakeEntity.setVelocity(velocity.x * 0.91, Math.max(velocity.y - 0.08, -0.5), velocity.z * 0.91);
            movePetBy(fakeEntity.getVelocity());
        }

        if (!isMoving && onGround) {
            fakeEntity.setVelocity(Vec3d.ZERO);
        }

        if (fakeEntity instanceof WolfEntity wolf) {
            wolf.setSprinting(isMoving && !isJumping);
            wolf.setSitting(!isMoving && onGround);
        }
    }

    private void keepPetVisualOnly() {
        if (fakeEntity instanceof GhostWolfEntity ghost) {
            ghost.keepVisualOnly();
        }
    }

    private void movePetBy(Vec3d movement) {
        if (fakeEntity == null) return;
        Vec3d entityPos = dev.client.api.compat.EntityCompat.pos(fakeEntity);
        fakeEntity.setPosition(entityPos.add(movement));
    }

    private void updatePetRotation(Entity target) {
        if (fakeEntity == null || target == null) return;
        Vec3d entityEyes = dev.client.api.compat.EntityCompat.pos(fakeEntity).add(0, fakeEntity.getStandingEyeHeight(), 0);
        Vec3d targetEyes = dev.client.api.compat.EntityCompat.pos(target).add(0, target.getStandingEyeHeight(), 0);
        Vec3d diff = targetEyes.subtract(entityEyes);
        double flatDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(diff.y, flatDist));
        fakeEntity.setYaw(yaw);
        fakeEntity.setPitch(pitch);
        fakeEntity.setBodyYaw(yaw);
        if (fakeEntity instanceof MobEntity mob) {
            mob.setHeadYaw(yaw);
        }
    }

    private boolean isSolid(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState blockState = mc.world.getBlockState(pos);
        return !blockState.isAir() && !blockState.getCollisionShape(mc.world, pos).isEmpty();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        removePet();
        wingsSelfBodyYawInitialized = false;
    }
}
