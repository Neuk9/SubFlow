# CSV 내역 가져오기 (자동 인식) 설계

- 작성일: 2026-11-24
- 상태: 리뷰 대기

## 1. 목표

카드/은행에서 내려받은 CSV 파일을 업로드하면,

1. 이미 등록된 구독과 결제 내역을 자동으로 매칭해 `BillingRecord`로 기록하고,
2. 등록되지 않은 반복 결제를 **신규 구독 후보**로 제안한다.

### 비목표 (이번 범위 제외)

- 실제 계좌/카드 API 연동 (오픈뱅킹·마이데이터는 사업자 등록 필요 → 불가)
- xlsx, PDF, 이메일 영수증 파싱
- 실시간 동기화, 다중 통화, 가져오기 이력 화면
- 5,000행 초과 파일

## 2. 배경

국내에서 계좌·카드 거래 내역을 조회하는 API는 오픈뱅킹 이용기관 등록 또는 마이데이터 허가가 필요하다. 대학 팀 프로젝트에서는 제도적으로 불가능하므로, **사용자가 직접 내려받은 CSV를 수입(import) 경로**로 사용한다.

## 3. 사용자 시나리오

1. 사용자가 카드사 앱에서 3개월 이용 내역을 CSV로 내려받는다.
2. `내 구독 서비스` 패널의 **내역 가져오기** 버튼을 누르고 파일을 올린다.
3. 앱이 컬럼을 자동 인식하고, 행별 매칭 결과와 신규 후보를 보여준다.
4. 사용자가 적용할 항목을 체크하고 **적용**을 누른다.
5. 결제 기록이 쌓이고, 선택한 후보가 구독으로 등록된다.
6. 같은 파일을 다시 올려도 중복 기록이 생기지 않는다.

## 4. 아키텍처

기존 레이어드 구조(Controller → Service → Repository/Domain)를 따른다.

```text
com.subflow.subscription.importer
├── controller/CsvImportController
├── service/CsvImportService
├── parser/CsvParser
├── parser/CsvEncodingDetector
├── parser/ColumnDetector
├── matcher/MerchantNormalizer
├── matcher/SubscriptionMatcher
├── matcher/CandidateFinder
├── matcher/CategoryGuesser
└── dto/ (PreviewResponse, PreviewRow, ImportCandidate, ConfirmRequest, ConfirmResponse)
```

- 기존 `ServiceBrand`(브랜드 별칭/색상)를 매칭과 후보 이름 생성에 재사용한다.
- 신규 구독 생성은 `SubscriptionCommandService.createSubscription()`을 재사용한다.
- 결제 기록 저장은 `BillingRecordRepository`를 사용한다.
- **DB 스키마 변경 없음.** 가져온 기록은 `BillingRecord.memo`에 `"가져오기: <가맹점>"`을 남긴다.

## 5. API

### 5.1 `POST /api/imports/preview` (multipart/form-data)

| 파트/파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `file` | O | CSV 파일 |
| `dateColumn` | X | 날짜 컬럼 인덱스(0-based). 매핑 UI에서 전달 |
| `merchantColumn` | X | 가맹점 컬럼 인덱스 |
| `amountColumn` | X | 금액 컬럼 인덱스 |
| `hasHeader` | X | 헤더 존재 여부. 기본 `true` |

- 파일은 저장하지 않는다. 미리보기는 상태를 갖지 않는다.
- 컬럼을 자동 탐지하지 못하면 `200` + `needsMapping: true`와 `headers`, `sampleRows`를 반환한다. 프론트는 매핑 UI를 띄우고 위 인덱스를 붙여 같은 파일을 다시 보낸다.

응답 예시:

