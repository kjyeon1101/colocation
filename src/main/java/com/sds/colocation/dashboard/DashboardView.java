package com.sds.colocation.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.sds.colocation.billing.RevenueSummary;

public record DashboardView(
        LocalDate today,
        int rackCount,
        int totalUnits,
        int usedUnits,
        double unitUtilization,
        int maxPowerWatts,
        int usedPowerWatts,
        double powerUtilization,
        int customerCount,
        int activeAllocations,
        int discountedAllocations,
        int rebootPendingServers,
        RevenueSummary currentMonth,
        List<RackUtilization> racks
) {

    public record RackUtilization(
            Long rackId,
            String rackName,
            int totalUnits,
            int usedUnits,
            double unitUtilization,
            int maxPowerWatts,
            int usedPowerWatts,
            double powerUtilization
    ) {
    }
}
