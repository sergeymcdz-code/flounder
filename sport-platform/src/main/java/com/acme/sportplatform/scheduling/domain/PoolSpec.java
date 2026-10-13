package com.acme.sportplatform.scheduling.domain;


public record PoolSpec(int lanes, int minSwimmersInHeat) {
    public PoolSpec {
        if (lanes < 1 || minSwimmersInHeat < 1 || minSwimmersInHeat > lanes)
            throw new IllegalArgumentException(
                    "Некорректное число дорожек или минимальный размер заплыва");
    }
}