```json
{
  "encoding": "UTF-8",
  "delimiter": ",",
  "headerRowIndex": 0,
  "needsMapping": false,
  "columns": { "date": 0, "merchant": 1, "amount": 2 },
  "rowCount": 8,
  "rows": [
    {
      "rowNumber": 2, "billingDate": "2026-11-25", "merchant": "넷플릭스", "amount": 17000,
      "matchedSubscriptionId": 1, "matchedServiceName": "넷플릭스 프리미엄",
      "confidence": 0.95, "matchType": "AUTO", "alreadyRecorded": false
    }
  ],
  "candidates": [
    {
      "merchant": "SPOTIFY AB", "suggestedName": "Spotify", "suggestedCategory": "MUSIC",
      "suggestedBillingCycle": "MONTHLY", "suggestedFee": 10900,
      "suggestedBillingDay": 15, "suggestedStartDate": "2026-08-15",
      "occurrences": 3, "rowNumbers": [2, 3, 4], "logoSlug": "spotify"
    }
  ],
  "oneOffCount": 2,
  "skipped": [{ "rowNumber": 9, "reason": "금액 없음" }]
}
```

- `matchType`: `AUTO`(0.7 이상) | `REVIEW`(0.4 이상 0.7 미만) | `NONE`
- `alreadyRecorded: true`인 행은 프론트에서 비활성화한다.

### 5.2 `POST /api/imports/confirm` (application/json)

```json
{
  "records": [
    { "subscriptionId": 1, "billingDate": "2026-11-25", "amount": 17000, "merchant": "넷플릭스" }
  ],
  "newSubscriptions": [
    { "serviceName": "Spotify", "category": "MUSIC", "fee": 10900,
      "billingCycle": "MONTHLY", "billingDay": 15, "startDate": "2026-08-15" }
  ]
}
```

```json
{ "recordedCount": 3, "skippedCount": 1, "createdSubscriptions": 1 }
```

- 단일 트랜잭션으로 처리한다.
- `(subscriptionId, billingDate, amount)`가 이미 존재하면 건너뛰고 `skippedCount`에 포함한다.
- `newSubscriptions`는 `SubscriptionCreateRequest`와 동일한 검증을 적용한다.
- 소유권은 기존 `SubscriptionFinder`를 통해 현재 사용자로 제한한다.

## 6. CSV 파싱

### 6.1 인코딩

1. UTF-8 BOM → UTF-8
2. 엄격 모드 UTF-8 디코딩 시도
3. 실패 시 `MS949`(CP949)로 재시도
4. 둘 다 실패 → `UNSUPPORTED_CSV`

### 6.2 구분자·헤더

- 구분자는 첫 10줄에서 `,` `;` `\t` 중 열 수가 가장 일관된 것을 선택한다.
- Apache Commons CSV로 파싱해 따옴표 안 쉼표·줄바꿈을 처리한다.
- 카드사 파일 상단의 안내문은 **헤더 후보 행 탐색**으로 건너뛴다. 헤더 후보 = 날짜/가맹점/금액 키워드 중 2개 이상을 포함한 첫 행.
- 헤더가 없으면 첫 행부터 데이터로 취급한다.

### 6.3 컬럼 탐지

| 역할 | 헤더 키워드 |
| --- | --- |
| 날짜 | 거래일, 거래일시, 이용일, 승인일, 결제일, 일자, date |
| 가맹점 | 가맹점, 가맹점명, 이용처, 내용, 적요, 상호, merchant, description |
| 금액 | 금액, 승인금액, 이용금액, 결제금액, 출금액, 출금, amount |

- 키워드로 찾지 못하면 값 패턴으로 추론한다: 날짜 형식 비율이 높은 열, 숫자 비율이 높은 열.
- 추론 신뢰도가 낮으면 `needsMapping: true`.

### 6.4 값 파싱

- 날짜: `yyyy-MM-dd`, `yyyy.MM.dd`, `yyyy/MM/dd`, `yyyyMMdd`, `yyyy-MM-dd HH:mm:ss`
- 금액: `,`·`원`·공백 제거, 음수/괄호 허용
- 금액이 비었거나 0 이하(입금·환불)인 행은 `skipped`로 분류
- 동일 파일 내 완전 중복 행(날짜+가맹점+금액)은 1건만 유지

