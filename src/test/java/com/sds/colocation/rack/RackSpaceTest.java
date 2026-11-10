package com.sds.colocation.rack;

import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.ServerType;
import com.sds.colocation.customer.Customer;
import com.sds.colocation.customer.CustomerTier;
import com.sds.colocation.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RackSpaceTest {

    private final Rack rack = Fixtures.rack(1L, "A-01", 1_000);
    private final Rack other = Fixtures.rack(2L, "A-02", 1_000);
    private final Customer customer = Fixtures.customer(1L, "고객", CustomerTier.STANDARD);

    private Allocation a1; // U1~2, 300W
    private Allocation a2; // U5~8, 500W
    private Allocation onOtherRack;

    @BeforeEach
    void setUp() {
        a1 = Fixtures.unitAllocation(1L, customer, rack, 1, 2, ServerType.CPU, 300, Fixtures.TODAY);
        a2 = Fixtures.unitAllocation(2L, customer, rack, 5, 4, ServerType.GPU, 500, Fixtures.TODAY);
        onOtherRack = Fixtures.unitAllocation(3L, customer, other, 1, 2, ServerType.CPU, 999, Fixtures.TODAY);
    }

    private RackSpace space(Allocation... allocations) {
        return new RackSpace(rack, List.of(allocations));
    }

    @Test
    @DisplayName("다른 랙과 해지된 임대 공간은 계산에서 제외")
    void filtersByRackAndStatus() {
        Allocation terminated = Fixtures.unitAllocation(4L, customer, rack, 20, 2, ServerType.CPU, 100, Fixtures.TODAY);
        terminated.terminate(Fixtures.TODAY);

        RackSpace space = space(a1, a2, onOtherRack, terminated);

        assertThat(space.allocations()).containsExactly(a1, a2);
        assertThat(space.usedUnits()).isEqualTo(6);
        assertThat(space.usedPowerWatts()).isEqualTo(800);
        assertThat(space.availablePowerWatts()).isEqualTo(200);
    }

    @Test
    @DisplayName("canSupply: 남은 전력과 같은 요청은 가능, 1W라도 넘으면 불가")
    void canSupplyBoundary() {
        RackSpace space = space(a1, a2); // 800W 사용, 최대 1,000W
        assertThat(space.canSupply(200)).isTrue();
        assertThat(space.canSupply(201)).isFalse();
    }

    @Test
    @DisplayName("isFree: 빈 구간 / 겹치는 구간 / 맞닿은 구간")
    void isFree() {
        RackSpace space = space(a1, a2);
        assertThat(space.isFree(3, 2)).isTrue();   // U3~4 (a1과 a2 사이)
        assertThat(space.isFree(2, 2)).isFalse();  // U2~3 (a1과 겹침)
        assertThat(space.isFree(4, 2)).isFalse();  // U4~5 (a2와 겹침)
        assertThat(space.isFree(9, 3)).isTrue();   // a2 바로 다음
    }

    @Test
    @DisplayName("isFree: 랙 범위 경계 (맨 위 칸 U42까지는 유효)")
    void isFreeRackBoundary() {
        RackSpace space = space(a1, a2);
        assertThat(space.isFree(41, 2)).isTrue();   // U41~42
        assertThat(space.isFree(42, 1)).isTrue();   // U42
        assertThat(space.isFree(42, 2)).isFalse();  // U42~43 범위 초과
        assertThat(space.isFree(0, 1)).isFalse();
    }

    @Test
    @DisplayName("isFreeExcept: 자기 자신과 겹치는 것은 허용")
    void isFreeExcept() {
        RackSpace space = space(a1, a2);
        assertThat(space.isFreeExcept(2, 2, 1L)).isTrue();   // a1(U1~2) 자신과만 겹침
        assertThat(space.isFreeExcept(2, 2, 2L)).isFalse();  // a1과 겹치는데 a1은 무시 대상이 아님
    }

    @Test
    @DisplayName("findCovering / findOverlapping")
    void findCoveringAndOverlapping() {
        RackSpace space = space(a1, a2);
        assertThat(space.findCovering(5, 2)).contains(a2);
        assertThat(space.findCovering(3, 2)).isEmpty();
        assertThat(space.findOverlapping(2, 4)).containsExactly(a1, a2);
    }
}
