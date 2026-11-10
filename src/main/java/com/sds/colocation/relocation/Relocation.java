package com.sds.colocation.relocation;

import java.time.LocalDate;

import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.DiscountPeriod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** 임대 공간 이전 이력. 화면 표시용 이름은 이전 시점 값을 그대로 남긴다. */
@Entity
public class Relocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id")
    private Allocation allocation;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String fromRackName;

    private int fromStartUnit;

    @Column(nullable = false)
    private String toRackName;

    private int toStartUnit;

    private int sizeUnits;

    @Column(nullable = false)
    private LocalDate relocatedDate;

    private int discountPercent;

    private LocalDate discountEndDate;

    private String reason;

    protected Relocation() {
    }

    public Relocation(Allocation allocation, String fromRackName, int fromStartUnit, LocalDate relocatedDate,
                      DiscountPeriod discount, String reason) {
        this.allocation = allocation;
        this.customerName = allocation.getCustomer().getName();
        this.fromRackName = fromRackName;
        this.fromStartUnit = fromStartUnit;
        this.toRackName = allocation.getRack().getName();
        this.toStartUnit = allocation.getStartUnit();
        this.sizeUnits = allocation.getSizeUnits();
        this.relocatedDate = relocatedDate;
        this.discountPercent = discount.getPercent();
        this.discountEndDate = discount.getEndDate();
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Allocation getAllocation() {
        return allocation;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getFromRackName() {
        return fromRackName;
    }

    public int getFromStartUnit() {
        return fromStartUnit;
    }

    public String getToRackName() {
        return toRackName;
    }

    public int getToStartUnit() {
        return toStartUnit;
    }

    public int getSizeUnits() {
        return sizeUnits;
    }

    public LocalDate getRelocatedDate() {
        return relocatedDate;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public LocalDate getDiscountEndDate() {
        return discountEndDate;
    }

    public String getReason() {
        return reason;
    }
}
