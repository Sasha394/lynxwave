package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.helper.client.projection.ProjectionUtil;
import dev.client.api.nullcry.helper.client.server.core.MineViewer;
import dev.client.api.nullcry.helper.entity.world.HandSwapHelper;
import dev.client.api.nullcry.helper.entity.world.InventoryHelper;
import dev.client.api.nullcry.helper.entity.world.ItemUseHelper;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.core.modules.ServerItemsOverlay;
import dev.other.task.scripts.Script;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerHelper extends Module {
    public static ServerHelper INSTANCE;

    KeyBind useCrossbow = new KeyBind("Выстрел арбалета", this::shouldDisplayFunTimeSettings).set(-1).register(this);
    KeyBind useShulker = new KeyBind("Открытие шалкера", this::shouldDisplayFunTimeSettings).set(-1).register(this);
    KeyBind useDesorient = new KeyBind("Дезориентация", this::shouldDisplayFunTimeSettings).set(-1).register(this);
    KeyBind useTrapFT = new KeyBind("Трапка", this::shouldDisplayFunTimeSettings).set(-1).register(this);
    KeyBind useDust = new KeyBind("Явная пыль", this::shouldDisplayFunTimeSettings).set(-1).register(this);
    KeyBind usePlast = new KeyBind("Пласт", this::shouldDisplayFunTimeSettings).set(-1).register(this);
    KeyBind useFlameWhirlwind = new KeyBind("Огненный смерч", this::shouldDisplayFunTimeSettings).set(-1).register(this);
    KeyBind useDivineAura = new KeyBind("Божья Аура", this::shouldDisplayFunTimeSettings).set(-1).register(this);
    KeyBind useSnowFT = new KeyBind("Снежок заморозка", this::shouldDisplayFunTimeSettings).set(-1).register(this);

    KeyBind useExplosiveTrap = new KeyBind("Взрывная трапка", this::shouldDisplayHolyWorldSettings).set(-1).register(this);
    KeyBind useTrapHw = new KeyBind("Трапка", this::shouldDisplayHolyWorldSettings).set(-1).register(this);
    KeyBind useStan = new KeyBind("Стан", this::shouldDisplayHolyWorldSettings).set(-1).register(this);
    KeyBind useJackLamp = new KeyBind("Светильник джейка", this::shouldDisplayHolyWorldSettings).set(-1).register(this);
    KeyBind useSnowHW = new KeyBind("Ком снега", this::shouldDisplayHolyWorldSettings).set(-1).register(this);
    KeyBind useFarewellGhoul = new KeyBind("Прощальный гул", this::shouldDisplayHolyWorldSettings).set(-1).register(this);
    KeyBind useExplosiveThing = new KeyBind("Взрывная штучка", this::shouldDisplayHolyWorldSettings).set(-1).register(this);
    KeyBind useExplosiveWand = new KeyBind("Взрывная палочка", this::shouldDisplayHolyWorldSettings).set(-1).register(this);
    KeyBind backpack = new KeyBind("Рюкзак", this::shouldDisplayHolyWorldSettings).set(-1).register(this);

    public CheckBox renderBinds = new CheckBox("Рендерить кнопки предметов", () -> true).defaultValue(true).register(this);

    CheckBox autoGps = new CheckBox("Авто точка", this::isFT).defaultValue(true).register(this);
    CheckBox consumables = new CheckBox("Таймер расходников", () -> isFT() || isHW() || isSpookyTime() || isFunSky()).defaultValue(false).register(this);
    CheckBox infoMine = new CheckBox("Информация о шахте", this::isFT).defaultValue(false).register(this);

    CheckBox onlyPvP = new CheckBox("Только в пвп", () -> true).defaultValue(false).register(this);
    CheckBox onlySlots = new CheckBox("Только в слоте", () -> true).defaultValue(false).register(this);
    CheckBox onlyPlayer = new CheckBox("Использовать если рядом игрок", () -> true).defaultValue(false).register(this);
    CheckBox legitUse = new CheckBox("Использовать легитно", () -> true).defaultValue(false).register(this);
    Slider radius = new Slider("Радиус", () -> onlyPlayer.getEnabled()).set(1, 100, 1).defaultValue(50).register(this);

    final List<Structure> structures = new CopyOnWriteArrayList<>();
    final List<ServerEvent> serverEvents = new CopyOnWriteArrayList<>();

    final Map<BlockPos, BlockState> blockStateMap = new ConcurrentHashMap<>();
    final Map<BlockPos, Boolean> trapCache = new ConcurrentHashMap<>();
    final Map<BlockPos, Boolean> bigTrapCache = new ConcurrentHashMap<>();
    final Map<BlockPos, Long> trapCacheTime = new ConcurrentHashMap<>();
    final Map<BlockPos, Long> bigTrapCacheTime = new ConcurrentHashMap<>();
    static final long CACHE_DURATION = 1000L;

    final ServerItemsOverlay serverItemsOverlay;
    final MineViewer mineViewer = new MineViewer();

    final HandSwapHelper interactionHandUtil = new HandSwapHelper();
    final Script scriptConstructor = new Script();

    long delay;

    public ServerHelper() {
        super("ServerHelper", ModuleCategory.Misc, "Вспомогательная функция для копий серверов \"FunTime\", \"HolyWorld\"");
        serverItemsOverlay = new ServerItemsOverlay();
    }

    private boolean isFunTimeFamily() {
        return isFT() || isSpookyTime() || isFunSky();
    }

    private boolean shouldDisplayFunTimeSettings() {
        return !isHW();
    }

    private boolean shouldDisplayHolyWorldSettings() {
        return !isFunTimeFamily();
    }

    private boolean includeFunTimeBindings() {
        return !isHW() && (isFunTimeFamily() || !isFunTimeFamily() && !isHW());
    }

    private boolean includeHolyWorldBindings() {
        return !isFunTimeFamily() && (isHW() || !isFunTimeFamily() && !isHW());
    }

    public boolean shouldRenderFunTimeOverlay() {
        if (!renderBinds.getEnabled()) {
            return false;
        }
        if (isHW()) {
            return false;
        }

        return includeFunTimeBindings() || includeHolyWorldBindings();
    }

    public boolean shouldRenderHolyWorldOverlay() {
        return renderBinds.getEnabled() && isHW();
    }

    @Subscribe
    public void onKey(KeyBindEvent event) {
        Map<KeyBind, ItemType> bindings = new LinkedHashMap<>();

        if (includeFunTimeBindings()) {
            bindings.put(useCrossbow, ItemType.CROSSBOW);
            bindings.put(useShulker, ItemType.SHULKER);
            bindings.put(useDesorient, ItemType.DESORIENT);
            bindings.put(useTrapFT, ItemType.TRAPFT);
            bindings.put(useDust, ItemType.DUST);
            bindings.put(usePlast, ItemType.PLAST);
            bindings.put(useFlameWhirlwind, ItemType.FLAME_WHIRLWIND);
            bindings.put(useDivineAura, ItemType.DIVINE_AURA);
            bindings.put(useSnowFT, ItemType.SNOWFT);
        }

        if (includeHolyWorldBindings()) {
            bindings.put(useExplosiveTrap, ItemType.EXPLOSIVETRAP);
            bindings.put(useTrapHw, ItemType.TRAPHW);
            bindings.put(useStan, ItemType.STAN);
            bindings.put(useJackLamp, ItemType.JACKLAMP);
            bindings.put(useSnowHW, ItemType.SNOWHW);
            bindings.put(useFarewellGhoul, ItemType.FAREWELLGHOUL);
            bindings.put(useExplosiveThing, ItemType.EXPLOSIVETHING);
            bindings.put(useExplosiveWand, ItemType.EXPLOSIVEWAND);
            bindings.put(backpack, ItemType.BACKPACK);
        }

        bindings.forEach((bind, type) -> {
            if (event.getKey() == bind.getKey() && (!onlyPvP.getEnabled() || isPvP())) {
                useItemFromInventory(type);
            }
        });
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        scriptConstructor.update();
        this.interactionHandUtil.handleItemChange(System.currentTimeMillis() - this.delay > 200L);
    }

    @Subscribe
    public void onDraw2D(RenderEvent.Draw2D draw2D) {
        cleanCache();

        structures.removeIf(cons -> cons.time() <= System.currentTimeMillis());

        if (consumables.getEnabled()) {
            structures.forEach(cons -> {
                ItemStack stack = cons.stack();
                if (stack.isEmpty()) return;

                double timeLeftSec = (cons.time() - System.currentTimeMillis()) / 1000.0;
                if (timeLeftSec <= 0) return;
                String text = MathUtil.round(timeLeftSec, 0.1F) + "с";

                var camera = mc.getEntityRenderDispatcher().camera;
                if (camera == null) return;

                Vec3d camPos = camera.getCameraPos();
                Vec3d camForward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
                Vec3d toPoint = cons.vec().subtract(camPos);
                if (toPoint.lengthSquared() <= 1e-6 || camForward.dotProduct(toPoint.normalize()) <= 0) return;

                Vec3d screen = ProjectionUtil.projectCoordinates(cons.vec());
                if (cons.anarchy() != getAnarchy() || !getWorldType().equals(cons.world())) return;
                float textSize = 9f;
                float iconScale = 0.8f;
                float pad = 4f;
                float spacing = 4f;

                float iconW = 16f * iconScale;
                float iconH = 16f * iconScale;
                float textWidth = Builder.INTER.get().getWidth(text, textSize);

                float totalW = iconW + spacing + textWidth;
                float blockW = pad * 2f + totalW;
                float blockH = pad * 2f + Math.max(iconH, textSize);

                float blockX = (float) (screen.x - blockW / 2f);
                float blockY = (float) (screen.y - blockH / 2f);

                HelperElements.rectElements(draw2D.getContext(), blockX, blockY, blockW, blockH);

                float iconX = blockX + pad + 1;
                float iconY = blockY + (blockH - iconH) / 2f - 1;
                MathUtil.scaleStart(draw2D.getContext().getMatrices(), iconX, iconY, iconScale);
                draw2D.getContext().drawItem(stack, (int) iconX, (int) iconY);
                draw2D.getContext().drawStackOverlay(mc.textRenderer, stack, (int) iconX, (int) iconY, null);
                MathUtil.scaleEnd(draw2D.getContext().getMatrices());

                float textX = blockX + pad + iconW + spacing;
                float textY = blockY + (blockH - textSize) / 2f - 0.5f;

                Builder.TEXT_BUILDER
                        .font(Builder.INTER.get())
                        .size(textSize)
                        .color(-1)
                        .text(text)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(draw2D.getContext()), textX, textY);
            });

            serverEvents.forEach(event -> {
                double timeOpen = (event.timeOpen - System.currentTimeMillis()) / 1000;
                double timeEnd = (event.timeEnd - System.currentTimeMillis()) / 1000;
                String distance = " [" + MathUtil.round(mc.getEntityRenderDispatcher().camera.getCameraPos().distanceTo(event.vec), 0.1) + "m" + "]";
                String time = timeOpen > 0 ? ("До начала: " + MathUtil.round(timeOpen, timeOpen < 30 ? 0.1F : 1) + "с").replace(".0", "")
                        : timeEnd > 0 ? ("До конца: " + MathUtil.round(timeEnd, timeEnd < 30 ? 0.1F : 1) + "с").replace(".0", "")
                        : "Конец ивента!";

                if (ProjectionUtil.canSee(event.vec) && event.anarchy == getAnarchy() && getWorldType().equals(event.world)) {
                    List<String> list = new ArrayList<>(Collections.singletonList(event.name + distance));
                    if (event.owner != null) list.add("Призван: " + Formatting.GOLD + event.owner);
                    list.add(time);
                    if (event.lvl != null) list.add(event.lvl);
                }
            });
        } else {
            structures.clear();
        }
    
        serverEvents.removeIf(event -> event.timeEnd + 90000 - System.currentTimeMillis() <= 0);

        if (infoMine.getEnabled()) {
            mineViewer.onRender2D(draw2D);
        } else {
            mineViewer.removeBossBar();
        }

        serverItemsOverlay.onRender(draw2D);
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (consumables.getEnabled()) {
            if ((isFT() || isSpookyTime() || isFunSky()) && event.getPacket() instanceof ChunkDeltaUpdateS2CPacket chunkDelta) {
                chunkDelta.visitUpdates((pos, state) -> blockStateMap.put(pos.toImmutable(), state));
                if (blockStateMap.size() > 50 && blockStateMap.size() < 600) {
                    chunkDelta.visitUpdates((pos, state) -> {
                        BlockPos immutable = pos.toImmutable();
                        Vec3d vec = immutable.toCenterPos();
                        BlockPos up2 = immutable.up(2);
                        BlockPos up3 = immutable.up(3);
                        if (isTrap(up2)) {
                            addStructure(Items.NETHERITE_SCRAP.getDefaultStack(), vec, System.currentTimeMillis() + 15000L);
                        } else if (isBigTrap(up3)) {
                            addStructure(Items.NETHERITE_SCRAP.getDefaultStack(), vec, System.currentTimeMillis() + 30000L);
                        }
                    });
                }
            }

            if (isHW() && event.getPacket() instanceof PlaySoundS2CPacket soundPacket) {
                if (soundPacket.getSound().toString().contains("minecraft:block.beacon.deactivate")) {
                    addStructure(Items.NETHER_STAR.getDefaultStack(), new Vec3d(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ()), System.currentTimeMillis() + 15000L);
                }
            }

            if (isHW() && event.getPacket() instanceof ParticleS2CPacket particlePacket) {
                String particleType = particlePacket.getParameters().getType().toString();
                if (particleType.contains("ExplosionSmokeParticle")) {
                    addStructure(Items.PRISMARINE_SHARD.getDefaultStack(), new Vec3d(particlePacket.getX(), particlePacket.getY(), particlePacket.getZ()), System.currentTimeMillis() + 11000L);
                }
            }
        }

        if (event.getPacket() instanceof GameMessageS2CPacket gameMessage) {
            Text content = gameMessage.content();
            String contentString = content.toString();
            String message = content.getString();
            String name = StringUtils.substringBetween(message, "|||   [", "]   ");
            if (name != null) {
                String position = StringUtils.substringBetween(contentString, "value='/gps ", "'");
                String lvl = StringUtils.substringBetween(message, "Уровень лута: ", "\n ║");
                String owner = StringUtils.substringBetween(message, "Призван игроком: ", "\n ║");

                if (position != null) {
                    String[] pose = position.split(" ");
                    BlockPos bp = BlockPos.ofFloored(
                            Integer.parseInt(pose[0]),
                            Integer.parseInt(pose[1]),
                            Integer.parseInt(pose[2])
                    );

                    if (autoGps.getEnabled()) {
                        int x = bp.getX(), y = bp.getY(), z = bp.getZ();
                        switch (name) {
                            case "Мистический сундук", "Вулкан", "Метеоритный дождь", "Маяк убийца",
                                 "Мистический Алтарь", "Загадочный маяк" ->
                                    printChat(".gps add " + name + " " + x + " " + y + " " + z);
                        }
                    }

                    Vec3d center = bp.toCenterPos();
                    switch (name) {
                        case "Мистический сундук" -> addEvent(name, lvl, owner, center, "overworld", 300, 0);
                        case "Вулкан" -> addEvent(name, lvl, owner, center, "overworld", 300, 120);
                        case "Метеоритный дождь", "Маяк убийца", "Мистический Алтарь" ->
                                addEvent(name, lvl, owner, center, "overworld", 360, 0);
                        case "Загадочный маяк" -> addEvent(name, lvl, owner, center, "overworld", 60, 180);
                    }
                } else {
                    if (autoGps.getEnabled()) {
                        switch (name) {
                            case "Сундук смерти" -> printChat(".gps add " + name + " -155 64 205");
                            case "Адская резня" -> printChat(".gps add " + name + " 48 87 73");
                        }
                    }

                    switch (name) {
                        case "Сундук смерти" ->
                                addEvent(name, lvl, owner, BlockPos.ofFloored(-155, 64, 205).toCenterPos(), "lobby", 300, 0);
                        case "Адская резня" ->
                                addEvent(name, lvl, owner, BlockPos.ofFloored(48, 87, 73).toCenterPos(), "lobby", 180, 120);
                    }
                }
            }
        }
    }

    private void useItemFromInventory(ItemType type) {
        findAndUseItem(type);
    }

    private void findAndUseItem(ItemType type) {
        String name = type.displayName;
        Item itemType = type.item;

        int hbSlot;
        int invSlot;

        if (type == ItemType.BACKPACK) {
            hbSlot = -1;
            invSlot = -1;

            for (int i = 0; i <= 8; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!s.isEmpty() && (s.isIn(ItemTags.SHULKER_BOXES) ||
                        (s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock))) {
                    hbSlot = i;
                    break;
                }
            }

            for (int i = 9; i <= 35; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!s.isEmpty() && (s.isIn(ItemTags.SHULKER_BOXES) ||
                        (s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock))) {
                    invSlot = i;
                    break;
                }
            }
        } else {
            hbSlot = InventoryHelper.getItem(itemType, true);
            invSlot = InventoryHelper.getItem(itemType, false);
        }

        if (onlySlots.getEnabled() && hbSlot == -1) {
            printClient("Переместите предмет " + name + " в слот.");
            return;
        }

        if (invSlot == -1 && hbSlot == -1) {
            printClient("В инвентаре нет " + getItemNameInGenitive(name));
            return;
        }

        if (onlyPlayer.getEnabled() && PlayerHelper.getRadiusPlayer(radius.getValue()) == null) {
            printClient("В указанном радиусе нет игроков.");
            return;
        }

        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(itemType))) {
            double remainingCooldown = PlayerUtil.getCooldownItems().entrySet().stream().filter(entry -> entry.getKey().getItem().equals(itemType)).map(Map.Entry::getValue).findFirst().orElse(0.0);
            printClient(String.format("Подождите %s до использования %s", PlayerHelper.formatTime(remainingCooldown), getItemNameInGenitive(name)));
            return;
        }

        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(itemType))) {
            double remainingCooldown = PlayerUtil.getCooldownItems().entrySet().stream().filter(entry -> entry.getKey().getItem().equals(itemType)).map(Map.Entry::getValue).findFirst().orElse(0.0);
            printClient(String.format("Подождите %s до использования %s", PlayerHelper.formatTime(remainingCooldown), getItemNameInGenitive(name)));
            return;
        }

        final int previousSlot = mc.player.getInventory().getSelectedSlot();
        interactionHandUtil.setOriginalSlot(previousSlot);
        final int finalHbSlot = hbSlot;
        final int finalInvSlot = invSlot;
        final boolean hwLegit = isHW() && legitUse.getEnabled();

        Runnable useAction = () -> {
            if (!isEnabled()) {
                return;
            }

            int usedSlot = hwLegit
                    ? ItemUseHelper.spoofUseItemHwLegit(previousSlot, finalHbSlot, finalInvSlot, Hand.MAIN_HAND, true)
                    : ItemUseHelper.spoofUseItem(previousSlot, finalHbSlot, finalInvSlot, Hand.MAIN_HAND, true, legitUse.getEnabled());
            if (usedSlot == -1) {
                return;
            }

            delay = System.currentTimeMillis();
        };

        useAction.run();
    }

    public Map<ItemType, Integer> getKeyItems() {
        Map<ItemType, KeyBind> binds = new LinkedHashMap<>();

        if (includeFunTimeBindings()) {
            binds.put(ItemType.CROSSBOW, useCrossbow);
            binds.put(ItemType.SHULKER, useShulker);
            binds.put(ItemType.DESORIENT, useDesorient);
            binds.put(ItemType.TRAPFT, useTrapFT);
            binds.put(ItemType.DUST, useDust);
            binds.put(ItemType.PLAST, usePlast);
            binds.put(ItemType.FLAME_WHIRLWIND, useFlameWhirlwind);
            binds.put(ItemType.DIVINE_AURA, useDivineAura);
            binds.put(ItemType.SNOWFT, useSnowFT);
        }

        if (includeHolyWorldBindings()) {
            binds.put(ItemType.EXPLOSIVETRAP, useExplosiveTrap);
            binds.put(ItemType.TRAPHW, useTrapHw);
            binds.put(ItemType.STAN, useStan);
            binds.put(ItemType.JACKLAMP, useJackLamp);
            binds.put(ItemType.SNOWHW, useSnowHW);
            binds.put(ItemType.FAREWELLGHOUL, useFarewellGhoul);
            binds.put(ItemType.EXPLOSIVETHING, useExplosiveThing);
            binds.put(ItemType.EXPLOSIVEWAND, useExplosiveWand);
            binds.put(ItemType.BACKPACK, backpack);
        }

        Map<ItemType, Integer> result = new LinkedHashMap<>();
        binds.forEach((type, bind) -> {
            int key = bind.getKey();
            if (key != 0 && key != -1) result.put(type, key);
        });
        return result;
    }

    private String getItemNameInGenitive(String name) {
        return Arrays.stream(ItemType.values())
                .filter(t -> t.displayName.equalsIgnoreCase(name))
                .map(t -> t.genitiveName)
                .findFirst().orElse(name);
    }

    private void addStructure(ItemStack stack, Vec3d vec, long time) {
        ItemStack displayStack = stack.copy();
        if (displayStack.isEmpty()) {
            return;
        }

        displayStack.setCount(1);
        Structure updated = new Structure(displayStack, vec, getWorldType(), getAnarchy(), time);

        for (int i = 0; i < structures.size(); i++) {
            Structure existing = structures.get(i);
            if (existing.vec().equals(vec)) {
                structures.set(i, updated);
                return;
            }
        }

        structures.add(updated);
    }

    private void addEvent(String name, String lvl, String owner, Vec3d vec3d, String world, int timeOpen, int timeLoot) {
        if (serverEvents.stream().noneMatch(server -> server.vec.equals(vec3d))) {
            long open = System.currentTimeMillis() + timeOpen * 1000L;
            long loot = open + timeLoot * 1000L;
            serverEvents.add(new ServerEvent(name, lvl, owner, vec3d, world, getAnarchy(), open, loot));
        }
    }

    private boolean isTrap(BlockPos center) {
        long now = System.currentTimeMillis();
        Long cachedTime = trapCacheTime.get(center);
        if (cachedTime != null && now - cachedTime < CACHE_DURATION) {
            return trapCache.getOrDefault(center, false);
        }

        boolean result = checkTrap(center);
        trapCache.put(center.toImmutable(), result);
        trapCacheTime.put(center.toImmutable(), now);
        return result;
    }

    private boolean checkTrap(BlockPos center) {
        int inconsistencies = 0;
        Vec3d centerVec = center.toCenterPos();
        Set<BlockPos> corners = Set.of(
                center.up(2).north().east(),
                center.up(2).north().west(),
                center.up(2).south().east(),
                center.up(2).south().west()
        );

        for (BlockPos pos : PlayerUtil.getCube(center, 2, 2)) {
            BlockPos immutable = pos.toImmutable();
            double distance = immutable.toCenterPos().distanceTo(centerVec);
            BlockState state = blockStateMap.get(immutable);

            if (distance < 2) {
                if (state != null && !state.isAir()) inconsistencies++;
            } else if (!corners.contains(immutable)) {
                if (state == null || state.isAir()) inconsistencies++;
            }

            if (inconsistencies > 1) return false;
        }

        return true;
    }

    private boolean isBigTrap(BlockPos center) {
        long now = System.currentTimeMillis();
        Long cachedTime = bigTrapCacheTime.get(center);
        if (cachedTime != null && now - cachedTime < CACHE_DURATION) {
            return bigTrapCache.getOrDefault(center, false);
        }

        boolean result = checkBigTrap(center);
        bigTrapCache.put(center.toImmutable(), result);
        bigTrapCacheTime.put(center.toImmutable(), now);
        return result;
    }

    private boolean checkBigTrap(BlockPos center) {
        int inconsistencies = 0;
        for (BlockPos pos : PlayerUtil.getCube(center, 3, 3)) {
            BlockPos immutable = pos.toImmutable();
            BlockState state = blockStateMap.get(immutable);
            int dx = Math.abs(immutable.getX() - center.getX());
            int dy = Math.abs(immutable.getY() - center.getY());
            int dz = Math.abs(immutable.getZ() - center.getZ());

            if (dx <= 2 && dy <= 2 && dz <= 2) {
                if (state != null && !state.isAir()) inconsistencies++;
            } else if (!immutable.equals(center.up(3))) {
                if (state == null || state.isAir()) inconsistencies++;
            }

            if (inconsistencies > 1) return false;
        }

        return true;
    }

    private void cleanCache() {
        long now = System.currentTimeMillis();

        trapCacheTime.entrySet().removeIf(e -> now - e.getValue() > CACHE_DURATION);
        trapCache.keySet().removeIf(pos -> !trapCacheTime.containsKey(pos));

        bigTrapCacheTime.entrySet().removeIf(e -> now - e.getValue() > CACHE_DURATION);
        bigTrapCache.keySet().removeIf(pos -> !bigTrapCacheTime.containsKey(pos));

        if (trapCache.size() > 10_000) { trapCache.clear(); trapCacheTime.clear(); }
        if (bigTrapCache.size() > 10_000) { bigTrapCache.clear(); bigTrapCacheTime.clear(); }
    }


    public record Structure(ItemStack stack, Vec3d vec, String world, int anarchy, long time) { }
    public record ServerEvent(String name, String lvl, String owner, Vec3d vec, String world, int anarchy, double timeOpen, double timeEnd) { }

    public enum ItemType {
        CROSSBOW("Арбалет", "Арбалета", Items.CROSSBOW),
        SHULKER("Шалкер", "Шалкера", Items.SHULKER_BOX),
        DESORIENT("Дезориентация", "Дезориентации", Items.ENDER_EYE),
        TRAPFT("Трапка", "Трапки", Items.NETHERITE_SCRAP),
        DUST("Явная пыль", "Явной пыли", Items.SUGAR),
        PLAST("Пласт", "Пласта", Items.DRIED_KELP),
        FLAME_WHIRLWIND("Огненный смерч", "Огненного смерча", Items.FIRE_CHARGE),
        DIVINE_AURA("Божья аура", "Божьей ауры", Items.PHANTOM_MEMBRANE),
        SNOWFT("Снежок заморозка", "Снежка заморозки", Items.SNOWBALL),

        EXPLOSIVETRAP("Взрывная трапка", "Взрывной трапки", Items.PRISMARINE_SHARD),
        TRAPHW("Трапка", "Трапки", Items.POPPED_CHORUS_FRUIT),
        STAN("Стан", "Стана", Items.NETHER_STAR),
        SNOWHW("Ком снега", "Кома снега", Items.SNOWBALL),
        FAREWELLGHOUL("Прощальный гул", "Прощального гула", Items.FIREWORK_STAR),
        JACKLAMP("Светильник Джейка", "Светильника Джейка", Items.CARVED_PUMPKIN),
        EXPLOSIVETHING("Взрывная штучка", "Взрывной штучки", Items.FIRE_CHARGE),
        EXPLOSIVEWAND("Взрывная палочка", "Взрывной палочки", Items.BLAZE_ROD),
        BACKPACK("Рюкзак", "Рюкзака", Items.PINK_SHULKER_BOX);

        public final String displayName;
        public final String genitiveName;
        public final Item item;

        ItemType(String displayName, String genitiveName, Item item) {
            this.displayName = displayName;
            this.genitiveName = genitiveName;
            this.item = item;
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        mineViewer.removeBossBar();
        structures.clear();
        serverEvents.clear();
        blockStateMap.clear();
        trapCache.clear();
        bigTrapCache.clear();
        trapCacheTime.clear();
        bigTrapCacheTime.clear();
    }
}
