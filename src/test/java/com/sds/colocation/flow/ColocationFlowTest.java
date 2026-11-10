package com.sds.colocation.flow;

import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.AllocationRepository;
import com.sds.colocation.allocation.ServerStatus;
import com.sds.colocation.allocation.ServerType;
import com.sds.colocation.billing.RevenueReport;
import com.sds.colocation.common.BusinessException;
import com.sds.colocation.customer.Customer;
import com.sds.colocation.customer.CustomerRepository;
import com.sds.colocation.customer.CustomerTier;
import com.sds.colocation.lease.HalfPosition;
import com.sds.colocation.lease.LeasePlan;
import com.sds.colocation.lease.LeaseRequest;
import com.sds.colocation.lease.LeaseService;
import com.sds.colocation.notification.Notification;
import com.sds.colocation.notification.NotificationRepository;
import com.sds.colocation.placement.PlacementRequest;
import com.sds.colocation.placement.PlacementResult;
import com.sds.colocation.power.PowerUpgradeRequest;
import com.sds.colocation.rack.Rack;
import com.sds.colocation.rack.RackRepository;
import com.sds.colocation.rack.RackService;
import com.sds.colocation.relocation.RelocateRequest;
import com.sds.colocation.relocation.Relocation;
import com.sds.colocation.relocation.RelocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업무 흐름(입고 · 이전 · 랙 단위 임대 · 수익)의 현재 동작을 고정하는 통합 테스트.
 * 시연 데이터는 끄고, 시계는 2026-11-10으로 고정한다. 각 테스트는 롤백된다.
 *
 * <p>알려진 버그 후보(이전 시 원래 랙 전력, 랙 맨 위 칸 입고 등)는 여기서 단정하지 않는다.
 * 버그 수정 미션에서 재현 테스트로 따로 다룬다.
 */
