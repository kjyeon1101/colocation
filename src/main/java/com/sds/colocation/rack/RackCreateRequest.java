package com.sds.colocation.rack;

import jakarta.validation.constraints.NotBlank;

/**
 * 랙 추가 요청. 이름만 받는다. 크기(42U)와 최대 전력(10kW)은 운영 기준값으로 정해지고,
 * 설치비가 설치한 달의 순수익에서 차감된다. 전력이 더 필요하면 전력 증설을 한다.
 */
public record RackCreateRequest(
        @NotBlank String name
) {
}
