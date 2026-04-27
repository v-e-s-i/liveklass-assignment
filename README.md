# Liveklass 크리에이터 정산 시스템

## 프로젝트 개요

Liveklass의 온라인 클래스 판매 데이터를 기반으로 크리에이터별 정산 금액을 계산하는 백엔드 API 프로젝트입니다.

이 시스템은 강의 판매 내역(`SaleRecord`)과 취소/환불 내역(`CancelRecord`)을 관리하고, 월 단위로 총 판매금액, 환불금액, 수수료, 정산 예정금액을 집계합니다. 함께 포함된 React 프론트엔드는 백엔드 API를 직접 호출해 주요 흐름을 확인하기 위한 최소 데모 UI입니다.

## 기술 스택

### Backend

- Java 17
- Spring Boot 3.5.6
- Spring Web
- Spring Data JPA
- Spring Validation
- Gradle
- H2 Database
- JUnit 5, MockMvc
- Springdoc OpenAPI

### Frontend

- React 19
- Vite
- JavaScript
- axios
- CSS

### 기타

- 시간대 기준: KST(`Asia/Seoul`)
- 금액 단위: 원, 정수
- 인증/권한: 실제 로그인 대신 요청 헤더 기반 검증
  - 크리에이터 API: `X-Creator-Id`
  - 관리자 API: `X-Role: ADMIN`

## 실행 방법

### Backend 실행

```bash
gradle bootRun
```

기본 API 주소:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

### Frontend 실행

```bash
npm install
npm run dev
```

기본 접속 URL:

```text
http://localhost:5173
```

프론트엔드는 기본적으로 같은 호스트의 백엔드 API 경로(`/api/v1/...`)를 호출하도록 구성되어 있습니다.

## 요구사항 해석 및 가정

- 정산 대상은 크리에이터가 판매한 강의의 판매/취소 데이터입니다.
- 판매 금액은 `paidAt`이 속한 KST 월에 귀속됩니다.
- 환불 금액은 원 판매월이 아니라 `canceledAt`이 속한 KST 월에 귀속됩니다.
- 월별 정산은 `총 판매금액 - 총 환불금액 = 순매출`을 먼저 계산하고, 순매출의 20%를 플랫폼 수수료로 차감합니다.
- 전월 판매분이 당월에 환불될 수 있으므로 순매출과 정산 예정금액은 음수가 될 수 있습니다.
- 부분 환불은 허용하지만 판매 건당 취소 레코드는 1개만 허용했습니다.
- 실제 결제 시스템, 사용자 로그인, 관리자 인증 시스템은 범위 밖으로 보고 헤더 기반 식별로 대체했습니다.
- KST 월 경계는 구현 안정성을 위해 `시작 시각 이상, 다음 경계 미만`의 half-open range로 처리했습니다.

## 설계 결정과 이유

### 정산 계산 로직

정산 계산은 `SettlementService`에 두고 컨트롤러는 요청 파라미터와 헤더를 전달하는 역할만 수행합니다. 판매 합계와 환불 합계는 각각 독립적으로 조회한 뒤 서비스 레이어에서 다음 값을 계산합니다.

```text
netSalesAmount = totalSalesAmount - totalRefundAmount
feeAmount = floor(netSalesAmount * 0.20)
payoutAmount = netSalesAmount - feeAmount
```

수수료율은 `application.properties`의 `platform.fee-rate=0.20`으로 관리합니다.

### canceledAt 기준 환불 처리

환불은 판매 발생월이 아니라 취소 완료 시각(`canceledAt`)이 속한 월에 반영했습니다. 예를 들어 1월 판매가 2월에 취소되면 1월에는 판매만, 2월에는 환불만 집계됩니다. 이는 실제 정산에서 취소 이벤트가 발생한 기간의 정산에 영향을 주는 방식이 더 자연스럽다고 판단했기 때문입니다.

### half-open range 사용

월/일 경계는 다음과 같이 처리합니다.

```text
from <= timestamp < toExclusive
```

이 방식은 `23:59:59.999999`처럼 소수 초가 있는 데이터가 누락되는 문제를 피할 수 있고, 인접 기간 사이의 중복 집계도 방지합니다.

### 에러 처리 방식

공통 에러 응답은 다음 JSON 형태로 통일했습니다.

```json
{
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "요청 값이 올바르지 않습니다.",
  "timestamp": "2026-04-27T10:00:00+09:00"
}
```

