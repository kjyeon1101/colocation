package com.sds.colocation.billing;

import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.ServerType;
import com.sds.colocation.customer.Customer;
import com.sds.colocation.customer.CustomerTier;
import com.sds.colocation.rack.Rack;
import com.sds.colocation.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** 요금 계산의 현재 동작을 고정한다. 기대값은 SPEC.md 5장의 예시와 같다. */
class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator(Fixtures.properties());
    private final Rack rack = Fixtures.rack(1L, "A-01", 10_000);
    private final Customer customer = Fixtures.customer(1L, "한빛커머스", CustomerTier.STANDARD);

    private Allocation cpu2u(LocalDate startDate) {
        return Fixtures.unitAllocation(1L, customer, rack, 1, 2, ServerType.CPU, 500, startDate);
    }

    @Nested
    @DisplayName("월 정가")
    class MonthlyFee {

        @Test
        @DisplayName("U 단위 CPU 서버는 크기(U) × 150,000원")
        void cpuUnitLease() {
            assertThat(calculator.monthlyFee(cpu2u(Fixtures.TODAY))).isEqualTo(300_000L);
        }

        @Test
        @DisplayName("U 단위 GPU 서버는 전력(W) × 400원")
        void gpuUnitLease() {
            Allocation gpu = Fixtures.unitAllocation(2L, customer, rack, 5, 4, ServerType.GPU, 2_400, Fixtures.TODAY);
            assertThat(calculator.monthlyFee(gpu)).isEqualTo(960_000L);
        }

        @Test
        @DisplayName("하프랙 + GPU 3대(각 3,000W) = 2,800,000 + 9,000W × 400")
        void halfRackWithGpuServers() {
            Allocation half = Fixtures.rackLease(3L, customer, rack, "HALF", 1, 21, Fixtures.TODAY);
            for (int i = 0; i < 3; i++) {
                half.addServer(Fixtures.server("gpu" + i, ServerType.GPU, 1 + i * 4, 4, 3_000));
            }
            assertThat(calculator.monthlyFee(half)).isEqualTo(6_400_000L);
        }

        @Test
        @DisplayName("풀랙 안의 CPU 서버는 0원, 임대료만 5,000,000원")
        void fullRackWithCpuServers() {
            Allocation full = Fixtures.rackLease(4L, customer, rack, "FULL", 1, 42, Fixtures.TODAY);
            for (int i = 0; i < 8; i++) {
                full.addServer(Fixtures.server("cpu" + i, ServerType.CPU, 1 + i * 2, 2, 400));
            }
            assertThat(calculator.monthlyFee(full)).isEqualTo(5_000_000L);
        }

        @Test
        @DisplayName("임대료: HALF / FULL / 그 외(1U)")
        void leaseFee() {
            assertThat(calculator.leaseFee("HALF")).isEqualTo(2_800_000L);
            assertThat(calculator.leaseFee("FULL")).isEqualTo(5_000_000L);
            assertThat(calculator.leaseFee("1U")).isZero();
        }
    }

    @Nested
    @DisplayName("오늘 청구액 (currentFee)")
    class CurrentFee {

        @Test
        @DisplayName("할인 기간 중이면 50% 할인가")
        void duringDiscount() {
            Allocation allocation = cpu2u(Fixtures.TODAY.minusMonths(2));
            allocation.applyDiscount(Fixtures.TODAY, 3, 50);
            assertThat(calculator.currentFee(allocation, Fixtures.TODAY)).isEqualTo(150_000L);
        }

        @Test
        @DisplayName("할인 종료일 당일부터는 정가 (종료일 미포함)")
        void discountEndDateIsExcluded() {
            Allocation allocation = cpu2u(Fixtures.TODAY.minusMonths(2));
            allocation.applyDiscount(Fixtures.TODAY, 3, 50);
            LocalDate endDate = Fixtures.TODAY.plusMonths(3);
            assertThat(calculator.currentFee(allocation, endDate.minusDays(1))).isEqualTo(150_000L);
            assertThat(calculator.currentFee(allocation, endDate)).isEqualTo(300_000L);
        }

        @Test
        @DisplayName("할인이 없으면 정가")
        void noDiscount() {
            assertThat(calculator.currentFee(cpu2u(Fixtures.TODAY), Fixtures.TODAY)).isEqualTo(300_000L);
        }
    }

    @Nested
    @DisplayName("월 청구액 (일할 계산)")
    class ChargeFor {

        private final YearMonth november = YearMonth.of(2026, 11); // 30일

        @Test
        @DisplayName("한 달 전체를 사용하면 정가 그대로")
        void fullMonth() {
            MonthlyCharge charge = calculator.chargeFor(cpu2u(LocalDate.of(2026, 10, 1)), november);
            assertThat(charge.baseFee()).isEqualTo(300_000L);
            assertThat(charge.proratedFee()).isEqualTo(300_000L);
            assertThat(charge.discountAmount()).isZero();
        }

        @Test
        @DisplayName("달 중간에 시작하면 사용 일수만큼 일할 (11/11 시작 = 20일)")
        void startsMidMonth() {
            MonthlyCharge charge = calculator.chargeFor(cpu2u(LocalDate.of(2026, 11, 11)), november);
            assertThat(charge.proratedFee()).isEqualTo(200_000L);
        }

        @Test
        @DisplayName("해지일은 과금하지 않는다 (11/16 해지 = 15일)")
        void terminatedDateIsExcluded() {
            Allocation allocation = cpu2u(LocalDate.of(2026, 10, 1));
            allocation.terminate(LocalDate.of(2026, 11, 16));
            assertThat(calculator.chargeFor(allocation, november).proratedFee()).isEqualTo(150_000L);
        }

        @Test
        @DisplayName("시작 전의 달과 해지 후의 달은 청구 없음")
        void outsideActivePeriod() {
            Allocation allocation = cpu2u(LocalDate.of(2026, 11, 11));
            assertThat(calculator.chargeFor(allocation, YearMonth.of(2026, 10))).isEqualTo(MonthlyCharge.NONE);

            Allocation terminated = cpu2u(LocalDate.of(2026, 9, 1));
            terminated.terminate(LocalDate.of(2026, 10, 1));
            assertThat(calculator.chargeFor(terminated, november)).isEqualTo(MonthlyCharge.NONE);
        }

        @Test
        @DisplayName("SPEC 예시: 11/26 이전 → 26~30일(5일) 50% 할인 = 25,000원")
        void specDiscountExample() {
            Allocation allocation = cpu2u(LocalDate.of(2026, 10, 1));
            allocation.applyDiscount(LocalDate.of(2026, 11, 26), 3, 50);

            MonthlyCharge charge = calculator.chargeFor(allocation, november);

            assertThat(charge.proratedFee()).isEqualTo(300_000L);
            assertThat(charge.discountAmount()).isEqualTo(25_000L);
            assertThat(charge.netFee()).isEqualTo(275_000L);
        }

        @Test
        @DisplayName("한 달 내내 할인이면 할인액은 정가의 50%")
        void discountForWholeMonth() {
            Allocation allocation = cpu2u(LocalDate.of(2026, 10, 1));
            allocation.applyDiscount(LocalDate.of(2026, 10, 15), 3, 50);
            assertThat(calculator.chargeFor(allocation, november).discountAmount()).isEqualTo(150_000L);
        }
    }
}
