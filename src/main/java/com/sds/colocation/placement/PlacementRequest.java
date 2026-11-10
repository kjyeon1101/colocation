package com.sds.colocation.placement;

import com.sds.colocation.allocation.ServerType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 서버 1대 입고 요청.
 *
 * @param requestKey 중복 요청 방지 키. 같은 키로 다시 요청하면 새로 입고하지 않고 처음 결과를 돌려준다.
 */
public record PlacementRequest(
        @NotNull Long customerId,
        @NotNull Long rackId,
        @Min(1) int startUnit,
        @NotBlank String hostname,
        @NotNull ServerType type,
        @Min(value = MIN_SERVER_UNITS, message = SERVER_UNITS_MESSAGE)
        @Max(value = MAX_SERVER_UNITS, message = SERVER_UNITS_MESSAGE) int sizeUnits,
        @Min(1) @Max(50000) int powerWatts,
        @Size(max = 64) String requestKey
) {

    /** 고객이 입고할 수 있는 서버 1대의 최소 크기(U). */
    public static final int MIN_SERVER_UNITS = 1;
    /** 고객이 입고할 수 있는 서버 1대의 최대 크기(U). */
    public static final int MAX_SERVER_UNITS = 8;
    static final String SERVER_UNITS_MESSAGE = "서버 크기는 " + MIN_SERVER_UNITS + "U ~ " + MAX_SERVER_UNITS + "U 사이여야 합니다.";
}
