package dev.client.api.injection;

import dev.client.modules.core.render.NoRender;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixins<S extends BipedEntityRenderState, M extends BipedEntityModel<S>, A extends BipedEntityModel<S>> extends FeatureRenderer<S, M> {

    public ArmorFeatureRendererMixins(FeatureRendererContext<S, M> context) {
        super(context);
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    public void onRenderArmor(MatrixStack matrices, OrderedRenderCommandQueue renderCommandQueue, ItemStack stack, EquipmentSlot slot, int light, S state, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("Armor")) ci.cancel();
    }
}
