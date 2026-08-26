package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.api.injection.accessor.IScreenInvoker;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.modules.core.misc.AHHelper;
import dev.client.modules.core.player.ItemScroller;
import dev.client.modules.core.combat.AutoSwap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixins<T extends ScreenHandler> {
    @Shadow @Final protected T handler;
    @Shadow protected abstract boolean isPointOverSlot(Slot slot, double pointX, double pointY);
    @Shadow protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);
    @Shadow @Nullable protected Slot focusedSlot;
    @Unique private ButtonWidget takeBtn, holdBtn, throwBtn;
    @Unique private final TimerUtil timerUtil = new TimerUtil();

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!(this.handler instanceof GenericContainerScreenHandler || this.handler instanceof ShulkerBoxScreenHandler)) return;
        HandledScreen<?> self = (HandledScreen<?>) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.isCreative()) return;

        String takeText = "Take";
        String holdText = "Hold";
        String throwText = "Throw all";

        if (mc.getLanguageManager().getLanguage().equals("ru_ru")) {
            takeText = "Забрать";
            holdText = "Сложить";
            throwText = "Выбросить все";
        }

        int left = self.x + self.backgroundWidth + 3;
        int top = self.y;

        takeBtn = ButtonWidget.builder(Text.of(takeText), b -> onTakePressed()).dimensions(left, top + 5, 80, 20).build();
        holdBtn = ButtonWidget.builder(Text.of(holdText), b -> onHoldPressed()).dimensions(left, top + 25, 80, 20).build();
        throwBtn = ButtonWidget.builder(Text.of(throwText), b -> onThrowAllPressed()).dimensions(left, top + 45, 80, 20).build();

        var delayOpt = new SimpleOption<>(
                "lynxwave.delay",
                SimpleOption.emptyTooltip(),
                (label, v) -> Text.literal("Задержка: " + String.format("%.2f", v)),
                SimpleOption.DoubleSliderCallbacks.INSTANCE,
                0.10D,
                v -> ((LynxWave.DelayHolder) mc.options).lumora$setChestScreenDelay(v)
        );

        delayOpt.setValue(((LynxWave.DelayHolder) mc.options).lumora$getChestScreenDelay());
        ((IScreenInvoker) this).lumora$addDrawableChild(delayOpt.createWidget(mc.options, self.x + self.backgroundWidth + 3, self.y + 66, 80));
        IScreenInvoker inv = (IScreenInvoker) this;
        inv.lumora$addDrawableChild(takeBtn);
        inv.lumora$addDrawableChild(holdBtn);
        inv.lumora$addDrawableChild(throwBtn);
        updateButtons();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        for (int i1 = 0; i1 < this.handler.slots.size(); ++i1) {
            Slot slot = this.handler.slots.get(i1);
            if (this.isPointOverSlot(slot, mouseX, mouseY) && slot.isEnabled()) {
                if (ItemScroller.INSTANCE.isEnabled() && GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), 0) == 1 && GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), 340) == 1 && MinecraftClient.getInstance().currentScreen != null && this.timerUtil.isReached(ItemScroller.INSTANCE.delay.getValue().longValue()) && slot.hasStack()) {
                    this.onMouseClick(slot, slot.id, 1, SlotActionType.QUICK_MOVE);
                    this.timerUtil.reset();
                }
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("TAIL"), cancellable = true)
    private void onKeyPressed(KeyInput keyInput, CallbackInfoReturnable<Boolean> ci) {
        if (!MinecraftClient.getInstance().options.dropKey.matchesKey(keyInput)) return;
        if (!ItemScroller.INSTANCE.isEnabled() || !dev.client.api.compat.InputCompat.isShiftDown() || !dev.client.api.compat.InputCompat.isControlDown()) return;

        if (this.focusedSlot == null || !this.focusedSlot.hasStack()) return;

        ItemStack clicked = this.focusedSlot.getStack();
        if (clicked.isEmpty()) return;

        for (Slot s : this.handler.slots) {
            if (s.hasStack() && s.getStack().isOf(clicked.getItem())) {
                MinecraftClient.getInstance().interactionManager.clickSlot(this.handler.syncId, s.id, 1, SlotActionType.THROW, MinecraftClient.getInstance().player);
            }
        }

        ci.setReturnValue(true);
        ci.cancel();
    }

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void onMouseClickAutoSwap(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (button != 0 || slot == null || !slot.hasStack()) return;

        AutoSwap autoSwap = AutoSwap.INSTANCE;
        if (autoSwap == null || !autoSwap.isWaitingForSelection()) return;

        autoSwap.acceptSelection(slot.getStack());
        ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onCloseAutoSwap(CallbackInfo ci) {
        AutoSwap autoSwap = AutoSwap.INSTANCE;
        if (autoSwap == null || !autoSwap.isWaitingForSelection()) {
            return;
        }
        if ((Object) this instanceof InventoryScreen) {
            autoSwap.cancelSelection();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (this.handler instanceof GenericContainerScreenHandler || this.handler instanceof ShulkerBoxScreenHandler)
            updateButtons();

        AHHelper.INSTANCE.analyze(this.handler);
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        AHHelper helper = AHHelper.INSTANCE;
        if (!helper.isEnabled() || !helper.isAuctionScreenActive()) {
            return;
        }

        if (slot == helper.getCheapestSlot()) {
            helper.renderCheapest(context, slot);
        } else if (slot == helper.getBestUnitSlot()) {
            helper.renderBest(context, slot);
        } else if (helper.isHighlightThree() && helper.getAdditionalUnitSlot() != null && slot == helper.getAdditionalUnitSlot()) {
            helper.renderAdditional(context, slot);
        }
    }

    @Unique
    private void updateButtons() {
        boolean hasAny = this.handler.slots.stream().anyMatch(Slot::hasStack);
        boolean hasChest = this.handler.slots.stream().anyMatch(s -> !(s.inventory instanceof PlayerInventory) && s.hasStack());
        if (takeBtn != null) takeBtn.active = hasChest;
        if (holdBtn != null) holdBtn.active = hasAny;
        if (throwBtn != null) throwBtn.active = hasChest;
    }

    @Unique
    private void onTakePressed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        new Thread(() -> {
            for (int i = 0; i < this.handler.slots.size(); i++) {
                Slot s = this.handler.getSlot(i);
                if (!(s.inventory instanceof PlayerInventory) && s.hasStack()) {
                    sleepDelay();
                    mc.execute(() -> mc.interactionManager.clickSlot(this.handler.syncId, s.id, 0, SlotActionType.QUICK_MOVE, mc.player));
                }
            }
        }, "LynxWave-Take").start();
    }

    @Unique
    private void onHoldPressed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        new Thread(() -> {
            for (int i = 0; i < this.handler.slots.size(); i++) {
                Slot s = this.handler.getSlot(i);
                if (s.hasStack()) {
                    sleepDelay();
                    mc.execute(() -> mc.interactionManager.clickSlot(this.handler.syncId, s.id, 0, SlotActionType.QUICK_MOVE, mc.player));
                }
            }
        }, "LynxWave-Hold").start();
    }

    @Unique
    private void onThrowAllPressed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        new Thread(() -> {
            for (int i = 0; i < this.handler.slots.size(); i++) {
                Slot s = this.handler.getSlot(i);
                if (s.hasStack() && !(s.inventory instanceof PlayerInventory)) {
                    sleepDelay();
                    int id = s.id;
                    mc.execute(() -> {
                        mc.interactionManager.clickSlot(this.handler.syncId, id, 0, SlotActionType.PICKUP, mc.player);
                        mc.interactionManager.clickSlot(this.handler.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
                    });
                }
            }
        }, "LynxWave-Throw").start();
    }

    @Unique
    private void sleepDelay() {
        double sec = ((LynxWave.DelayHolder) MinecraftClient.getInstance().options).lumora$getChestScreenDelay();
        try {
            Thread.sleep((long) (sec * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
