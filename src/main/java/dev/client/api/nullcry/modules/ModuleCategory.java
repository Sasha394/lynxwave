package dev.client.api.nullcry.modules;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ModuleCategory {

    Combat('A'),
    Movement('B'),
    Render('C'),
    Player('D'),
    Misc('E');

    char icon;

}
