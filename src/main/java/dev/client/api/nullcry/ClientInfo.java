package dev.client.api.nullcry;

/*
 * *****************************************************************************
 * *                                                                         * *
 * *                             author : NullCry                            * *
 * *            Пишу в надежде того, что этот код никто не увидит...         * *
 * *                             01.07.2025                                  * *
 * *                                                                         * *
 * *****************************************************************************
 */

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ClientInfo {
    private final String name;
    private final String nameSpace;
    private final String version;
    private final String folderPath;
    private final BuildType type;

    /**
     * Конструктор для создания информации о клиенте.
     *
     * @param name       Название клиента
     * @param version    Версия клиента
     * @param folderPath Путь до папки клиента
     * @param type       Тип билда клиента
     */
    public ClientInfo(String name, String version, String folderPath, BuildType type) {
        this.name = name;
        this.nameSpace = name.toLowerCase() + "/";
        this.version = version;
        this.folderPath = folderPath;
        this.type = type;
    }

    /**
     * Перечисление типов билда клиента.
     */
    public enum BuildType {
        Release,
        Private,
        Beta
    }
}