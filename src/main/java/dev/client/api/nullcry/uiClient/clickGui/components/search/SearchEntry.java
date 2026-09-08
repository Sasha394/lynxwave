package dev.client.api.nullcry.uiClient.clickGui.components.search;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;

/**
 * Строка выдачи поиска: либо сам модуль, либо его настройка.
 *
 * @param module  модуль, которому принадлежит строка
 * @param setting настройка или {@code null}, если строка — сам модуль
 * @param label   что писать в строке
 * @param score   штраф для сортировки; меньше — выше в списке
 */
public record SearchEntry(Module module, Setting setting, String label, int score) {

    public boolean isSetting() {
        return this.setting != null;
    }
}
