package com.sds.colocation.lease;

import java.time.LocalDate;
import java.util.List;

/**
 * 랙 단위 임대 계획. 자리를 비우기 위해 이전해야 할 기존 임대 공간과 이전 비용(할인액)을 보여준다.
 *
 * @param feasible          계획대로 실행할 수 있으면 true
 * @param reason            실행할 수 없을 때 이유
 * @param totalDiscountCost 이전 고객에게 할인 기간 동안 깎아 주는 총액
 */
public record LeasePlan(
        Long customerId,
        String customerName,
        Long rackId,
        String rackName,
        String leaseUnit,
        int startUnit,
        int endUnit,
        long monthlyFee,
        boolean feasible,
        String reason,
        List<Move> moves,
        long totalDiscountCost,
        LocalDate discountEndDate
) {

    public record Move(
            Long allocationId,
            String customerName,
            Long fromRackId,
            String fromRackName,
            int fromStartUnit,
            Long toRackId,
            String toRackName,
            int toStartUnit,
            int sizeUnits,
            int powerWatts,
            long monthlyFee,
            long discountedFee
    ) {
    }
}
