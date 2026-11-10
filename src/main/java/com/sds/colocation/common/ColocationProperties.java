package com.sds.colocation.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "colocation")
public record ColocationProperties(
        Rack rack,
        Pricing pricing,
        Relocation relocation,
        Power power,
        Notification notification,
        String zone
) {

    /**
     * @param defaultPowerWatts 새로 추가하는 랙의 최대 전력. 더 필요하면 전력 증설로 올린다.
     * @param installCost       새 랙 1개의 설치비. 설치한 달의 순수익에서 한 번에 차감한다.
     */
    public record Rack(int defaultUnits, int halfUnits, int defaultPowerWatts, long installCost) {
    }

    /** CPU 서버는 공간(U) 기준, GPU 서버는 전력(W) 기준으로 과금한다. */
    public record Pricing(long cpuPricePerUnit, long gpuPricePerWatt, long halfRackFee, long fullRackFee) {
    }

    public record Relocation(int discountPercent, int discountMonths) {
    }

    public record Power(int upgradeStepWatts, long upgradeStepCost) {
    }

    public record Notification(long simulatedLatencyMs) {
    }
}
