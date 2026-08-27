package dev.client.api.nullcry.modules;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.modules.core.combat.*;
import dev.client.modules.core.misc.*;
import dev.client.modules.core.movement.*;
import dev.client.modules.core.player.*;
import dev.client.modules.core.render.*;
import dev.client.modules.core.render.motionblur.MotionBlur;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ModuleManager extends CopyOnWriteArrayList<Module> implements ClientApi {

    public void init() {
        registerModule(
                // Combat
                new AntiBot(),
                new KillAura(),
                new AutoExplosion(),
                new AutoAnchor(),
                new AutoSwap(),
                new AutoTotem(),
                new Criticals(),
                new HitBoxes(),
                new NoFriendDamage(),
                new NoEntityTrace(),
                new NoServerDesync(),
                // new ProjectionHelper(),
                new TriggerBot(),
                new Velocity(),
                new HitSound(),

                // Movement
                new ElytraMotion(),
                new AirJump(),
                new AirStuck(),
                new HighJump(),
                new Flight(),
                new ElytraGlide(),
                new FreeLook(),
                new FreeCamera(),
                new TargetStrafe(),
                new TargetPearl(),
                new GuiWalk(),
                new NoJumpDelay(),
                new NoSlow(),
                new NoPush(),
                new NoWeb(),
                new Sprint(),
                new Spider(),
                new Speed(),
                new Strafe(),
                new WaterSpeed(),
                new WaterLeave(),
                new Timer(),
                new DogFly(),
                new ElytraBooster(),
                //new AresSpeed(),

                // Player
                new AntiAFK(),
                new AutoFix(),
                new AutoUse(),
                new AutoPilot(),
                new AutoPotion(),
                new AutoRespawn(),
                new AutoArmor(),
                new AutoTool(),
                new AutoFish(),
                new InventoryPlus(),
                new ItemScroller(),
                new NoInteract(),
                new FastBreak(),
                new FastPlace(),
                new TapeMouse(),
                new AutoMessage(),
                new CreeperFarm(),

                // MISCELLANEOUS
                new ElytraHelper(),
               // new FakePearl(),
                new AirPlace(),
                new FastExp(),
                new ActionDetect(),
                new AutoLeave(),
                new AutoBuy(),
                new AutoAuth(),
                new AutoDuel(),
                new AutoAccept(),
                new AutoBrewPotion(),
                new AutoClanUpgrade(),
                new BaseFinder(),
                new Xray(),
                new ChestStealer(),
                new AutoChestWarden(),
                new ClickHelper(),
                new AHHelper(),
                new ServerHelper(),
                new ReallyWorldHelper(),
                new PotionCombiner(),
                new DiscordRPC(),
                new LockSlot(),
                new NameProtect(),
                new IRC(),
                new SRPSpoof(),
                new TPLoot(),
                new UseTracker(),
                new TotemSound(),

                // Render
                new ArmorDurability(),
                new BlockHighLight(),
                new ContainerESP(),
                new Crosshair(),
                new Cosmetic(),
                new Arrows(),
                new SmoothCamera(),
                new EntityESP(),
                new FriendMarkers(),
                new NameTags(),
                new HitEffect(),
                new LineGlyphs(),
                new PVPAI(),
                new Zoom(),
                new AspectRatio(),
                new CustomWorld(),
                new Rain(),
                new FullBright(),
                new SeeInvisible(),
                new NoRender(),
                new ViewModel(),
                new JumpCircle(),
                //new JumpCircles(),
                new Particles(),
                new WorldParticles(),
                new ItemPhysic(),
                //
                new Trails(),
                new MotionBlur(),
                new Interface(),
                new SwordAnimation(),
               // new GlassHands(),
                new ShaderHand(),
                new Prediction(),
                new ExtraTab(),
                new TargetEsp(),
                new Chat()
        );
        LynxWave.getInstance().getEventBus().register(this);
    }

    private void registerModule(Module... modules) {
        addAll(Arrays.asList(modules));
    }

    public List<Module> getModules() {
        return new java.util.ArrayList<>(this);
    }

    public <T extends Module> T get(final Class<T> clazz) {
        return this.stream()
                .filter(module -> module.getClass().equals(clazz))
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    public <T extends Module> T get(final String name, Class<T> clazz) {
        return this.stream()
                .filter(module -> module.getName().equalsIgnoreCase(name) && clazz.isInstance(module))
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    public List<Module> get(final ModuleCategory moduleCategory) {
        return this.stream().filter(module -> module.getModuleCategory() == moduleCategory)
                .sorted(java.util.Comparator.comparingInt(this::indexOf))
                .toList();
    }


    @Subscribe
    private void onKey(KeyBindEvent event) {
        int pressedKey = event.getKey();
        if (pressedKey == GLFW.GLFW_KEY_UNKNOWN || pressedKey == 0) return;

        for (Module module : this) {
            Integer moduleKey = module.getKey();
            if (moduleKey == null || moduleKey == GLFW.GLFW_KEY_UNKNOWN || moduleKey == 0) continue;

            if (moduleKey == pressedKey) {
                module.toggle();
            }
        }
    }
}
