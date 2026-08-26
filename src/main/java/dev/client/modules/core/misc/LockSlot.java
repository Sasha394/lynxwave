package dev.client.modules.core.misc;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;

public class LockSlot extends Module {
    public static LockSlot INSTANCE;

    public LockSlot() {
        super("LockSlot", ModuleCategory.Misc, "Не дает выбросить предмет с выбранных слотов");
    }

    public ModeElement mode = new ModeElement("Режим", () -> true).set("Все слоты", "Кастом").defaultValue("Все слоты").register(this);
    public SelectElements slot = new SelectElements("Слоты", () -> mode.isSelected("Кастом")).set("1","2","3","4","5","6","7","8","9").defaultValue("1","2","3","4","5").register(this);
    public CheckBox pressedCtrl = new CheckBox("Работать с ctrl", () -> true).defaultValue(false).register(this);

    public boolean isSlotProtected(int slotIndex0to8) {
        if (!this.isEnabled()) return false;
        if (mode.isSelected("Все слоты")) return true;
        if (!mode.isSelected("Кастом")) return false;
        if (slotIndex0to8 < 0 || slotIndex0to8 > 8) return false;
        String human = String.valueOf(slotIndex0to8 + 1);
        return slot.isSelected(human);
    }
}

