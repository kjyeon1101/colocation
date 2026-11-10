package com.sds.colocation.allocation;

import java.time.LocalDate;
import java.util.List;

import com.sds.colocation.customer.CustomerTier;

public record AllocationView(
        Long id,
        Long customerId,
        String customerName,
        CustomerTier customerTier,
        String contactEmail,
        Long rackId,
        String rackName,
        String leaseUnit,
        int startUnit,
        int endUnit,
        int sizeUnits,
        AllocationStatus status,
        LocalDate startDate,
        LocalDate endDate,
        int powerWatts,
        long monthlyFee,
        long currentFee,
        boolean discountActive,
        int discountPercent,
        LocalDate discountStartDate,
        LocalDate discountEndDate,
        boolean rebootRequired,
        String memo,
        List<ServerView> servers
) {
}
