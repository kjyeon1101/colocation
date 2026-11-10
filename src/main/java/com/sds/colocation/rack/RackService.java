package com.sds.colocation.rack;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sds.colocation.billing.FeeCalculator;
import com.sds.colocation.billing.MonthlyCharge;
import com.sds.colocation.billing.RevenueReport;
import com.sds.colocation.billing.RevenueSummary;
import com.sds.colocation.common.BusinessException;
import com.sds.colocation.common.ColocationProperties;
import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.AllocationRepository;
import com.sds.colocation.allocation.AllocationStatus;
import com.sds.colocation.allocation.AllocationViewMapper;
import com.sds.colocation.allocation.DiscountPeriod;
import com.sds.colocation.allocation.Server;
import com.sds.colocation.allocation.ServerRepository;
import com.sds.colocation.allocation.ServerType;
import com.sds.colocation.customer.Customer;
import com.sds.colocation.customer.CustomerRepository;
import com.sds.colocation.notification.Notification;
import com.sds.colocation.notification.NotificationRepository;
import com.sds.colocation.placement.BulkPlacementRequest;
import com.sds.colocation.placement.PlacementRequest;
import com.sds.colocation.placement.PlacementResult;
import com.sds.colocation.power.PowerUpgrade;
import com.sds.colocation.power.PowerUpgradeRepository;
import com.sds.colocation.power.PowerUpgradeRequest;
import com.sds.colocation.power.PowerUpgradeView;
import com.sds.colocation.relocation.RelocateRequest;
import com.sds.colocation.relocation.Relocation;
import com.sds.colocation.relocation.RelocationCompletedEvent;
import com.sds.colocation.relocation.RelocationRepository;
import com.sds.colocation.relocation.RelocationView;

/**
 * 랙 운영 서비스. 랙 조회·추가, 서버 입고, 이전, 전력 증설, 월 정산을 처리한다.
 */
@Service
public class RackService {

    private static final Logger log = LoggerFactory.getLogger(RackService.class);

    private final RackRepository rackRepository;
    private final AllocationRepository allocationRepository;
    private final ServerRepository serverRepository;
    private final CustomerRepository customerRepository;
    private final AllocationViewMapper allocationViewMapper;
    private final RackInstallationRepository rackInstallationRepository;
    private final RelocationRepository relocationRepository;
    private final PowerUpgradeRepository powerUpgradeRepository;
    private final NotificationRepository notificationRepository;
    private final FeeCalculator feeCalculator;
    private final TransactionTemplate transactionTemplate;
    private final ColocationProperties properties;
    private final Clock clock;

