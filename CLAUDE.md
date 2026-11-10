# COLO·OPS Backend

> 데이터센터 **코로케이션(랙 공간·전력) 관리 서비스**의 백엔드.
> 고객 서버를 랙에 입고하고, 엔터프라이즈 고객의 하프랙/풀랙 임대를 위해 기존 고객을 **이전**시키며, 전력 증설·랙 설치비까지 반영한 **월 수익**을 계산한다.

📎 상위 폴더 `colocation_r5`의 `SPEC.md`(서비스 명세) · `README.md`(과제 소개) · `frontend/`(React, 수정 대상 아님)는 이 git 저장소에 **포함되지 않는다**. 도메인 규칙이 헷갈리면 `../SPEC.md`를 먼저 확인한다.

---

## 1. 빠른 시작

| 항목 | 내용 |
|---|---|
| 실행 | `./gradlew bootRun` (Windows: `gradlew.bat bootRun`) |
| 테스트 | `./gradlew test` |
| 빌드 | `./gradlew build` |
| 앱 화면 | http://localhost:8080 |
| H2 콘솔 | http://localhost:8080/h2-console — JDBC URL `jdbc:h2:mem:colocation`, 사용자 `sa`, 비밀번호 없음 |

- DB는 **메모리 H2**(`ddl-auto=create-drop`). 재시작하면 데이터가 초기화된다.
- 시작할 때 `seed.DemoDataInitializer`가 시연 데이터(랙 5개, 고객 8곳, 이전·증설 이력)를 **실행일 기준 날짜**로 다시 만든다. (`colocation.seed.enabled`)
- 화면(React) 빌드 결과가 `src/main/resources/static`에 들어 있어 **Node 없이** 실행된다.
- `src/test/java`에는 **테스트가 아직 없다.** 직접 작성한다.

## 2. 기술 스택

| 영역 | 내용 |
|---|---|
| 프레임워크 | Spring Boot 4.1.1 (Gradle) |
| 언어 | Java 17 (toolchain) |
| 영속성 | Spring Data JPA (Hibernate 7) + H2 (메모리) |
| 검증 | Bean Validation |
| 테스트 | JUnit (`useJUnitPlatform`) |

---

## 3. 패키지 구조

`com.sds.colocation` 아래에 **기능(도메인)별 패키지**로 나뉘며, 각 패키지에 Entity · Repository · Service · Controller · DTO(`*Request` / `*View`)가 함께 있다.

| 패키지 | 역할 | 핵심 클래스 |
|---|---|---|
| `common` | 공통 설정·예외 | `ColocationProperties`, `ClockConfig`, `BusinessException`, `GlobalExceptionHandler`, `ErrorResponse` |
| `rack` | 랙·입고·이전·증설·정산 총괄 | `Rack`, `RackSpace`, `RackInstallation`, **`RackService`** |
| `customer` | 고객 | `Customer`, `CustomerTier` |
| `allocation` | 임대 공간·서버 | `Allocation`, `Server`, `DiscountPeriod`, `AllocationViewMapper` |
| `placement` | 서버 입고 API | `PlacementController` (로직은 `RackService`) |
| `lease` | 하프랙/풀랙 임대 | `LeaseService`, `SpacePlanner`, `LeasePlan` |
| `relocation` | 이전 이력·안내 | `Relocation`, `RelocationCompletedEvent` |
| `power` | 전력 증설 이력 | `PowerUpgrade` |
| `billing` | 요금·수익 계산 | **`FeeCalculator`**, `MonthlyCharge`, `RevenueController` |
| `notification` | 메일 발송 이력 | `Notification`, `NotificationService` |
| `dashboard` | 대시보드·설정 조회 | `DashboardService` |
| `seed` | 시연 데이터 | `DemoDataInitializer` |

### API 목록 (`/api`)

| 영역 | 엔드포인트 |
|---|---|
| 랙 | `GET /racks` · `GET /racks/{id}` · `POST /racks` · `GET /rack-installations` |
| 고객 | `GET /customers` · `POST /customers` |
| 임대 공간 | `GET /allocations` · `GET /allocations/{id}` · `PUT /allocations/{id}` · `POST /allocations/{id}/terminate` |
| 서버 | `POST /servers/{id}/reboot-complete` |
| 입고 | `POST /placements` · `POST /placements/bulk` |
| 랙 단위 임대 | `POST /leases/plan` (계획만) · `POST /leases` (실행) |
| 이전 | `POST /allocations/{allocationId}/relocate` · `GET /relocations` |
| 전력 증설 | `POST /racks/{rackId}/power-upgrades` · `GET /power-upgrades` |
| 수익 | `GET /revenue?month=yyyy-MM` · `GET /revenue/trend?months=6` |
| 기타 | `GET /notifications` · `GET /dashboard` · `GET /settings` |

---

## 4. 도메인 모델

### 4.1 Allocation (임대 공간)

