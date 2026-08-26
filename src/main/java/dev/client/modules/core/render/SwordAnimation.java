package dev.client.modules.core.render;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ClickSetting;
import dev.client.api.nullcry.modules.settings.Collection;
import dev.client.api.nullcry.modules.settings.CurveSetting;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.uiClient.clickGui.ClickGuiScreen;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.modules.core.combat.KillAura;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class SwordAnimation extends Module {
    public static SwordAnimation INSTANCE;

    public SwordAnimation() {
        super("SwordAnimation", ModuleCategory.Render, "Устанавливает анимацию предметов на выбранный");
        setupCustomMode();
        setupCustomCollections();
    }

    public final ModeElement mode = new ModeElement("Режим", () -> true).set("None", "Mode 1", "Mode 2", "Mode 3", "Mode 4", "Mode 5", "Mode 6").defaultValue("None").register(this);
    public final Slider power = new Slider("Сила", () -> !mode.isSelected("None") && !mode.isSelected("Mode 2")).set(1.0f, 10.0f, 1).defaultValue(5.0f).register(this);
    public final Slider speed = new Slider("Скорость", () -> !mode.isSelected("None") && !mode.isSelected("Mode 2")).set(3.0f, 10.0f, 1).defaultValue(10.0f).register(this);
    public final Slider scale = new Slider("Размер", () -> true).set(0.5f, 1.5f, 0.1f).defaultValue(1.0f).register(this);
    public final CheckBox onlyKillAura = new CheckBox("Только с киллаурой", () -> true).defaultValue(false).register(this);

    public final CurveSetting customCurve = new CurveSetting("Анимация", () -> mode.isSelected("Custom"))
            .defaultValue(0.2f, 0.0f, 0.8f, 1.0f)
            .register(this);
    public final CheckBox customReturn = new CheckBox("Возвращать", () -> mode.isSelected("Custom")).defaultValue(true).register(this);

    public final Collection animFrom = new Collection("Анимация от", () -> mode.isSelected("Custom")).register(this);
    public final Slider fromAnchorX = new Slider("Опора X", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider fromAnchorY = new Slider("Опора Y", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider fromAnchorZ = new Slider("Опора Z", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider fromOffsetX = new Slider("Отклонение X", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider fromOffsetY = new Slider("Отклонение Y", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider fromOffsetZ = new Slider("Отклонение Z", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider fromRotateX = new Slider("Поворот X", () -> true).set(-180.0f, 180.0f, 1.0f).defaultValue(0.0f);
    public final Slider fromRotateY = new Slider("Поворот Y", () -> true).set(-180.0f, 180.0f, 1.0f).defaultValue(0.0f);
    public final Slider fromRotateZ = new Slider("Поворот Z", () -> true).set(-180.0f, 180.0f, 1.0f).defaultValue(0.0f);

    public final Collection animTo = new Collection("Анимация до", () -> mode.isSelected("Custom")).register(this);
    public final Slider toAnchorX = new Slider("Опора X", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider toAnchorY = new Slider("Опора Y", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider toAnchorZ = new Slider("Опора Z", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider toOffsetX = new Slider("Отклонение X", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider toOffsetY = new Slider("Отклонение Y", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider toOffsetZ = new Slider("Отклонение Z", () -> true).set(-2.0f, 2.0f, 0.1f).defaultValue(0.0f);
    public final Slider toRotateX = new Slider("Поворот X", () -> true).set(-180.0f, 180.0f, 1.0f).defaultValue(0.0f);
    public final Slider toRotateY = new Slider("Поворот Y", () -> true).set(-180.0f, 180.0f, 1.0f).defaultValue(0.0f);
    public final Slider toRotateZ = new Slider("Поворот Z", () -> true).set(-180.0f, 180.0f, 1.0f).defaultValue(0.0f);

    public final ClickSetting resetCustom = new ClickSetting("Сбросить Custom", () -> mode.isSelected("Custom"))
            .onClick(this::resetCustomSettings)
            .cooldown(300)
            .register(this);

    private float previewSwingProgress = 0f;
    private long previewLastMs = 0L;
    private long customPreviewUntilMs = 0L;
    private long lastCustomSignature = Long.MIN_VALUE;

    private void setupCustomMode() {
        if (!mode.getValues().contains("Custom")) {
            mode.getValues().add("Custom");
        }
    }

    private void setupCustomCollections() {
        fromAnchorX.collection(animFrom);
        fromAnchorY.collection(animFrom);
        fromAnchorZ.collection(animFrom);
        fromOffsetX.collection(animFrom);
        fromOffsetY.collection(animFrom);
        fromOffsetZ.collection(animFrom);
        fromRotateX.collection(animFrom);
        fromRotateY.collection(animFrom);
        fromRotateZ.collection(animFrom);

        toAnchorX.collection(animTo);
        toAnchorY.collection(animTo);
        toAnchorZ.collection(animTo);
        toOffsetX.collection(animTo);
        toOffsetY.collection(animTo);
        toOffsetZ.collection(animTo);
        toRotateX.collection(animTo);
        toRotateY.collection(animTo);
        toRotateZ.collection(animTo);
    }


    public void animationProcess(MatrixStack matrices, float swingProgress, Arm handSide) {
        float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2);

        if (onlyKillAura.getEnabled() && KillAura.INSTANCE.getTarget() == null && !isCustomPreviewActive()) {
            return;
        }

        float scaleVal = scale.getValue();
        float powerVal = power.getValue();
        float speedVal = speed.getValue();
        int handSign = handSide == Arm.RIGHT ? 1 : -1;
        int i = handSide == Arm.RIGHT ? 1 : -1;

        switch (mode.getValue()) {
            case "None":
                if (mc.options.mainArm.getValue() == Arm.RIGHT) {
                    matrices.scale(scale.getValue(), scale.getValue(), scale.getValue());
                }
                break;
            case "Mode 1":
                float swingAngle = -90 - (powerVal * 10) * anim;
                float currentAngle = MathHelper.lerp(anim, -90, swingAngle);
                applyEquipOffset(matrices, handSide, 0.0F);
                matrices.scale(scaleVal, scaleVal, scaleVal);
                matrices.translate(handSign * 0.4f, 0.1f, -0.5);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handSign * 90));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(handSign * -60));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(currentAngle));
                break;
            case "Mode 2": {
                applyEquipOffset(matrices, handSide, 0.0F);
                matrices.scale(scaleVal, scaleVal, scaleVal);
                matrices.translate(0.0f, 0.1f, 0.0f);

                if (swingProgress > 1.0e-3f) {
                    float eased = MathHelper.sin(swingProgress * ((float) Math.PI) * 0.5F);
                    float rotationAngle = eased * 360.0f * (10 / 10.0f);
                    if (rotationAngle > 360.0f) rotationAngle = 360.0f;

                    matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(rotationAngle));
                }
                break;
            }
            case "Mode 3":
                applyEquipOffset(matrices, handSide, 0.0F);
                matrices.scale(scaleVal, scaleVal, scaleVal);
                matrices.translate(handSign * 0.4f, 0, -0.5f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handSign * 90));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(handSign * -30));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90 - (powerVal * 10) * anim));
                break;
            case "Mode 4":
                applyEquipOffset(matrices, handSide, 0.0F);
                matrices.scale(scaleVal, scaleVal, scaleVal);
                matrices.translate(handSign * 0.8F, -0.5F, -0.71999997F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handSign * 45.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(anim * -20.0F * speedVal / 10.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(anim * -20.0F * powerVal / 10.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(anim * -40.0F * powerVal / 10.0F));
                matrices.translate(handSign * -0.1F, 0.2F, 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handSign * 30.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handSign * 60.0F));
                break;
            case "Mode 5":
                applyEquipOffset(matrices, handSide, 0.0F);
                matrices.scale(scaleVal, scaleVal, scaleVal);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (45.0F + MathHelper.sin(swingProgress * swingProgress * (float) Math.PI) * (speed.getValue() / 10.0f) * -20.0F)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI) * (power.getValue() / 10.0f) * -20.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI) * (power.getValue() / 10.0f) * -80.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45.0F));
                break;
            case "Mode 6":
                applyEquipOffset(matrices, handSide, 0.0F);
                matrices.scale(scaleVal, scaleVal, scaleVal);
                float offset = MathHelper.sin(swingProgress * (float) Math.PI);
                matrices.translate(0.0F, offset * -0.6F * (powerVal / 10.0F), 0.0F);
                break;
            case "Custom":
                applyEquipOffset(matrices, handSide, 0.0F);
                matrices.scale(scaleVal, scaleVal, scaleVal);
                float customProgress = resolveCustomProgress(swingProgress);
                applyCustomTransform(matrices, handSide, customProgress);
                break;
            default:
                break;
        }
    }

    public void renderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue renderCommandQueue, int light) {
        if (!player.isUsingSpyglass()) {
            if (mode.isSelected("Custom")) {
                updateCustomPreviewState();
            } else {
                resetCustomPreviewState();
            }
            boolean bl = hand == Hand.MAIN_HAND;
            Arm arm = bl ? player.getMainArm() : player.getMainArm().getOpposite();
            matrices.push();
            ViewModel viewModel = ViewModel.INSTANCE;
            if (viewModel != null && viewModel.isEnabled()) {
                boolean rightArm = arm == Arm.RIGHT;
                float x = rightArm ? viewModel.right_x.getValue() : viewModel.left_x.getValue();
                float y = rightArm ? viewModel.right_y.getValue() : viewModel.left_y.getValue();
                float z = rightArm ? viewModel.right_z.getValue() : viewModel.left_z.getValue();
                matrices.translate(x, y, z);
            }
            if (item.isOf(Items.CROSSBOW)) {
                boolean bl2 = CrossbowItem.isCharged(item);
                boolean bl3 = arm == Arm.RIGHT;
                int i = bl3 ? 1 : -1;
                if (player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand) {
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    matrices.translate((float) i * -0.4785682F, -0.094387F, 0.05731531F);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-11.935F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * 65.3F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * -9.785F));
                    float f = (float) item.getMaxUseTime(player) - ((float) mc.player.getItemUseTimeLeft() - tickDelta + 1.0F);
                    float g = f / (float) CrossbowItem.getPullTime(item, player);
                    if (g > 1.0F) {
                        g = 1.0F;
                    }

                    if (g > 0.1F) {
                        float h = MathHelper.sin((f - 0.1F) * 1.3F);
                        float j = g - 0.1F;
                        float k = h * j;
                        matrices.translate(k * 0.0F, k * 0.004F, k * 0.0F);
                    }

                    matrices.translate(g * 0.0F, g * 0.0F, g * 0.04F);
                    matrices.scale(1.0F, 1.0F, 1.0F + g * 0.2F);
                    matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) i * 45.0F));
                } else {
                    float fx = -0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                    float gx = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) (Math.PI * 2));
                    float h = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
                    matrices.translate((float) i * fx, gx, h);
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    this.applySwingOffset(matrices, arm, swingProgress);
                    if (bl2 && swingProgress < 0.001F && bl) {
                        matrices.translate((float) i * -0.641864F, 0.0F, 0.0F);
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * 10.0F));
                    }
                }

                applyViewModelTransform(matrices, viewModel, arm);
                this.renderTrackedItem(player, item, bl3 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !bl3, matrices, renderCommandQueue, light, viewModel, arm, tickDelta);
            } else {
                boolean bl2 = arm == Arm.RIGHT;
                boolean previewActive = isCustomPreviewActive();

                if (player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand) {
                    int l = bl2 ? 1 : -1;
                    switch (item.getUseAction()) {
                        case NONE, BLOCK:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            break;
                        case EAT:
                        case DRINK:
                            this.applyEatOrDrinkTransformation(matrices, tickDelta, arm, item);
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            break;
                        case BOW:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            matrices.translate((float) l * -0.2785682F, 0.18344387F, 0.15731531F);
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-13.935F));
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) l * 35.3F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) l * -9.785F));
                            float mx = (float) item.getMaxUseTime(player) - ((float) mc.player.getItemUseTimeLeft() - tickDelta + 1.0F);
                            float fxx = mx / 20.0F;
                            fxx = (fxx * fxx + fxx * 2.0F) / 3.0F;
                            if (fxx > 1.0F) {
                                fxx = 1.0F;
                            }

                            if (fxx > 0.1F) {
                                float gx = MathHelper.sin((mx - 0.1F) * 1.3F);
                                float h = fxx - 0.1F;
                                float j = gx * h;
                                matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            matrices.translate(fxx * 0.0F, fxx * 0.0F, fxx * 0.04F);
                            matrices.scale(1.0F, 1.0F, 1.0F + fxx * 0.2F);
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) l * 45.0F));
                            break;
                        case SPEAR:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            matrices.translate((float) l * -0.5F, 0.7F, 0.1F);
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-55.0F));
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) l * 35.3F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) l * -9.785F));
                            float m = (float) item.getMaxUseTime(player) - ((float) mc.player.getItemUseTimeLeft() - tickDelta + 1.0F);
                            float fx = m / 10.0F;
                            if (fx > 1.0F) {
                                fx = 1.0F;
                            }

                            if (fx > 0.1F) {
                                float gx = MathHelper.sin((m - 0.1F) * 1.3F);
                                float h = fx - 0.1F;
                                float j = gx * h;
                                matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            matrices.translate(0.0F, 0.0F, fx * 0.2F);
                            matrices.scale(1.0F, 1.0F, 1.0F + fx * 0.2F);
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) l * 45.0F));
                            break;
                        case BRUSH:
                            this.applyBrushTransformation(matrices, tickDelta, arm, item, equipProgress);
                    }
                } else if (player.isUsingRiptide()) {
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    int l = bl2 ? 1 : -1;
                    matrices.translate((float) l * -0.4F, 0.8F, 0.3F);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) l * 65.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) l * -85.0F));
                } else {
                    boolean isDominant = (arm == player.getMainArm());
                    boolean canCustom = this.isEnabled() && !mode.isSelected("None") && (!onlyKillAura.getEnabled() || KillAura.INSTANCE.getTarget() != null || previewActive);
                    boolean useCustom = canCustom && isDominant;

                    if (useCustom) {
                        float appliedSwing = swingProgress;
                        if (mode.isSelected("Custom")) {
                            appliedSwing = resolvePreviewSwingProgress(swingProgress);
                        }
                        animationProcess(matrices, appliedSwing, arm);
                    } else {
                        float n = -0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                        float mxx = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) (Math.PI * 2));
                        float fxx = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
                        int o = arm == Arm.RIGHT ? 1 : -1;
                        matrices.translate((float) o * n, mxx, fxx);
                        this.applyEquipOffset(matrices, arm, equipProgress);
                        this.applySwingOffset(matrices, arm, swingProgress);
                    }
                }

                boolean scaleAllowed = this.isEnabled() && (arm == player.getMainArm()) && (!onlyKillAura.getEnabled() || KillAura.INSTANCE.getTarget() != null || previewActive);

                if (scaleAllowed) {
                    float s = scale.getValue();
                    if (s != 1.0f) matrices.scale(s, s, s);
                }

                applyViewModelTransform(matrices, viewModel, arm);
                this.renderTrackedItem(player, item, bl2 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !bl2, matrices, renderCommandQueue, light, viewModel, arm, tickDelta);
            }

            matrices.pop();
        }
    }

    private void applyViewModelTransform(MatrixStack matrices, ViewModel viewModel, Arm arm) {
        if (viewModel == null || !viewModel.isEnabled()) {
            return;
        }

        float rotation = viewModel.getRenderRotation(arm);
        if (rotation != 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        }

        float handScale = viewModel.getRenderScale(arm);
        if (handScale != 1.0f) {
            matrices.scale(handScale, handScale, handScale);
        }
    }

    private void renderTrackedItem(LivingEntity entity,
                                   ItemStack stack,
                                   ItemDisplayContext renderMode,
                                   boolean leftHanded,
                                   MatrixStack matrices,
                                   OrderedRenderCommandQueue renderCommandQueue,
                                   int light,
                                   ViewModel viewModel,
                                   Arm arm,
                                   float tickDelta) {
        boolean capture = viewModel != null && viewModel.isEnabled();
        if (capture) {
            viewModel.beginRenderedItemCapture(arm, tickDelta);
        }
        try {
            renderItem(entity, stack, renderMode, leftHanded, matrices, renderCommandQueue, light);
        } finally {
            if (capture) {
                viewModel.endRenderedItemCapture();
            }
        }
    }

    private void applyBrushTransformation(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack, float equipProgress) {
        this.applyEquipOffset(matrices, arm, equipProgress);
        float f = (float) (mc.player.getItemUseTimeLeft() % 10);
        float g = f - tickDelta + 1.0F;
        float h = 1.0F - g / 10.0F;
        float n = -15.0F + 75.0F * MathHelper.cos(h * 2.0F * (float) Math.PI);

        if (arm != Arm.RIGHT) {
            matrices.translate(0.1, 0.83, 0.35);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(n));
            matrices.translate(-0.3, 0.22, 0.35);
        } else {
            matrices.translate(-0.25, 0.22, 0.35);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(0.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(n));
        }
    }

    private void applyEatOrDrinkTransformation(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack) {
        float f = (float) mc.player.getItemUseTimeLeft() - tickDelta + 1.0F;
        float g = f / (float) stack.getMaxUseTime(mc.player);
        if (g < 0.8F) {
            float h = MathHelper.abs(MathHelper.cos(f / 4.0F * (float) Math.PI) * 0.1F);
            matrices.translate(0.0F, h, 0.0F);
        }

        float h = 1.0F - (float) Math.pow(g, 27.0);
        int i = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(h * 0.6F * (float) i, h * -0.5F, h * 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * h * 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(h * 10.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * h * 30.0F));
    }

    private void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate((float) i * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
    }

    private void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + f * -20.0F)));
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -20.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
    }

    private float resolveCustomProgress(float swingProgress) {
        float progress = MathHelper.clamp(swingProgress, 0f, 1f);
        if (customReturn.getEnabled()) {
            progress = progress <= 0.5f ? progress * 2f : (1f - progress) * 2f;
        }
        progress = MathHelper.clamp(progress, 0f, 1f);
        return customCurve.evaluate(progress);
    }

    private void applyCustomTransform(MatrixStack matrices, Arm handSide, float progress) {
        float handSign = handSide == Arm.RIGHT ? 1f : -1f;

        float anchorX = MathHelper.lerp(progress, fromAnchorX.getValue(), toAnchorX.getValue()) * handSign;
        float anchorY = MathHelper.lerp(progress, fromAnchorY.getValue(), toAnchorY.getValue());
        float anchorZ = MathHelper.lerp(progress, fromAnchorZ.getValue(), toAnchorZ.getValue());

        float offsetX = MathHelper.lerp(progress, fromOffsetX.getValue(), toOffsetX.getValue()) * handSign;
        float offsetY = MathHelper.lerp(progress, fromOffsetY.getValue(), toOffsetY.getValue());
        float offsetZ = MathHelper.lerp(progress, fromOffsetZ.getValue(), toOffsetZ.getValue());

        float rotX = MathHelper.lerp(progress, fromRotateX.getValue(), toRotateX.getValue());
        float rotY = MathHelper.lerp(progress, fromRotateY.getValue(), toRotateY.getValue()) * handSign;
        float rotZ = MathHelper.lerp(progress, fromRotateZ.getValue(), toRotateZ.getValue()) * handSign;

        matrices.translate(anchorX, anchorY, anchorZ);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));
        matrices.translate(offsetX, offsetY, offsetZ);
    }

    private void resetCustomSettings() {
        customCurve.resetToDefault();
        customReturn.resetToDefault();
        for (Setting setting : animFrom.getSettings()) {
            setting.resetToDefault();
        }
        for (Setting setting : animTo.getSettings()) {
            setting.resetToDefault();
        }
    }

    private float resolvePreviewSwingProgress(float swingProgress) {
        if (!isCustomPreviewActive()) {
            previewSwingProgress = 0f;
            previewLastMs = 0L;
            return swingProgress;
        }

        if (swingProgress > 1.0e-3f) {
            previewSwingProgress = swingProgress;
            previewLastMs = System.currentTimeMillis();
            return swingProgress;
        }

        long now = System.currentTimeMillis();
        if (previewLastMs == 0L) {
            previewLastMs = now;
        }

        float deltaMs = now - previewLastMs;
        previewLastMs = now;

        float durationMs = getSwingDurationTicks() * 50f;
        if (durationMs <= 0f) {
            return 0f;
        }

        previewSwingProgress += deltaMs / durationMs;
        previewSwingProgress -= (float) Math.floor(previewSwingProgress);
        return previewSwingProgress;
    }

    private boolean isCustomPreviewActive() {
        return mode.isSelected("Custom")
                && mc.currentScreen instanceof ClickGuiScreen
                && System.currentTimeMillis() <= customPreviewUntilMs;
    }

    private void updateCustomPreviewState() {
        if (!(mc.currentScreen instanceof ClickGuiScreen) || !mode.isSelected("Custom")) {
            resetCustomPreviewState();
            return;
        }

        long signature = computeCustomSignature();
        if (lastCustomSignature == Long.MIN_VALUE) {
            lastCustomSignature = signature;
            return;
        }

        if (signature != lastCustomSignature) {
            lastCustomSignature = signature;
            customPreviewUntilMs = System.currentTimeMillis() + 1500L;
        }
    }

    private void resetCustomPreviewState() {
        customPreviewUntilMs = 0L;
        lastCustomSignature = Long.MIN_VALUE;
    }

    private long computeCustomSignature() {
        long hash = 1469598103934665603L;
        hash = hashFloat(hash, customCurve.getX1());
        hash = hashFloat(hash, customCurve.getY1());
        hash = hashFloat(hash, customCurve.getX2());
        hash = hashFloat(hash, customCurve.getY2());
        hash = hashBool(hash, customReturn.getEnabled());
        hash = hashFloat(hash, fromAnchorX.getValue());
        hash = hashFloat(hash, fromAnchorY.getValue());
        hash = hashFloat(hash, fromAnchorZ.getValue());
        hash = hashFloat(hash, fromOffsetX.getValue());
        hash = hashFloat(hash, fromOffsetY.getValue());
        hash = hashFloat(hash, fromOffsetZ.getValue());
        hash = hashFloat(hash, fromRotateX.getValue());
        hash = hashFloat(hash, fromRotateY.getValue());
        hash = hashFloat(hash, fromRotateZ.getValue());
        hash = hashFloat(hash, toAnchorX.getValue());
        hash = hashFloat(hash, toAnchorY.getValue());
        hash = hashFloat(hash, toAnchorZ.getValue());
        hash = hashFloat(hash, toOffsetX.getValue());
        hash = hashFloat(hash, toOffsetY.getValue());
        hash = hashFloat(hash, toOffsetZ.getValue());
        hash = hashFloat(hash, toRotateX.getValue());
        hash = hashFloat(hash, toRotateY.getValue());
        hash = hashFloat(hash, toRotateZ.getValue());
        hash = hashFloat(hash, speed.getValue());
        hash = hashFloat(hash, scale.getValue());
        return hash;
    }

    private long hashFloat(long hash, float value) {
        hash ^= Float.floatToIntBits(value);
        return hash * 1099511628211L;
    }

    private long hashBool(long hash, boolean value) {
        hash ^= value ? 1L : 0L;
        return hash * 1099511628211L;
    }

    private int getSwingDurationTicks() {
        int val = 25 - (speed.getValue().intValue() * 2);
        return Math.max(1, val);
    }

    public void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode, boolean leftHanded, MatrixStack matrices, OrderedRenderCommandQueue renderCommandQueue, int light) {
        if (!stack.isEmpty()) {
            mc.gameRenderer.firstPersonRenderer.renderItem(entity, stack, renderMode, matrices, renderCommandQueue, light);
        }
    }
}