## 7. 기존 구독 매칭

`MerchantNormalizer`: 소문자화 → 공백·특수문자 제거 → 법인 접미사 제거(`(주)`, `주식회사`, `㈜`, `Inc`, `Ltd`, `Corp`, `AB`).

점수:

| 신호 | 점수 |
| --- | --- |
| `ServiceBrand` 기준 동일 브랜드 | 0.6 |
| 정규화 이름 포함 관계 | 0.5 |
| 금액이 `fee` 또는 `monthlyExpense`와 일치 | 0.3 |
| 금액 오차 ±5% 이내 | 0.2 |
| 거래일의 일(day)과 `billingDay` 차이 ±2일 이내 | 0.1 |

- 점수는 합산하되 **최대 1.0으로 상한**한다.
- 최고 점수 후보를 선택한다.
- `>= 0.7` → `AUTO`, `0.4 <= score < 0.7` → `REVIEW`, `< 0.4` → 매칭 없음
- 최고 점수가 동점이면 `REVIEW`로 내린다.
- `CANCELED` 상태 구독은 매칭 대상에서 제외한다.
- 하나의 거래는 하나의 구독에만 매칭된다.
- `alreadyRecorded` 판정은 행마다 쿼리하지 않고, 현재 사용자의 결제 기록을 1회 조회해 메모리에서 비교한다.

## 8. 신규 구독 후보

매칭되지 않은 행을 `정규화 가맹점`으로 묶는다.

1. 1회만 등장 → `oneOffCount`에 집계, 후보 제외
2. 2회 이상 + 인접 간격 편차 ±3일 이내 + 모든 금액이 중앙값 ±5% 이내 → 후보
3. 주기 추정(중앙 간격): 6~8일 `WEEKLY`, 26~35일 `MONTHLY`, 350~380일 `YEARLY`
4. 제안 값
   - 이름: 브랜드명(있으면) 또는 접미사 제거한 가맹점명
   - 카테고리: `CategoryGuesser`(브랜드 → 카테고리, 키워드 → 카테고리, 실패 시 `OTHER`)
   - 금액: 중앙값, 결제일: 최근 거래일의 일, 시작일: 최초 거래일
5. 이미 같은 정규화 이름 + 금액의 구독이 있으면 후보에서 제외

## 9. 저장과 멱등성

- `BillingRecord` 생성: `subscription`, `billingDate`, `amount`, `status=PAID`, `memo="가져오기: <가맹점>"`
- 중복 판정: `existsBySubscriptionIdAndBillingDateAndAmount`
- 신규 구독: `SubscriptionCommandService.createSubscription()` 재사용 (결제일·다음 결제일 계산 일원화)
- 확정 전체를 하나의 트랜잭션으로 처리

## 10. 에러 처리

기존 `BusinessException` + `GlobalExceptionHandler`를 사용한다.

| ErrorCode | HTTP | 상황 |
| --- | --- | --- |
| `EMPTY_IMPORT_FILE` | 400 | 빈 파일 |
| `UNSUPPORTED_CSV` | 400 | 인코딩/형식 판별 실패 |
| `IMPORT_LIMIT_EXCEEDED` | 400 | 5,000행 초과 |
| `INVALID_IMPORT_ROW` | 400 | 확정 요청 자체가 잘못된 경우(존재하지 않는 구독 ID 등) |

미리보기 단계의 행 단위 문제(금액 없음, 입금 등)는 전체 실패가 아니라 `skipped`로 응답한다.

## 11. 화면

- `내 구독 서비스` 패널 헤더에 **내역 가져오기** 버튼
- 모달 3단계: 파일 선택 → 미리보기(매핑/매칭/후보) → 결과 요약
- 미리보기 테이블: 체크박스, 날짜, 가맹점, 금액, 매칭 구독, 신뢰도
- 후보 카드: 이름, 카테고리, 주기, 금액, 근거(발생 횟수)
- 일회성 항목은 접힌 상태로 건수만 표시
- 스타일은 기존 `sf-*` 디자인 토큰을 따른다.