랙의 **연속된 U 구간**. `leaseUnit`은 **문자열**이다.

| `leaseUnit` | 의미 | 크기 | 안에 들어가는 서버 |
|---|---|---|---|
| `"1U"` | U 단위 임대 | 서버 1대와 동일 | 서버 1대 |
| `"HALF"` | 하프랙 | 21U (LOWER: U1~21 / UPPER: U22~42) | 같은 고객 서버 여러 대 |
| `"FULL"` | 풀랙 | 42U (U1~42) | 같은 고객 서버 여러 대 |

### 4.2 주요 규칙

| 항목 | 규칙 |
|---|---|
| 랙 크기 | 기본 42U (`rack.default-units`) |
| 전력 | 랙 단위 한도 `maxPowerWatts` 대비 사용량 `usedPowerWatts`. 입고·이전 시 검사 |
| 상태 | `AllocationStatus` ACTIVE / TERMINATED (해지 시 `endDate` 설정, 해지일부터 과금 안 함) |
| 재부팅 | 이전 후 서버에 재부팅 필요 표시 (`ServerStatus`) |
| 이력 테이블 | `Relocation` · `PowerUpgrade` · `RackInstallation` — 화면 표시용 이름은 **그 시점 값을 복사**해 저장 |
| 할인 | `Allocation.discounts`에 **이전할 때마다 1건씩 누적, 삭제 금지** (지난 달 수익 재계산에 사용) |

---

## 5. 업무 흐름

| 흐름 | 진입점 | 처리 순서 |
|---|---|---|
| **서버 입고** | `RackService.place` / `placeBulk` | 범위 검사 → 전력 검사 → (하프/풀랙 안이면) 소유 고객·자리 확인 후 서버만 추가 / (아니면) 빈 자리 확인 후 `1U` Allocation 생성 |
| **수동 이전** | `RackService.relocate` | `1U` 공간만 가능 (하프/풀랙은 `RACK_LEASE_NOT_MOVABLE`) → 자리·전력 검사 → `move` |
| **랙 단위 임대** | `LeaseService.plan` / `execute` | ENTERPRISE만 가능 → 겹치는 `1U` 공간을 `SpacePlanner`로 다른 빈자리에 가상 배치 → 불가 시 `LEASE_NOT_FEASIBLE` → 실행 시 각 공간 `move` 후 임대 Allocation 생성 |
| **이전 공통 처리** | `RackService.move` | 위치 이동(서버 포함) → 보상 할인 추가 → 재부팅 표시 → `Relocation` 저장 → 안내 메일 발송 흉내 + `Notification` 저장 |
| **전력 증설 / 랙 추가** | `RackService` | 증설비·설치비를 해당 월 순수익에서 **한 번에** 차감 |

**참고**

- 입고(단건)는 `requestKey`로 **멱등** 처리한다.
- 일괄 입고는 건별 트랜잭션(`TransactionTemplate`)이며, 실패 건은 **건너뛰고 로그만** 남긴다.
- `SpacePlanner`는 원래 랙을 우선하고, 아래쪽(U1)부터 빈자리를 찾는다.
- `move`의 자리·전력 검사는 **호출하는 쪽의 책임**이다.

---

## 6. 요금 계산 (`billing.FeeCalculator`)

### 월 정가

```
월 정가 = 임대료(하프/풀랙만) + Σ 서버 요금
```

| 구분 | 요금 |
|---|---|
| 하프랙 임대료 | 2,800,000원 |
| 풀랙 임대료 | 5,000,000원 |
| GPU 서버 | 전력(W) × 400원 — 임대 방식과 무관하게 별도 과금 |
| CPU 서버 (`1U` 임대) | 크기(U) × 150,000원 |
| CPU 서버 (하프/풀랙 안) | 0원 (공간 요금이 임대료에 포함) |

### 할인·일할 계산

| 항목 | 규칙 |
|---|---|
| 이전 보상 할인 | 이전일부터 3개월 후 같은 날 **전까지** 50% (종료일 미포함) |
| 할인 중복 | 이전을 또 하면 기간이 추가되며, 겹치는 날은 규칙(`SPEC.md` 5.2)에 따라 적용 |
| 오늘 청구액 `currentFee` | 월 정가 × (1 − 오늘 할인율) |
| 사용 일수 | 그 달 안에서 `startDate` ~ `endDate` 전날 |
| 일할 금액 | 월 정가 × 사용 일수 / 그 달 일수 |
| 할인 금액 | 월 정가 × Σ(사용한 날의 할인율) / (100 × 그 달 일수) |

### 월 순수익

```
순수익 = Σ 일할 금액 − Σ 할인 금액 − 그 달 전력 증설비 − 그 달 랙 설치비
```

- 해지된 임대 공간도 **사용한 날만큼** 수익에 포함된다.
- 증설비·설치비는 **발생한 달에 한 번에** 차감되므로 그 달 순수익이 크게 마이너스일 수 있다.

