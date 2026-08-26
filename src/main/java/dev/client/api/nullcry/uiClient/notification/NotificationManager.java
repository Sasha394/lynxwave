package dev.client.api.nullcry.uiClient.notification;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.other.SoundUtil;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager implements ClientApi {
    public final CopyOnWriteArrayList<NotificationRender> notificationRenders = new CopyOnWriteArrayList<>();
    private final NotificationDragging dock = NotificationDragging.INSTANCE;

    private static final SoundUtil INFO_SOUND = new SoundUtil("info", () -> NotificationDragging.INSTANCE.getVolumeSetting().getValue());
    private static final SoundUtil WARNING_SOUND = new SoundUtil("warning", () -> NotificationDragging.INSTANCE.getVolumeSetting().getValue());

    private final NotificationRender demoRender = new NotificationRender("Это пример уведомления!", 999, NotificationType.INFO, RenderType.WORLD); {
        demoRender.setY(Float.NaN);
        demoRender.getAnimation().setValue(0.0);
    }

    private final NotificationType[] demoCycle = new NotificationType[] {
            NotificationType.SUCCESS, NotificationType.ERROR, NotificationType.INFO, NotificationType.WARNING, NotificationType.CONFIG
    };

    float prevAnchorX = Float.NaN;
    float prevAnchorY = Float.NaN;

    int demoIdx = 0;
    long demoLastSwitch = 0L;
    int demoSwitchMs = 1000;

    public void add(Text formattedText, int time, NotificationType type, RenderType renderType, ItemStack itemStack) {
        if (notificationRenders.size() >= 8) {
            for (NotificationRender r : notificationRenders) if (!r.animation.isDone()) r.animation.run(0.0);
        } else {
            NotificationRender r = new NotificationRender(formattedText, time, type, renderType, itemStack);
            r.setY(Float.NaN);
            notificationRenders.add(r);
            playSound(type);
        }
    }

    public void add(Text text, int time, NotificationType type, RenderType renderType) {
        if (notificationRenders.size() >= 8) {
            for (NotificationRender r : notificationRenders) if (!r.animation.isDone()) r.animation.run(0.0);
        } else {
            NotificationRender r = new NotificationRender(text, time, type, renderType);
            r.setY(Float.NaN);
            notificationRenders.add(r);
            playSound(type);
        }
    }

    public void add(String text, int time, NotificationType type, RenderType renderType) {
        if (notificationRenders.size() >= 8) {
            for (NotificationRender r : notificationRenders) if (!r.animation.isDone()) r.animation.run(0.0);
        } else {
            NotificationRender r = new NotificationRender(text, time, type, renderType);
            r.setY(Float.NaN);
            notificationRenders.add(r);
            playSound(type);
        }
    }

    private void playSound(NotificationType type) {
        switch (type) {
            case INFO -> INFO_SOUND.play();
            case WARNING -> WARNING_SOUND.play();
            default -> {}
        }
    }

    public void draw(DrawContext context, RenderType renderType) {
        draw(context, new MatrixStack(), renderType);
    }

    public void draw(DrawContext context, MatrixStack matrices, RenderType renderType) {
        if (mc.getDebugHud().shouldShowDebugHud()) return;

        final boolean chatOpen = mc.inGameHud.getChatHud().isChatFocused();
        if (chatOpen) {
            long now = System.currentTimeMillis();
            if (demoLastSwitch == 0L) demoLastSwitch = now;
            if (now - demoLastSwitch >= demoSwitchMs) {
                demoIdx = (demoIdx + 1) % demoCycle.length;
                demoRender.setNotificationType(demoCycle[demoIdx]);
                demoLastSwitch = now;
            }
            demoRender.animation.run(1.0);
        } else {
            demoRender.animation.run(0.0);
        }
        demoRender.animation.update();

        final float anchorX = dock.getAnchorX();
        final float anchorY = dock.getAnchorY();
        final boolean dragging = dock.isDragging();
        final Float spacingSetting = dock.getSpacingSetting().getValue();
        final float spacing = spacingSetting != null ? spacingSetting : 2f;

        final float scale = dock.hudScale();
        dock.draggable().setScale(scale);
        final boolean scaled = Math.abs(scale - 1f) > 1.0E-4f;
        if (scaled) {
            // Фон тостов рисуется через матрицу контекста, а текст/иконки — через MatrixStack,
            // поэтому масштабируем обе вокруг якоря дока.
            matrices.push();
            matrices.translate(anchorX, anchorY, 0f);
            matrices.scale(scale, scale, 1f);
            matrices.translate(-anchorX, -anchorY, 0f);

            org.joml.Matrix3x2fStack ctxStack = context.getMatrices();
            ctxStack.pushMatrix();
            ctxStack.translate(anchorX, anchorY);
            ctxStack.scale(scale, scale);
            ctxStack.translate(-anchorX, -anchorY);
        }

        float maxWidth = 0f;
        float totalHeight = 0f;

        float anchorDY = 0f;
        if (!Float.isNaN(prevAnchorY)) anchorDY = anchorY - prevAnchorY;

        final boolean demoVisible = !(demoRender.animation.isDone() && demoRender.animation.getValue() == 0.0);

        if (demoVisible) {
            maxWidth = Math.max(maxWidth, demoRender.getWidth());
        }
        if (chatOpen && demoVisible) {
            totalHeight += demoRender.getHeight() + spacing;
        }

        for (NotificationRender r : notificationRenders) {
            if (r.getRenderType() != renderType) continue;
            long alive = System.currentTimeMillis() - r.getTime();
            boolean expired = alive > (r.time2 * 1000L);
            if (!expired) {
                maxWidth = Math.max(maxWidth, r.getWidth());
                totalHeight += r.getHeight() + spacing;
            }
        }

        if (totalHeight <= 0f) {
            totalHeight = demoVisible ? demoRender.getHeight() + spacing : 40f;
        }

        final float dockWidth = Math.max(maxWidth, 160f);
        dock.updateSize(dockWidth, totalHeight);

        final float demoDockWidth = Math.max(demoRender.getWidth(), 160f);
        final float demoCenterX = anchorX + demoDockWidth / 2f;

        float currY = anchorY;

        if (demoVisible) {
            if (chatOpen) {
                float slotY = currY;

                if (Float.isNaN(demoRender.getY())) {
                    demoRender.setY(slotY);
                } else if (dragging) {
                    demoRender.setY(demoRender.getY() + anchorDY);
                }

                demoRender.setY(MathUtil.fast(demoRender.getY(), slotY, 10));

                float centeredX = demoCenterX - demoRender.getWidth() / 2f;
                demoRender.setX(centeredX);

                float dDrawX = (float) Math.floor(demoRender.getX() + 0.5f);
                float dDrawY = (float) Math.floor(demoRender.getY() + 0.5f);
                demoRender.draw(context, matrices, dDrawX, dDrawY, (float) demoRender.animation.getValue());

                currY += demoRender.getHeight() + spacing;
            } else {
                if (Float.isNaN(demoRender.getY())) demoRender.setY(anchorY);

                float centeredX = demoCenterX - demoRender.getWidth() / 2f;
                demoRender.setX(centeredX);

                float dDrawX = (float) Math.floor(demoRender.getX() + 0.5f);
                float dDrawY = (float) Math.floor(demoRender.getY() + 0.5f);
                demoRender.draw(context, matrices, dDrawX, dDrawY, (float) demoRender.animation.getValue());
            }
        } else {
            if (!Float.isNaN(demoRender.getY())) demoRender.setY(Float.NaN);
        }

        for (NotificationRender r : notificationRenders) {
            if (r.getRenderType() != renderType) continue;

            long alive = System.currentTimeMillis() - r.getTime();
            boolean expired = alive > (r.time2 * 1000L);

            r.animation.run(expired ? 0.0 : 1.0);
            r.animation.update();

            if (r.animation.isDone() && r.animation.getValue() == 0.0) {
                notificationRenders.remove(r);
                continue;
            }

            float centeredX = demoCenterX - r.getWidth() / 2f;

            if (!expired) {
                float slotY = currY;

                if (Float.isNaN(r.getY())) {
                    r.setY(slotY);
                } else if (dragging) {
                    r.setY(r.getY() + anchorDY);
                }

                r.setY(MathUtil.fast(r.getY(), slotY, 10));
                r.setX(centeredX);

                float drawX = (float) Math.floor(r.getX() + 0.5f);
                float drawY = (float) Math.floor(r.getY() + 0.5f);
                r.draw(context, matrices, drawX, drawY, (float) r.animation.getValue());

                currY += r.getHeight() + spacing;
            } else {
                r.setX(centeredX);

                float drawX = (float) Math.floor(r.getX() + 0.5f);
                float drawY = (float) Math.floor(r.getY() + 0.5f);
                r.draw(context, matrices, drawX, drawY, (float) r.animation.getValue());
            }
        }

        if (scaled) {
            context.getMatrices().popMatrix();
            matrices.pop();
        }

        if (dragging) {
            prevAnchorX = anchorX;
            prevAnchorY = anchorY;
        } else {
            prevAnchorX = Float.NaN;
            prevAnchorY = Float.NaN;
        }
    }

}
