package com.sds.colocation.billing;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.sds.colocation.common.ColocationProperties;
import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.Server;
import com.sds.colocation.allocation.ServerType;

/**
 * 요금 계산 규칙.
 * <ul>
 *     <li>CPU 서버: 차지하는 U 수 × U당 단가 (하프랙/풀랙 안의 CPU 서버는 공간 요금이 임대료에 포함)</li>
 *     <li>GPU 서버: 소비 전력(W) × W당 단가 (어느 임대 방식이든 전력 요금은 별도)</li>
 *     <li>하프랙/풀랙: 고정 임대료</li>
 * </ul>
 */
@Component
public class FeeCalculator {

    private final ColocationProperties.Pricing pricing;

    public FeeCalculator(ColocationProperties properties) {
        this.pricing = properties.pricing();
    }

    public long monthlyFee(Allocation allocation) {
        long rate = leaseFee(allocation.getLeaseUnit());
        for (Server server : allocation.getServers()) {
            rate += serverFee(server, allocation.getLeaseUnit());
        }
        return rate;
    }

    public long leaseFee(String leaseUnit) {
        if (leaseUnit.equals("HALF")) {
            return pricing.halfRackFee();
        }
        if (leaseUnit.equals("FULL")) {
            return pricing.fullRackFee();
        }
        return 0;
    }

    public long serverFee(Server server, String leaseUnit) {
        return serverFee(server.getType(), server.getSizeUnits(), server.getPowerWatts(), leaseUnit);
    }

    public long serverFee(ServerType type, int sizeUnits, int powerWatts, String leaseUnit) {
        long rate;
        if (type == ServerType.GPU) {
            rate = pricing.gpuPricePerWatt();
            return powerWatts * rate;
        }
        if (!leaseUnit.equals("1U")) {
            return 0;
        }
        rate = pricing.cpuPricePerUnit();
        return sizeUnits * rate;
    }

    /** 오늘 기준으로 실제 청구되는 월 요금 (할인 중이면 할인가). */
    public long currentFee(Allocation allocation, LocalDate today) {
        long fee = monthlyFee(allocation);
        int rate = allocation.discountPercentOn(today);
        return fee - fee * rate / 100;
    }

    /** 해당 월의 청구액. 사용 일수와 할인 일수만큼 일할 계산한다. */
    public MonthlyCharge chargeFor(Allocation allocation, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate nextMonthStart = month.plusMonths(1).atDay(1);
        LocalDate activeFrom = later(allocation.getStartDate(), monthStart);
        LocalDate activeUntil = allocation.getEndDate() == null ? nextMonthStart : earlier(allocation.getEndDate(), nextMonthStart);
        if (!activeFrom.isBefore(activeUntil)) {
            return MonthlyCharge.NONE;
        }

        long rate = monthlyFee(allocation);
        int daysInMonth = month.lengthOfMonth();
        long activeDays = ChronoUnit.DAYS.between(activeFrom, activeUntil);
        long proratedFee = rate * activeDays / daysInMonth;
        long baseFee = rate;

        rate = 0;
        if (!allocation.getDiscounts().isEmpty()) {
            for (LocalDate day = activeFrom; day.isBefore(activeUntil); day = day.plusDays(1)) {
                rate += allocation.discountPercentOn(day);
            }
        }
        long discountAmount = baseFee * rate / (100L * daysInMonth);
        return new MonthlyCharge(baseFee, proratedFee, discountAmount);
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
