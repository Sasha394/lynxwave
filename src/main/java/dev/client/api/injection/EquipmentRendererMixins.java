package dev.client.api.injection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.client.api.nullcry.helper.entity.EntityRenderStateOwner;
import dev.client.modules.core.render.ArmorDurability;
import dev.client.modules.core.render.Beautifully;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixins {
    @Unique private static final ThreadLocal<ItemStack> AR_STACK = new ThreadLocal<>();
    @Unique private static final ThreadLocal<Object> LYNX_STATE = new ThreadLocal<>();

    @Inject(method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V", at = @At("HEAD"))
    private void armorTint$head(EquipmentModel.LayerType layerType, RegistryKey<EquipmentAsset> assetKey, Model<?> model, Object state, ItemStack stack, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Identifier texture, int outlineColor, int initialOrder, CallbackInfo ci) {
        AR_STACK.set(stack);
        LYNX_STATE.set(state);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V", at = @At("RETURN"))
    private void armorTint$return(EquipmentModel.LayerType layerType, RegistryKey<EquipmentAsset> assetKey, Model<?> model, Object state, ItemStack stack, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Identifier texture, int outlineColor, int initialOrder, CallbackInfo ci) {
        AR_STACK.remove();
        LYNX_STATE.remove();
    }

    @ModifyArg(method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"), index = 6)
    private int armorTint$modifyColorArg(int color) {
        if (lynx$fadingLocalPlayer()) color = Beautifully.applyFade(color);
        if (!ArmorDurability.INSTANCE.isEnabled()) return color;
        ItemStack stack = AR_STACK.get();
        if (stack == null) return color;
        float[] mul = computeTint(stack);
        return multiplyArgb(color, mul[0], mul[1], mul[2]);
    }

    @Unique
    private static float[] computeTint(ItemStack stack) {
        float durability = 1.0f;
        if (!stack.isEmpty() && stack.isDamaged()) {
            int max = stack.getMaxDamage();
            int dmg = stack.getDamage();
            if (max > 0) durability = (float)(max - dmg) / (float)max;
        }
        return mapDurabilityToTint(durability);
    }

    @Unique
    private static int multiplyArgb(int argb, float mr, float mg, float mb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>>  8) & 0xFF;
        int b = (argb)        & 0xFF;
        r = clamp(Math.round(r * mr));
        g = clamp(Math.round(g * mg));
        b = clamp(Math.round(b * mb));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Unique private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

    @Unique
    private static float[] mapDurabilityToTint(float d) {
        int interval = (int)(d * 100f / 5f);
        float r,g,b;
        switch (interval) {
            case 20: r=1f; g=1f; b=1f; break;
            case 19: r=0.8f; g=1f; b=0.8f; break;
            case 18: r=0.6f; g=1f; b=0.6f; break;
            case 17: r=0.4f; g=1f; b=0.4f; break;
            case 16: r=0.2f; g=1f; b=0.2f; break;
            case 15: r=0f; g=1f; b=0f; break;
            case 14: r=0f; g=0.9f; b=0f; break;
            case 13: r=0f; g=0.8f; b=0f; break;
            case 12: r=0f; g=0.7f; b=0f; break;
            case 11: r=0f; g=0.6f; b=0f; break;
            case 10: r=0.5f; g=1f; b=0f; break;
            case 9:  r=0.7f; g=1f; b=0f; break;
            case 8:  r=0.9f; g=1f; b=0f; break;
            case 7:  r=1f; g=1f; b=0f; break;
            case 6:  r=1f; g=0.8f; b=0f; break;
            case 5:  r=1f; g=0.6f; b=0f; break;
            case 4:  r=1f; g=0.4f; b=0f; break;
            case 3:  r=1f; g=0.2f; b=0f; break;
            case 2:  r=1f; g=0f; b=0f; break;
            case 1:  r=0.8f; g=0f; b=0f; break;
            case 0:  r=0.6f; g=0f; b=0f; break;
            default: r=1f; g=1f; b=1f; break;
        }
        return new float[]{r,g,b};
    }

    /** Идёт ли сейчас переход камеры Beautifully на модели локального игрока. */
    @Unique
    private static boolean lynx$fadingLocalPlayer() {
        if (!Beautifully.playerFading()) return false;
        Object state = LYNX_STATE.get();
        return state instanceof EntityRenderState renderState
                && ((EntityRenderStateOwner) renderState).lynx$getEntity() == MinecraftClient.getInstance().player;
    }

    // Броня рисуется слоем cutout — на время перехода подменяем его на translucent.
    @WrapOperation(method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayers;armorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer lynx$beautifullyArmorLayer(Identifier texture, Operation<RenderLayer> original) {
        return lynx$fadingLocalPlayer()
                ? RenderLayers.itemEntityTranslucentCull(texture)
                : original.call(texture);
    }
}
