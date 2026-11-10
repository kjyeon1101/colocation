package com.sds.colocation.rack;

import java.util.List;

import com.sds.colocation.allocation.AllocationView;

public record RackView(
        Long id,
        String name,
        int totalUnits,
        int maxPowerWatts,
        int usedPowerWatts,
        int usedUnits,
        List<AllocationView> allocations
) {
}
