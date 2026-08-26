package dev.client.api.injection;

import dev.client.modules.core.render.Cosmetic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraFeatureRendererMixins {
    @Shadow @Final private EquipmentRenderer equipmentRenderer;
    @Shadow @Final private ElytraEntityModel model;
    @Shadow @Final private ElytraEntityModel babyModel;

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lynx$wingsRender(MatrixStack matrices, OrderedRenderCommandQueue renderCommandQueue, int light, BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        Cosmetic cosmetic = Cosmetic.INSTANCE;
        if (cosmetic == null || !cosmetic.isWingsActive()) return;
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !lynx$isLocalPlayer(playerState, client)) return;

        ItemStack itemStack = state.equippedChestStack;
        if (itemStack.isEmpty()) {
            itemStack = Items.ELYTRA.getDefaultStack();
        }

        EquippableComponent equippable = itemStack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) return;

        Identifier texture = lynx$resolveTexture(state, cosmetic);
        ElytraEntityModel elytraModel = state.baby ? this.babyModel : this.model;
        matrices.push();
        matrices.translate(0.0F, 0.0F, 0.125F);
        elytraModel.setAngles(state);
        if (equippable.assetId().isPresent()) {
            this.equipmentRenderer.render(EquipmentModel.LayerType.WINGS, equippable.assetId().get(), elytraModel, state, itemStack, matrices, renderCommandQueue, light, texture, state.outlineColor, 0);
        }
        matrices.pop();
        ci.cancel();
    }

    @Unique
    private static boolean lynx$isLocalPlayer(PlayerEntityRenderState playerState, MinecraftClient client) {
        if (playerState.id == client.player.getId()) {
            return true;
        }
        return playerState.playerName != null
                && playerState.playerName.getString().equalsIgnoreCase(client.player.getNameForScoreboard());
    }

    @Unique
    private static Identifier lynx$resolveTexture(BipedEntityRenderState state, Cosmetic cosmetic) {
        if (state instanceof PlayerEntityRenderState playerState) {
            SkinTextures skins = playerState.skinTextures;
            if (cosmetic.isWingsActive()) {
                return cosmetic.getWingsTexture();
            }
            if (skins != null && skins.elytra() != null) return skins.elytra().texturePath();
            if (skins != null && skins.cape() != null && playerState.capeVisible) return skins.cape().texturePath();
        }
        return null;
    }
}
