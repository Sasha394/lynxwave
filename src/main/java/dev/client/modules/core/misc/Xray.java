package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Xray extends Module {
    public static Xray INSTANCE;

    public Xray() {
        super("Xray", ModuleCategory.Misc, "Подсвечивает добавленные блоки или древние обломки (режим Ancient)");
    }

    public ModeElement mode = new ModeElement("Режим", () -> true).set("Default", "Ancient").defaultValue("Default").register(this);
    public Slider scanRadius = new Slider("Радиус", () -> true).set(10,100,1).defaultValue(20).register(this);
    public CheckBox render = new CheckBox("Информация", () -> mode.isSelected("Ancient")).defaultValue(true).register(this);

    @Getter
    private final Set<Block> customXrayBlocks = new HashSet<>();
    public BlockPos clicking = null;
    private final ArrayList<BlockPos> highlightedDebris = new ArrayList<>();
    private final ArrayList<BlockPos> ores = new ArrayList<>();
    private final ArrayList<BlockPos> clicked = new ArrayList<>();
    private Thread thread;
    int foundDebrisCount = 0;

    @Subscribe
    public void onDraw3D(RenderEvent.Draw3D event) {
        BlockPos playerPos = mc.player.getBlockPos();
        this.highlightedDebris.clear();

        int radius = this.scanRadius.getValue().intValue();

        if (mode.isSelected("Default")) {
            for (int x = -radius; x <= radius; ++x) {
                for (int y = -radius; y <= radius; ++y) {
                    for (int z = -radius; z <= radius; ++z) {
                        BlockPos pos = playerPos.add(x, y, z);
                        Block block = mc.world.getBlockState(pos).getBlock();

                        if (shouldHighlight(block)) {
                            int rgba = -1;
                            Box box = new Box(pos);
                            DrawUtil.renderOutline(box, new Color(rgba, true), 1.5f);
                            this.highlightedDebris.add(pos);
                        }
                    }
                }
            }
        } else if (mode.isSelected("Ancient")) {
            for (int x = -radius; x <= radius; ++x) {
                for (int y = -radius; y <= radius; ++y) {
                    for (int z = -radius; z <= radius; ++z) {
                        BlockPos pos = playerPos.add(x, y, z);
                        Block block = mc.world.getBlockState(pos).getBlock();
                        if (block == Blocks.ANCIENT_DEBRIS
                                && this.hasAtLeastTwoAirBlocksAround(pos)
                                && !this.hasTwoQuartzOrGoldNearby(pos)
                                && this.hasAtLeastFiveAirInCube(pos)
                                && !this.hasTooManyAncientDebrisNearby(pos)) {

                            Box box = new Box(pos);
                            DrawUtil.renderOutline(box, new Color(ColorUtils.rgb(255, 215, 0), true), 1.5f);
                            this.highlightedDebris.add(pos);
                        }
                    }
                }
            }

            if (!this.highlightedDebris.isEmpty() && (this.thread == null || !this.thread.isAlive())) {
                this.startClickingThread();
            }

            for (BlockPos pos : new ArrayList<>(this.ores)) {
                if (mc.world.getBlockState(pos).getBlock() != Blocks.ANCIENT_DEBRIS) {
                    this.ores.remove(pos);
                } else {
                    DrawUtil.renderOutline(new Box(pos), new Color(Color.CYAN.getRGB(), true), 1.5f);
                }
            }
        }
    }

    @Subscribe
    public void onRender2D(RenderEvent.Draw2D event) {
        if (!mode.isSelected("Ancient") || !render.getEnabled()) return;

        float screenWidth = mc.getWindow().getScaledWidth();
        float screenHeight = mc.getWindow().getScaledHeight();

        Set<BlockPos> totalDebrisSet = new HashSet<>(this.highlightedDebris);
        totalDebrisSet.addAll(this.ores);

        int foundCount = totalDebrisSet.size();
        String foundText = "Найдено: " + foundCount;

        float textWidth = Builder.INTER.get().getWidth(foundText, 10);
        float yStart = screenHeight / 2 + 150;

        Builder.TEXT_BUILDER
                .text(foundText)
                .size(10)
                .font(Builder.INTER.get())
                .color(-1)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), (screenWidth - textWidth) / 2f, yStart);

    }


    @Subscribe
    public void onPacket(PacketEvent event) {
        if (!mode.isSelected("Ancient")) return;

        var pkt = event.getPacket();

        if (pkt instanceof BundleS2CPacket bundle) {
            for (var sub : bundle.getPackets()) {
                handleBlockDeltaPacket(sub);
                handleSingleBlockPacket(sub);
            }
            return;
        }

        handleBlockDeltaPacket(pkt);
        handleSingleBlockPacket(pkt);
    }

    private void handleBlockDeltaPacket(Object pkt) {
        if (!(pkt instanceof ChunkDeltaUpdateS2CPacket packet)) return;

        mc.execute(() -> {
            packet.visitUpdates((BlockPos pos, BlockState state) -> {
                if (state.isOf(Blocks.ANCIENT_DEBRIS)) {
                    this.ores.add(pos.toImmutable());
                } else {
                    this.ores.remove(pos);
                }

                for (Direction d : Direction.values()) {
                    BlockPos n = pos.offset(d);
                    BlockState ns = mc.world.getBlockState(n);
                    if (ns.isOf(Blocks.ANCIENT_DEBRIS)) {
                        this.ores.add(n.toImmutable());
                    } else {
                        if (!ns.isOf(Blocks.ANCIENT_DEBRIS)) this.ores.remove(n);
                    }
                }
            });
        });
    }

    private void handleSingleBlockPacket(Object pkt) {
        if (!(pkt instanceof BlockUpdateS2CPacket packet)) return;

        mc.execute(() -> {
            BlockPos pos = packet.getPos();
            BlockState state = packet.getState();

            if (state.isOf(Blocks.ANCIENT_DEBRIS)) {
                this.ores.add(pos.toImmutable());
            } else {
                this.ores.remove(pos);
            }

            for (Direction d : Direction.values()) {
                BlockPos n = pos.offset(d);
                BlockState ns = mc.world.getBlockState(n);
                if (ns.isOf(Blocks.ANCIENT_DEBRIS)) {
                    this.ores.add(n.toImmutable());
                } else {
                    this.ores.remove(n);
                }
            }
        });
    }

    private void startClickingThread() {
        this.thread = new Thread(() -> {
            for (BlockPos pos : this.highlightedDebris) {
                if (!this.clicked.contains(pos)) {
                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
                    this.clicked.add(pos);
                    this.clicking = pos;
                    ++this.foundDebrisCount;

                    try {
                        Thread.sleep(450L);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
            this.clicking = null;
        });
        this.thread.start();
    }

    private boolean hasAtLeastTwoAirBlocksAround(BlockPos pos) {
        int airBlockCount = 0;
        BlockPos[] surroundingPositions = {pos.north(), pos.south(), pos.east(), pos.west(), pos.up(), pos.down()};

        for (BlockPos surroundingPos : surroundingPositions) {
            Block surroundingBlock = mc.world.getBlockState(surroundingPos).getBlock();
            if (surroundingBlock == Blocks.AIR || surroundingBlock == Blocks.LAVA) {
                ++airBlockCount;
            }

            if (airBlockCount >= 2) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTwoQuartzOrGoldNearby(BlockPos pos) {
        int quartzOrGoldCount = 0;

        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    BlockPos nearbyPos = pos.add(x, y, z);
                    Block nearbyBlock = mc.world.getBlockState(nearbyPos).getBlock();
                    if (nearbyBlock == Blocks.NETHER_QUARTZ_ORE || nearbyBlock == Blocks.NETHER_GOLD_ORE) {
                        ++quartzOrGoldCount;
                    }

                    if (quartzOrGoldCount >= 4) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasAtLeastFiveAirInCube(BlockPos pos) {
        int airBlockCount = 0;

        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    BlockPos nearbyPos = pos.add(x, y, z);
                    Block nearbyBlock = mc.world.getBlockState(nearbyPos).getBlock();
                    if (nearbyBlock == Blocks.AIR || nearbyBlock == Blocks.LAVA) {
                        ++airBlockCount;
                    }

                    if (airBlockCount >= 4) {
                        return true;
                    }
                }
            }
        }

        return airBlockCount >= 4;
    }

    private boolean hasTooManyAncientDebrisNearby(BlockPos pos) {
        int ancientDebrisCount = 0;

        for (int x = -3; x <= 2; ++x) {
            for (int y = -2; y <= 2; ++y) {
                for (int z = -2; z <= 3; ++z) {
                    BlockPos nearbyPos = pos.add(x, y, z);
                    Block nearbyBlock = mc.world.getBlockState(nearbyPos).getBlock();
                    if (nearbyBlock == Blocks.ANCIENT_DEBRIS) {
                        ++ancientDebrisCount;
                    }

                    if (ancientDebrisCount > 3) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void addXrayBlock(Block block) {
        customXrayBlocks.add(block);
    }

    public void removeXrayBlock(Block block) {
        customXrayBlocks.remove(block);
    }

    private boolean shouldHighlight(Block block) {
        return customXrayBlocks.contains(block);
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (mode.isSelected("Default")) {
            printClient(Formatting.GRAY + "Чтобы добавить блоки Xray, " + Formatting.WHITE + "напиши: .assists xray");
        } else if (mode.isSelected("Ancient")) {
            foundDebrisCount = 0;
            highlightedDebris.clear();
            ores.clear();
            clicked.clear();
            clicking = null;
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        if (mode.isSelected("Ancient")) {
            foundDebrisCount = 0;
            highlightedDebris.clear();
            ores.clear();
            clicked.clear();
            clicking = null;

            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        }
    }
}