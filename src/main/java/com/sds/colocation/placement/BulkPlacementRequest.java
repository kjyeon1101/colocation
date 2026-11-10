package com.sds.colocation.placement;

import java.util.List;

import com.sds.colocation.allocation.ServerType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 한 고객의 서버 여러 대를 한 번에 입고한다. */
public record BulkPlacementRequest(
        @NotNull Long customerId,
        @NotEmpty @Size(max = 50) List<@Valid Item> servers
) {

    public record Item(
            @NotNull Long rackId,
            @Min(1) int startUnit,
            @NotBlank String hostname,
            @NotNull ServerType type,
            @Min(value = PlacementRequest.MIN_SERVER_UNITS, message = PlacementRequest.SERVER_UNITS_MESSAGE)
            @Max(value = PlacementRequest.MAX_SERVER_UNITS, message = PlacementRequest.SERVER_UNITS_MESSAGE) int sizeUnits,
            @Min(1) @Max(50000) int powerWatts
    ) {
    }
}
