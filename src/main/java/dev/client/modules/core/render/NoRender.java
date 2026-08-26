package dev.client.modules.core.render;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.SelectElements;

public class NoRender extends Module {
    public static NoRender INSTANCE;

    public NoRender() {
        super("NoRender", ModuleCategory.Render, "Убирает указанные негативные эффекты с экрана");
    }

    public final SelectElements mode = new SelectElements("Убирать", () -> true).set("NoHurtCam","Fire","Totem","Blocks","ScoreBoard","BossBar","Particles", "Bad effects", "Armor", "Fog", "Limbs","Clip").defaultValue("NoHurtCam","Fire","Particles").register(this);
}
