package com.sds.colocation.relocation;

import java.time.LocalDate;

/** 이전 완료 후 고객에게 보내는 재부팅 안내 내용. */
public record RelocationCompletedEvent(
        Long customerId,
        String customerName,
        String contactEmail,
        Long allocationId,
        String fromRackName,
        int fromStartUnit,
        String toRackName,
        int toStartUnit,
        int discountPercent,
        LocalDate discountEndDate
) {

    public String title() {
        return "[재부팅 안내] 임대 공간 #" + allocationId + " 서버 이전 완료";
    }

    public String message() {
        return customerName + " 고객님, 서버가 " + fromRackName + " U" + fromStartUnit + "에서 "
                + toRackName + " U" + toStartUnit + "(으)로 이전되었습니다. 서버 재부팅이 필요합니다. "
                + "이전에 대한 보상으로 " + discountEndDate + " 전까지 임대료 " + discountPercent + "% 할인이 적용됩니다.";
    }
}
