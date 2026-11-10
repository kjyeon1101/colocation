package com.sds.colocation.allocation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sds.colocation.billing.FeeCalculator;

@Component
public class AllocationViewMapper {

    private final FeeCalculator feeCalculator;
    private final Clock clock;

    public AllocationViewMapper(FeeCalculator feeCalculator, Clock clock) {
        this.feeCalculator = feeCalculator;
        this.clock = clock;
    }

    public AllocationView toView(Allocation allocation) {
        LocalDate today = LocalDate.now(clock);
        boolean rebootRequired = allocation.getServers().stream()
                .anyMatch(server -> server.getStatus() == ServerStatus.REBOOT_REQUIRED);
        Optional<DiscountPeriod> discount = allocation.latestDiscount();
        return new AllocationView(
                allocation.getId(),
                allocation.getCustomer().getId(),
                allocation.getCustomer().getName(),
                allocation.getCustomer().getTier(),
                allocation.getCustomer().getContactEmail(),
                allocation.getRack().getId(),
                allocation.getRack().getName(),
                allocation.getLeaseUnit().equals("1U") ? "UNIT" : allocation.getLeaseUnit(),
                allocation.getStartUnit(),
                allocation.getEndUnit(),
                allocation.getSizeUnits(),
                allocation.getStatus(),
                allocation.getStartDate(),
                allocation.getEndDate(),
                allocation.getTotalPowerWatts(),
                feeCalculator.monthlyFee(allocation),
                feeCalculator.currentFee(allocation, today),
                allocation.isDiscountActiveOn(today),
                discount.map(DiscountPeriod::getPercent).orElse(0),
                discount.map(DiscountPeriod::getStartDate).orElse(null),
                discount.map(DiscountPeriod::getEndDate).orElse(null),
                rebootRequired,
                allocation.getMemo(),
                allocation.getServers().stream().map(ServerView::from).toList()
        );
    }
}
