package com.sds.colocation.allocation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.sds.colocation.customer.Customer;
import com.sds.colocation.rack.Rack;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

/**
 * 고객이 임대한 랙의 연속된 U 구간(임대 공간).
 * U 단위 임대 공간은 서버 1대와 크기가 같고, 하프랙/풀랙 임대 공간은 그 구간 안에 고객 서버를 여러 대 둘 수 있다.
 */
@Entity
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rack_id")
    private Rack rack;

    /** "1U" = U 단위, "HALF" = 하프랙, "FULL" = 풀랙 */
    @Column(nullable = false)
    private String leaseUnit;

    private int startUnit;

    private int sizeUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllocationStatus status = AllocationStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDate startDate;

    /** 해지일. 이 날짜부터는 과금하지 않는다. */
    private LocalDate endDate;

    /** 이전할 때마다 하나씩 쌓이는 할인 기간. 지난 달 수익을 다시 계산할 때도 쓰므로 지우지 않는다. */
    @ElementCollection
    @CollectionTable(name = "allocation_discount", joinColumns = @JoinColumn(name = "allocation_id"))
    @OrderBy("startDate ASC")
    private List<DiscountPeriod> discounts = new ArrayList<>();

    private String memo;

    @OneToMany(mappedBy = "allocation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startUnit ASC")
    private List<Server> servers = new ArrayList<>();

    protected Allocation() {
    }

    public Allocation(Customer customer, Rack rack, String leaseUnit, int startUnit, int sizeUnits, LocalDate startDate) {
        if (!rack.isValidRange(startUnit, sizeUnits)) {
            throw new IllegalArgumentException("랙 범위를 벗어난 위치입니다. (U" + startUnit + ", " + sizeUnits + "U)");
        }
        this.customer = customer;
        this.rack = rack;
        this.leaseUnit = leaseUnit;
        this.startUnit = startUnit;
        this.sizeUnits = sizeUnits;
        this.startDate = startDate;
    }

    public int getEndUnit() {
        return startUnit + sizeUnits - 1;
    }

    public boolean overlaps(int otherStart, int otherSize) {
        int otherEnd = otherStart + otherSize - 1;
        return startUnit <= otherEnd && otherStart <= getEndUnit();
    }

    public boolean covers(int otherStart, int otherSize) {
        return startUnit <= otherStart && otherStart + otherSize - 1 <= getEndUnit();
    }

    public boolean isOwnedBy(Long customerId) {
        return customer.getId() == customerId;
    }

    public boolean isActive() {
        return status == AllocationStatus.ACTIVE;
    }

    public int getTotalPowerWatts() {
        return servers.stream().mapToInt(Server::getPowerWatts).sum();
    }

    public void addServer(Server server) {
        if (!covers(server.getStartUnit(), server.getSizeUnits())) {
            throw new IllegalArgumentException("서버가 임대 공간을 벗어납니다.");
        }
        servers.add(server);
        server.assignTo(this);
    }

    public boolean hasServerOverlapping(int otherStart, int otherSize) {
        return servers.stream().anyMatch(server -> server.overlaps(otherStart, otherSize));
    }

    /** 임대 공간을 다른 위치로 옮긴다. 안에 있는 서버도 같은 간격만큼 함께 이동한다. */
    public void moveTo(Rack targetRack, int targetStartUnit) {
        if (!targetRack.isValidRange(targetStartUnit, sizeUnits)) {
            throw new IllegalArgumentException("이동할 위치가 랙 범위를 벗어납니다.");
        }
        int offset = targetStartUnit - startUnit;
        servers.forEach(server -> server.shift(offset));
        this.rack = targetRack;
        this.startUnit = targetStartUnit;
    }

    public DiscountPeriod applyDiscount(LocalDate from, int months, int percent) {
        DiscountPeriod discount = new DiscountPeriod(from, from.plusMonths(months), percent);
        discounts.add(discount);
        return discount;
    }

    /** 그날 적용되는 할인율. */
    public int discountPercentOn(LocalDate date) {
        return discounts.stream()
                .filter(discount -> discount.covers(date))
                .mapToInt(DiscountPeriod::getPercent)
                .sum();
    }

    public boolean isDiscountActiveOn(LocalDate date) {
        return discountPercentOn(date) > 0;
    }

    /** 가장 최근에 시작한 할인. 화면 표시용. */
    public Optional<DiscountPeriod> latestDiscount() {
        return discounts.stream().max(Comparator.comparing(DiscountPeriod::getStartDate));
    }

    public void markServersRebootRequired() {
        servers.forEach(Server::requireReboot);
    }

    public void terminate(LocalDate date) {
        this.status = AllocationStatus.TERMINATED;
        this.endDate = date;
    }

    public void updateMemo(String memo) {
        this.memo = memo;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Rack getRack() {
        return rack;
    }

    public String getLeaseUnit() {
        return leaseUnit;
    }

    public int getStartUnit() {
        return startUnit;
    }

    public int getSizeUnits() {
        return sizeUnits;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public List<DiscountPeriod> getDiscounts() {
        return discounts;
    }

    public String getMemo() {
        return memo;
    }

    public List<Server> getServers() {
        return servers;
    }
}
