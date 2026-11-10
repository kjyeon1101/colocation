package com.sds.colocation.lease;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sds.colocation.billing.FeeCalculator;
import com.sds.colocation.common.BusinessException;
import com.sds.colocation.common.ColocationProperties;
import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.AllocationRepository;
import com.sds.colocation.allocation.AllocationView;
import com.sds.colocation.allocation.AllocationViewMapper;
import com.sds.colocation.customer.Customer;
import com.sds.colocation.customer.CustomerRepository;
import com.sds.colocation.customer.CustomerTier;
import com.sds.colocation.rack.Rack;
import com.sds.colocation.rack.RackRepository;
import com.sds.colocation.rack.RackService;

/**
 * 랙 단위 임대. 하프랙·풀랙 자리에 있는 기존 U 단위 고객을 다른 빈자리로 이전시키고 공간을 통째로 임대한다.
 */
@Service
@Transactional(readOnly = true)
public class LeaseService {

    private final CustomerRepository customerRepository;
    private final RackRepository rackRepository;
    private final AllocationRepository allocationRepository;
    private final RackService rackService;
    private final FeeCalculator feeCalculator;
    private final AllocationViewMapper allocationViewMapper;
    private final ColocationProperties properties;
    private final Clock clock;

    public LeaseService(CustomerRepository customerRepository, RackRepository rackRepository,
                        AllocationRepository allocationRepository, RackService rackService,
                        FeeCalculator feeCalculator, AllocationViewMapper allocationViewMapper,
                        ColocationProperties properties, Clock clock) {
        this.customerRepository = customerRepository;
        this.rackRepository = rackRepository;
        this.allocationRepository = allocationRepository;
        this.rackService = rackService;
        this.feeCalculator = feeCalculator;
        this.allocationViewMapper = allocationViewMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public LeasePlan plan(LeaseRequest request) {
        Customer customer = getCustomer(request.customerId());
        Rack rack = rackRepository.findById(request.rackId())
                .orElseThrow(() -> BusinessException.notFound("랙", request.rackId()));
        List<Allocation> activeAllocations = allocationRepository.findAllActiveWithDetails();
        return buildPlan(customer, rack, request, activeAllocations).plan();
    }

    @Transactional
    public AllocationView execute(LeaseRequest request) {
        Customer customer = getCustomer(request.customerId());
        Rack rack = rackRepository.findById(request.rackId())
                .orElseThrow(() -> BusinessException.notFound("랙", request.rackId()));
        List<Allocation> activeAllocations = allocationRepository.findAllActiveWithDetails();
        PlanResult result = buildPlan(customer, rack, request, activeAllocations);
        if (!result.plan().feasible()) {
            throw BusinessException.conflict("LEASE_NOT_FEASIBLE", result.plan().reason());
        }

        Map<Long, Rack> racksById = rackRepository.findAll().stream()
                .collect(Collectors.toMap(Rack::getId, Function.identity()));
        String reason = customer.getName() + " " + (request.leaseUnit().equals("FULL") ? "풀랙" : "하프랙") + " 임대 공간 확보";
        for (LeasePlan.Move move : result.plan().moves()) {
            Allocation allocation = result.allocationsById().get(move.allocationId());
            rackService.move(allocation, racksById.get(move.toRackId()), move.toStartUnit(), reason);
        }

        Allocation lease = allocationRepository.save(new Allocation(customer, rack, request.leaseUnit(),
                result.plan().startUnit(), result.plan().endUnit() - result.plan().startUnit() + 1,
                LocalDate.now(clock)));
        return allocationViewMapper.toView(lease);
    }

    private record PlanResult(LeasePlan plan, Map<Long, Allocation> allocationsById) {
    }

    private PlanResult buildPlan(Customer customer, Rack rack, LeaseRequest request, List<Allocation> activeAllocations) {
        if (!request.leaseUnit().equals("HALF") && !request.leaseUnit().equals("FULL")) {
            throw BusinessException.badRequest("INVALID_LEASE_UNIT", "랙 단위 임대는 하프랙 또는 풀랙만 가능합니다.");
        }
        if (customer.getTier() != CustomerTier.ENTERPRISE) {
            throw BusinessException.badRequest("NOT_ENTERPRISE", "하프랙·풀랙 임대는 엔터프라이즈 고객만 신청할 수 있습니다.");
        }
        int[] range = targetRange(rack, request);
        int startUnit = range[0];
        int sizeUnits = range[1];
        LocalDate today = LocalDate.now(clock);
        LocalDate discountEndDate = today.plusMonths(properties.relocation().discountMonths());
        long monthlyFee = feeCalculator.leaseFee(request.leaseUnit());

        List<Allocation> conflicts = activeAllocations.stream()
                .filter(allocation -> allocation.getRack().getId().equals(rack.getId()))
                .filter(allocation -> allocation.overlaps(startUnit, sizeUnits))
                .sorted(Comparator.comparing(Allocation::getSizeUnits).reversed().thenComparing(Allocation::getId))
                .toList();
        Map<Long, Allocation> allocationsById = conflicts.stream()
                .collect(Collectors.toMap(Allocation::getId, Function.identity()));

        Optional<Allocation> otherLease = conflicts.stream()
                .filter(allocation -> !allocation.getLeaseUnit().equals("1U"))
                .findFirst();
        if (otherLease.isPresent()) {
            String reason = "이미 " + otherLease.get().getCustomer().getName() + "의 "
                    + (otherLease.get().getLeaseUnit().equals("FULL") ? "풀랙" : "하프랙") + " 임대 공간과 겹칩니다.";
            return new PlanResult(new LeasePlan(customer.getId(), customer.getName(), rack.getId(), rack.getName(),
                    request.leaseUnit(), startUnit, startUnit + sizeUnits - 1, monthlyFee, false, reason,
                    List.of(), 0, discountEndDate), allocationsById);
        }

        SpacePlanner planner = new SpacePlanner(rackRepository.findAllByOrderByIdAsc(), activeAllocations);
        conflicts.forEach(planner::release);
        planner.reserve(rack.getId(), startUnit, sizeUnits, 0);

        int discountPercent = properties.relocation().discountPercent();
        int discountMonths = properties.relocation().discountMonths();
        List<LeasePlan.Move> moves = new ArrayList<>();
        String failure = null;
        for (Allocation allocation : conflicts) {
            Optional<SpacePlanner.Slot> slot = planner.findSlot(allocation.getSizeUnits(), allocation.getTotalPowerWatts(),
                    rack.getId());
            if (slot.isEmpty()) {
                failure = "임대 공간 #" + allocation.getId() + "(" + allocation.getCustomer().getName() + ", "
                        + allocation.getSizeUnits() + "U)을 옮길 빈자리가 없습니다. 랙 추가나 전력 증설이 필요합니다.";
                break;
            }
            planner.reserve(slot.get().rack().getId(), slot.get().startUnit(), allocation.getSizeUnits(),
                    allocation.getTotalPowerWatts());
            long fee = feeCalculator.monthlyFee(allocation);
            moves.add(new LeasePlan.Move(allocation.getId(), allocation.getCustomer().getName(),
                    rack.getId(), rack.getName(), allocation.getStartUnit(),
                    slot.get().rack().getId(), slot.get().rack().getName(), slot.get().startUnit(),
                    allocation.getSizeUnits(), allocation.getTotalPowerWatts(),
                    fee, fee - fee * discountPercent / 100));
        }

        long totalDiscountCost = moves.stream()
                .mapToLong(move -> (move.monthlyFee() - move.discountedFee()) * discountMonths)
                .sum();
        LeasePlan plan = new LeasePlan(customer.getId(), customer.getName(), rack.getId(), rack.getName(),
                request.leaseUnit(), startUnit, startUnit + sizeUnits - 1, monthlyFee, failure == null, failure,
                moves, totalDiscountCost, discountEndDate);
        return new PlanResult(plan, allocationsById);
    }

    /** @return [시작 U, 크기] */
    private int[] targetRange(Rack rack, LeaseRequest request) {
        if (request.leaseUnit().equals("FULL")) {
            return new int[]{1, rack.getTotalUnits()};
        }
        if (request.position() == null) {
            throw BusinessException.badRequest("POSITION_REQUIRED", "하프랙은 위치(LOWER/UPPER)를 골라야 합니다.");
        }
        int half = properties.rack().halfUnits();
        return request.position() == HalfPosition.LOWER
                ? new int[]{1, half}
                : new int[]{half + 1, rack.getTotalUnits() - half};
    }

    private Customer getCustomer(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> BusinessException.notFound("고객", id));
    }
}
