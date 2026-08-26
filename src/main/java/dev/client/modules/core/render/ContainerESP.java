package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.render.core.DrawUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class ContainerESP extends Module {

    public ContainerESP() {
        super("ContainerESP", ModuleCategory.Render, "Отображает место-положение контейнеров через стены");
    }

    CheckBox all = new CheckBox("Показать все", () -> true).defaultValue(true).register(this);

    SelectElements blocks = new SelectElements("Отображать", () -> !all.getEnabled()).set("Сундук", "Запертый сундук", "Бочка", "Печь", "Эндер сундук", "Шалкер", "Спавнер").defaultValue("Сундук", "Шалкер", "Спавнер").register(this);

    private static final Map<String, Predicate<BlockEntity>> BLOCKS = new HashMap<>();

    static {
        BLOCKS.put("Сундук", be -> be instanceof ChestBlockEntity);
        BLOCKS.put("Запертый сундук", be -> be instanceof TrappedChestBlockEntity);
        BLOCKS.put("Бочка", be -> be instanceof BarrelBlockEntity);
        BLOCKS.put("Печь", be -> be instanceof AbstractFurnaceBlockEntity);
        BLOCKS.put("Эндер сундук", be -> be instanceof EnderChestBlockEntity);
        BLOCKS.put("Шалкер", be -> be instanceof ShulkerBoxBlockEntity);
        BLOCKS.put("Спавнер", be -> be instanceof MobSpawnerBlockEntity);
    }

    private static Color blocksColor(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return new Color(255, 215, 0, 160);
        if (be instanceof TrappedChestBlockEntity) return new Color(255, 80, 80, 160);
        if (be instanceof BarrelBlockEntity) return new Color(205, 133, 63, 160);
        if (be instanceof AbstractFurnaceBlockEntity) return new Color(255, 140, 0, 160);
        if (be instanceof EnderChestBlockEntity) return new Color(138, 43, 226, 160);
        if (be instanceof ShulkerBoxBlockEntity) return new Color(147, 112, 219, 160);
        if (be instanceof MobSpawnerBlockEntity) return new Color(64, 224, 208, 160);
        return new Color(255, 255, 255, 160);
    }

    @Subscribe
    public void onDraw3D(RenderEvent.Draw3D event) {
        if (mc.world == null || mc.player == null) return;

        int viewDist = mc.options.getViewDistance().getValue();
        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());

        for (int cx = playerChunk.x - viewDist; cx <= playerChunk.x + viewDist; cx++) {
            for (int cz = playerChunk.z - viewDist; cz <= playerChunk.z + viewDist; cz++) {

                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : getBlockEntitiesSafe(chunk, mc.world)) {
                    if (!shouldRender(be)) continue;
                    BlockPos pos = be.getPos();
                    BlockState state = mc.world.getBlockState(pos);
                    Box box = state.getOutlineShape(mc.world, pos).isEmpty() ? new Box(pos) : state.getOutlineShape(mc.world, pos).getBoundingBox().offset(pos);
                    DrawUtil.renderOutline(box, blocksColor(be), 2.0f);
                }
            }
        }
    }

    private Iterable<BlockEntity> getBlockEntitiesSafe(WorldChunk chunk, ClientWorld world) {
        try {
            return chunk.getBlockEntities().values();
        } catch (Throwable ignored) {
            java.util.List<BlockEntity> list = new java.util.ArrayList<>();
            for (BlockPos p : chunk.getBlockEntityPositions()) {
                BlockEntity be = world.getBlockEntity(p);
                if (be != null) list.add(be);
            }
            return list;
        }
    }

    private boolean shouldRender(BlockEntity be) {
        if (all.getEnabled()) {
            return isContainer(be);
        }

        for (String name : blocks.getSelected()) {
            Predicate<BlockEntity> p = BLOCKS.get(name);
            if (p != null && p.test(be)) {
                return true;
            }
        }
        return false;
    }

    private boolean isContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof TrappedChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof AbstractFurnaceBlockEntity
                || be instanceof EnderChestBlockEntity
                || be instanceof ShulkerBoxBlockEntity
                || be instanceof MobSpawnerBlockEntity;
    }
}

