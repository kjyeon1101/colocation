package com.sds.colocation.rack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.sds.colocation.allocation.Allocation;

/** 한 랙의 현재 점유 상태(임대 공간과 전력 사용량)를 계산한다. */
public class RackSpace {

    private final Rack rack;
    private final List<Allocation> allocations;

    public RackSpace(Rack rack, Collection<Allocation> allocations) {
        this.rack = rack;
        this.allocations = allocations.stream()
                .filter(Allocation::isActive)
                .filter(allocation -> allocation.getRack().getId().equals(rack.getId()))
                .toList();
    }

    public Rack rack() {
        return rack;
    }

    public List<Allocation> allocations() {
        return allocations;
    }

    public int usedPowerWatts() {
        return allocations.stream().mapToInt(Allocation::getTotalPowerWatts).sum();
    }

    public int availablePowerWatts() {
        return rack.getMaxPowerWatts() - usedPowerWatts();
    }

    public int usedUnits() {
        return allocations.stream().mapToInt(Allocation::getSizeUnits).sum();
    }

    public boolean canSupply(int additionalWatts) {
        return usedPowerWatts() + additionalWatts <= rack.getMaxPowerWatts();
    }

    public Optional<Allocation> findCovering(int startUnit, int sizeUnits) {
        return allocations.stream()
                .filter(allocation -> allocation.covers(startUnit, sizeUnits))
                .findFirst();
    }

    public List<Allocation> findOverlapping(int startUnit, int sizeUnits) {
        return allocations.stream()
                .filter(allocation -> allocation.overlaps(startUnit, sizeUnits))
                .toList();
    }

    public boolean isFree(int startUnit, int sizeUnits) {
        return rack.isValidRange(startUnit, sizeUnits) && findOverlapping(startUnit, sizeUnits).isEmpty();
    }

    /** 지정한 임대 공간 자신은 빼고 비어 있는지 확인한다. (같은 랙 안에서 자리를 옮길 때 사용) */
    public boolean isFreeExcept(int startUnit, int sizeUnits, Long ignoredAllocationId) {
        return rack.isValidRange(startUnit, sizeUnits)
                && findOverlapping(startUnit, sizeUnits).stream()
                .allMatch(allocation -> allocation.getId().equals(ignoredAllocationId));
    }
}
