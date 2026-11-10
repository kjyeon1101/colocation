package com.sds.colocation.billing;

public record RevenueSummary(
        String month,
        long grossRevenue,
        long discountAmount,
        long constructionCost,
        long rackInstallCost,
        long netRevenue
) {
}
