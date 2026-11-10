package com.sds.colocation.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CustomerCreateRequest(
        @NotBlank String name,
        @NotNull CustomerTier tier,
        String contactName,
        @Email String contactEmail
) {
}
