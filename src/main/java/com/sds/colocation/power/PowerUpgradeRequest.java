package com.sds.colocation.power;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 증설 단계 수. 한 단계마다 정해진 W만큼 늘어나고 공사비가 든다. */
public record PowerUpgradeRequest(@Min(1) @Max(10) int steps) {
}
