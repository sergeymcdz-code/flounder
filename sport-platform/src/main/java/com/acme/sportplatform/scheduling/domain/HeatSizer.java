package com.acme.sportplatform.scheduling.domain;

import java.util.Arrays;

public final class HeatSizer {
    private HeatSizer() {}

    public static int[] sizes(int n, int lanes) {
        return sizes(n, lanes, 3);
    }

    public static int[] sizes(int n, int lanes, int minSwimmers) {
        if (n <= 0) return new int[0];
        if (lanes <= 0)
            throw new IllegalArgumentException("Число дорожек должно быть положительным");
        if (n <= lanes) return new int[] {n};
        int full = n / lanes, rest = n % lanes;
        int[] out = new int[full + (rest == 0 ? 0 : 1)];
        Arrays.fill(out, lanes);
        if (rest >= minSwimmers) out[0] = rest;
        else if (rest != 0) {
            out[0] = minSwimmers;
            out[1] = lanes - (minSwimmers - rest);
        }
        return out;
    }
}
