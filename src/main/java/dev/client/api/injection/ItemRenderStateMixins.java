package dev.client.api.injection;

import dev.client.modules.core.render.ViewModel;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderState.class)
public abstract class ItemRenderStateMixins {
    @Inject(method = "render", at = @At("HEAD"))
    private void lynx$captureFirstPersonItemBounds(MatrixStack matrices,
                                                    OrderedRenderCommandQueue renderCommandQueue,
                                                    int light,
                                                    int overlay,
                                                    int seed,
                                                    CallbackInfo ci) {
        ViewModel viewModel = ViewModel.INSTANCE;
        if (viewModel != null) {
            viewModel.captureRenderedItemBounds((ItemRenderState) (Object) this, matrices);
        }
    }
}
