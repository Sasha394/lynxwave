package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.DrawUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class BaseFinder extends Module {
    public static BaseFinder INSTANCE;

    public BaseFinder() {
        super("BaseFinder", ModuleCategory.Misc, "Сканирует территории и подсвечивает блоки");
    }

    private final Slider scanRadius = new Slider("Радиус", () -> true)
            .set(5f, 128f, 1f)
            .defaultValue(80f)
            .register(this);

    private final Slider scanDelay = new Slider("Таймер", () -> true)
            .set(1f, 30f, 1f)
            .defaultValue(4f)
            .register(this);

    private final ColorPicker highlightColor = new ColorPicker("Цвет подсветки", () -> true)
            .set(-1)
            .defaultValue(ColorUtils.rgba(140, 64, 255, 255))
            .register(this);

    private final TimerUtil scanTimer = TimerUtil.create();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "BaseFinder-Scanner");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicReference<Future<?>> activeScan = new AtomicReference<>();
    private volatile List<BlockPos> detectedBlocks = Collections.emptyList();

    private static final int MAX_TRACKED_BLOCKS = 2048;
    private static final Set<Block> VALUABLE_BLOCKS = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.ANCIENT_DEBRIS,
            Blocks.RESPAWN_ANCHOR,
            Blocks.REINFORCED_DEEPSLATE,
            Blocks.NETHER_PORTAL,
            Blocks.END_PORTAL_FRAME,
            Blocks.LODESTONE,
            Blocks.BEACON,
            Blocks.DRAGON_EGG
    );

    @Subscribe
    public void onDraw3D(RenderEvent.Draw3D event) {
        if (mc == null || mc.player == null || mc.world == null) return;

        scheduleScan();

        if (detectedBlocks.isEmpty()) return;

        Color color = new Color(highlightColor.getColorRGBA(), true);
        for (BlockPos pos : detectedBlocks) {
            DrawUtil.renderOutline(new Box(pos), color, 1.5f);
        }
    }

    private void scheduleScan() {
        long delay = (long) (scanDelay.getValue() * 1000f);
        Future<?> future = activeScan.get();
        if (future != null && !future.isDone()) return;

        if (scanTimer.hasTimeElapsed(delay, false)) {
            scanTimer.reset();
            activeScan.set(executor.submit(this::scan));
        }
    }

    private void scan() {
        if (!isEnabled() || mc == null || mc.player == null || mc.world == null) return;

        int radius = MathHelper.floor(scanRadius.getValue());
        BlockPos playerPos = mc.player.getBlockPos();
        int minX = playerPos.getX() - radius;
        int maxX = playerPos.getX() + radius;
        int minY = Math.max(mc.world.getBottomY(), playerPos.getY() - radius);
        int maxY = Math.min(mc.world.getTopYInclusive(), playerPos.getY() + radius);
        int minZ = playerPos.getZ() - radius;
        int maxZ = playerPos.getZ() + radius;

        ArrayList<BlockPos> result = new ArrayList<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = minX; x <= maxX && !Thread.currentThread().isInterrupted(); x++) {
            for (int y = minY; y <= maxY && !Thread.currentThread().isInterrupted(); y++) {
                for (int z = minZ; z <= maxZ && !Thread.currentThread().isInterrupted(); z++) {
                    if (!isEnabled() || mc.world == null) return;

                    mutable.set(x, y, z);
                    BlockState state = mc.world.getBlockState(mutable);
                    if (state.isAir()) continue;

                    if (!isInterestingBlock(state, mutable)) continue;

                    result.add(mutable.toImmutable());
                    if (result.size() >= MAX_TRACKED_BLOCKS) {
                        detectedBlocks = List.copyOf(result);
                        return;
                    }
                }
            }
        }

        if (!Thread.currentThread().isInterrupted() && isEnabled()) {
            detectedBlocks = result.isEmpty() ? Collections.emptyList() : List.copyOf(result);
        }
    }

    private boolean isInterestingBlock(BlockState state, BlockPos pos) {
        if (mc == null || mc.world == null) return false;

        if (mc.world.getBlockEntity(pos) != null) return true;
        if (state.getLuminance() >= 10) return true;

        Block block = state.getBlock();
        if (VALUABLE_BLOCKS.contains(block)) return true;

        return state.isIn(BlockTags.BEDS)
                || state.isIn(BlockTags.CAMPFIRES)
                || state.isIn(BlockTags.CANDLES)
                || block == Blocks.CRAFTING_TABLE
                || block == Blocks.FLETCHING_TABLE
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.LOOM;
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        detectedBlocks = Collections.emptyList();
        scanTimer.setTime(0L);
        Future<?> future = activeScan.getAndSet(null);
        if (future != null) {
            future.cancel(true);
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        detectedBlocks = Collections.emptyList();
        Future<?> future = activeScan.getAndSet(null);
        if (future != null) {
            future.cancel(true);
        }
    }
}
