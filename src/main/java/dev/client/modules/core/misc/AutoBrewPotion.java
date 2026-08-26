package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

public class AutoBrewPotion extends Module {

    public AutoBrewPotion() {
        super("AutoBrewPotion", ModuleCategory.Misc, "Автоматически варит зелья");
    }

    ModeElement mode = new ModeElement("Варить", () -> true).set("Зелье скорости", "Зелье силы", "Зелье огнестойкости").defaultValue("Зелье скорости").register(this);
    Slider delay = new Slider("Задержка", () -> true).set(100, 1000, 10).defaultValue(100).register(this);
    CheckBox loot = new CheckBox("Забирать в инвентарь", () -> true).defaultValue(true).register(this);
    CheckBox addGunpowder = new CheckBox("Добавлять порох", () -> true).defaultValue(false).register(this);

    private final TimerUtil timer = new TimerUtil();

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player.currentScreenHandler instanceof BrewingStandScreenHandler) {
            if (isFuelEmpty()) {
                if (findItemInBrewingStand(Items.BLAZE_POWDER) == -1) {
                    printClient("Нет огненного порошка для заправки!");
                    toggle();
                    ;
                }
                swapOneItem(Items.BLAZE_POWDER, 4);
            }

            for (int i = 0; i < 3; i++) {
                if (getItem(i) instanceof AirBlockItem) {
                    int waterPotionSlot = findPotionInBrewingStand("water");
                    if (waterPotionSlot == -1) {
                        toggle();
                        printClient("Нет бутылок с водой!");
                    }
                    if (timer.isReached((long) (delay.getValue() * 3))) {
                        swapOneItem(waterPotionSlot, i);
                        timer.reset();
                    }
                }
            }

            if (getItem(3) instanceof AirBlockItem) {
                if (identicalPotionsCheck("water")) {
                    if (findItemInBrewingStand(Items.NETHER_WART) == -1) {
                        toggle();
                        printClient("Нет нароста!");
                    }
                    swapOneItem(Items.NETHER_WART, 3);
                }

                if (mode.isSelected("Зелье скорости")) {
                    if (identicalPotionsCheck("awkward")) {
                        if (findItemInBrewingStand(Items.SUGAR) == -1) {
                            printClient("Нет сахара для зелья скорости!");
                            toggle();
                        }
                        swapOneItem(Items.SUGAR, 3);
                    }
                } else if (mode.isSelected("Зелье силы")) {
                    if (identicalPotionsCheck("awkward")) {
                        if (findItemInBrewingStand(Items.BLAZE_POWDER) == -1) {
                            printClient("Нет огненного порошка для зелья силы!");
                            toggle();
                        }
                        swapOneItem(Items.BLAZE_POWDER, 3);
                    }
                } else if (mode.isSelected("Зелье огнестойкости")) {
                    if (identicalPotionsCheck("awkward")) {
                        if (findItemInBrewingStand(Items.MAGMA_CREAM) == -1) {
                            printClient("Нет слизи магмы для зелья огнестойкости!");
                            toggle();
                        }
                        swapOneItem(Items.MAGMA_CREAM, 3);
                    }
                }

                if (identicalPotionsCheck("strength") || identicalPotionsCheck("swiftness")) {
                    if (findItemInBrewingStand(Items.GLOWSTONE_DUST) == -1) {
                        printClient("Нет светокамня для усиления зелья!");
                        toggle();
                    }
                    swapOneItem(Items.GLOWSTONE_DUST, 3);
                }

                if (identicalPotionsCheck("fire_resistance")) {
                    if (findItemInBrewingStand(Items.REDSTONE) == -1) {
                        printClient("Нет редстоуна для увеличения длительности зелья!");
                        toggle();
                    }
                    swapOneItem(Items.REDSTONE, 3);
                }

                if (identicalPotionsCheck("strong_strength") || identicalPotionsCheck("strong_swiftness") || identicalPotionsCheck("long_fire_resistance")) {
                    if (addGunpowder.getEnabled()) {
                        if (findItemInBrewingStand(Items.GUNPOWDER) == -1) {
                            printClient("Нет пороха для создания взрывных зелий!");
                            toggle();
                        }
                        if (identicalSplashPotionsCheck()) {
                            if (loot.getEnabled()) {
                                loot();
                            }
                        } else {
                            swapOneItem(Items.GUNPOWDER, 3);
                        }
                    } else {
                        if (loot.getEnabled()) {
                            loot();
                        }
                    }
                }
            }
        }
    }

    private void loot() {
        for (int i = 0; i < 3; i++) {
            if (timer.isReached((long) (delay.getValue() * 2))) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            }
        }
    }

    private boolean identicalSplashPotionsCheck() {
        boolean needIng = true;
        for (int i = 0; i < 3; i++) {
            if (mc.player.currentScreenHandler.slots.get(i).getStack().getItem() != Items.SPLASH_POTION) {
                needIng = false;
            }
        }
        return needIng;
    }

    private boolean identicalPotionsCheck(String potionId) {
        Potion targetPotion = Registries.POTION.get(Identifier.ofVanilla(potionId));
        if (targetPotion == null) return false;

        for (int i = 0; i < 3; i++) {
            var stack = mc.player.currentScreenHandler.slots.get(i).getStack();
            if (stack.isEmpty()) return false;

            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents == null || contents.potion().isEmpty()) return false;

            Potion potion = contents.potion().get().value();
            if (!potion.equals(targetPotion)) return false;
        }

        return true;
    }

    public void swapOneItem(Item item, int to) {
        if (timer.isReached((long) (delay.getValue() * 2))) {
            int slot;
            if ((slot = findItemInBrewingStand(item)) != -1) {
                swapOneItem(slot, to);
                timer.reset();
            }
        }
    }

    public void swapOneItem(int from, int to) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, to, 1, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
    }

    private Item getItem(int slotId) {
        return mc.player.currentScreenHandler.slots.get(slotId).getStack().getItem();
    }

    private boolean isFuelEmpty() {
        return getItem(4) instanceof AirBlockItem;
    }

    private int findPotionInBrewingStand(String potionId) {
        Potion targetPotion = Registries.POTION.get(Identifier.ofVanilla(potionId));
        if (targetPotion == null) return -1;

        for (int i = 5; i < 41; i++) {
            var stack = mc.player.currentScreenHandler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents != null && contents.potion().isPresent()) {
                Potion potion = contents.potion().get().value();
                if (potion.equals(targetPotion)) {
                    return i;
                }
            }
        }

        return -1;
    }

    private int findItemInBrewingStand(Item item) {
        for (int i = 5; i < 41; i++) {
            if (getItem(i) == item) return i;
        }
        return -1;
    }
}
