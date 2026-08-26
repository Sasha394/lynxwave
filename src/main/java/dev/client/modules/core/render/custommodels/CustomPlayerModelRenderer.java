package dev.client.modules.core.render.custommodels;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import dev.client.modules.core.render.Cosmetic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CustomPlayerModelRenderer {
    private static final Map<String, Supplier<CustomModel>> MODEL_FACTORIES = new HashMap<>();
    private static final Map<String, CustomModel> MODEL_CACHE = new HashMap<>();

    static {
        MODEL_FACTORIES.put(Cosmetic.MODEL_RABBIT, CustomPlayerModelRenderer::createRabbit);
        MODEL_FACTORIES.put(Cosmetic.MODEL_FREDDY, CustomPlayerModelRenderer::createFreddy);
        MODEL_FACTORIES.put(Cosmetic.MODEL_TUNG, CustomPlayerModelRenderer::createTung);
        MODEL_FACTORIES.put(Cosmetic.MODEL_INK_DEMON, () -> createDemon(Cosmetic.MODEL_INK_DEMON, "models/inkdemon.png", 64, 64));
    }

    public static boolean render(
            String modelName,
            PlayerEntityModel vanillaModel,
            PlayerEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            int overlay,
            int color,
            int outlineColor
    ) {
        CustomModel customModel = getModel(modelName, state.id);
        if (customModel == null) {
            return false;
        }

        customModel.copyAngles(vanillaModel, state);
        matrices.push();
        customModel.transform(matrices, state);
        RenderLayer layer = RenderLayers.entityTranslucent(customModel.texture);
        queue.submitModelPart(
                customModel.root,
                matrices,
                layer,
                light,
                overlay,
                (Sprite) null,
                false,
                false,
                color,
                (ModelCommandRenderer.CrumblingOverlayCommand) null,
                outlineColor
        );
        matrices.pop();
        return true;
    }

    private static CustomModel getModel(String modelName, int entityId) {
        Supplier<CustomModel> factory = MODEL_FACTORIES.get(modelName);
        if (factory == null) {
            return null;
        }

        if (MODEL_CACHE.size() > 128) {
            MODEL_CACHE.clear();
        }

        return MODEL_CACHE.computeIfAbsent(modelName + "#" + entityId, ignored -> factory.get());
    }

    private static CustomModel createRabbit() {
        ModelPart root = createRoot(64, 64, data -> {
            ModelPartData rabbit = data.addChild(
                    "rabbit",
                    box(28, 45, -5.0F, -13.0F, -5.0F, 10.0F, 11.0F, 8.0F),
                    ModelTransform.origin(0.0F, 24.0F, 0.0F)
            );
            rabbit.addChild("right_leg", box(0, 32, -2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F), ModelTransform.origin(-3.0F, -2.0F, -1.0F));
            rabbit.addChild("left_leg", box(0, 32, -2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F), ModelTransform.origin(3.0F, -2.0F, -1.0F));
            rabbit.addChild("left_arm", box(24, 16, 0.0F, 0.0F, -2.0F, 2.0F, 8.0F, 4.0F), ModelTransform.of(5.0F, -13.0F, -1.0F, 0.0F, 0.0F, -0.0873F));
            rabbit.addChild("right_arm", box(24, 16, -2.0F, 0.0F, -2.0F, 2.0F, 8.0F, 4.0F), ModelTransform.of(-5.0F, -13.0F, -1.0F, 0.0F, 0.0F, 0.0873F));

            ModelPartData head = rabbit.addChild(
                    "head",
                    ModelPartBuilder.create()
                            .uv(0, 0).cuboid(-3.0F, 0.0F, -4.0F, 6.0F, 1.0F, 6.0F)
                            .uv(0, 45).cuboid(-4.0F, -11.0F, -4.0F, 8.0F, 11.0F, 8.0F)
                            .uv(56, 0).cuboid(-5.0F, -9.0F, -5.0F, 2.0F, 3.0F, 2.0F)
                            .uv(56, 0).cuboid(3.0F, -9.0F, -5.0F, 2.0F, 3.0F, 2.0F)
                            .uv(46, 0).cuboid(1.0F, -20.0F, 0.0F, 3.0F, 9.0F, 1.0F)
                            .uv(46, 0).cuboid(-4.0F, -20.0F, 0.0F, 3.0F, 9.0F, 1.0F),
                    ModelTransform.origin(0.0F, -14.0F, -1.0F)
            );
            head.addChild("nose", box(0, 7, -1.5F, -4.0F, -5.5F, 3.0F, 2.0F, 1.0F), ModelTransform.NONE);
        });

        return new CustomModel(Cosmetic.MODEL_RABBIT, Identifier.of("lynxwave", "models/rabbit.png"), root, root.getChild("rabbit"),
                root.getChild("rabbit").getChild("head"),
                root.getChild("rabbit"),
                root.getChild("rabbit").getChild("left_arm"),
                root.getChild("rabbit").getChild("right_arm"),
                root.getChild("rabbit").getChild("left_leg"),
                root.getChild("rabbit").getChild("right_leg")) {
            @Override
            void transform(MatrixStack matrices, PlayerEntityRenderState state) {
                matrices.scale(1.25F, 1.25F, 1.25F);
                matrices.translate(0.0F, -0.3F, 0.0F);
            }

            @Override
            void copyAngles(PlayerEntityModel vanilla, PlayerEntityRenderState state) {
                copyHeadAngles(vanilla);
                clearAngles(body);
                copyLimbAngles(vanilla);
                leftArm.roll -= 0.0873F;
                rightArm.roll += 0.0873F;
            }
        };
    }

    private static CustomModel createFreddy() {
        ModelPart root = createRoot(100, 80, data -> {
            ModelPartData body = data.addChild("body", box(0, 0, -1.0F, -14.0F, -1.0F, 2.0F, 24.0F, 2.0F), ModelTransform.origin(0.0F, -9.0F, 0.0F));
            body.addChild("torso", box(8, 0, -6.0F, -9.0F, -4.0F, 12.0F, 18.0F, 8.0F), ModelTransform.rotation((float) Math.PI / 180.0F, 0.0F, 0.0F));
            body.addChild("crotch", box(56, 0, -5.5F, 0.0F, -3.5F, 11.0F, 3.0F, 7.0F), ModelTransform.origin(0.0F, 9.5F, 0.0F));

            ModelPartData head = body.addChild("head", box(39, 22, -5.5F, -8.0F, -4.5F, 11.0F, 8.0F, 9.0F), ModelTransform.origin(0.0F, -13.0F, -0.5F));
            head.addChild("jaw", box(49, 65, -5.0F, 0.0F, -4.5F, 10.0F, 3.0F, 9.0F), ModelTransform.of(0.0F, 0.5F, 0.0F, 0.08726646F, 0.0F, 0.0F));
            head.addChild("nose", box(17, 67, -4.0F, -2.0F, -3.0F, 8.0F, 4.0F, 3.0F), ModelTransform.origin(0.0F, -2.0F, -4.5F));
            ModelPartData rightEar = head.addChild("right_ear", box(8, 0, -1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F), ModelTransform.of(-4.5F, -5.5F, 0.0F, 0.05235988F, 0.0F, -1.0471976F));
            rightEar.addChild("right_ear_pad", box(85, 0, -2.0F, -5.0F, -1.0F, 4.0F, 4.0F, 2.0F), ModelTransform.origin(0.0F, -1.0F, 0.0F));
            ModelPartData leftEar = head.addChild("left_ear", box(40, 0, -1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F), ModelTransform.of(4.5F, -5.5F, 0.0F, 0.05235988F, 0.0F, 1.0471976F));
            leftEar.addChild("left_ear_pad", box(40, 39, -2.0F, -5.0F, -1.0F, 4.0F, 4.0F, 2.0F), ModelTransform.origin(0.0F, -1.0F, 0.0F));
            ModelPartData hat = head.addChild("hat", box(70, 24, -3.0F, -0.5F, -3.0F, 6.0F, 1.0F, 6.0F), ModelTransform.of(0.0F, -8.4F, 0.0F, (float) (-Math.PI) / 180.0F, 0.0F, 0.0F));
            hat.addChild("hat_top", box(78, 61, -2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F), ModelTransform.origin(0.0F, 0.1F, 0.0F));

            ModelPartData rightArm = body.addChild("right_arm", box(48, 0, -1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), ModelTransform.of(-6.5F, -8.0F, 0.0F, 0.0F, 0.0F, 0.2617994F));
            rightArm.addChild("right_arm_pad", box(70, 10, -2.5F, 0.0F, -2.5F, 5.0F, 9.0F, 5.0F), ModelTransform.origin(0.0F, 0.5F, 0.0F));
            ModelPartData rightForearm = rightArm.addChild("right_forearm", box(90, 20, -1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), ModelTransform.of(0.0F, 9.6F, 0.0F, -0.17453292F, 0.0F, 0.0F));
            rightForearm.addChild("right_hand", box(20, 26, -2.0F, 0.0F, -2.5F, 4.0F, 4.0F, 5.0F), ModelTransform.of(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, -0.05235988F));

            ModelPartData leftArm = body.addChild("left_arm", box(62, 10, -1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), ModelTransform.of(6.5F, -8.0F, 0.0F, 0.0F, 0.0F, -0.2617994F));
            leftArm.addChild("left_arm_pad", box(38, 54, -2.5F, 0.0F, -2.5F, 5.0F, 9.0F, 5.0F), ModelTransform.origin(0.0F, 0.5F, 0.0F));
            ModelPartData leftForearm = leftArm.addChild("left_forearm", box(90, 48, -1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), ModelTransform.of(0.0F, 9.6F, 0.0F, -0.17453292F, 0.0F, 0.0F));
            leftForearm.addChild("left_hand", box(58, 56, -1.0F, 0.0F, -2.5F, 4.0F, 4.0F, 5.0F), ModelTransform.of(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.05235988F));

            ModelPartData rightLeg = body.addChild("right_leg", box(90, 8, -1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), ModelTransform.origin(-3.3F, 12.5F, 0.0F));
            rightLeg.addChild("right_leg_pad", box(73, 33, -3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F), ModelTransform.origin(0.0F, 0.5F, 0.0F));
            ModelPartData rightLowerLeg = rightLeg.addChild("right_lower_leg", box(20, 35, -1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), ModelTransform.of(0.0F, 9.6F, 0.0F, (float) Math.PI / 90.0F, 0.0F, 0.0F));
            rightLowerLeg.addChild("right_lower_leg_pad", box(0, 39, -2.5F, 0.0F, -3.0F, 5.0F, 7.0F, 6.0F), ModelTransform.origin(0.0F, 0.5F, 0.0F));
            rightLowerLeg.addChild("right_foot", box(22, 39, -2.5F, 0.0F, -6.0F, 5.0F, 3.0F, 8.0F), ModelTransform.of(0.0F, 8.0F, 0.0F, (float) (-Math.PI) / 90.0F, 0.0F, 0.0F));
            ModelPartData leftLeg = body.addChild("left_leg", box(54, 10, -1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), ModelTransform.origin(3.3F, 12.5F, 0.0F));
            leftLeg.addChild("left_leg_pad", box(48, 39, -3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F), ModelTransform.origin(0.0F, 0.5F, 0.0F));
            ModelPartData leftLowerLeg = leftLeg.addChild("left_lower_leg", box(72, 48, -1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), ModelTransform.of(0.0F, 9.6F, 0.0F, (float) Math.PI / 90.0F, 0.0F, 0.0F));
            leftLowerLeg.addChild("left_lower_leg_pad", box(16, 50, -2.5F, 0.0F, -3.0F, 5.0F, 7.0F, 6.0F), ModelTransform.origin(0.0F, 0.5F, 0.0F));
            leftLowerLeg.addChild("left_foot", box(72, 50, -2.5F, 0.0F, -6.0F, 5.0F, 3.0F, 8.0F), ModelTransform.of(0.0F, 8.0F, 0.0F, (float) (-Math.PI) / 90.0F, 0.0F, 0.0F));
        });

        ModelPart body = root.getChild("body");
        return new CustomModel(Cosmetic.MODEL_FREDDY, Identifier.of("lynxwave", "models/freddy.png"), root, body, body.getChild("head"), body,
                body.getChild("left_arm"), body.getChild("right_arm"), body.getChild("left_leg"), body.getChild("right_leg")) {
            @Override
            void transform(MatrixStack matrices, PlayerEntityRenderState state) {
                matrices.scale(0.75F, 0.65F, 0.75F);
                matrices.translate(0.0F, 0.85F, 0.0F);
            }

            @Override
            void copyAngles(PlayerEntityModel vanilla, PlayerEntityRenderState state) {
                copyHeadAngles(vanilla);
                clearAngles(body);
                copyLimbAngles(vanilla);
            }
        };
    }

    private static CustomModel createTung() {
        ModelPart root = createRoot(64, 64, data -> {
            ModelPartData tung = data.addChild("tung", ModelPartBuilder.create(), ModelTransform.origin(0.0F, 24.0F, 0.0F));
            ModelPartData chest = tung.addChild("chest", ModelPartBuilder.create(), ModelTransform.origin(0.0F, -11.0F, 1.0F));
            chest.addChild("body", box(0, 18, -3.0F, -5.5F, -2.5F, 6.0F, 11.0F, 5.0F, 0.3F), ModelTransform.origin(0.0F, -5.5F, 0.0F));
            ModelPartData head = chest.addChild("head",
                    ModelPartBuilder.create()
                            .uv(0, 0).cuboid(-3.5F, -11.17242F, -2.89262F, 7.0F, 12.0F, 6.0F, new Dilation(0.15F))
                            .uv(35, 4).cuboid(-3.45F, -7.67242F, -2.99262F, 2.0F, 2.0F, 0.9F, new Dilation(0.5F))
                            .uv(35, 4).mirrored().cuboid(1.45F, -7.67242F, -2.99262F, 2.0F, 2.0F, 0.9F, new Dilation(0.5F)).mirrored(false)
                            .uv(36, 29).cuboid(-4.1F, -8.42242F, -3.69262F, 3.25F, 0.5F, 2.25F)
                            .uv(36, 29).cuboid(0.85F, -8.42242F, -3.69262F, 3.25F, 0.5F, 2.25F),
                    ModelTransform.origin(0.0F, -12.12758F, -0.10738F)
            );
            head.addChild("nose", box(30, 24, -1.0F, -2.5F, -1.0F, 1.5F, 5.0F, 2.0F, 0.15F), ModelTransform.of(0.25F, -5.67242F, -2.64262F, 0.34906585F, 0.0F, 0.0F));
            head.addChild("brow_left", box(30, 31, -1.5F, -0.5F, -1.0F, 3.0F, 1.0F, 2.0F), ModelTransform.origin(-2.25F, -9.27242F, -2.64262F));
            head.addChild("brow_right", box(30, 31, -1.5F, -0.5F, -1.0F, 3.0F, 1.0F, 2.0F), ModelTransform.origin(2.25F, -9.27242F, -2.64262F));
            head.addChild("eye_left", box(6, 34, -0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, 0.4F), ModelTransform.origin(-2.35F, -5.97242F, -2.69262F));
            head.addChild("eye_right", box(6, 34, -0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, 0.4F), ModelTransform.origin(2.35F, -5.97242F, -2.69262F));

            ModelPartData leftArm = chest.addChild("left_arm", box(26, 0, 0.25F, 5.15F, -1.0F, 2.0F, 5.0F, 2.0F, 0.1F), ModelTransform.origin(3.75F, -10.35F, 0.0F));
            leftArm.addChild("left_arm_top", box(22, 24, -1.0F, -3.0F, -1.0F, 2.0F, 6.0F, 2.0F), ModelTransform.of(0.75F, 2.35F, 0.0F, 0.0F, 0.0F, 0.17453292F));
            ModelPartData rightArm = chest.addChild("right_arm", box(26, 0, -2.25F, 5.15F, -1.0F, 2.0F, 5.0F, 2.0F, 0.1F), ModelTransform.origin(-3.75F, -10.35F, 0.0F));
            rightArm.addChild("right_arm_top", box(22, 24, -1.0F, -3.0F, -1.0F, 2.0F, 6.0F, 2.0F), ModelTransform.of(-0.75F, 2.35F, 0.0F, 0.0F, 0.0F, -0.17453292F));
            rightArm.addChild("bat", box(34, 6, 0.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F), ModelTransform.of(-1.25F, 9.1F, 0.1F, -1.5707964F, 1.134464F, -1.5707964F));

            ModelPartData leftLeg = tung.addChild("left_leg",
                    ModelPartBuilder.create()
                            .uv(26, 7).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new Dilation(0.15F))
                            .uv(22, 32).cuboid(-1.0F, 3.0F, -1.0F, 2.0F, 2.0F, 2.0F, new Dilation(0.3F))
                            .uv(26, 0).cuboid(-1.0F, 4.0F, -1.0F, 2.0F, 5.0F, 2.0F)
                            .uv(22, 18).cuboid(-1.5F, 9.0F, -3.0F, 3.0F, 2.0F, 4.0F),
                    ModelTransform.origin(2.0F, -11.0F, 1.0F)
            );
            ModelPartData rightLeg = tung.addChild("right_leg",
                    ModelPartBuilder.create()
                            .uv(26, 7).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new Dilation(0.15F))
                            .uv(22, 32).cuboid(-1.0F, 3.0F, -1.0F, 2.0F, 2.0F, 2.0F, new Dilation(0.3F))
                            .uv(26, 0).cuboid(-1.0F, 4.0F, -1.0F, 2.0F, 5.0F, 2.0F)
                            .uv(22, 18).cuboid(-1.5F, 9.0F, -3.0F, 3.0F, 2.0F, 4.0F),
                    ModelTransform.origin(-2.0F, -11.0F, 1.0F)
            );
        });

        ModelPart tung = root.getChild("tung");
        ModelPart chest = tung.getChild("chest");
        return new CustomModel(Cosmetic.MODEL_TUNG, Identifier.of("lynxwave", "models/tung.png"), root, tung, chest.getChild("head"), chest,
                chest.getChild("left_arm"), chest.getChild("right_arm"), tung.getChild("left_leg"), tung.getChild("right_leg"));
    }

    private static CustomModel createDemon(String name, String texturePath, int textureWidth, int textureHeight) {
        ModelPart root = createRoot(textureWidth, textureHeight, data -> {
            ModelPartData body = data.addChild("body", box(0, 16, -4.5F, 0.0F, -2.5F, 9.0F, 13.0F, 5.0F), ModelTransform.origin(0.0F, 0.0F, 0.0F));
            ModelPartData head = data.addChild("head", box(0, 0, -4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0.25F), ModelTransform.origin(0.0F, 0.0F, 0.0F));
            head.addChild("left_horn", box(32, 8, 0.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F, 0.1F), ModelTransform.of(3.0F, -7.0F, 0.0F, -0.35F, -0.35F, 0.65F));
            head.addChild("right_horn", box(32, 8, -6.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F, 0.1F), ModelTransform.of(-3.0F, -7.0F, 0.0F, -0.35F, 0.35F, -0.65F));
            data.addChild("left_arm", box(24, 16, -1.0F, -2.0F, -2.0F, 4.0F, 14.0F, 4.0F), ModelTransform.origin(5.0F, 2.0F, 0.0F));
            data.addChild("right_arm", box(24, 16, -3.0F, -2.0F, -2.0F, 4.0F, 14.0F, 4.0F), ModelTransform.origin(-5.0F, 2.0F, 0.0F));
            data.addChild("left_leg", box(48, 22, -2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), ModelTransform.origin(2.0F, 12.0F, 0.0F));
            data.addChild("right_leg", box(48, 22, -2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), ModelTransform.origin(-2.0F, 12.0F, 0.0F));
            body.addChild("left_wing", box(40, 12, -1.0F, 0.0F, 0.0F, 12.0F, 13.0F, 0.0F), ModelTransform.of(4.0F, 1.0F, 3.0F, 0.1F, -0.8F, 0.2F));
            body.addChild("right_wing", box(40, 12, -11.0F, 0.0F, 0.0F, 12.0F, 13.0F, 0.0F), ModelTransform.of(-4.0F, 1.0F, 3.0F, 0.1F, 0.8F, -0.2F));
        });

        return new CustomModel(name, Identifier.of("lynxwave", texturePath), root, root, root.getChild("head"), root.getChild("body"),
                root.getChild("left_arm"), root.getChild("right_arm"), root.getChild("left_leg"), root.getChild("right_leg"));
    }

    private static ModelPart createRoot(int textureWidth, int textureHeight, Consumer<ModelPartData> builder) {
        ModelData data = new ModelData();
        builder.accept(data.getRoot());
        return TexturedModelData.of(data, textureWidth, textureHeight).createModel();
    }

    private static ModelPartBuilder box(int u, int v, float x, float y, float z, float width, float height, float depth) {
        return ModelPartBuilder.create().uv(u, v).cuboid(x, y, z, width, height, depth);
    }

    private static ModelPartBuilder box(int u, int v, float x, float y, float z, float width, float height, float depth, float dilation) {
        return ModelPartBuilder.create().uv(u, v).cuboid(x, y, z, width, height, depth, new Dilation(dilation));
    }

    private static class CustomModel {
        protected final String name;
        protected final Identifier texture;
        protected final ModelPart root;
        protected final ModelPart bodyRoot;
        protected final ModelPart head;
        protected final ModelPart body;
        protected final ModelPart leftArm;
        protected final ModelPart rightArm;
        protected final ModelPart leftLeg;
        protected final ModelPart rightLeg;

        protected CustomModel(
                String name,
                Identifier texture,
                ModelPart root,
                ModelPart bodyRoot,
                ModelPart head,
                ModelPart body,
                ModelPart leftArm,
                ModelPart rightArm,
                ModelPart leftLeg,
                ModelPart rightLeg
        ) {
            this.name = name;
            this.texture = texture;
            this.root = root;
            this.bodyRoot = bodyRoot;
            this.head = head;
            this.body = body;
            this.leftArm = leftArm;
            this.rightArm = rightArm;
            this.leftLeg = leftLeg;
            this.rightLeg = rightLeg;
        }

        void transform(MatrixStack matrices, PlayerEntityRenderState state) {
        }

        void copyAngles(PlayerEntityModel vanilla, PlayerEntityRenderState state) {
            copyHeadAngles(vanilla);
            copyBodyAngles(vanilla);
            copyLimbAngles(vanilla);
        }

        protected void copyHeadAngles(PlayerEntityModel vanilla) {
            head.pitch = vanilla.head.pitch;
            head.yaw = vanilla.head.yaw;
            head.roll = vanilla.head.roll;
        }

        protected void copyBodyAngles(PlayerEntityModel vanilla) {
            body.pitch = vanilla.body.pitch;
            body.yaw = vanilla.body.yaw;
            body.roll = vanilla.body.roll;
        }

        protected void copyLimbAngles(PlayerEntityModel vanilla) {
            leftArm.pitch = vanilla.leftArm.pitch;
            leftArm.yaw = vanilla.leftArm.yaw;
            leftArm.roll = vanilla.leftArm.roll;

            rightArm.pitch = vanilla.rightArm.pitch;
            rightArm.yaw = vanilla.rightArm.yaw;
            rightArm.roll = vanilla.rightArm.roll;

            leftLeg.pitch = vanilla.leftLeg.pitch;
            leftLeg.yaw = vanilla.leftLeg.yaw;
            leftLeg.roll = vanilla.leftLeg.roll;

            rightLeg.pitch = vanilla.rightLeg.pitch;
            rightLeg.yaw = vanilla.rightLeg.yaw;
            rightLeg.roll = vanilla.rightLeg.roll;
        }

        protected void clearAngles(ModelPart part) {
            part.pitch = 0.0F;
            part.yaw = 0.0F;
            part.roll = 0.0F;
        }
    }

    private CustomPlayerModelRenderer() {
    }
}
