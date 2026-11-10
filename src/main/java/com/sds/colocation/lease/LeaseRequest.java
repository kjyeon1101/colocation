package com.sds.colocation.lease;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 엔터프라이즈 고객의 하프랙·풀랙 임대 요청.
 *
 * @param leaseUnit "HALF" = 하프랙, "FULL" = 풀랙
 * @param position  하프랙일 때만 필요하다.
 */
public record LeaseRequest(
        @NotNull Long customerId,
        @NotNull Long rackId,
        @NotBlank String leaseUnit,
        HalfPosition position
) {
}
