package com.supernutrition.g3benchmark.poc;

import java.util.Arrays;

/** Pure, Android-independent math used by the research runner. */
final class PocInferenceMath {
    private PocInferenceMath() {
    }

    static float[] softmax(float[] logits) {
        if (logits.length == 0) {
            return new float[0];
        }
        float maximum = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            if (Float.isNaN(logit)) {
                throw new IllegalArgumentException("logits contain NaN");
            }
            maximum = Math.max(maximum, logit);
        }
        double sum = 0.0;
        float[] probabilities = new float[logits.length];
        for (int index = 0; index < logits.length; index++) {
            probabilities[index] = (float) Math.exp(logits[index] - maximum);
            sum += probabilities[index];
        }
        if (!(sum > 0.0) || Double.isInfinite(sum)) {
            throw new IllegalArgumentException("logits cannot be normalized");
        }
        for (int index = 0; index < probabilities.length; index++) {
            probabilities[index] /= (float) sum;
        }
        return probabilities;
    }

    static int[] topK(float[] scores, int requested) {
        int count = Math.min(Math.max(0, requested), scores.length);
        int[] indexes = new int[count];
        boolean[] used = new boolean[scores.length];
        Arrays.fill(indexes, -1);
        for (int rank = 0; rank < count; rank++) {
            int best = -1;
            for (int index = 0; index < scores.length; index++) {
                if (!used[index] && (best < 0 || scores[index] > scores[best])) {
                    best = index;
                }
            }
            if (best < 0) {
                break;
            }
            used[best] = true;
            indexes[rank] = best;
        }
        return indexes;
    }

    static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    static int gridCellIndex(float left, float top, float right, float bottom) {
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;
        int column = Math.min(2, (int) (clamp01(centerX) * 3.0f));
        int row = Math.min(2, (int) (clamp01(centerY) * 3.0f));
        return row * 3 + column;
    }

    static float[] gridCellBounds(int cellIndex) {
        if (cellIndex < 0 || cellIndex >= 9) {
            throw new IllegalArgumentException("grid cell must be in [0, 8]");
        }
        int row = cellIndex / 3;
        int column = cellIndex % 3;
        return new float[]{
                column / 3.0f,
                row / 3.0f,
                (column + 1) / 3.0f,
                (row + 1) / 3.0f
        };
    }

    static float iou(float leftA, float topA, float rightA, float bottomA,
                     float leftB, float topB, float rightB, float bottomB) {
        float left = Math.max(leftA, leftB);
        float top = Math.max(topA, topB);
        float right = Math.min(rightA, rightB);
        float bottom = Math.min(bottomA, bottomB);
        float intersection = Math.max(0.0f, right - left) * Math.max(0.0f, bottom - top);
        float areaA = Math.max(0.0f, rightA - leftA) * Math.max(0.0f, bottomA - topA);
        float areaB = Math.max(0.0f, rightB - leftB) * Math.max(0.0f, bottomB - topB);
        float union = areaA + areaB - intersection;
        return union <= 0.0f ? 0.0f : intersection / union;
    }
}
