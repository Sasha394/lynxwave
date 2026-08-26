package dev.client.modules.core.player;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import net.minecraft.block.*;

public class NoInteract extends Module {
    public static NoInteract INSTANCE;

    public NoInteract() {
        super("NoInteract", ModuleCategory.Player, "Убирает взаимодействие с указанными объектами");
    }

    public ModeElement mode = new ModeElement("Игнорировать", () -> true)
            .set("Все блоки", "Кастом")
            .defaultValue("Кастом")
            .register(this);

    public SelectElements filter = new SelectElements("Объекты", () -> mode.isSelected("Кастом"))
            .set(
                    "Сундук",
                    "Сундук ловушка",
                    "Печь",
                    "Наковальня",
                    "Верстак",
                    "Воронка",
                    "Проигрыватель",
                    "Нотный блок",
                    "Эндер-сундук",
                    "Раздатчик",
                    "Выбрасыватель",
                    "Шалкербокс",
                    "Забор",
                    "Калитка",
                    "Люк"
            )
            .defaultValue("Сундук", "Печь")
            .register(this);

    public CheckBox onlyKillAura = new CheckBox("Только с киллаурой", () -> true).defaultValue(false).register(this);
    public CheckBox noPlaceBall = new CheckBox("Не ставить шары", () -> true).defaultValue(false).register(this);

    public static String getBlockName(Block block) {
        if (block == Blocks.CHEST) return "Сундук";
        if (block == Blocks.TRAPPED_CHEST) return "Сундук ловушка";
        if (block == Blocks.FURNACE) return "Печь";
        if (block == Blocks.ANVIL) return "Наковальня";
        if (block == Blocks.CRAFTING_TABLE) return "Верстак";
        if (block == Blocks.HOPPER) return "Воронка";
        if (block == Blocks.JUKEBOX) return "Проигрыватель";
        if (block == Blocks.NOTE_BLOCK) return "Нотный блок";
        if (block == Blocks.ENDER_CHEST) return "Эндер-сундук";
        if (block == Blocks.DISPENSER) return "Раздатчик";
        if (block == Blocks.DROPPER) return "Выбрасыватель";
        if (block instanceof ShulkerBoxBlock) return "Шалкербокс";
        if (block instanceof FenceBlock) return "Забор";
        if (block instanceof FenceGateBlock) return "Калитка";
        if (block instanceof TrapdoorBlock) return "Люк";
        return "Неизвестно";
    }
}