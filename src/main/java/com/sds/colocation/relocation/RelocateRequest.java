package com.sds.colocation.relocation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RelocateRequest(
        @NotNull Long rackId,
        @Min(1) int startUnit,
        String reason
) {
}
