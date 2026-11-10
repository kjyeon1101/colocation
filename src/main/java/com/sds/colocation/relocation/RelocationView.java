package com.sds.colocation.relocation;

import java.time.LocalDate;

public record RelocationView(
        Long id,
        Long allocationId,
        String customerName,
        String fromRackName,
        int fromStartUnit,
        String toRackName,
        int toStartUnit,
        int sizeUnits,
        LocalDate relocatedDate,
        int discountPercent,
        LocalDate discountEndDate,
        String reason
) {

    public static RelocationView from(Relocation relocation) {
        return new RelocationView(relocation.getId(), relocation.getAllocation().getId(),
                relocation.getCustomerName(), relocation.getFromRackName(), relocation.getFromStartUnit(),
                relocation.getToRackName(), relocation.getToStartUnit(), relocation.getSizeUnits(),
                relocation.getRelocatedDate(), relocation.getDiscountPercent(), relocation.getDiscountEndDate(),
                relocation.getReason());
    }
}
