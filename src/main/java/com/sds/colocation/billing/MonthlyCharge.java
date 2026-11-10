package com.sds.colocation.billing;

/**
 * 임대 공간 하나의 한 달 청구액.
 *
 * @param baseFee        한 달 전체를 사용했을 때의 정가
 * @param proratedFee    실제 사용 일수만큼 일할 계산한 금액 (할인 전)
 * @param discountAmount 이전 보상 할인으로 깎인 금액
 */
public record MonthlyCharge(long baseFee, long proratedFee, long discountAmount) {

    public static final MonthlyCharge NONE = new MonthlyCharge(0, 0, 0);

    public long netFee() {
        return proratedFee - discountAmount;
    }
}
