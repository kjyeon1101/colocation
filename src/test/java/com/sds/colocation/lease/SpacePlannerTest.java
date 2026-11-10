package com.sds.colocation.lease;

import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.ServerType;
import com.sds.colocation.customer.Customer;
import com.sds.colocation.customer.CustomerTier;
import com.sds.colocation.rack.Rack;
import com.sds.colocation.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SpacePlannerTest {

    private final Rack rackA = Fixtures.rack(1L, "A-01", 10_000);
    private final Rack rackB = Fixtures.rack(2L, "A-02", 10_000);
    private final Customer customer = Fixtures.customer(1L, "고객", CustomerTier.STANDARD);

    private Allocation unit(long id, Rack rack, int start, int size, int watts) {
        return Fixtures.unitAllocation(id, customer, rack, start, size, ServerType.CPU, watts, Fixtures.TODAY);
    }

    @Test
    @DisplayName("원래 랙의 가장 아래(U1)부터 빈자리를 찾는다")
    void prefersOriginalRackFromBottom() {
        Allocation existing = unit(1L, rackA, 1, 2, 300); // A-01 U1~2
        SpacePlanner planner = new SpacePlanner(List.of(rackA, rackB), List.of(existing));

        Optional<SpacePlanner.Slot> slot = planner.findSlot(2, 100, rackA.getId());

        assertThat(slot).isPresent();
        assertThat(slot.get().rack()).isSameAs(rackA);
        assertThat(slot.get().startUnit()).isEqualTo(3);
    }

    @Test
    @DisplayName("선호 랙을 지정하면 그 랙을 먼저 본다")
    void usesPreferredRackFirst() {
        SpacePlanner planner = new SpacePlanner(List.of(rackA, rackB), List.of());

        SpacePlanner.Slot slot = planner.findSlot(2, 100, rackB.getId()).orElseThrow();

        assertThat(slot.rack()).isSameAs(rackB);
        assertThat(slot.startUnit()).isEqualTo(1);
    }

    @Test
    @DisplayName("원래 랙에 자리가 없으면 다른 랙으로 간다")
    void fallsBackToOtherRack() {
        Allocation full = Fixtures.rackLease(1L, customer, rackA, "FULL", 1, 42, Fixtures.TODAY);
        SpacePlanner planner = new SpacePlanner(List.of(rackA, rackB), List.of(full));

        SpacePlanner.Slot slot = planner.findSlot(2, 100, rackA.getId()).orElseThrow();

        assertThat(slot.rack()).isSameAs(rackB);
        assertThat(slot.startUnit()).isEqualTo(1);
    }

    @Test
    @DisplayName("전력이 부족한 랙은 건너뛴다")
    void skipsRackWithoutPower() {
        Rack small = Fixtures.rack(3L, "S-01", 1_000);
        Allocation heavy = unit(1L, small, 1, 2, 900);
        SpacePlanner planner = new SpacePlanner(List.of(small, rackB), List.of(heavy));

        SpacePlanner.Slot slot = planner.findSlot(2, 200, small.getId()).orElseThrow();

        assertThat(slot.rack()).isSameAs(rackB);
    }

    @Test
    @DisplayName("어느 랙에도 자리가 없으면 비어 있다")
    void noSlot() {
        Allocation fullA = Fixtures.rackLease(1L, customer, rackA, "FULL", 1, 42, Fixtures.TODAY);
        Allocation fullB = Fixtures.rackLease(2L, customer, rackB, "FULL", 1, 42, Fixtures.TODAY);
        SpacePlanner planner = new SpacePlanner(List.of(rackA, rackB), List.of(fullA, fullB));

        assertThat(planner.findSlot(1, 1, rackA.getId())).isEmpty();
    }

    @Test
    @DisplayName("release로 비운 자리는 다시 쓸 수 있고, reserve한 자리는 막힌다")
    void releaseAndReserve() {
        Allocation existing = unit(1L, rackA, 1, 2, 300);
        SpacePlanner planner = new SpacePlanner(List.of(rackA), List.of(existing));

        planner.release(existing);
        assertThat(planner.findSlot(2, 100, rackA.getId()).orElseThrow().startUnit()).isEqualTo(1);

        planner.reserve(rackA.getId(), 1, 2, 100);
        assertThat(planner.findSlot(2, 100, rackA.getId()).orElseThrow().startUnit()).isEqualTo(3);
    }

    @Test
    @DisplayName("랙 맨 위 칸(U42)까지 채우는 자리도 찾는다")
    void findsSlotAtTopOfRack() {
        Allocation lower = Fixtures.rackLease(1L, customer, rackA, "HALF", 1, 40, Fixtures.TODAY); // U1~40
        SpacePlanner planner = new SpacePlanner(List.of(rackA), List.of(lower));

        SpacePlanner.Slot slot = planner.findSlot(2, 100, rackA.getId()).orElseThrow();

        assertThat(slot.startUnit()).isEqualTo(41);
    }
}
