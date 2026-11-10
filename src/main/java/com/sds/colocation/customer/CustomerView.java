package com.sds.colocation.customer;

public record CustomerView(
        Long id,
        String name,
        CustomerTier tier,
        String contactName,
        String contactEmail,
        int activeAllocations,
        int serverCount,
        int usedUnits,
        int powerWatts,
        long monthlyFee
) {
}