@SpringBootTest(properties = {
        "colocation.seed.enabled=false",
        "colocation.notification.simulated-latency-ms=0"
})
@Import(ColocationFlowTest.FixedClockConfig.class)
@Transactional
class ColocationFlowTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired RackService rackService;
    @Autowired LeaseService leaseService;
    @Autowired RackRepository rackRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired RelocationRepository relocationRepository;
    @Autowired NotificationRepository notificationRepository;

    Rack rackA;
    Rack rackB;
    Customer standard;
    Customer enterprise;

    @BeforeEach
    void setUp() {
        rackA = rackRepository.save(new Rack("T-A", 42, 10_000));
        rackB = rackRepository.save(new Rack("T-B", 42, 10_000));
        standard = customerRepository.save(new Customer("일반고객", CustomerTier.STANDARD, "김일반", "std@example.com"));
        enterprise = customerRepository.save(new Customer("엔터고객", CustomerTier.ENTERPRISE, "박엔터", "ent@example.com"));
    }

    private PlacementResult place(Customer customer, Rack rack, int startUnit, String hostname, ServerType type,
                                  int sizeUnits, int powerWatts, String requestKey) {
        return rackService.place(new PlacementRequest(customer.getId(), rack.getId(), startUnit, hostname, type,
                sizeUnits, powerWatts, requestKey));
    }

    private PlacementResult placeCpu(Customer customer, Rack rack, int startUnit, int sizeUnits, int powerWatts) {
        return place(customer, rack, startUnit, "cpu-" + startUnit, ServerType.CPU, sizeUnits, powerWatts, null);
    }

    private String errorCodeOf(Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            return e.getCode();
        }
        return null;
    }

    // ------------------------------------------------------------ 입고

    @Test
    @DisplayName("입고: U 단위 임대 공간이 만들어지고 랙 전력이 늘어난다")
    void placeCreatesUnitAllocation() {
        PlacementResult result = placeCpu(standard, rackA, 5, 2, 500);

        assertThat(result.duplicated()).isFalse();
        assertThat(result.rackName()).isEqualTo("T-A");
        assertThat(result.startUnit()).isEqualTo(5);
        Allocation allocation = allocationRepository.findById(result.allocationId()).orElseThrow();
        assertThat(allocation.getLeaseUnit()).isEqualTo("1U");
        assertThat(allocation.getStartDate()).isEqualTo(TODAY);
        assertThat(rackRepository.findById(rackA.getId()).orElseThrow().getUsedPowerWatts()).isEqualTo(500);
    }

    @Test
    @DisplayName("입고: 같은 requestKey로 다시 요청하면 새로 입고하지 않고 처음 결과를 돌려준다")
    void placeIsIdempotentByRequestKey() {
        PlacementResult first = place(standard, rackA, 5, "web01", ServerType.CPU, 2, 500, "key-1");
        PlacementResult second = place(standard, rackA, 5, "web01", ServerType.CPU, 2, 500, "key-1");

        assertThat(first.duplicated()).isFalse();
        assertThat(second.duplicated()).isTrue();
        assertThat(second.serverId()).isEqualTo(first.serverId());
        assertThat(rackRepository.findById(rackA.getId()).orElseThrow().getUsedPowerWatts()).isEqualTo(500);
    }

    @Test
    @DisplayName("입고: 이미 사용 중인 자리는 SLOT_OCCUPIED")
    void placeRejectsOccupiedSlot() {
        placeCpu(standard, rackA, 5, 2, 500);

        assertThat(errorCodeOf(() -> placeCpu(enterprise, rackA, 6, 2, 100))).isEqualTo("SLOT_OCCUPIED");
    }

    @Test
    @DisplayName("입고: 랙 전력 한도를 넘으면 POWER_EXCEEDED")
    void placeRejectsPowerOverflow() {
        placeCpu(standard, rackA, 1, 2, 9_500);

        assertThat(errorCodeOf(() -> placeCpu(standard, rackA, 10, 2, 501))).isEqualTo("POWER_EXCEEDED");
        assertThatCodeFree(() -> placeCpu(standard, rackA, 10, 2, 500));
    }

    @Test
    @DisplayName("입고: 랙 범위를 벗어나면 OUT_OF_RANGE")
    void placeRejectsOutOfRange() {
        assertThat(errorCodeOf(() -> placeCpu(standard, rackA, 42, 2, 100))).isEqualTo("OUT_OF_RANGE");
    }

    @Test
    @DisplayName("입고: 존재하지 않는 고객·랙은 NOT_FOUND")
    void placeRejectsUnknownCustomerOrRack() {
        assertThat(errorCodeOf(() -> rackService.place(new PlacementRequest(999_999L, rackA.getId(), 1, "h",
                ServerType.CPU, 1, 100, null)))).isEqualTo("NOT_FOUND");
        assertThat(errorCodeOf(() -> rackService.place(new PlacementRequest(standard.getId(), 999_999L, 1, "h",
                ServerType.CPU, 1, 100, null)))).isEqualTo("NOT_FOUND");
    }

    private void assertThatCodeFree(Runnable action) {
        action.run(); // 예외 없이 끝나야 한다
    }

    // ------------------------------------------------------------ 이전

    @Test
    @DisplayName("이전: 위치가 바뀌고 이력 · 50% 할인 · 재부팅 표시 · 안내 알림이 생긴다")
    void relocateMovesAndRecordsEverything() {
        PlacementResult placed = placeCpu(standard, rackA, 5, 2, 500);

        rackService.relocate(placed.allocationId(), new RelocateRequest(rackB.getId(), 10, null));

        Allocation allocation = allocationRepository.findById(placed.allocationId()).orElseThrow();
        assertThat(allocation.getRack().getId()).isEqualTo(rackB.getId());
        assertThat(allocation.getStartUnit()).isEqualTo(10);
        assertThat(allocation.getServers().get(0).getStartUnit()).isEqualTo(10);
        assertThat(allocation.getServers().get(0).getStatus()).isEqualTo(ServerStatus.REBOOT_REQUIRED);
        assertThat(allocation.discountPercentOn(TODAY)).isEqualTo(50);
        assertThat(allocation.discountPercentOn(TODAY.plusMonths(3))).isZero();
        assertThat(rackRepository.findById(rackB.getId()).orElseThrow().getUsedPowerWatts()).isEqualTo(500);

        List<Relocation> history = relocationRepository.findAllByOrderByRelocatedDateDescIdDesc();
        assertThat(history).hasSize(1);
        Relocation relocation = history.get(0);
        assertThat(relocation.getFromRackName()).isEqualTo("T-A");
        assertThat(relocation.getToRackName()).isEqualTo("T-B");
        assertThat(relocation.getFromStartUnit()).isEqualTo(5);
        assertThat(relocation.getToStartUnit()).isEqualTo(10);
        assertThat(relocation.getDiscountPercent()).isEqualTo(50);
        assertThat(relocation.getDiscountEndDate()).isEqualTo(TODAY.plusMonths(3));
        assertThat(relocation.getReason()).isEqualTo("운영자 수동 이전");

        List<Notification> notifications = notificationRepository.findAllByOrderBySentAtDescIdDesc();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getCustomerName()).isEqualTo("일반고객");
    }

    @Test
    @DisplayName("이전: 현재 위치와 같은 자리는 SAME_POSITION")
    void relocateRejectsSamePosition() {
        PlacementResult placed = placeCpu(standard, rackA, 5, 2, 500);

        assertThat(errorCodeOf(() -> rackService.relocate(placed.allocationId(),
                new RelocateRequest(rackA.getId(), 5, null)))).isEqualTo("SAME_POSITION");
    }

    @Test
    @DisplayName("이전: 대상 자리가 사용 중이면 SLOT_OCCUPIED")
    void relocateRejectsOccupiedTarget() {
        PlacementResult placed = placeCpu(standard, rackA, 5, 2, 500);
        placeCpu(enterprise, rackB, 10, 2, 100);

        assertThat(errorCodeOf(() -> rackService.relocate(placed.allocationId(),
                new RelocateRequest(rackB.getId(), 11, null)))).isEqualTo("SLOT_OCCUPIED");
    }

    @Test
    @DisplayName("이전: 하프랙·풀랙 임대 공간은 RACK_LEASE_NOT_MOVABLE")
    void relocateRejectsRackLease() {
        leaseService.execute(new LeaseRequest(enterprise.getId(), rackA.getId(), "FULL", null));
        Allocation lease = allocationRepository.findAll().stream()
                .filter(allocation -> allocation.getLeaseUnit().equals("FULL")).findFirst().orElseThrow();

        assertThat(errorCodeOf(() -> rackService.relocate(lease.getId(),
                new RelocateRequest(rackB.getId(), 1, null)))).isEqualTo("RACK_LEASE_NOT_MOVABLE");
    }

    // ------------------------------------------------------------ 랙 단위 임대

    @Test
    @DisplayName("풀랙 임대: 기존 고객은 다른 랙으로 이전되고, 임대 공간이 만들어진다")
    void fullRackLeaseRelocatesExistingCustomer() {
        PlacementResult placed = placeCpu(standard, rackA, 5, 2, 500);

        LeasePlan plan = leaseService.plan(new LeaseRequest(enterprise.getId(), rackA.getId(), "FULL", null));

        assertThat(plan.feasible()).isTrue();
        assertThat(plan.startUnit()).isEqualTo(1);
        assertThat(plan.endUnit()).isEqualTo(42);
        assertThat(plan.monthlyFee()).isEqualTo(5_000_000L);
        assertThat(plan.moves()).hasSize(1);
        assertThat(plan.moves().get(0).toRackId()).isEqualTo(rackB.getId());
        assertThat(plan.moves().get(0).toStartUnit()).isEqualTo(1);

        leaseService.execute(new LeaseRequest(enterprise.getId(), rackA.getId(), "FULL", null));

        Allocation moved = allocationRepository.findById(placed.allocationId()).orElseThrow();
        assertThat(moved.getRack().getId()).isEqualTo(rackB.getId());
        Allocation lease = allocationRepository.findAll().stream()
                .filter(allocation -> allocation.getLeaseUnit().equals("FULL")).findFirst().orElseThrow();
        assertThat(lease.getRack().getId()).isEqualTo(rackA.getId());
        assertThat(lease.getStartUnit()).isEqualTo(1);
        assertThat(lease.getSizeUnits()).isEqualTo(42);
        assertThat(relocationRepository.findAll()).hasSize(1);
        assertThat(notificationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("하프랙 임대: LOWER는 U1~21, UPPER는 U22~42")
    void halfRackPositions() {
        LeasePlan lower = leaseService.plan(
                new LeaseRequest(enterprise.getId(), rackA.getId(), "HALF", HalfPosition.LOWER));
        LeasePlan upper = leaseService.plan(
                new LeaseRequest(enterprise.getId(), rackA.getId(), "HALF", HalfPosition.UPPER));

        assertThat(lower.startUnit()).isEqualTo(1);
        assertThat(lower.endUnit()).isEqualTo(21);
        assertThat(upper.startUnit()).isEqualTo(22);
        assertThat(upper.endUnit()).isEqualTo(42);
        assertThat(lower.monthlyFee()).isEqualTo(2_800_000L);
    }

    @Test
    @DisplayName("랙 단위 임대: 엔터프라이즈가 아니면 NOT_ENTERPRISE")
    void leaseRequiresEnterprise() {
        assertThat(errorCodeOf(() -> leaseService.plan(
                new LeaseRequest(standard.getId(), rackA.getId(), "FULL", null)))).isEqualTo("NOT_ENTERPRISE");
    }

    @Test
    @DisplayName("랙 단위 임대: 하프랙에 위치가 없으면 POSITION_REQUIRED, 단위가 틀리면 INVALID_LEASE_UNIT")
    void leaseValidatesRequest() {
        assertThat(errorCodeOf(() -> leaseService.plan(
                new LeaseRequest(enterprise.getId(), rackA.getId(), "HALF", null)))).isEqualTo("POSITION_REQUIRED");
        assertThat(errorCodeOf(() -> leaseService.plan(
                new LeaseRequest(enterprise.getId(), rackA.getId(), "1U", null)))).isEqualTo("INVALID_LEASE_UNIT");
    }

    @Test
    @DisplayName("랙 단위 임대: 옮길 빈자리가 없으면 계획은 불가능, 실행은 LEASE_NOT_FEASIBLE")
    void leaseNotFeasibleWhenNowhereToMove() {
        placeCpu(standard, rackA, 5, 2, 500);
        leaseService.execute(new LeaseRequest(enterprise.getId(), rackB.getId(), "FULL", null)); // B를 통째로 점유

        LeaseRequest request = new LeaseRequest(enterprise.getId(), rackA.getId(), "FULL", null);
        LeasePlan plan = leaseService.plan(request);

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.reason()).isNotBlank();
        assertThat(errorCodeOf(() -> leaseService.execute(request))).isEqualTo("LEASE_NOT_FEASIBLE");
    }

    @Test
    @DisplayName("랙 단위 임대: 이미 다른 랙 단위 임대가 있는 구간은 불가능")
    void leaseConflictsWithOtherRackLease() {
        leaseService.execute(new LeaseRequest(enterprise.getId(), rackA.getId(), "FULL", null));

        LeasePlan plan = leaseService.plan(new LeaseRequest(enterprise.getId(), rackA.getId(),
                "HALF", HalfPosition.LOWER));

        assertThat(plan.feasible()).isFalse();
    }

    // ------------------------------------------------------------ 입고 + 임대 공간

    @Test
    @DisplayName("하프랙 안 입고: 임대한 고객은 서버를 추가할 수 있고, 다른 고객은 OCCUPIED_BY_OTHER")
    void placeInsideRackLease() {
        leaseService.execute(new LeaseRequest(enterprise.getId(), rackA.getId(), "HALF", HalfPosition.LOWER));

        PlacementResult inside = placeCpu(enterprise, rackA, 3, 2, 400);
        assertThat(inside.startUnit()).isEqualTo(3);
        assertThat(allocationRepository.findById(inside.allocationId()).orElseThrow().getLeaseUnit())
                .isEqualTo("HALF");

        assertThat(errorCodeOf(() -> placeCpu(standard, rackA, 10, 2, 100))).isEqualTo("OCCUPIED_BY_OTHER");
        assertThat(errorCodeOf(() -> placeCpu(enterprise, rackA, 4, 2, 100))).isEqualTo("SLOT_OCCUPIED");
    }

    // ------------------------------------------------------------ 전력 증설 · 수익

    @Test
    @DisplayName("전력 증설: 1단계마다 최대 전력 +5,000W, 공사비 1억 원")
    void powerUpgrade() {
        rackService.upgradePower(rackA.getId(), new PowerUpgradeRequest(2));

        assertThat(rackRepository.findById(rackA.getId()).orElseThrow().getMaxPowerWatts()).isEqualTo(20_000);
        assertThat(rackService.powerUpgradeHistory()).hasSize(1);
        assertThat(rackService.powerUpgradeHistory().get(0).cost()).isEqualTo(200_000_000L);
    }

    @Test
    @DisplayName("월 수익: 오늘 입고한 2U CPU = 11월 21일치 일할 210,000원")
    void monthlyRevenueProratesFromStartDate() {
        placeCpu(standard, rackA, 5, 2, 500);

        RevenueReport report = rackService.monthlyRevenue(YearMonth.of(2026, 11));

        assertThat(report.lines()).hasSize(1);
        assertThat(report.grossRevenue()).isEqualTo(210_000L);
        assertThat(report.discountAmount()).isZero();
        assertThat(report.netRevenue()).isEqualTo(210_000L);
        assertThat(rackService.monthlyRevenue(YearMonth.of(2026, 10)).lines()).isEmpty();
    }

    @Test
    @DisplayName("월 수익: 전력 증설 공사비는 공사한 달 순수익에서 한 번에 차감")
    void monthlyRevenueSubtractsConstructionCost() {
        placeCpu(standard, rackA, 5, 2, 500);
        rackService.upgradePower(rackA.getId(), new PowerUpgradeRequest(1));

        RevenueReport november = rackService.monthlyRevenue(YearMonth.of(2026, 11));
        RevenueReport december = rackService.monthlyRevenue(YearMonth.of(2026, 12));

        assertThat(november.constructionCost()).isEqualTo(100_000_000L);
        assertThat(november.netRevenue()).isEqualTo(210_000L - 100_000_000L);
        assertThat(december.constructionCost()).isZero();
        assertThat(december.grossRevenue()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("월 수익: 이전하면 그 날부터 할인액이 반영된다")
    void monthlyRevenueReflectsRelocationDiscount() {
        PlacementResult placed = placeCpu(standard, rackA, 5, 2, 500);
        rackService.relocate(placed.allocationId(), new RelocateRequest(rackB.getId(), 10, null));

        RevenueReport november = rackService.monthlyRevenue(YearMonth.of(2026, 11));

        // 11/10 입고 → 21일치 210,000원, 그 21일 모두 50% 할인 → 105,000원
        assertThat(november.grossRevenue()).isEqualTo(210_000L);
        assertThat(november.discountAmount()).isEqualTo(105_000L);
        assertThat(november.netRevenue()).isEqualTo(105_000L);
    }

    @Test
    @DisplayName("수익 추이: 요청한 개월 수만큼 오래된 달부터 나열")
    void revenueTrend() {
        var trend = rackService.revenueTrend(YearMonth.of(2026, 11), 3);

        assertThat(trend).extracting("month").containsExactly("2026-09", "2026-10", "2026-11");
    }
}
