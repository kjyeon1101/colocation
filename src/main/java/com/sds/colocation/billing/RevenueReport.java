package com.sds.colocation.billing;

import java.util.List;

/**
 * 월 수익 리포트.
 * 순수익 = 일할 계산한 임대료 합계 - 이전 보상 할인액 - 그 달의 전력 증설 공사비 - 그 달의 랙 설치비
 */
public record RevenueReport(
        String month,
        long grossRevenue,
        long discountAmount,
        long constructionCost,
        long rackInstallCost,
        long netRevenue,
        List<Line> lines
) {

    public record Line(
            Long allocationId,
            String customerName,
            String rackName,
            String leaseUnit,
            long baseFee,
            long proratedFee,
            long discountAmount,
            long netFee
    ) {
    }

    public RevenueSummary summary() {
        return new RevenueSummary(month, grossRevenue, discountAmount, constructionCost, rackInstallCost, netRevenue);
    }
}