비즈니스 예외는 `BusinessException`과 `ErrorCode`로 관리하고, 유효성 검증 오류와 요청 파싱 오류는 `GlobalExceptionHandler`에서 API 계약에 맞는 에러 코드로 변환합니다.

### 패키지 구조

도메인 단위로 패키지를 분리했습니다.

```text
com.example.liveklass
├── common
├── creator
├── course
├── sale
└── settlement
```

각 도메인은 Controller, Service, Repository, Entity를 가까운 위치에 배치해 작은 과제 범위에서 구조를 빠르게 파악할 수 있도록 했습니다.

## 미구현 / 제약사항

- 실제 인증/인가 시스템은 구현하지 않았습니다. 과제 범위에서는 헤더 기반 검증만 사용합니다.
- 정산 상태(`PENDING`, `CONFIRMED`, `PAID`)는 구현하지 않았습니다.
- 정산 결과 저장 및 중복 정산 방지 테이블은 구현하지 않았습니다.
- CSV/Excel export는 구현하지 않았습니다.
- 수수료율 이력 관리는 구현하지 않았고, 고정 수수료율 20%만 사용합니다.
- 에러 코드는 API 계약 내에서 의미를 맞추되, 일부 요청 검증 오류는 범용 `INVALID_REQUEST`로 처리합니다.
- 데이터베이스는 H2 인메모리 DB를 사용하므로 애플리케이션 재시작 시 데이터가 초기화됩니다.

## AI 활용 범위

- Codex를 사용해 백엔드 API 구현, 테스트 케이스 보강, 프론트엔드 데모 UI 구현을 진행했습니다.
- API 계약, 정산 경계 조건, 에러 응답 일관성, 테스트 누락 시나리오를 QA 관점으로 점검했습니다.
- 최종 판단과 설계 선택은 과제 요구사항을 기준으로 검토해 반영했습니다.

## API 목록 및 예시

### 1. 판매 등록

`POST /api/v1/sale-records`

Request:

```json
{
  "id": "sale-100",
  "courseId": "course-1",
  "studentId": "student-100",
  "amount": 50000,
  "paidAt": "2025-03-05T10:00:00+09:00"
}
```

Response `201 Created`:

```json
{
  "id": "sale-100",
  "courseId": "course-1",
  "creatorId": "creator-1",
  "studentId": "student-100",
  "amount": 50000,
  "paidAt": "2025-03-05T10:00:00+09:00",
  "canceled": false,
  "createdAt": "2026-04-27T10:00:00+09:00"
}
```

주요 검증:

- `amount >= 1`
- `paidAt`은 미래일 수 없음
- 중복 `id` 불가
- 존재하는 `courseId` 필요

### 2. 취소 등록

`POST /api/v1/sale-records/{saleRecordId}/cancel`

Request:

```json
{
  "refundAmount": 20000,
  "canceledAt": "2025-03-25T15:00:00+09:00"
}
```

Response `201 Created`:

```json
{
  "saleRecordId": "sale-100",
  "refundAmount": 20000,
  "canceledAt": "2025-03-25T15:00:00+09:00",
  "createdAt": "2026-04-27T10:00:00+09:00"
}
```

주요 검증:

- 판매 건당 취소는 1회만 가능
- `refundAmount`는 1 이상, 판매 금액 이하
- `canceledAt`은 `paidAt` 이후여야 함

### 3. 판매 조회

`GET /api/v1/sale-records?creatorId=creator-1&from=2025-03-01&to=2025-03-31&page=0&size=20`

Header:

```text
X-Creator-Id: creator-1
```

Response `200 OK`:

```json
{
  "content": [
    {
      "id": "sale-100",
      "courseId": "course-1",
      "courseTitle": "Spring Boot 입문",
      "studentId": "student-100",
      "amount": 50000,
      "paidAt": "2025-03-05T10:00:00+09:00",
      "canceled": true,
      "cancelInfo": {
        "refundAmount": 20000,
        "canceledAt": "2025-03-25T15:00:00+09:00"
      }
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

### 4. 월별 정산 조회

`GET /api/v1/settlements/monthly?creatorId=creator-1&yearMonth=2025-03`

Header:

```text
X-Creator-Id: creator-1
```

Response `200 OK`:

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

보안 검증:

- `creatorId`와 `X-Creator-Id`가 다르면 `403 FORBIDDEN`
- `X-Creator-Id`가 없으면 `403 FORBIDDEN`

### 5. 관리자 정산 요약

`GET /api/v1/admin/settlements/summary?from=2025-03-01&to=2025-03-31`

Header:

```text
X-Role: ADMIN
```

Response `200 OK`:

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
    }
  ],
  "summary": {
    "creatorId": null,
    "creatorName": null,
    "totalSalesAmount": 260000,
    "totalRefundAmount": 120000,
    "netSalesAmount": 140000,
    "feeAmount": 28000,
    "payoutAmount": 112000,
    "saleCount": 4,
    "cancelCount": 2
  }
}
```

