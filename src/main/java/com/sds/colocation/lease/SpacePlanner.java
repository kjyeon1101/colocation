package com.sds.colocation.lease;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.rack.Rack;

/**
 * 이전 계획을 세우기 위한 가상 배치판. 실제 데이터는 바꾸지 않고, 임대 공간을 빼고 넣어 보며 빈자리를 찾는다.
 */
class SpacePlanner {

    record Slot(Rack rack, int startUnit) {
    }

    private record Block(int start, int end) {

        boolean overlaps(int otherStart, int otherEnd) {
            return start <= otherEnd && otherStart <= end;
        }
    }

    private static final class RackState {
        private final Rack rack;
        private final List<Block> blocks = new ArrayList<>();
        private int usedPowerWatts;

        private RackState(Rack rack) {
            this.rack = rack;
        }

        private boolean isFree(int start, int size) {
            int end = start + size - 1;
            return rack.isValidRange(start, size) && blocks.stream().noneMatch(block -> block.overlaps(start, end));
        }
    }

    private final Map<Long, RackState> racks = new LinkedHashMap<>();

    SpacePlanner(List<Rack> allRacks, Collection<Allocation> activeAllocations) {
        allRacks.forEach(rack -> racks.put(rack.getId(), new RackState(rack)));
        activeAllocations.forEach(allocation -> {
            RackState state = racks.get(allocation.getRack().getId());
            state.blocks.add(new Block(allocation.getStartUnit(), allocation.getEndUnit()));
            state.usedPowerWatts += allocation.getTotalPowerWatts();
        });
    }

    void release(Allocation allocation) {
        RackState state = racks.get(allocation.getRack().getId());
        state.blocks.remove(new Block(allocation.getStartUnit(), allocation.getEndUnit()));
        state.usedPowerWatts -= allocation.getTotalPowerWatts();
    }

    void reserve(Long rackId, int startUnit, int sizeUnits, int powerWatts) {
        RackState state = racks.get(rackId);
        state.blocks.add(new Block(startUnit, startUnit + sizeUnits - 1));
        state.usedPowerWatts += powerWatts;
    }

    /** 먼저 원래 랙에서, 그다음 다른 랙에서 아래쪽(U1)부터 빈자리를 찾는다. */
    Optional<Slot> findSlot(int sizeUnits, int powerWatts, Long preferredRackId) {
        return racks.values().stream()
                .sorted(Comparator.comparing((RackState state) -> !state.rack.getId().equals(preferredRackId)))
                .filter(state -> state.usedPowerWatts + powerWatts <= state.rack.getMaxPowerWatts())
                .flatMap(state -> firstFreeStart(state, sizeUnits).map(start -> new Slot(state.rack, start)).stream())
                .findFirst();
    }

    private Optional<Integer> firstFreeStart(RackState state, int sizeUnits) {
        for (int start = 1; start + sizeUnits - 1 <= state.rack.getTotalUnits(); start++) {
            if (state.isFree(start, sizeUnits)) {
                return Optional.of(start);
            }
        }
        return Optional.empty();
    }
}