## 12. 샘플 CSV

실제 동작 확인용으로 3종을 제공한다. 위치: `docs/samples/`

### 12.1 `standard-utf8.csv` — 표준(UTF-8, 쉼표)

```csv
거래일,이용처,승인금액
2026-08-15,SPOTIFY AB,10900
2026-09-15,SPOTIFY AB,10900
2026-10-15,SPOTIFY AB,10900
2026-09-25,넷플릭스,17000
2026-10-25,넷플릭스,17000
2026-11-25,넷플릭스,17000
2026-10-02,쿠팡 와우 멤버십,7890
2026-11-20,스타벅스,6500
```

기대 결과: 넷플릭스 3건 AUTO 매칭, Spotify 신규 후보, 스타벅스/쿠팡 일회성(쿠팡은 데모 데이터에 구독이 있으면 REVIEW).

### 12.2 `card-cp949.csv` — 카드사형(CP949, 상단 안내문, 따옴표 금액)

```csv
신한카드 이용대금 명세서
조회기간,2026.08.01,2026.11.30
이용일,가맹점명,이용금액
2026.08.15,스포티파이,"10,900"
2026.09.15,스포티파이,"10,900"
2026.10.15,스포티파이,"10,900"
2026.09.25,넷플릭스코리아,"17,000"
2026.10.25,넷플릭스코리아,"17,000"
2026.11.25,넷플릭스코리아,"17,000"
```

### 12.3 `bank-semicolon.csv` — 은행형(세미콜론, 일시, 출금/입금 분리)

```csv
거래일시;적요;출금액;입금액
2026-08-15 09:12:00;SPOTIFY AB;10900;
2026-09-15 09:12:00;SPOTIFY AB;10900;
2026-10-15 09:12:00;SPOTIFY AB;10900;
2026-09-25 10:02:00;NETFLIX.COM;17000;
2026-10-25 10:02:00;NETFLIX.COM;17000;
2026-11-25 10:02:00;NETFLIX.COM;17000;
2026-11-05 08:00:00;급여;0;3200000
```

기대 결과: 입금 행은 `skipped`, 나머지는 매칭/후보로 분류.

### 12.4 실제 은행 CSV로 확인할 때

1. 파일을 그대로 업로드한다.
2. 자동 탐지가 실패하면 매핑 UI에서 날짜·적요·출금액 컬럼을 지정한다.
3. 출금/입금이 분리된 파일은 **출금액** 컬럼을 금액으로 선택한다.
4. CP949 파일도 인코딩 자동 감지로 처리된다.

## 13. 테스트 전략 (TDD)

- `CsvEncodingDetector`: UTF-8, BOM, CP949
- `CsvParser`: 따옴표 안 쉼표, 상단 안내문, 세미콜론, 날짜/금액 포맷
- `ColumnDetector`: 한글 헤더, 패턴 추론, `needsMapping`
- `MerchantNormalizer`: 법인 접미사, 도메인 표기
- `SubscriptionMatcher`: 브랜드 일치, 금액 오차, 날짜 근접, 동점, 취소 구독 제외, 기존 기록 중복
- `CandidateFinder`: 주/월/연 주기, 1회성 제외, 금액 편차
- `CsvImportService`: 확정 저장, 멱등성(같은 파일 2회), 소유권 제한
- `CsvImportController`: MockMvc multipart 미리보기/확정, 빈 파일, 5,000행 초과

## 14. 의존성

- `org.apache.commons:commons-csv:1.14.1` 추가 (따옴표·개행 처리)
- `spring.servlet.multipart.max-file-size: 5MB`, `max-request-size: 5MB`

## 15. 향후 확장

- xlsx 파서 추가 (`ImportSource` 인터페이스로 분리)
- 가져오기 이력 저장
- 반복 결제 자동 학습(주기·금액 변동 허용 범위)
