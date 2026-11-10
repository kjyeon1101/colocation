package com.sds.colocation.billing;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sds.colocation.rack.RackService;

@RestController
@RequestMapping("/api/revenue")
public class RevenueController {

    private final RackService rackService;
    private final Clock clock;

    public RevenueController(RackService rackService, Clock clock) {
        this.rackService = rackService;
        this.clock = clock;
    }

    /** @param month yyyy-MM 형식. 없으면 이번 달. */
    @GetMapping
    public RevenueReport monthly(@RequestParam(required = false) YearMonth month) {
        return rackService.monthlyRevenue(month == null ? YearMonth.now(clock) : month);
    }

    @GetMapping("/trend")
    public List<RevenueSummary> trend(@RequestParam(required = false) YearMonth endMonth,
                                      @RequestParam(defaultValue = "6") int months) {
        if (months < 1 || months > 24) {
            throw new IllegalArgumentException("months는 1~24 사이여야 합니다.");
        }
        return rackService.revenueTrend(endMonth == null ? YearMonth.now(clock) : endMonth, months);
    }
}
