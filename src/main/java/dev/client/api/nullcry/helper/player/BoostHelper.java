package dev.client.api.nullcry.helper.player;

import net.minecraft.util.math.MathHelper;

public final class BoostHelper {

    public static double computeBoost(float lastYaw, float lastPitch) {
        boolean passive = true;
        float countableSpeed;

        int[] yawVectors      = {-45, 45, 135, -135};
        int[] addYawVectors   = {-90, 90, 180, -180, 0};
        int[] pitchVectors    = {-45, 45};

        int minYawIdx = findClosestVector(lastYaw, yawVectors);
        float maxYawDist = Math.abs(MathHelper.wrapDegrees(lastYaw) - yawVectors[minYawIdx]);

        int addYawIdx = findClosestVector(lastYaw, addYawVectors);
        float addYawDist = Math.abs(MathHelper.wrapDegrees(lastYaw) - addYawVectors[addYawIdx]);

        float realBoostable = passive ? 1.5f : 1.67f;
        countableSpeed = (minYawIdx == -1) ? realBoostable : 2.06f - maxYawDist * 0.56F / 45F;

        if (addYawDist < 10) {
            countableSpeed += 0.1f - 0.1f * addYawDist / 10F;
        }

        int pitchIdx = findClosestVector(lastPitch, pitchVectors);
        float pitchDist = Math.abs(Math.abs(lastPitch) - Math.abs(pitchVectors[pitchIdx]));
        if (pitchDist < 26) {
            countableSpeed = Math.max(1.94f, countableSpeed);
            countableSpeed += 0.05f - pitchDist * 0.05F / 26F;
        }

        countableSpeed = Math.min(2.045f, countableSpeed);

        if (lastPitch > -55 && lastPitch < -19f) {
            countableSpeed = 1.91f;
        } else if (lastPitch < -55) {
            countableSpeed = 1.54f;
        }

        if (lastPitch > 19f && lastPitch < 55) {
            countableSpeed = 1.8f;
        } else if (lastPitch > 55) {
            countableSpeed = 1.54f;
        }

        return countableSpeed;
    }

    private static int findClosestVector(float angle, int[] vectors) {
        int idx = -1;
        float min = Float.MAX_VALUE;
        for (int i = 0; i < vectors.length; i++) {
            float d = Math.abs(MathHelper.wrapDegrees(angle) - vectors[i]);
            if (d < min) { min = d; idx = i; }
        }
        return idx;
    }
}
