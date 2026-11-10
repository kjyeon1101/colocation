package com.sds.colocation.allocation;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 이전 보상 할인 기간. endDate는 할인에 포함되지 않는다. */
@Embeddable
public class DiscountPeriod {

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private int percent;

    protected DiscountPeriod() {
    }

    public DiscountPeriod(LocalDate startDate, LocalDate endDate, int percent) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.percent = percent;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && date.isBefore(endDate);
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getPercent() {
        return percent;
    }
}
