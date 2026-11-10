package com.sds.colocation.allocation;

import com.sds.colocation.customer.Customer;
import com.sds.colocation.customer.CustomerTier;
import com.sds.colocation.rack.Rack;
import com.sds.colocation.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllocationTest {

    private final Rack rack = Fixtures.rack(1L, "A-01", 10_000);
    private final Rack otherRack = Fixtures.rack(2L, "A-02", 10_000);
    private final Customer customer = Fixtures.customer(1L, "고객", CustomerTier.STANDARD);

    @Test
    @DisplayName("랙 범위를 벗어난 임대 공간은 만들 수 없다")
    void rejectsOutOfRackRange() {
        assertThatThrownBy(() -> new Allocation(customer, rack, "1U", 0, 2, Fixtures.TODAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Allocation(customer, rack, "1U", 42, 2, Fixtures.TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("랙 맨 위 칸(U42)까지 쓰는 임대 공간은 만들 수 있다")
    void topUnitIsValid() {
        Allocation allocation = new Allocation(customer, rack, "1U", 41, 2, Fixtures.TODAY);
        assertThat(allocation.getEndUnit()).isEqualTo(42);
    }

    @Test
    @DisplayName("overlaps: 구간이 하나라도 겹치면 true, 맞닿기만 하면 false")
    void overlaps() {
        Allocation allocation = new Allocation(customer, rack, "1U", 5, 3, Fixtures.TODAY); // U5~7
        assertThat(allocation.overlaps(7, 2)).isTrue();
        assertThat(allocation.overlaps(3, 3)).isTrue();
        assertThat(allocation.overlaps(8, 2)).isFalse();
        assertThat(allocation.overlaps(3, 2)).isFalse();
    }

    @Test
    @DisplayName("covers: 구간이 완전히 안에 들어올 때만 true")
    void covers() {
        Allocation allocation = new Allocation(customer, rack, "HALF", 1, 21, Fixtures.TODAY);
        assertThat(allocation.covers(1, 21)).isTrue();
        assertThat(allocation.covers(20, 2)).isTrue();   // U20~21
        assertThat(allocation.covers(21, 2)).isFalse();  // U21~22는 한 칸 벗어남
        assertThat(allocation.covers(22, 1)).isFalse();
    }

    @Test
    @DisplayName("임대 공간을 벗어나는 서버는 추가할 수 없다")
    void rejectsServerOutsideLease() {
        Allocation half = new Allocation(customer, rack, "HALF", 1, 21, Fixtures.TODAY);
        assertThatThrownBy(() -> half.addServer(Fixtures.server("x", ServerType.CPU, 21, 2, 100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("총 전력은 서버 전력의 합")
    void totalPower() {
        Allocation half = new Allocation(customer, rack, "HALF", 1, 21, Fixtures.TODAY);
        half.addServer(Fixtures.server("a", ServerType.GPU, 1, 4, 3_000));
        half.addServer(Fixtures.server("b", ServerType.CPU, 5, 2, 400));
        assertThat(half.getTotalPowerWatts()).isEqualTo(3_400);
    }

    @Test
    @DisplayName("moveTo: 랙과 시작 위치가 바뀌고 서버도 같은 간격만큼 이동")
    void moveToShiftsServers() {
        Allocation allocation = Fixtures.unitAllocation(1L, customer, rack, 5, 2, ServerType.CPU, 300, Fixtures.TODAY);

        allocation.moveTo(otherRack, 10);

        assertThat(allocation.getRack()).isSameAs(otherRack);
        assertThat(allocation.getStartUnit()).isEqualTo(10);
        assertThat(allocation.getServers().get(0).getStartUnit()).isEqualTo(10);
    }

    @Test
    @DisplayName("moveTo: 랙 범위를 벗어나는 위치로는 이동 불가")
    void moveToRejectsOutOfRange() {
        Allocation allocation = Fixtures.unitAllocation(1L, customer, rack, 5, 2, ServerType.CPU, 300, Fixtures.TODAY);
        assertThatThrownBy(() -> allocation.moveTo(otherRack, 42)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("할인 기간: 시작일 포함, 종료일(3개월 후 같은 날) 미포함")
    void discountPeriodBoundaries() {
        Allocation allocation = new Allocation(customer, rack, "1U", 1, 2, Fixtures.TODAY);
        LocalDate from = LocalDate.of(2026, 11, 10);
        allocation.applyDiscount(from, 3, 50);

        assertThat(allocation.discountPercentOn(from.minusDays(1))).isZero();
        assertThat(allocation.discountPercentOn(from)).isEqualTo(50);
        assertThat(allocation.discountPercentOn(LocalDate.of(2027, 2, 9))).isEqualTo(50);
        assertThat(allocation.discountPercentOn(LocalDate.of(2027, 2, 10))).isZero();
        assertThat(allocation.isDiscountActiveOn(from)).isTrue();
    }

    @Test
    @DisplayName("terminate: 상태가 TERMINATED로 바뀌고 해지일이 기록된다")
    void terminate() {
        Allocation allocation = new Allocation(customer, rack, "1U", 1, 2, Fixtures.TODAY);
        allocation.terminate(LocalDate.of(2026, 12, 1));

        assertThat(allocation.isActive()).isFalse();
        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.TERMINATED);
        assertThat(allocation.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("markServersRebootRequired: 모든 서버가 재부팅 필요 상태가 된다")
    void rebootRequired() {
        Allocation allocation = Fixtures.unitAllocation(1L, customer, rack, 1, 2, ServerType.CPU, 300, Fixtures.TODAY);
        allocation.markServersRebootRequired();
        assertThat(allocation.getServers()).allMatch(server -> server.getStatus() == ServerStatus.REBOOT_REQUIRED);
    }
}
