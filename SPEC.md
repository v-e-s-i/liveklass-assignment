# 크리에이터 정산 API — 상세 기획서

> 버전: 1.0 | 작성일: 2026-04-27

---

## 목차

1. [도메인 개요](#1-도메인-개요)
2. [용어 정의](#2-용어-정의)
3. [기술 스택](#3-기술-스택)
4. [데이터 모델](#4-데이터-모델)
5. [비즈니스 규칙](#5-비즈니스-규칙)
6. [API 명세](#6-api-명세)
7. [초기 데이터 적재](#7-초기-데이터-적재)
8. [에러 처리](#8-에러-처리)
9. [선택 구현 설계](#9-선택-구현-설계)
10. [검증 시나리오](#10-검증-시나리오)

---

## 1. 도메인 개요

```
크리에이터 → 강의 개설 → 수강생 결제
                              ↓
                         판매 내역 (SaleRecord)
                              ↓ 취소 발생 시
                         취소 내역 (CancelRecord)
                              ↓
                    월별 정산 금액 계산
                    (총 판매 - 환불 - 수수료 = 정산 예정액)
```

플랫폼은 수수료를 차감한 **정산 예정 금액**을 크리에이터에게 지급한다.  
정산 기준은 **결제/취소 완료 일시(KST)** 이며, 월 단위로 집계한다.

---

## 2. 용어 정의

| 용어 | 설명 |
|------|------|
| 크리에이터 (Creator) | 강의를 개설·판매하는 콘텐츠 제공자 |
| 강의 (Course) | 크리에이터가 등록한 판매 단위 콘텐츠 |
| 수강생 (Student) | 강의를 구매한 사용자 |
| 판매 내역 (SaleRecord) | 수강생의 강의 결제 한 건을 나타내는 레코드 |
| 취소 내역 (CancelRecord) | 판매 내역에 연결된 환불/취소 레코드 |
| 총 판매 금액 | 해당 월 판매 내역의 `amount` 합계 |
| 환불 금액 | 해당 월 취소 내역의 `refundAmount` 합계 |
| 순 판매 금액 | 총 판매 금액 − 환불 금액 |
| 플랫폼 수수료 | 순 판매 금액 × 수수료율 (기본 20%) |
| 정산 예정 금액 | 순 판매 금액 − 플랫폼 수수료 |
| 정산 (Settlement) | 특정 월의 크리에이터 정산 결과 레코드 (선택) |

---

## 3. 기술 스택

| 항목 | 선택 | 비고 |
|------|------|------|
| 언어 | Java 17 | |
| 프레임워크 | Spring Boot 3.x | |
| ORM | Spring Data JPA + Hibernate | |
| DB | H2 (개발/테스트), MySQL 8 (프로덕션 선택 가능) | |
| 빌드 | Gradle | |
| 테스트 | JUnit 5, MockMvc | |
| 문서화 | Springdoc OpenAPI (Swagger UI) | `/swagger-ui.html` |
| 인증 | 헤더 기반 단순 식별 (`X-Creator-Id`, `X-Role`) | 실제 인증 미구현 |

---

## 4. 데이터 모델

### 4-1. ERD

```
Creator 1──N Course 1──N SaleRecord 1──0..1 CancelRecord
                                            
Creator 1──N Settlement (선택)
FeePolicy (수수료 이력, 선택)
```

### 4-2. 테이블 상세

#### `creator` — 크리에이터

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | VARCHAR(50) | PK | 외부에서 지정하는 문자열 ID |
| name | VARCHAR(100) | NOT NULL | 크리에이터 이름 |
| created_at | DATETIME | NOT NULL | 등록 일시 |

#### `course` — 강의

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | VARCHAR(50) | PK | 외부에서 지정하는 문자열 ID |
| creator_id | VARCHAR(50) | FK(creator.id), NOT NULL | 강의 소유 크리에이터 |
| title | VARCHAR(200) | NOT NULL | 강의 제목 |
| created_at | DATETIME | NOT NULL | 등록 일시 |

#### `sale_record` — 판매 내역

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | VARCHAR(50) | PK | 외부에서 지정하는 문자열 ID |
| course_id | VARCHAR(50) | FK(course.id), NOT NULL | 판매된 강의 |
| student_id | VARCHAR(100) | NOT NULL | 수강생 식별자 |
| amount | BIGINT | NOT NULL, ≥ 0 | 결제 금액 (원 단위) |
| paid_at | DATETIME | NOT NULL | 결제 완료 일시 (UTC 저장) |
| created_at | DATETIME | NOT NULL | 레코드 생성 일시 |

- `amount`는 0 이상의 정수. 소수점 없음.
- `paid_at`은 **UTC로 저장**하되, 정산 집계 시 KST(UTC+9)로 변환하여 월 경계를 계산한다.
- 동일 `course_id` + `student_id` 조합이 복수 존재할 수 있다 (재구매 허용).

#### `cancel_record` — 취소/환불 내역

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 내부 생성 ID |
| sale_record_id | VARCHAR(50) | FK(sale_record.id), UNIQUE, NOT NULL | 연결된 판매 내역 (1:1) |
| refund_amount | BIGINT | NOT NULL, ≥ 0 | 실제 환불 금액 |
| canceled_at | DATETIME | NOT NULL | 취소 완료 일시 (UTC 저장) |
| created_at | DATETIME | NOT NULL | 레코드 생성 일시 |

- 판매 건당 최대 1개의 취소 내역만 허용 (`UNIQUE` 제약).
- `refund_amount ≤ sale_record.amount` 를 애플리케이션 레이어에서 검증.
- 부분 환불 허용 (`refund_amount < amount`).

#### `settlement` — 정산 레코드 (선택 구현)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| creator_id | VARCHAR(50) | FK(creator.id), NOT NULL | |
| year_month | VARCHAR(7) | NOT NULL | 정산 연월 (`2025-03` 형식) |
| total_sales | BIGINT | NOT NULL | 총 판매 금액 |
| total_refunds | BIGINT | NOT NULL | 환불 금액 |
| net_sales | BIGINT | NOT NULL | 순 판매 금액 |
| fee_rate | DECIMAL(5,4) | NOT NULL | 적용 수수료율 (예: 0.2000) |
| fee_amount | BIGINT | NOT NULL | 수수료 금액 |
| payout_amount | BIGINT | NOT NULL | 정산 예정 금액 |
| status | VARCHAR(20) | NOT NULL | `PENDING` / `CONFIRMED` / `PAID` |
| confirmed_at | DATETIME | NULL | CONFIRMED 전환 일시 |
| paid_at | DATETIME | NULL | PAID 전환 일시 |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |

- `(creator_id, year_month)` UNIQUE 인덱스 → 중복 정산 방지.

#### `fee_policy` — 수수료율 이력 (선택 구현)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| rate | DECIMAL(5,4) | NOT NULL | 수수료율 (예: 0.2000 = 20%) |
| effective_from | DATE | NOT NULL | 적용 시작일 |
| effective_to | DATE | NULL | 적용 종료일 (NULL = 현재 유효) |
| created_at | DATETIME | NOT NULL | |

- 특정 월의 수수료율은 해당 월 1일 기준 유효한 정책을 조회하여 적용.
- 기본값: `rate = 0.2000`, `effective_from = 2000-01-01`, `effective_to = NULL`.

---

## 5. 비즈니스 규칙

### 5-1. 정산 기간 기준

| 이벤트 | 기준 일시 | 월 귀속 기준 |
|--------|----------|-------------|
| 판매 | `sale_record.paid_at` | KST 월 |
| 취소 | `cancel_record.canceled_at` | KST 월 |

**KST 월 경계**: `YYYY-MM-01 00:00:00 KST` ~ `YYYY-MM-말일 23:59:59 KST`

> UTC 저장 예시: 2025-01-31T23:30:00+09:00 → UTC: 2025-01-31T14:30:00Z → KST 1월로 집계

### 5-2. 월별 정산 계산식

```
총 판매 금액  = SUM(sale_record.amount)          [해당 월 paid_at 기준]
환불 금액     = SUM(cancel_record.refund_amount) [해당 월 canceled_at 기준]
순 판매 금액  = 총 판매 금액 − 환불 금액
플랫폼 수수료 = FLOOR(순 판매 금액 × 수수료율)   [원 단위 절사]
정산 예정 금액 = 순 판매 금액 − 플랫폼 수수료
```

- 수수료는 **원 단위 이하 절사(FLOOR)** 처리.
- 판매와 취소는 **각자 독립된 월 기준**으로 귀속 (예: 1월 판매 → 2월 취소 시, 1월 정산에는 해당 판매가 포함되고 2월 정산에는 환불만 차감됨).
- 순 판매 금액이 음수가 될 수 있다 (예: 전월 판매분이 당월 취소되는 경우). 음수 정산 결과는 그대로 응답.

### 5-3. 수수료율

- 기본 수수료율: **20% (0.20)**
- 수수료율은 설정값으로 관리. `application.properties`에 `platform.fee-rate=0.20` 으로 정의하고 `@Value`로 주입.
- 선택 구현 시 `fee_policy` 테이블에서 해당 월 기준 유효한 정책을 조회.

### 5-4. 판매 내역 등록 유효성 검사

| 필드 | 규칙 |
|------|------|
| courseId | 존재하는 강의 ID여야 함 |
| studentId | 빈 문자열 불가 |
| amount | 1 이상의 정수 |
| paidAt | 현재 일시 이후 불가 (미래 결제 불가) |

### 5-5. 취소 내역 등록 유효성 검사

| 필드 | 규칙 |
|------|------|
| saleRecordId | 존재하는 판매 내역 ID여야 함 |
| saleRecordId | 이미 취소 내역이 존재하면 등록 불가 (중복 취소 방지) |
| refundAmount | 1 이상, `sale_record.amount` 이하 |
| canceledAt | `sale_record.paid_at` 이후여야 함 |

---

## 6. API 명세

### 공통 사항

- Base URL: `/api/v1`
- Content-Type: `application/json`
- 인증: 요청 헤더로 식별자 전달
  - `X-Creator-Id`: 크리에이터 API 호출 시
  - `X-Role: ADMIN`: 운영자 API 호출 시 (값이 `ADMIN`이 아니면 403 반환)
- 날짜/시간 형식: ISO 8601 (`2025-03-05T10:00:00+09:00`)
- 금액 단위: 원 (정수)

### 6-1. 크리에이터 관리

#### `POST /api/v1/creators`

크리에이터 등록 (샘플 데이터 적재 또는 테스트 편의용).

**Request Body**
```json
{
  "id": "creator-1",
  "name": "김강사"
}
```

**Response `201 Created`**
```json
{
  "id": "creator-1",
  "name": "김강사",
  "createdAt": "2026-04-27T10:00:00+09:00"
}
```

**Error Cases**
- `409 Conflict`: 이미 존재하는 ID

---

### 6-2. 강의 관리

#### `POST /api/v1/courses`

강의 등록.

**Request Body**
```json
{
  "id": "course-1",
  "creatorId": "creator-1",
  "title": "Spring Boot 입문"
}
```

**Response `201 Created`**
```json
{
  "id": "course-1",
  "creatorId": "creator-1",
  "title": "Spring Boot 입문",
  "createdAt": "2026-04-27T10:00:00+09:00"
}
```

**Error Cases**
- `404 Not Found`: `creatorId`에 해당하는 크리에이터 없음
- `409 Conflict`: 이미 존재하는 강의 ID

---

### 6-3. 판매 내역 관리

#### `POST /api/v1/sale-records`

판매 내역 등록.

**Request Body**
```json
{
  "id": "sale-1",
  "courseId": "course-1",
  "studentId": "student-1",
  "amount": 50000,
  "paidAt": "2025-03-05T10:00:00+09:00"
}
```

**Response `201 Created`**
```json
{
  "id": "sale-1",
  "courseId": "course-1",
  "creatorId": "creator-1",
  "studentId": "student-1",
  "amount": 50000,
  "paidAt": "2025-03-05T10:00:00+09:00",
  "canceled": false,
  "createdAt": "2026-04-27T10:00:00+09:00"
}
```

**Error Cases**
- `404 Not Found`: `courseId`에 해당하는 강의 없음
- `409 Conflict`: 이미 존재하는 판매 내역 ID

---

#### `POST /api/v1/sale-records/{saleRecordId}/cancel`

취소 내역 등록.

**Path Parameter**
- `saleRecordId`: 취소할 판매 내역 ID

**Request Body**
```json
{
  "refundAmount": 80000,
  "canceledAt": "2025-03-25T15:00:00+09:00"
}
```

**Response `201 Created`**
```json
{
  "saleRecordId": "sale-3",
  "refundAmount": 80000,
  "canceledAt": "2025-03-25T15:00:00+09:00",
  "createdAt": "2026-04-27T10:00:00+09:00"
}
```

**Error Cases**
- `404 Not Found`: `saleRecordId`에 해당하는 판매 내역 없음
- `409 Conflict`: 이미 취소 내역이 존재
- `422 Unprocessable Entity`: `refundAmount > amount` 또는 `canceledAt < paidAt`

---

#### `GET /api/v1/sale-records`

판매 내역 목록 조회.

**Query Parameters**

| 파라미터 | 필수 | 타입 | 설명 |
|----------|------|------|------|
| creatorId | 필수 | String | 크리에이터 ID |
| from | 선택 | String (date) | 조회 시작일 `YYYY-MM-DD` (paid_at 기준, KST) |
| to | 선택 | String (date) | 조회 종료일 `YYYY-MM-DD` (paid_at 기준, KST, 해당일 23:59:59 포함) |
| page | 선택 | int | 페이지 번호 (0-based, 기본 0) |
| size | 선택 | int | 페이지 크기 (기본 20, 최대 100) |

**Response `200 OK`**
```json
{
  "content": [
    {
      "id": "sale-1",
      "courseId": "course-1",
      "courseTitle": "Spring Boot 입문",
      "studentId": "student-1",
      "amount": 50000,
      "paidAt": "2025-03-05T10:00:00+09:00",
      "canceled": false,
      "cancelInfo": null
    },
    {
      "id": "sale-3",
      "courseId": "course-2",
      "courseTitle": "JPA 실전",
      "studentId": "student-3",
      "amount": 80000,
      "paidAt": "2025-03-20T09:00:00+09:00",
      "canceled": true,
      "cancelInfo": {
        "refundAmount": 80000,
        "canceledAt": "2025-03-25T15:00:00+09:00"
      }
    }
  ],
  "totalElements": 4,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

**Header 인증 (`X-Creator-Id`)**: 헤더 값이 존재하면 `creatorId` 파라미터와 일치 여부 확인. 불일치 시 `403 Forbidden`.

---

### 6-4. 정산 금액 계산 (크리에이터용)

#### `GET /api/v1/settlements/monthly`

크리에이터 월별 정산 조회.

**Query Parameters**

| 파라미터 | 필수 | 타입 | 설명 |
|----------|------|------|------|
| creatorId | 필수 | String | 크리에이터 ID |
| yearMonth | 필수 | String | 조회 연월 (`2025-03`) |

**Response `200 OK`**
```json
{
  "creatorId": "creator-1",
  "creatorName": "김강사",
  "yearMonth": "2025-03",
  "period": {
    "from": "2025-03-01T00:00:00+09:00",
    "to": "2025-03-31T23:59:59+09:00"
  },
  "totalSalesAmount": 260000,
  "totalRefundAmount": 120000,
  "netSalesAmount": 140000,
  "feeRate": 0.20,
  "feeAmount": 28000,
  "payoutAmount": 112000,
  "saleCount": 4,
  "cancelCount": 2
}
```

> `creator-1`의 2025-03 예시:
> - 판매: sale-1(50,000) + sale-2(50,000) + sale-3(80,000) + sale-4(80,000) = 260,000
> - 환불: sale-3 전액(80,000) + sale-4 부분(40,000) = 120,000
> - 순 판매: 140,000
> - 수수료: 28,000 (20%)
> - 정산 예정: 112,000

**조회 결과가 0건인 경우**: 모든 금액을 0으로 응답 (404 아님).

```json
{
  "creatorId": "creator-3",
  "creatorName": "박강사",
  "yearMonth": "2025-03",
  "period": {
    "from": "2025-03-01T00:00:00+09:00",
    "to": "2025-03-31T23:59:59+09:00"
  },
  "totalSalesAmount": 0,
  "totalRefundAmount": 0,
  "netSalesAmount": 0,
  "feeRate": 0.20,
  "feeAmount": 0,
  "payoutAmount": 0,
  "saleCount": 0,
  "cancelCount": 0
}
```

**Error Cases**
- `400 Bad Request`: `yearMonth` 형식 오류
- `404 Not Found`: `creatorId`에 해당하는 크리에이터 없음

---

### 6-5. 정산 집계 (운영자용)

#### `GET /api/v1/admin/settlements/summary`

기간 내 전체 크리에이터 정산 현황 집계.

**Headers**
- `X-Role: ADMIN` (필수, 없거나 ADMIN이 아니면 `403 Forbidden`)

**Query Parameters**

| 파라미터 | 필수 | 타입 | 설명 |
|----------|------|------|------|
| from | 필수 | String (date) | 집계 시작일 `YYYY-MM-DD` |
| to | 필수 | String (date) | 집계 종료일 `YYYY-MM-DD` |

- `from` ≤ `to` 검증 필요
- 날짜 기준: 판매는 `paid_at` KST, 취소는 `canceled_at` KST

**Response `200 OK`**
```json
{
  "period": {
    "from": "2025-03-01",
    "to": "2025-03-31"
  },
  "feeRate": 0.20,
  "items": [
    {
      "creatorId": "creator-1",
      "creatorName": "김강사",
      "totalSalesAmount": 260000,
      "totalRefundAmount": 120000,
      "netSalesAmount": 140000,
      "feeAmount": 28000,
      "payoutAmount": 112000,
      "saleCount": 4,
      "cancelCount": 2
    },
    {
      "creatorId": "creator-2",
      "creatorName": "이강사",
      "totalSalesAmount": 60000,
      "totalRefundAmount": 0,
      "netSalesAmount": 60000,
      "feeAmount": 12000,
      "payoutAmount": 48000,
      "saleCount": 1,
      "cancelCount": 0
    }
  ],
  "summary": {
    "totalSalesAmount": 320000,
    "totalRefundAmount": 120000,
    "netSalesAmount": 200000,
    "feeAmount": 40000,
    "payoutAmount": 160000,
    "saleCount": 5,
    "cancelCount": 2
  }
}
```

- `items`는 `payoutAmount` 내림차순 정렬.
- 해당 기간에 거래 내역이 없는 크리에이터는 응답에서 제외.

**Error Cases**
- `400 Bad Request`: `from > to` 또는 날짜 형식 오류
- `403 Forbidden`: `X-Role` 헤더 없음 또는 `ADMIN`이 아님

---

### 6-6. 정산 확정 (선택 구현)

#### `POST /api/v1/admin/settlements/confirm`

특정 월 특정 크리에이터의 정산을 확정한다. 현재 계산된 값으로 `settlement` 레코드를 생성(또는 갱신).

**Headers**: `X-Role: ADMIN`

**Request Body**
```json
{
  "creatorId": "creator-1",
  "yearMonth": "2025-03"
}
```

**Response `200 OK`**
```json
{
  "settlementId": 1,
  "creatorId": "creator-1",
  "yearMonth": "2025-03",
  "status": "CONFIRMED",
  "payoutAmount": 112000,
  "confirmedAt": "2026-04-27T10:00:00+09:00"
}
```

**Error Cases**
- `409 Conflict`: 이미 `CONFIRMED` 또는 `PAID` 상태

---

#### `POST /api/v1/admin/settlements/{settlementId}/pay`

정산 지급 처리 (`CONFIRMED → PAID`).

**Headers**: `X-Role: ADMIN`

**Response `200 OK`**
```json
{
  "settlementId": 1,
  "status": "PAID",
  "paidAt": "2026-04-27T11:00:00+09:00"
}
```

**Error Cases**
- `404 Not Found`: settlementId 없음
- `409 Conflict`: 현재 상태가 `CONFIRMED`가 아님

---

### 6-7. CSV 다운로드 (선택 구현)

#### `GET /api/v1/admin/settlements/export`

기간 내 정산 내역 CSV 다운로드.

**Headers**: `X-Role: ADMIN`

**Query Parameters**: `from`, `to` (날짜, 6-5와 동일)

**Response `200 OK`**
```
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="settlement_2025-03-01_2025-03-31.csv"
```

**CSV 컬럼**
```
크리에이터ID,크리에이터명,총판매금액,환불금액,순판매금액,수수료율,수수료금액,정산예정금액,판매건수,취소건수
```

---

## 7. 초기 데이터 적재

### 7-1. 방식

애플리케이션 시작 시 `DataInitializer` 빈이 실행되어 샘플 데이터를 삽입한다.  
이미 존재하는 ID는 삽입을 건너뛴다 (멱등성 보장).

```java
@Component
@Profile("!test") // 테스트 프로파일에서는 실행 안 함
public class DataInitializer implements CommandLineRunner { ... }
```

### 7-2. 적재 순서

1. Creator 3건
2. Course 4건
3. SaleRecord 7건
4. CancelRecord (케이스 3, 4, 5에 해당하는 취소 내역 — 아래 참조)

### 7-3. 취소 내역 초기 데이터

샘플 데이터에 취소 내역은 명시되어 있지 않으므로, 아래 데이터를 기획서 기준으로 초기 적재한다.

| saleRecordId | refundAmount | canceledAt (KST) | 케이스 설명 |
|---|---|---|---|
| sale-3 | 80,000 | 2025-03-25T15:00:00+09:00 | 전액 환불, 3월 취소 |
| sale-4 | 40,000 | 2025-03-28T10:00:00+09:00 | 부분 환불, 3월 취소 |
| sale-5 | 60,000 | 2025-02-03T09:00:00+09:00 | 1월 판매 → 2월 취소 |

---

## 8. 에러 처리

### 8-1. 공통 에러 응답 형식

```json
{
  "status": 400,
  "code": "INVALID_YEAR_MONTH",
  "message": "yearMonth 형식이 올바르지 않습니다. 예: 2025-03",
  "timestamp": "2026-04-27T10:00:00+09:00"
}
```

### 8-2. 에러 코드 목록

| HTTP 상태 | code | 발생 조건 |
|-----------|------|----------|
| 400 | INVALID_DATE_FORMAT | 날짜/시간 파싱 실패 |
| 400 | INVALID_REQUEST | 요청 파라미터 또는 요청 값이 올바르지 않음 |
| 400 | INVALID_YEAR_MONTH | yearMonth 형식 오류 |
| 400 | INVALID_DATE_RANGE | from > to |
| 400 | INVALID_AMOUNT | amount < 1 |
| 400 | FUTURE_PAID_AT | paidAt이 현재 시각 이후 |
| 400 | INVALID_REFUND_AMOUNT | refundAmount < 1 또는 > amount |
| 400 | INVALID_CANCEL_DATE | canceledAt < paidAt |
| 403 | FORBIDDEN | X-Role != ADMIN 또는 크리에이터 ID 불일치 |
| 404 | CREATOR_NOT_FOUND | 크리에이터 없음 |
| 404 | COURSE_NOT_FOUND | 강의 없음 |
| 404 | SALE_RECORD_NOT_FOUND | 판매 내역 없음 |
| 404 | SETTLEMENT_NOT_FOUND | 정산 레코드 없음 |
| 409 | DUPLICATE_CREATOR | 크리에이터 ID 중복 |
| 409 | DUPLICATE_COURSE | 강의 ID 중복 |
| 409 | DUPLICATE_SALE_RECORD | 판매 내역 ID 중복 |
| 409 | ALREADY_CANCELED | 이미 취소된 판매 내역 |
| 409 | SETTLEMENT_ALREADY_CONFIRMED | 이미 확정된 정산 |
| 422 | INVALID_SETTLEMENT_STATUS | 상태 전환 불가 |
| 500 | INTERNAL_SERVER_ERROR | 예상치 못한 서버 오류 |

### 8-3. 글로벌 예외 핸들러

`@RestControllerAdvice`로 구현. Bean Validation 실패(`MethodArgumentNotValidException`)도 동일 형식으로 변환.

---

## 9. 선택 구현 설계

### 9-1. 정산 확정 상태 관리

```
PENDING → CONFIRMED → PAID
```

- `PENDING`: 정산 확정 전 (미생성 또는 생성 직후)
- `CONFIRMED`: 운영자가 정산 금액을 확정한 상태. 이후 판매/취소 내역이 추가되어도 확정 금액 불변.
- `PAID`: 크리에이터에게 지급 완료.

전환 규칙:
- `CONFIRMED` → `PENDING` 역방향 불가.
- `PAID` → 어떤 상태로도 변경 불가.

### 9-2. 중복 정산 방지

`settlement` 테이블의 `(creator_id, year_month)` UNIQUE 제약으로 DB 레벨에서 보장.  
`confirm` API 호출 시 이미 `CONFIRMED` 또는 `PAID`이면 `409` 반환.

### 9-3. 수수료율 이력

`fee_policy` 테이블에서 해당 월 1일 기준 `effective_from ≤ targetDate AND (effective_to IS NULL OR effective_to ≥ targetDate)`인 정책을 조회.  
정산 확정 시 해당 수수료율을 `settlement.fee_rate`에 저장하여 이후 변경에 영향받지 않도록 한다.

---

## 10. 검증 시나리오

### 케이스 1, 2 — 정상 판매 (creator-1, 2025-03)

`GET /api/v1/settlements/monthly?creatorId=creator-1&yearMonth=2025-03`

| 항목 | 기대값 |
|------|--------|
| totalSalesAmount | 260,000 |
| totalRefundAmount | 120,000 |
| netSalesAmount | 140,000 |
| feeAmount | 28,000 |
| payoutAmount | 112,000 |
| saleCount | 4 |
| cancelCount | 2 |

### 케이스 5 — 월 경계 (1월 판매 → 2월 취소)

`GET /api/v1/settlements/monthly?creatorId=creator-2&yearMonth=2025-01`

| 항목 | 기대값 |
|------|--------|
| totalSalesAmount | 60,000 | (sale-5 포함) |
| totalRefundAmount | 0 | (취소는 2월에 발생) |
| netSalesAmount | 60,000 |
| feeAmount | 12,000 |
| payoutAmount | 48,000 |

`GET /api/v1/settlements/monthly?creatorId=creator-2&yearMonth=2025-02`

| 항목 | 기대값 |
|------|--------|
| totalSalesAmount | 0 | (2월 판매 없음) |
| totalRefundAmount | 60,000 | (sale-5 취소는 2월) |
| netSalesAmount | -60,000 |
| feeAmount | -12,000 |
| payoutAmount | -48,000 |

### 케이스 7 — 빈 월 조회 (creator-3, 2025-03)

`GET /api/v1/settlements/monthly?creatorId=creator-3&yearMonth=2025-03`

- sale-7은 2025-02 결제 → 3월 조회 결과 없음
- 모든 금액 0, saleCount 0 응답

### 운영자 집계 검증

`GET /api/v1/admin/settlements/summary?from=2025-03-01&to=2025-03-31`
- `X-Role: ADMIN` 헤더 필수
- items에 creator-1, creator-2 포함 (creator-3 제외 — 3월 거래 없음)

---

## 부록 A. 패키지 구조 (권장)

```
com.example.liveklass
├── creator/
│   ├── Creator.java
│   ├── CreatorRepository.java
│   ├── CreatorService.java
│   └── CreatorController.java
├── course/
│   ├── Course.java
│   ├── CourseRepository.java
│   ├── CourseService.java
│   └── CourseController.java
├── sale/
│   ├── SaleRecord.java
│   ├── CancelRecord.java
│   ├── SaleRecordRepository.java
│   ├── CancelRecordRepository.java
│   ├── SaleService.java
│   └── SaleController.java
├── settlement/
│   ├── SettlementService.java        # 정산 계산 핵심 로직
│   ├── SettlementController.java
│   ├── AdminSettlementController.java
│   ├── Settlement.java               # (선택) 정산 확정 엔티티
│   └── FeePolicy.java                # (선택) 수수료율 이력
├── common/
│   ├── exception/
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   └── init/
│       └── DataInitializer.java
└── LiveklassApplication.java
```

## 부록 B. 시간대 처리 가이드

```java
// KST ZoneId 상수 정의
public static final ZoneId KST = ZoneId.of("Asia/Seoul");

// paidAt (OffsetDateTime) → KST 월 경계로 변환
YearMonth yearMonth = YearMonth.of(2025, 3);
ZonedDateTime from = yearMonth.atDay(1).atStartOfDay(KST);       // 2025-03-01T00:00:00+09:00
ZonedDateTime to   = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(KST); // 2025-03-31T23:59:59+09:00

// JPQL 조건 (UTC로 저장된 경우)
// paid_at BETWEEN :fromUtc AND :toUtc
Instant fromUtc = from.toInstant();
Instant toUtc   = to.toInstant();
```

> DB에 UTC로 저장하고 조회 시 범위를 UTC Instant로 변환하여 쿼리하는 것을 권장.  
> 또는 DB 컬럼을 `TIMESTAMP WITH TIME ZONE`으로 선언하고 JPA에서 `OffsetDateTime`으로 매핑.
