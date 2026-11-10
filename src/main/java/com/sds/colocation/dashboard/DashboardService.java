package com.sds.colocation.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.AllocationRepository;
import com.sds.colocation.allocation.ServerStatus;
import com.sds.colocation.customer.CustomerRepository;
import com.sds.colocation.rack.RackRepository;
import com.sds.colocation.rack.RackService;
import com.sds.colocation.rack.RackSpace;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final RackRepository rackRepository;
    private final AllocationRepository allocationRepository;
    private final CustomerRepository customerRepository;
    private final RackService rackService;
    private final Clock clock;

    public DashboardService(RackRepository rackRepository, AllocationRepository allocationRepository,
                            CustomerRepository customerRepository, RackService rackService, Clock clock) {
        this.rackRepository = rackRepository;
        this.allocationRepository = allocationRepository;
        this.customerRepository = customerRepository;
        this.rackService = rackService;
        this.clock = clock;
    }

    public DashboardView summary() {
        LocalDate today = LocalDate.now(clock);
        List<Allocation> allocations = allocationRepository.findAllActiveWithDetails();
        Map<Long, List<Allocation>> allocationsByRack = allocations.stream()
                .collect(Collectors.groupingBy(allocation -> allocation.getRack().getId()));

        List<DashboardView.RackUtilization> racks = rackRepository.findAllByOrderByIdAsc().stream()
                .map(rack -> new RackSpace(rack, allocationsByRack.getOrDefault(rack.getId(), List.of())))
                .map(space -> new DashboardView.RackUtilization(
                        space.rack().getId(),
                        space.rack().getName(),
                        space.rack().getTotalUnits(),
                        space.usedUnits(),
                        rate(space.usedUnits(), space.rack().getTotalUnits()),
                        space.rack().getMaxPowerWatts(),
                        space.usedPowerWatts(),
                        rate(space.usedPowerWatts(), space.rack().getMaxPowerWatts())))
                .toList();

        int totalUnits = racks.stream().mapToInt(DashboardView.RackUtilization::totalUnits).sum();
        int usedUnits = racks.stream().mapToInt(DashboardView.RackUtilization::usedUnits).sum();
        int maxPower = racks.stream().mapToInt(DashboardView.RackUtilization::maxPowerWatts).sum();
        int usedPower = racks.stream().mapToInt(DashboardView.RackUtilization::usedPowerWatts).sum();
        int discounted = (int) allocations.stream().filter(allocation -> allocation.isDiscountActiveOn(today)).count();
        int rebootPending = (int) allocations.stream()
                .flatMap(allocation -> allocation.getServers().stream())
                .filter(server -> server.getStatus() == ServerStatus.REBOOT_REQUIRED)
                .count();

        return new DashboardView(today, racks.size(), totalUnits, usedUnits, rate(usedUnits, totalUnits),
                maxPower, usedPower, rate(usedPower, maxPower), (int) customerRepository.count(), allocations.size(),
                discounted, rebootPending, rackService.monthlyRevenue(YearMonth.from(today)).summary(), racks);
    }

    private static double rate(int used, int total) {
        if (total == 0) {
            return 0;
        }
        return Math.round(used * 1000.0 / total) / 10.0;
    }
}