---

## 7. 설정값 (`application.properties`)

모든 운영 기준값은 `colocation.*` 프리픽스이며 `ColocationProperties`(record)로 바인딩된다. **코드에 값을 하드코딩하지 말고** 여기서 읽는다.

| 키 | 기본값 | 설명 |
|---|---|---|
| `rack.default-units` | 42 | 랙 기본 U |
| `rack.half-units` | 21 | 하프랙 U |
| `rack.default-power-watts` | 10000 | 새 랙 기본 최대 전력 |
| `rack.install-cost` | 300,000,000 | 랙 1개 설치비 |
| `pricing.cpu-price-per-unit` | 150,000 | CPU U당 단가 |
| `pricing.gpu-price-per-watt` | 400 | GPU W당 단가 |
| `pricing.half-rack-fee` / `full-rack-fee` | 2,800,000 / 5,000,000 | 하프/풀랙 임대료 |
| `relocation.discount-percent` / `discount-months` | 50 / 3 | 이전 보상 할인 |
| `power.upgrade-step-watts` / `upgrade-step-cost` | 5000 / 100,000,000 | 전력 증설 1단계 |
| `notification.simulated-latency-ms` | 300 | 메일 발송 지연 흉내 |
| `seed.enabled` | true | 시연 데이터 생성 |
| `zone` | Asia/Seoul | 시간대 |

> 현재 날짜는 `LocalDate.now()`가 아니라 주입된 **`Clock`**(`ClockConfig`)을 사용한다. 테스트에서 시간을 고정할 수 있도록 이 관례를 지킨다.

---

## 8. 에러 응답

모든 에러는 `{"code": "...", "message": "..."}` 형태이며, `BusinessException`의 팩토리(`notFound` / `badRequest` / `conflict`)로 던진다.

| 코드 | HTTP | 언제 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 입력값 검증 실패 |
| `OUT_OF_RANGE` | 400 | U 위치가 랙 범위 밖 |
| `ID_MISMATCH` | 400 | URL id ≠ 본문 id |
| `SAME_POSITION` | 400 | 지금 자리로 이전 |
| `NOT_ENTERPRISE` / `INVALID_LEASE_UNIT` / `POSITION_REQUIRED` | 400 | 랙 단위 임대 조건 위반 |
| `NOT_FOUND` | 404 | 대상 없음 |
| `POWER_EXCEEDED` | 409 | 랙 전력 한도 초과 |
| `SLOT_OCCUPIED` | 409 | 자리가 이미 사용 중 |
| `OCCUPIED_BY_OTHER` | 409 | 다른 고객의 임대 공간 |
| `LEASE_NOT_FEASIBLE` | 409 | 이전할 빈자리 없음 / 다른 랙 단위 임대와 겹침 |
| `RACK_LEASE_NOT_MOVABLE` / `NOT_ACTIVE` / `ALREADY_TERMINATED` | 409 | 이전·해지할 수 없는 임대 공간 |

---

## 9. 코딩 관례

| 주제 | 관례 |
|---|---|
| 언어 | 주석·Javadoc·사용자 메시지는 **한국어** |
| Entity | JPA용 `protected` 기본 생성자 + 명시적 getter (Lombok 없음). 상태 변경은 Entity 메서드(`moveTo`, `terminate`, `applyDiscount` 등)로 |
| Service | 생성자 주입. 조회 `@Transactional(readOnly = true)`, 변경 `@Transactional` |
| 지연 로딩 | `open-in-view=false` → 트랜잭션 밖 지연 로딩 주의. fetch join 쿼리(`findAllActiveWithDetails`, `findWithDetailsById`) 사용 |
| 응답 | Entity 직접 노출 금지, `*View` record로 변환 |
| 예외 | 비즈니스 오류 → `BusinessException`, 입력 오류 → Bean Validation / `IllegalArgumentException` (`GlobalExceptionHandler`가 처리) |

---

## 10. ⚠️ 알려진 주의점

이 코드베이스는 유지보수 과제용이라 **명세와 다르거나 비효율적인 코드가 섞여 있을 수 있다.** 동작의 기준은 항상 `../SPEC.md`다.

| 위치 | 의심 포인트 |
|---|---|
| `RackService.move` | 서비스(트랜잭션) 안에서 `Thread.sleep`으로 메일 발송 흉내 |
| `RackService.placeServer` | `allocationRepository.findAll()` 전체 조회 후 메모리 계산 |
| `Allocation` / `FeeCalculator` / `LeaseService` | `leaseUnit`을 문자열(`"1U"`, `"HALF"`, `"FULL"`)로 비교 |
| `Allocation.isOwnedBy` | `customer.getId() == customerId` — `Long` 객체를 `==`로 비교 |

- DB가 메모리라서 스키마 마이그레이션 도구는 없다.
