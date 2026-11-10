package com.sds.colocation.allocation;

import jakarta.validation.constraints.Size;

/** 임대 공간 정보 수정 요청. 본문의 id는 선택값이며, 보낸 경우 URL의 id와 같아야 한다. */
public record AllocationUpdateRequest(
        Long id,
        @Size(max = 500) String memo
) {
}