## 데이터 모델 설명

### SaleRecord

판매 1건을 나타냅니다.

주요 필드:

- `id`: 외부에서 지정하는 판매 ID
- `course`: 판매된 강의
- `studentId`: 구매한 수강생 ID
- `amount`: 판매 금액
- `paidAt`: 결제 완료 시각
- `createdAt`: 레코드 생성 시각

### CancelRecord

판매에 연결된 취소/환불 1건을 나타냅니다.

주요 필드:

- `saleRecord`: 원 판매 내역
- `refundAmount`: 환불 금액
- `canceledAt`: 취소 완료 시각
- `createdAt`: 레코드 생성 시각

판매 1건에는 최대 1개의 취소 내역만 연결됩니다.

### Settlement 관련 개념

정산 결과는 별도 테이블에 저장하지 않고 조회 시점에 계산합니다.

주요 집계 값:

- `totalSalesAmount`: 기간 내 판매 금액 합계
- `totalRefundAmount`: 기간 내 환불 금액 합계
- `netSalesAmount`: 판매 금액 - 환불 금액
- `feeAmount`: 순매출의 20%
- `payoutAmount`: 순매출 - 수수료
- `saleCount`: 판매 건수
- `cancelCount`: 취소 건수

## 테스트 실행 방법

전체 백엔드 테스트:

```bash
gradle test
```

주요 정산/판매 API 테스트만 실행:

```bash
gradle test --tests com.example.liveklass.settlement.SettlementControllerTest
```

현재 테스트는 MockMvc 기반 통합 테스트로 작성되어 있으며, H2 인메모리 DB를 사용합니다.

테스트 포함 범위:

- 판매 등록 성공
- 취소 등록 성공
- 판매 목록 조회
- 월별 정산 계산
- 관리자 정산 요약
- 크리에이터 헤더 권한 검증
- 관리자 Role 검증
- 중복 판매 ID
- 중복 취소
- 잘못된 금액/날짜/요청 파라미터
- KST 월 경계 처리
- 공통 에러 응답 JSON 형태

## 추가 데이터 가이드 반영

과제의 추가 데이터 가이드를 반영해 기본 성공 케이스 외에 다음 시나리오를 테스트 데이터와 함께 추가했습니다.

### 동일 월 다수 취소

- 2025년 3월에 동일 크리에이터의 여러 판매 건을 취소하는 데이터를 구성했습니다.
- 이유: 환불 합계와 취소 건수가 단일 취소에만 맞춰져 있으면 실제 정산 집계 오류를 놓칠 수 있기 때문입니다.

### 다른 월 환불 처리

- 1월 판매 건을 2월에 취소하는 데이터를 구성했습니다.
- 이유: 환불이 원 판매월이 아니라 `canceledAt` 기준 월에 반영되는지 검증하기 위해서입니다.

### 미래 날짜 입력

- 미래 `paidAt`으로 판매 등록을 시도하는 테스트를 추가했습니다.
- 이유: 실제 결제가 완료되지 않은 미래 결제 데이터가 정산 대상에 들어가는 것을 막아야 하기 때문입니다.

### 잘못된 yearMonth

- `2025-13`처럼 올바르지 않은 `yearMonth` 요청을 검증했습니다.
- 이유: 월별 정산 API는 월 파라미터가 핵심 입력이므로 잘못된 형식이 명확한 400 응답으로 처리되어야 하기 때문입니다.

### KST 월 경계

- `2025-03-01T00:00:00+09:00`에 발생한 판매/취소가 3월 정산에 포함되는지 검증했습니다.
- `2025-03-31T23:59:59.999999+09:00`처럼 월 말 소수 초 데이터가 누락되지 않는지도 검증했습니다.
- 이유: 정산 시스템에서 월 경계 누락은 금액 오류로 직결되므로, half-open range 구현이 실제로 안전한지 확인하기 위해서입니다.

