package com.acme.sportplatform.scheduling.domain;

public final class LaneOrder {
    private LaneOrder() {}

    public static int[] forLanes(int lanes) {
        if (lanes < 1)
            throw new IllegalArgumentException("Число дорожек должно быть положительным");
        int[] order = new int[lanes];
        int center = lanes / 2 + (lanes % 2 == 0 ? 0 : 1);
        order[0] = center;
        int left = center - 1, right = center + 1;
        boolean takeRight = true;
        for (int i = 1; i < lanes; i++) {
            if (takeRight && right <= lanes) order[i] = right++;
            else if (!takeRight && left >= 1) order[i] = left--;
            else if (right <= lanes) order[i] = right++;
            else order[i] = left--;
            takeRight = !takeRight;
        }
        return order;
    }
}
