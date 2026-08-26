package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import com.mojang.datafixers.util.Pair;
import dev.client.api.nullcry.events.core.other.ScoreBoardEvent;
import dev.client.api.nullcry.helper.player.ItemUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.render.ColorUtils;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.List;

public class AHHelper extends Module {
    public static AHHelper INSTANCE;

    public AHHelper() {
        super("AH Helper", ModuleCategory.Misc, "Помощник в поиске выгодных предметов на аукционе");
    }

    public final CheckBox highlightThree = new CheckBox("Подсвечивать 3 слота", () -> true)
            .defaultValue(false)
            .register(this);

    public final ColorPicker thirdSlotColor = new ColorPicker("Цвет дополнительного", highlightThree::getEnabled)
            .defaultValue(ColorUtils.rgba(140, 64, 196, 255))
            .register(this);

    public final CheckBox checkBalance = new CheckBox("Учитывать баланс", () -> true)
            .defaultValue(false)
            .register(this);

    public final CheckBox onlyUndamaged = new CheckBox("Без повреждений", () -> true)
            .defaultValue(false)
            .register(this);

    public final CheckBox onlyEnchanted = new CheckBox("Только с чарами", () -> true)
            .defaultValue(false)
            .register(this);

    public final ColorPicker cheapSlotColor = new ColorPicker("Цвет дешевого", () -> true)
            .defaultValue(ColorUtils.rgba(140, 64, 255, 255))
            .register(this);

    public final ColorPicker goodSlotColor = new ColorPicker("Цвет выгодного", () -> true)
            .defaultValue(ColorUtils.rgba(140, 255, 255, 255))
            .register(this);

    private int balance = -1;

    @Getter private Slot cheapestSlot;
    @Getter private Slot bestUnitSlot;
    @Getter private Slot additionalUnitSlot;
    @Getter private boolean auctionScreenActive = false;

    private int cheapestPrice = Integer.MAX_VALUE;
    private int bestUnitPrice = Integer.MAX_VALUE;
    private int additionalUnitPrice = Integer.MAX_VALUE;

    @Subscribe
    public void onScoreBoardEvent(ScoreBoardEvent event) {
        List<Pair<ScoreboardEntry, Text>> lines = event.getList();
        if (lines.isEmpty()) {
            resetBalance();
            return;
        }

        for (Pair<ScoreboardEntry, Text> pair : lines) {
            String component = pair.getSecond().getString();
            if (component.contains("Монет:")) {
                String[] splitted = component.split(":");
                if (splitted.length > 1) {
                    String digits = splitted[1].replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        try {
                            balance = Integer.parseInt(digits);
                            return;
                        } catch (NumberFormatException ignored) {
                            resetBalance();
                            return;
                        }
                    }
                }
                resetBalance();
                return;
            }
        }
    }

    public void analyze(ScreenHandler handler) {
        if (!isEnabled()) {
            auctionScreenActive = false;
            clearHighlights();
            return;
        }

        if (!isAuction(handler)) {
            auctionScreenActive = false;
            clearHighlights();
            return;
        }

        auctionScreenActive = true;
        cheapestSlot = null;
        bestUnitSlot = null;
        additionalUnitSlot = null;
        cheapestPrice = Integer.MAX_VALUE;
        bestUnitPrice = Integer.MAX_VALUE;
        additionalUnitPrice = Integer.MAX_VALUE;

        int slotCount = Math.min(handler.slots.size(), 44);
        for (int i = 0; i < slotCount; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            if (onlyUndamaged.getEnabled() && stack.isDamageable() && stack.getDamage() > 0) continue;
            if (onlyEnchanted.getEnabled() && stack.getEnchantments().isEmpty()) continue;

            int price = ItemUtil.getPrice(stack);
            if (price <= 0) continue;

            if (checkBalance.getEnabled()) {
                if (balance == -1 || price == -1 || price >= balance) continue;
            }

            if (price < cheapestPrice) {
                cheapestPrice = price;
                cheapestSlot = slot;
            }

            int count = Math.max(1, stack.getCount());
            int unitPrice = price / count;

            if (unitPrice < bestUnitPrice || slot == bestUnitSlot) {
                if (slot != bestUnitSlot) {
                    additionalUnitSlot = bestUnitSlot;
                    additionalUnitPrice = bestUnitPrice;
                }
                bestUnitSlot = slot;
                bestUnitPrice = unitPrice;
            } else if (slot != bestUnitSlot && unitPrice < additionalUnitPrice) {
                additionalUnitSlot = slot;
                additionalUnitPrice = unitPrice;
            }
        }

        if (bestUnitSlot == additionalUnitSlot) {
            additionalUnitSlot = null;
        }
    }

    private void clearHighlights() {
        cheapestSlot = null;
        bestUnitSlot = null;
        additionalUnitSlot = null;
        cheapestPrice = Integer.MAX_VALUE;
        bestUnitPrice = Integer.MAX_VALUE;
        additionalUnitPrice = Integer.MAX_VALUE;
    }

    private boolean isAuction(ScreenHandler handler) {
        return handler != null
                && handler.slots.size() == 90
                && handler.getSlot(49).hasStack()
                && handler.getSlot(49).getStack().isOf(Items.NETHER_STAR);
    }

    public boolean isHighlightThree() {
        return highlightThree.getEnabled();
    }

    public void renderCheapest(DrawContext context, Slot slot) {
        fillSlot(context, slot, cheapSlotColor.getColorRGBA());
    }

    public void renderBest(DrawContext context, Slot slot) {
        fillSlot(context, slot, goodSlotColor.getColorRGBA());
    }

    public void renderAdditional(DrawContext context, Slot slot) {
        fillSlot(context, slot, thirdSlotColor.getColorRGBA());
    }

    private void fillSlot(DrawContext context, Slot slot, int color) {
        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    public void resetBalance() {
        balance = -1;
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        auctionScreenActive = false;
        clearHighlights();
        resetBalance();
    }
}