    public RackService(RackRepository rackRepository, AllocationRepository allocationRepository,
                       ServerRepository serverRepository, CustomerRepository customerRepository,
                       AllocationViewMapper allocationViewMapper, RackInstallationRepository rackInstallationRepository,
                       RelocationRepository relocationRepository, PowerUpgradeRepository powerUpgradeRepository,
                       NotificationRepository notificationRepository, FeeCalculator feeCalculator,
                       TransactionTemplate transactionTemplate, ColocationProperties properties, Clock clock) {
        this.rackRepository = rackRepository;
        this.allocationRepository = allocationRepository;
        this.serverRepository = serverRepository;
        this.customerRepository = customerRepository;
        this.allocationViewMapper = allocationViewMapper;
        this.rackInstallationRepository = rackInstallationRepository;
        this.relocationRepository = relocationRepository;
        this.powerUpgradeRepository = powerUpgradeRepository;
        this.notificationRepository = notificationRepository;
        this.feeCalculator = feeCalculator;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 랙

    @Transactional(readOnly = true)
    public List<RackView> findAll() {
        List<RackView> result = new ArrayList<>();
        for (Rack rack : rackRepository.findAllByOrderByIdAsc()) {
            List<Allocation> allocations = allocationRepository.findByRackIdAndStatus(rack.getId(), AllocationStatus.ACTIVE);
            result.add(toView(rack, allocations));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RackView findById(Long id) {
        Rack rack = getRack(id);
        return toView(rack, allocationRepository.findActiveByRackId(id));
    }

    /** 새 랙은 항상 기본 크기·기본 전력으로 만들고, 설치비 이력을 오늘 날짜로 남긴다. */
    @Transactional
    public RackView create(RackCreateRequest request) {
        if (rackRepository.existsByName(request.name())) {
            throw BusinessException.conflict("DUPLICATE_RACK", "이미 같은 이름의 랙이 있습니다: " + request.name());
        }
        ColocationProperties.Rack rackPolicy = properties.rack();
        Rack rack = rackRepository.save(new Rack(request.name(),
                rackPolicy.defaultUnits(), rackPolicy.defaultPowerWatts()));
        rackInstallationRepository.save(new RackInstallation(rack, rackPolicy.installCost(), LocalDate.now(clock)));
        return toView(rack, List.of());
    }

    @Transactional(readOnly = true)
    public List<RackInstallationView> installationHistory() {
        return rackInstallationRepository.findAllByOrderByInstalledDateDescIdDesc().stream()
                .map(RackInstallationView::from)
                .toList();
    }

    public Rack getRack(Long id) {
        return rackRepository.findById(id).orElseThrow(() -> BusinessException.notFound("랙", id));
    }

    private RackView toView(Rack rack, List<Allocation> allocations) {
        RackSpace space = new RackSpace(rack, allocations);
        return new RackView(
                rack.getId(),
                rack.getName(),
                rack.getTotalUnits(),
                rack.getMaxPowerWatts(),
                rack.getUsedPowerWatts(),
                space.usedUnits(),
                space.allocations().stream()
                        .sorted((a, b) -> Integer.compare(a.getStartUnit(), b.getStartUnit()))
                        .map(allocationViewMapper::toView)
                        .toList()
        );
    }

    // ------------------------------------------------------------------ 입고

    @Transactional
    public PlacementResult place(PlacementRequest request) {
        String requestKey = normalize(request.requestKey());
        if (requestKey != null) {
            Optional<Server> alreadyPlaced = serverRepository.findByRequestKey(requestKey);
            if (alreadyPlaced.isPresent()) {
                return PlacementResult.of(alreadyPlaced.get(), true);
            }
        }
        Server server = placeServer(request.customerId(), request.rackId(), request.startUnit(), request.hostname(),
                request.type(), request.sizeUnits(), request.powerWatts(), requestKey);
        return PlacementResult.of(server, false);
    }

    public List<PlacementResult> placeBulk(BulkPlacementRequest request) {
        List<PlacementResult> results = new ArrayList<>();
        for (BulkPlacementRequest.Item item : request.servers()) {
            try {
                PlacementResult result = transactionTemplate.execute(status -> PlacementResult.of(
                        placeServer(request.customerId(), item.rackId(), item.startUnit(), item.hostname(),
                                item.type(), item.sizeUnits(), item.powerWatts(), null), false));
                results.add(result);
            } catch (BusinessException e) {
                log.warn("일괄 입고 중 {} 입고 실패: {}", item.hostname(), e.getMessage());
            }
        }
        return results;
    }

    private Server placeServer(Long customerId, Long rackId, int startUnit, String hostname, ServerType type,
                               int sizeUnits, int powerWatts, String requestKey) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("고객", customerId));
        Rack rack = rackRepository.findById(rackId)
                .orElseThrow(() -> BusinessException.notFound("랙", rackId));
        if (startUnit < 1 || startUnit + sizeUnits > rack.getTotalUnits()) {
            throw BusinessException.badRequest("OUT_OF_RANGE",
                    rack.getName() + " 랙은 U1~U" + rack.getTotalUnits() + "까지입니다. (요청: U" + startUnit + ", " + sizeUnits + "U)");
        }

        RackSpace space = new RackSpace(rack, allocationRepository.findAll());
        if (!space.canSupply(powerWatts)) {
            throw BusinessException.conflict("POWER_EXCEEDED",
                    rack.getName() + " 랙 전력 한도를 넘습니다. (사용 " + space.usedPowerWatts() + "W + 요청 "
                            + powerWatts + "W > 최대 " + rack.getMaxPowerWatts() + "W)");
        }

        LocalDate today = LocalDate.now(clock);
        Server server = new Server(hostname, type, startUnit, sizeUnits, powerWatts, today, requestKey);

        Optional<Allocation> leasedSpace = space.findCovering(startUnit, sizeUnits)
                .filter(allocation -> !allocation.getLeaseUnit().equals("1U"));
        if (leasedSpace.isPresent()) {
            Allocation lease = leasedSpace.get();
            if (!lease.isOwnedBy(customerId)) {
                throw BusinessException.conflict("OCCUPIED_BY_OTHER",
                        "다른 고객(" + lease.getCustomer().getName() + ")이 임대한 공간입니다.");
            }
            if (lease.hasServerOverlapping(startUnit, sizeUnits)) {
                throw BusinessException.conflict("SLOT_OCCUPIED", "임대 공간 안의 해당 위치에 이미 서버가 있습니다.");
            }
            lease.addServer(server);
            rack.addUsedPower(powerWatts);
            return serverRepository.save(server);
        }

        if (!space.isFree(startUnit, sizeUnits)) {
            throw BusinessException.conflict("SLOT_OCCUPIED",
                    rack.getName() + " U" + startUnit + "~U" + (startUnit + sizeUnits - 1) + " 구간은 이미 사용 중입니다.");
        }
        Allocation allocation = new Allocation(customer, rack, "1U", startUnit, sizeUnits, today);
        allocation.addServer(server);
        allocationRepository.save(allocation);
        rack.addUsedPower(powerWatts);
        return server;
    }

    private static String normalize(String requestKey) {
        return requestKey == null || requestKey.isBlank() ? null : requestKey.trim();
    }

    // ------------------------------------------------------------------ 이전

    /** 운영자가 U 단위 임대 공간을 직접 다른 자리로 옮긴다. */
    @Transactional
    public RelocationView relocate(Long allocationId, RelocateRequest request) {
        Allocation allocation = allocationRepository.findWithDetailsById(allocationId)
                .orElseThrow(() -> BusinessException.notFound("임대 공간", allocationId));
        if (!allocation.isActive()) {
            throw BusinessException.conflict("NOT_ACTIVE", "해지된 임대 공간은 이전할 수 없습니다.");
        }
        if (!allocation.getLeaseUnit().equals("1U")) {
            throw BusinessException.conflict("RACK_LEASE_NOT_MOVABLE", "하프랙·풀랙 임대 공간은 이전 대상이 아닙니다.");
        }
        Rack target = rackRepository.findById(request.rackId())
                .orElseThrow(() -> BusinessException.notFound("랙", request.rackId()));
        boolean sameRack = target.getId().equals(allocation.getRack().getId());
        if (sameRack && allocation.getStartUnit() == request.startUnit()) {
            throw BusinessException.badRequest("SAME_POSITION", "현재 위치와 같은 자리입니다.");
        }

        RackSpace targetSpace = new RackSpace(target, allocationRepository.findActiveByRackId(target.getId()));
        if (!targetSpace.isFreeExcept(request.startUnit(), allocation.getSizeUnits(), allocation.getId())) {
            throw BusinessException.conflict("SLOT_OCCUPIED",
                    target.getName() + " U" + request.startUnit() + "부터 " + allocation.getSizeUnits() + "U를 비울 수 없습니다.");
        }
        int powerWatts = allocation.getTotalPowerWatts();
        if (!sameRack && target.getUsedPowerWatts() + powerWatts > target.getMaxPowerWatts()) {
            throw BusinessException.conflict("POWER_EXCEEDED", target.getName() + " 랙의 남은 전력이 부족합니다.");
        }

        String reason = request.reason() == null || request.reason().isBlank() ? "운영자 수동 이전" : request.reason();
        return RelocationView.from(move(allocation, target, request.startUnit(), reason));
    }

    /**
     * 임대 공간을 옮기고, 할인 적용·재부팅 표시·이력 저장·안내 메일 발송을 한다.
     * 자리와 전력 검사는 호출하는 쪽(수동 이전, 랙 단위 임대)이 끝낸 상태여야 한다.
     */
    @Transactional
    public Relocation move(Allocation allocation, Rack target, int targetStartUnit, String reason) {
        LocalDate today = LocalDate.now(clock);
        String fromRackName = allocation.getRack().getName();
        int fromStartUnit = allocation.getStartUnit();
        boolean sameRack = target.getId().equals(allocation.getRack().getId());
        int powerWatts = allocation.getTotalPowerWatts();

        allocation.moveTo(target, targetStartUnit);
        if (!sameRack) {
            target.addUsedPower(powerWatts);
        }

        // 이전 보상 할인
        ColocationProperties.Relocation policy = properties.relocation();
        DiscountPeriod discount = allocation.applyDiscount(today, policy.discountMonths(),
                policy.discountPercent());
        allocation.markServersRebootRequired();
        Relocation relocation = relocationRepository.save(
                new Relocation(allocation, fromRackName, fromStartUnit, today, discount, reason));

        // 고객 재부팅 안내 메일 발송 (외부 메일 시스템 호출 흉내: 응답이 늦게 온다)
        Customer customer = allocation.getCustomer();
        RelocationCompletedEvent notice = new RelocationCompletedEvent(
                customer.getId(), customer.getName(), customer.getContactEmail(),
                allocation.getId(), fromRackName, fromStartUnit, target.getName(), targetStartUnit,
                discount.getPercent(), discount.getEndDate());
        try {
            Thread.sleep(properties.notification().simulatedLatencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[MAIL] to={} title={}", customer.getContactEmail(), notice.title());
        notificationRepository.save(new Notification(customer.getId(), customer.getName(), customer.getContactEmail(),
                notice.title(), notice.message(), LocalDateTime.now(clock)));

        return relocation;
    }

    @Transactional(readOnly = true)
    public List<RelocationView> relocationHistory() {
        return relocationRepository.findAllByOrderByRelocatedDateDescIdDesc().stream()
                .map(RelocationView::from)
                .toList();
    }

    // ------------------------------------------------------------------ 전력 증설

    @Transactional
    public PowerUpgradeView upgradePower(Long rackId, PowerUpgradeRequest request) {
        Rack rack = getRack(rackId);
        ColocationProperties.Power power = properties.power();
        int beforeWatts = rack.getMaxPowerWatts();
        rack.increaseMaxPower(request.steps() * power.upgradeStepWatts());
        long cost = request.steps() * power.upgradeStepCost();
        PowerUpgrade upgrade = powerUpgradeRepository.save(
                new PowerUpgrade(rack, beforeWatts, request.steps(), cost, LocalDate.now(clock)));
        return PowerUpgradeView.from(upgrade);
    }

    @Transactional(readOnly = true)
    public List<PowerUpgradeView> powerUpgradeHistory() {
        return powerUpgradeRepository.findAllByOrderByUpgradedDateDescIdDesc().stream()
                .map(PowerUpgradeView::from)
                .toList();
    }

    // ------------------------------------------------------------------ 정산

    @Transactional(readOnly = true)
    public RevenueReport monthlyRevenue(YearMonth month) {
        return buildReport(month, allocationRepository.findAllActiveWithDetails());
    }

    /** 끝 달을 포함해 최근 months개월의 요약. */
    @Transactional(readOnly = true)
    public List<RevenueSummary> revenueTrend(YearMonth endMonth, int months) {
        List<Allocation> allocations = allocationRepository.findAllActiveWithDetails();
        List<RevenueSummary> result = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            result.add(buildReport(endMonth.minusMonths(i), allocations).summary());
        }
        return result;
    }

    private RevenueReport buildReport(YearMonth month, List<Allocation> allocations) {
        List<RevenueReport.Line> lines = allocations.stream()
                .sorted(Comparator.comparing(Allocation::getId))
                .map(allocation -> toLine(allocation, feeCalculator.chargeFor(allocation, month)))
                .filter(line -> line.proratedFee() > 0)
                .toList();

        long gross = lines.stream().mapToLong(RevenueReport.Line::proratedFee).sum();
        long discount = lines.stream().mapToLong(RevenueReport.Line::discountAmount).sum();
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        long construction = powerUpgradeRepository.sumCostBetween(from, to);
        long rackInstall = rackInstallationRepository.sumCostBetween(from, to);
        return new RevenueReport(month.toString(), gross, discount, construction, rackInstall,
                gross - discount - construction - rackInstall, lines);
    }

    private RevenueReport.Line toLine(Allocation allocation, MonthlyCharge charge) {
        return new RevenueReport.Line(
                allocation.getId(),
                allocation.getCustomer().getName(),
                allocation.getRack().getName(),
                allocation.getLeaseUnit().equals("1U") ? "UNIT" : allocation.getLeaseUnit(),
                charge.baseFee(),
                charge.proratedFee(),
                charge.discountAmount(),
                charge.netFee()
        );
    }
}
