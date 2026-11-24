# CSV 내역 가져오기 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카드/은행 CSV를 업로드하면 기존 구독 결제를 자동 매칭·기록하고, 반복 결제를 신규 구독 후보로 제안한다.

**Architecture:** 기존 레이어드 구조를 따라 `subscription/importer` 하위에 파서 → 컬럼 탐지 → 매칭/후보 판별 → 서비스 → 컨트롤러를 배치한다. 미리보기는 상태를 저장하지 않고, 확정 요청에서 선택된 행만 단일 트랜잭션으로 반영한다. DB 스키마 변경은 없다.

**Tech Stack:** Java 21, Spring Boot 4.1.2, Spring MVC, Spring Data JPA, Apache Commons CSV 1.14.1, JUnit 5 + AssertJ + Mockito

**Spec:** `docs/superpowers/specs/2026-11-24-csv-import-design.md`

## Global Constraints

- 새 외부 의존성은 `org.apache.commons:commons-csv:1.14.1` 하나만 추가한다.
- DB 스키마/Flyway 마이그레이션을 변경하지 않는다.
- 파일 크기 최대 5MB, 행 수 최대 5,000건.
- 사용자 노출 메시지는 한국어로 작성한다.
- 모든 프로덕션 코드는 실패하는 테스트를 먼저 작성한다(TDD).
- **커밋은 사용자가 명시적으로 요청할 때만 한다.** 이 계획에는 커밋 단계가 없다.
- 패키지 루트: `com.subflow.subscription.importer`

## File Structure

```text
src/main/java/com/subflow/subscription/importer/
├── controller/CsvImportController.java
├── service/CsvImportService.java
├── parser/CsvEncodingDetector.java
├── parser/CsvParser.java
├── parser/ColumnDetector.java
├── parser/ValueParser.java
├── parser/CsvRow.java
├── parser/CsvDocument.java
├── parser/ColumnMapping.java
├── parser/ColumnDetection.java
├── parser/ParsedPayment.java
├── matcher/MerchantNormalizer.java
├── matcher/SubscriptionMatcher.java
├── matcher/CandidateFinder.java
├── matcher/CategoryGuesser.java
├── matcher/MatchType.java
├── matcher/MatchResult.java
├── matcher/ImportCandidate.java
└── dto/
    ├── ImportPreviewRow.java
    ├── ImportPreviewResponse.java
    ├── SkippedRow.java
    ├── ImportRecordRequest.java
    ├── ImportConfirmRequest.java
    └── ImportConfirmResponse.java
```

수정 파일: `build.gradle`, `application.yml`, `ErrorCode.java`, `BillingRecordRepository.java`, `index.mustache`, `app.js`, `app.css`

## Review Focus

1. **CP949 파일의 한글 가맹점명**이 깨지지 않고 넷플릭스로 매칭되는가 → Task 1, Task 5 테스트
2. **상단 안내문·빈 줄이 있는 카드사 CSV**에서 헤더 행을 잘못 잡지 않는가 → Task 2 테스트
3. **과거 3개월 내역**에서 날짜 신호가 `nextBillingDate`가 아니라 `billingDay` 기준으로 동작하는가 → Task 5 테스트
4. **같은 파일 재업로드** 시 중복 `BillingRecord`가 생기지 않는가 → Task 8 테스트
5. **5,000행 경계**에서 제한이 정확히 동작하고 4,999행은 통과하는가 → Task 7 테스트

---

### Task 1: 인코딩 감지와 CSV 파싱

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/com/subflow/subscription/importer/parser/CsvRow.java`
- Create: `src/main/java/com/subflow/subscription/importer/parser/CsvDocument.java`
- Create: `src/main/java/com/subflow/subscription/importer/parser/CsvEncodingDetector.java`
- Create: `src/main/java/com/subflow/subscription/importer/parser/CsvParser.java`
- Test: `src/test/java/com/subflow/subscription/importer/parser/CsvEncodingDetectorTest.java`
- Test: `src/test/java/com/subflow/subscription/importer/parser/CsvParserTest.java`
- Test resource: `src/test/resources/csv/standard-utf8.csv`, `card-cp949.csv`, `bank-semicolon.csv` (내용은 `docs/samples/`와 동일)

**Interfaces:**
- Produces:
  - `record CsvRow(int rowNumber, List<String> values)`
  - `record CsvDocument(String encoding, char delimiter, List<CsvRow> rows)`
  - `static Charset CsvEncodingDetector.detect(byte[] content)`
  - `CsvDocument CsvParser.parse(byte[] content)`

- [ ] **Step 1: 의존성 추가**

`build.gradle`의 `implementation` 블록에 추가한다.

```gradle
implementation 'org.apache.commons:commons-csv:1.14.1'
```

- [ ] **Step 2: 실패하는 테스트 작성**

`CsvEncodingDetectorTest`:

```java
@Test
@DisplayName("UTF-8 BOM이 있으면 UTF-8로 판별한다")
void detectsUtf8Bom() {
    byte[] bytes = concat(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "가맹점".getBytes(UTF_8));
    assertThat(CsvEncodingDetector.detect(bytes)).isEqualTo(UTF_8);
}

@Test
@DisplayName("CP949 한글 파일을 CP949로 판별한다")
void detectsCp949() {
    byte[] bytes = readResource("/csv/card-cp949.csv");
    assertThat(CsvEncodingDetector.detect(bytes)).isEqualTo(Charset.forName("MS949"));
}
```

`CsvParserTest`:

```java
@Test
@DisplayName("따옴표 안의 쉼표를 하나의 값으로 파싱한다")
void keepsQuotedComma() {
    CsvDocument doc = parser.parse("이용일,가맹점명,이용금액\n2026.08.15,\"스타벅스, 강남\",\"10,900\"\n".getBytes(UTF_8));
    assertThat(doc.rows().get(1).values()).containsExactly("2026.08.15", "스타벅스, 강남", "10,900");
}

@Test
@DisplayName("세미콜론 구분자를 자동 감지한다")
void detectsSemicolon() {
    CsvDocument doc = parser.parse("거래일시;적요;출금액\n2026-08-15 09:12:00;SPOTIFY AB;10900\n".getBytes(UTF_8));
    assertThat(doc.delimiter()).isEqualTo(';');
    assertThat(doc.rows().get(1).values()).hasSize(3);
}

@Test
@DisplayName("CP949 파일의 한글을 깨지지 않게 읽는다")
void readsCp949Korean() {
    CsvDocument doc = parser.parse(readResource("/csv/card-cp949.csv"));
    assertThat(doc.encoding()).isEqualTo("MS949");
    assertThat(doc.rows().stream().flatMap(r -> r.values().stream()))
            .anyMatch(v -> v.contains("넷플릭스"));
}
```

테스트 헬퍼 `concat(byte[], byte[])`, `readResource(String)`는 해당 테스트 클래스의 private 메서드로 작성한다.

- [ ] **Step 3: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.parser.*'`
Expected: 컴파일 실패 (`CsvEncodingDetector` 없음)

- [ ] **Step 4: 최소 구현**

- `CsvEncodingDetector`: BOM 확인 → 엄격 UTF-8 디코딩(`CodingErrorAction.REPORT`) 성공 시 `UTF_8`, 실패 시 `Charset.forName("MS949")`
- `CsvParser`: `CsvEncodingDetector`로 디코딩 → 첫 10줄에서 `,` `;` `\t`별 필드 수 편차가 가장 작은 구분자 선택 → Commons CSV `CSVFormat.DEFAULT.builder().setDelimiter(d).build()`로 파싱 → `rowNumber`는 1부터
- 빈 내용이면 `rows`가 빈 `CsvDocument` 반환 (예외는 Task 7에서 처리)

- [ ] **Step 5: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.parser.*'`
Expected: PASS

---

### Task 2: 컬럼 자동 탐지

**Files:**
- Create: `src/main/java/com/subflow/subscription/importer/parser/ColumnMapping.java`
- Create: `src/main/java/com/subflow/subscription/importer/parser/ColumnDetection.java`
- Create: `src/main/java/com/subflow/subscription/importer/parser/ColumnDetector.java`
- Test: `src/test/java/com/subflow/subscription/importer/parser/ColumnDetectorTest.java`

**Interfaces:**
- Consumes: `CsvDocument`, `CsvRow`
- Produces:
  - `record ColumnMapping(int date, int merchant, int amount)`
  - `record ColumnDetection(ColumnMapping mapping, int headerRowIndex, boolean needsMapping)`
  - `ColumnDetection ColumnDetector.detect(CsvDocument document, boolean hasHeader)`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test
@DisplayName("한글 헤더에서 날짜·가맹점·금액 컬럼을 찾는다")
void detectsKoreanHeaders() {
    CsvDocument doc = parse("거래일,이용처,승인금액\n2026-08-15,SPOTIFY AB,10900\n");
    ColumnDetection result = detector.detect(doc, true);
    assertThat(result.mapping()).isEqualTo(new ColumnMapping(0, 1, 2));
    assertThat(result.needsMapping()).isFalse();
}

@Test
@DisplayName("상단 안내문을 건너뛰고 실제 헤더 행을 찾는다")
void skipsPreamble() {
    CsvDocument doc = parse("신한카드 이용대금 명세서\n조회기간,2026.08.01,2026.11.30\n이용일,가맹점명,이용금액\n2026.08.15,스포티파이,\"10,900\"\n");
    ColumnDetection result = detector.detect(doc, true);
    assertThat(result.headerRowIndex()).isEqualTo(2);
    assertThat(result.mapping()).isEqualTo(new ColumnMapping(0, 1, 2));
}

@Test
@DisplayName("출금/입금이 분리된 은행 헤더에서 출금액을 금액으로 고른다")
void prefersWithdrawalColumn() {
    CsvDocument doc = parse("거래일시;적요;출금액;입금액\n2026-08-15 09:12:00;SPOTIFY AB;10900;\n");
    assertThat(detector.detect(doc, true).mapping()).isEqualTo(new ColumnMapping(0, 1, 2));
}

@Test
@DisplayName("헤더가 없으면 값 패턴으로 추론한다")
void infersFromValues() {
    CsvDocument doc = parse("2026-08-15,SPOTIFY AB,10900\n2026-09-15,SPOTIFY AB,10900\n");
    ColumnDetection result = detector.detect(doc, true);
    assertThat(result.headerRowIndex()).isEqualTo(-1);
    assertThat(result.mapping()).isEqualTo(new ColumnMapping(0, 1, 2));
}

@Test
@DisplayName("추론이 불가능하면 needsMapping을 참으로 반환한다")
void needsMappingWhenAmbiguous() {
    CsvDocument doc = parse("a,b,c\nx,y,z\n");
    assertThat(detector.detect(doc, true).needsMapping()).isTrue();
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.parser.ColumnDetectorTest'`
Expected: 컴파일 실패

- [ ] **Step 3: 최소 구현**

- 헤더 후보: 날짜/가맹점/금액 키워드 중 **2개 이상** 포함한 첫 행. 키워드는 스펙 6.3 표를 그대로 사용한다.
- 금액 키워드가 여러 개면 `출금`이 포함된 컬럼을 우선한다.
- 헤더가 없으면 처음 20개 데이터 행으로 추론: 날짜 파싱 성공률 80% 이상인 열 = 날짜, 숫자 파싱 성공률 80% 이상이고 날짜가 아닌 열 = 금액, 남은 열 중 가장 긴 문자열 평균 = 가맹점.
- 세 역할이 모두 결정되지 않으면 `needsMapping=true`, `mapping=null`.

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.parser.ColumnDetectorTest'`
Expected: PASS

---

### Task 3: 날짜·금액 값 파싱

**Files:**
- Create: `src/main/java/com/subflow/subscription/importer/parser/ValueParser.java`
- Create: `src/main/java/com/subflow/subscription/importer/parser/ParsedPayment.java`
- Test: `src/test/java/com/subflow/subscription/importer/parser/ValueParserTest.java`

**Interfaces:**
- Produces:
  - `record ParsedPayment(int rowNumber, LocalDate billingDate, String merchant, BigDecimal amount)`
  - `static Optional<LocalDate> ValueParser.parseDate(String raw)`
  - `static Optional<BigDecimal> ValueParser.parseAmount(String raw)`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@ParameterizedTest
@ValueSource(strings = {"2026-08-15", "2026.08.15", "2026/08/15", "20260815", "2026-08-15 09:12:00"})
void parsesSupportedDateFormats(String raw) {
    assertThat(ValueParser.parseDate(raw)).contains(LocalDate.of(2026, 8, 15));
}

@Test
void parsesAmountWithCommaAndWon() {
    assertThat(ValueParser.parseAmount("\"10,900원\"")).contains(new BigDecimal("10900"));
}

@Test
void rejectsBlankOrZeroAmount() {
    assertThat(ValueParser.parseAmount("")).isEmpty();
    assertThat(ValueParser.parseAmount("0")).isEmpty();
}

@Test
void keepsNegativeAmount() {
    assertThat(ValueParser.parseAmount("-5000")).contains(new BigDecimal("-5000"));
}

@Test
void returnsEmptyForInvalidDate() {
    assertThat(ValueParser.parseDate("조회기간")).isEmpty();
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.parser.ValueParserTest'`
Expected: 컴파일 실패

- [ ] **Step 3: 최소 구현**

- 날짜: 공백 제거 후 `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd`, `yyyy.MM.dd`, `yyyy/MM/dd`, `yyyyMMdd` 순으로 시도
- 금액: `"` `,` `원` 공백 제거 → `BigDecimal` → 0 이하이면 `Optional.empty()`

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.parser.ValueParserTest'`
Expected: PASS

---

### Task 4: 가맹점 정규화

**Files:**
- Create: `src/main/java/com/subflow/subscription/importer/matcher/MerchantNormalizer.java`
- Test: `src/test/java/com/subflow/subscription/importer/matcher/MerchantNormalizerTest.java`

**Interfaces:**
- Produces: `static String MerchantNormalizer.normalize(String merchant)`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@ParameterizedTest
@CsvSource({
        "'NETFLIX.COM', 'netflix'",
        "'Netflix, Inc.', 'netflix'",
        "'넷플릭스코리아', '넷플릭스코리아'",
        "'(주)스포티파이', '스포티파이'",
        "'Spotify AB', 'spotify'"
})
void normalizesMerchant(String raw, String expected) {
    assertThat(MerchantNormalizer.normalize(raw)).isEqualTo(expected);
}

@Test
void returnsEmptyForNull() {
    assertThat(MerchantNormalizer.normalize(null)).isEmpty();
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.matcher.MerchantNormalizerTest'`
Expected: 컴파일 실패

- [ ] **Step 3: 최소 구현**

소문자화 → `.com` 등 도메인 접미사 제거 → 법인 접미사(`(주)`, `주식회사`, `㈜`, `inc`, `ltd`, `corp`, `ab`, `co`) 제거 → 공백·특수문자 제거. 한글은 그대로 둔다.

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.matcher.MerchantNormalizerTest'`
Expected: PASS

---

### Task 5: 기존 구독 매칭

**Files:**
- Create: `src/main/java/com/subflow/subscription/importer/matcher/MatchType.java`
- Create: `src/main/java/com/subflow/subscription/importer/matcher/MatchResult.java`
- Create: `src/main/java/com/subflow/subscription/importer/matcher/SubscriptionMatcher.java`
- Test: `src/test/java/com/subflow/subscription/importer/matcher/SubscriptionMatcherTest.java`

**Interfaces:**
- Consumes: `ServiceBrand.resolve`, `MerchantNormalizer.normalize`, `Subscription`
- Produces:
  - `enum MatchType { AUTO, REVIEW, NONE }`
  - `record MatchResult(Long subscriptionId, String serviceName, double score, MatchType type)`
  - `Optional<MatchResult> SubscriptionMatcher.match(LocalDate billingDate, String merchant, BigDecimal amount, List<Subscription> subscriptions)`
  - 동점일 때는 `MatchResult(null, null, score, REVIEW)`를 반환한다(프론트에서 미선택 상태로 표시).

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test
@DisplayName("브랜드와 금액이 일치하면 자동 매칭한다")
void autoMatchesByBrandAndAmount() {
    Subscription netflix = subscription(1L, "넷플릭스 프리미엄", "17000", 25);
    MatchResult result = matcher.match(LocalDate.of(2026, 9, 25), "NETFLIX.COM", new BigDecimal("17000"), List.of(netflix)).orElseThrow();
    assertThat(result.type()).isEqualTo(MatchType.AUTO);
    assertThat(result.subscriptionId()).isEqualTo(1L);
    assertThat(result.score()).isGreaterThanOrEqualTo(0.7);
}

@Test
@DisplayName("과거 내역도 billingDay 기준으로 날짜 신호를 계산한다")
void usesBillingDayNotNextBillingDate() {
    Subscription netflix = subscription(1L, "넷플릭스 프리미엄", "17000", 25);
    MatchResult result = matcher.match(LocalDate.of(2026, 3, 25), "넷플릭스", new BigDecimal("17000"), List.of(netflix)).orElseThrow();
    assertThat(result.type()).isEqualTo(MatchType.AUTO);
}

@Test
@DisplayName("브랜드는 같지만 금액이 다르면 확인 필요로 내린다")
void downgradesWhenAmountDiffers() {
    Subscription netflix = subscription(1L, "넷플릭스 프리미엄", "17000", 25);
    MatchResult result = matcher.match(LocalDate.of(2026, 9, 25), "NETFLIX.COM", new BigDecimal("5500"), List.of(netflix)).orElseThrow();
    assertThat(result.type()).isEqualTo(MatchType.REVIEW);
}

@Test
@DisplayName("최고 점수가 동점이면 확인 필요로 처리한다")
void tieBecomesReview() {
    Subscription a = subscription(1L, "넷플릭스", "17000", 25);
    Subscription b = subscription(2L, "넷플릭스", "17000", 25);
    assertThat(matcher.match(LocalDate.of(2026, 9, 25), "넷플릭스", new BigDecimal("17000"), List.of(a, b)).orElseThrow().type())
            .isEqualTo(MatchType.REVIEW);
}

@Test
@DisplayName("해지된 구독은 매칭 대상에서 제외한다")
void ignoresCanceled() {
    Subscription canceled = subscription(1L, "넷플릭스", "17000", 25);
    canceled.cancelImmediately(LocalDate.of(2026, 1, 1));
    assertThat(matcher.match(LocalDate.of(2026, 9, 25), "넷플릭스", new BigDecimal("17000"), List.of(canceled))).isEmpty();
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.matcher.SubscriptionMatcherTest'`
Expected: 컴파일 실패

- [ ] **Step 3: 최소 구현**

스펙 7장 점수표를 그대로 적용한다. 신호별 점수를 합산하고 `Math.min(1.0, score)`. 점수가 같은 최고 후보가 2개 이상이면 `REVIEW`. `CANCELED` 제외. 점수 `< 0.4`면 `Optional.empty()`.

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.matcher.SubscriptionMatcherTest'`
Expected: PASS

---

### Task 6: 신규 구독 후보 판별

**Files:**
- Create: `src/main/java/com/subflow/subscription/importer/matcher/CategoryGuesser.java`
- Create: `src/main/java/com/subflow/subscription/importer/matcher/ImportCandidate.java`
- Create: `src/main/java/com/subflow/subscription/importer/matcher/CandidateFinder.java`
- Test: `src/test/java/com/subflow/subscription/importer/matcher/CandidateFinderTest.java`

**Interfaces:**
- Consumes: `ParsedPayment`, `ServiceBrand`, `MerchantNormalizer`, `Subscription`
- Produces:
  - `record ImportCandidate(String merchant, String suggestedName, Category suggestedCategory, BillingCycle suggestedBillingCycle, BigDecimal suggestedFee, int suggestedBillingDay, LocalDate suggestedStartDate, int occurrences, List<Integer> rowNumbers, String logoSlug)`
  - `static Category CategoryGuesser.guess(ServiceBrand brand, String merchant)`
  - `List<ImportCandidate> CandidateFinder.find(List<ParsedPayment> unmatched, List<Subscription> existingSubscriptions)`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test
@DisplayName("매월 반복되는 미등록 가맹점을 월간 후보로 제안한다")
void findsMonthlyCandidate() {
    var rows = List.of(
            payment(2, "2026-08-15", "SPOTIFY AB", "10900"),
            payment(3, "2026-09-15", "SPOTIFY AB", "10900"),
            payment(4, "2026-10-15", "SPOTIFY AB", "10900"));
    var result = finder.find(rows, List.of());
    assertThat(result).hasSize(1);
    var candidate = result.get(0);
    assertThat(candidate.suggestedName()).isEqualTo("Spotify");
    assertThat(candidate.suggestedBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
    assertThat(candidate.suggestedBillingDay()).isEqualTo(15);
    assertThat(candidate.suggestedFee()).isEqualByComparingTo("10900");
    assertThat(candidate.occurrences()).isEqualTo(3);
}

@Test
@DisplayName("1회만 등장하면 후보로 제안하지 않는다")
void ignoresOneOff() {
    var rows = List.of(payment(2, "2026-11-20", "스타벅스", "6500"));
    assertThat(finder.find(rows, List.of())).isEmpty();
}

@Test
@DisplayName("금액 편차가 크면 후보로 제안하지 않는다")
void ignoresVaryingAmounts() {
    var rows = List.of(
            payment(2, "2026-08-15", "SPOTIFY AB", "10900"),
            payment(3, "2026-09-15", "SPOTIFY AB", "20000"),
            payment(4, "2026-10-15", "SPOTIFY AB", "10900"));
    assertThat(finder.find(rows, List.of())).isEmpty();
}

@Test
@DisplayName("이미 등록된 구독과 같은 이름·금액이면 후보에서 제외한다")
void excludesExistingSubscription() {
    var rows = List.of(
            payment(2, "2026-08-15", "NETFLIX.COM", "17000"),
            payment(3, "2026-09-15", "NETFLIX.COM", "17000"));
    var existing = List.of(subscription(1L, "넷플릭스 프리미엄", "17000", 15));
    assertThat(finder.find(rows, existing)).isEmpty();
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.matcher.CandidateFinderTest'`
Expected: 컴파일 실패

- [ ] **Step 3: 최소 구현**

스펙 8장을 그대로 적용한다. 주기 판정은 중앙 간격 기준이며, 어느 구간에도 속하지 않으면 후보에서 제외한다. 이름은 `ServiceBrand`가 있으면 브랜드 표시명(스펙의 제안 이름), 없으면 정규화 전 가맹점명에서 법인 접미사만 제거한 값. 기존 구독과의 중복 제외는 **정규화 이름이 같거나 `ServiceBrand`가 같은 경우**를 모두 포함한다.

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.matcher.CandidateFinderTest'`
Expected: PASS

---

### Task 7: 미리보기 서비스

**Files:**
- Create: `src/main/java/com/subflow/subscription/importer/dto/ImportPreviewRow.java`
- Create: `src/main/java/com/subflow/subscription/importer/dto/SkippedRow.java`
- Create: `src/main/java/com/subflow/subscription/importer/dto/ImportPreviewResponse.java`
- Create: `src/main/java/com/subflow/subscription/importer/service/CsvImportService.java`
- Modify: `src/main/java/com/subflow/subscription/repository/BillingRecordRepository.java`
- Modify: `src/main/java/com/subflow/global/exception/ErrorCode.java` (이 태스크에서는 빈 파일/형식/행 수 3개만 추가)
- Test: `src/test/java/com/subflow/subscription/importer/service/CsvImportServicePreviewTest.java`

**Interfaces:**
- Consumes: Task 1~6의 모든 컴포넌트, `SubscriptionRepository`, `SubscriptionFinder`, `BillingRecordRepository`
- Produces:
  - `record ImportPreviewRow(int rowNumber, LocalDate billingDate, String merchant, BigDecimal amount, Long matchedSubscriptionId, String matchedServiceName, double confidence, MatchType matchType, boolean alreadyRecorded)`
  - `record SkippedRow(int rowNumber, String reason)`
  - `record ImportPreviewResponse(String encoding, char delimiter, int headerRowIndex, boolean needsMapping, ColumnMapping columns, List<String> headers, List<List<String>> sampleRows, int rowCount, List<ImportPreviewRow> rows, List<ImportCandidate> candidates, int oneOffCount, List<SkippedRow> skipped)`
  - `ImportPreviewResponse CsvImportService.preview(MultipartFile file, Integer dateColumn, Integer merchantColumn, Integer amountColumn, boolean hasHeader)`
  - Repository: `List<BillingRecord> findAllBySubscriptionOwnerEmail(String ownerEmail)`

- [ ] **Step 1: 실패하는 테스트 작성**

Mockito 단위 테스트로 작성한다.

```java
@Test
@DisplayName("미리보기에서 매칭 행과 신규 후보를 함께 반환한다")
void buildsPreview() {
    given(subscriptionFinder.currentOwnerEmail()).willReturn("me@example.com");
    given(subscriptionRepository.findAllByOwnerEmailAndStatusOrderByNextBillingDateAsc("me@example.com", ACTIVE))
            .willReturn(List.of(subscription(1L, "넷플릭스 프리미엄", "17000", 25)));
    given(billingRecordRepository.findAllBySubscriptionOwnerEmail("me@example.com")).willReturn(List.of());

    var response = service.preview(csvFromResource("standard-utf8.csv"), null, null, null, true);

    assertThat(response.needsMapping()).isFalse();
    assertThat(response.rows()).anySatisfy(row -> {
        assertThat(row.merchant()).isEqualTo("넷플릭스");
        assertThat(row.matchType()).isEqualTo(MatchType.AUTO);
    });
    assertThat(response.candidates()).extracting(ImportCandidate::suggestedName).contains("Spotify");
}

@Test
@DisplayName("이미 기록된 결제는 alreadyRecorded로 표시한다")
void marksAlreadyRecorded() {
    // given: 넷플릭스 구독 + 2026-09-25/17000 BillingRecord
    assertThat(response.rows()).anySatisfy(row -> {
        if (row.merchant().equals("넷플릭스")) assertThat(row.alreadyRecorded()).isTrue();
    });
}

@Test
@DisplayName("5,000건을 넘으면 IMPORT_LIMIT_EXCEEDED를 던진다")
void rejectsTooManyRows() {
    var file = csvWithRows(5001);
    assertThatThrownBy(() -> service.preview(file, null, null, null, true))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.IMPORT_LIMIT_EXCEEDED);
}

@Test
@DisplayName("4,999건은 통과한다")
void allowsJustUnderLimit() {
    assertThat(service.preview(csvWithRows(4999), null, null, null, true).rowCount()).isEqualTo(4999);
}

@Test
@DisplayName("0 이하 금액 행은 skipped로 분류한다")
void skipsNonPositiveAmount() {
    var response = service.preview(csv("거래일,이용처,승인금액\n2026-11-05,급여,0\n"), null, null, null, true);
    assertThat(response.skipped()).extracting(SkippedRow::rowNumber).contains(2);
}

@Test
@DisplayName("빈 파일은 EMPTY_IMPORT_FILE을 던진다")
void rejectsEmptyFile() {
    assertThatThrownBy(() -> service.preview(csv(""), null, null, null, true))
            .isInstanceOf(BusinessException.class);
}
```

테스트 헬퍼 `csv(String content)`, `csvFromResource(String name)`, `csvWithRows(int count)`는 private 메서드로 작성한다.

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.service.CsvImportServicePreviewTest'`
Expected: 컴파일 실패

- [ ] **Step 3: 최소 구현**

`preview` 흐름:

1. 빈 파일 → `EMPTY_IMPORT_FILE`
2. `CsvParser.parse`
3. `ColumnDetector.detect`. `dateColumn` 등이 전달되면 그 값으로 `ColumnMapping`을 덮어쓰고 `needsMapping=false`
4. `needsMapping`이면 헤더/샘플 행만 담아 반환
5. 헤더 이후 행을 `ValueParser`로 변환. 실패 행은 `skipped`에 사유와 함께 추가
6. 사용자 ACTIVE 구독과 결제 기록을 각각 1회 조회
7. 행별 `SubscriptionMatcher.match` + `alreadyRecorded` 판정
8. 매칭 안 된 행은 `CandidateFinder.find`로 후보 생성, 후보에 속하지 않은 행 수를 `oneOffCount`로 집계
9. `ImportPreviewResponse` 반환

`ErrorCode`에 `EMPTY_IMPORT_FILE`, `UNSUPPORTED_CSV`, `IMPORT_LIMIT_EXCEEDED` 추가.
`BillingRecordRepository`에 `findAllBySubscriptionOwnerEmail(String ownerEmail)` 추가.

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.service.CsvImportServicePreviewTest'`
Expected: PASS

---

### Task 8: 확정 서비스

**Files:**
- Create: `src/main/java/com/subflow/subscription/importer/dto/ImportRecordRequest.java`
- Create: `src/main/java/com/subflow/subscription/importer/dto/ImportConfirmRequest.java`
- Create: `src/main/java/com/subflow/subscription/importer/dto/ImportConfirmResponse.java`
- Modify: `src/main/java/com/subflow/subscription/importer/service/CsvImportService.java`
- Modify: `src/main/java/com/subflow/subscription/repository/BillingRecordRepository.java`
- Modify: `src/main/java/com/subflow/global/exception/ErrorCode.java` (`INVALID_IMPORT_ROW` 추가)
- Test: `src/test/java/com/subflow/subscription/importer/service/CsvImportServiceConfirmTest.java`

**Interfaces:**
- Consumes: `SubscriptionFinder`, `SubscriptionCommandService`, `BillingRecordRepository`
- Produces:
  - `record ImportRecordRequest(Long subscriptionId, LocalDate billingDate, BigDecimal amount, String merchant)`
  - `record ImportConfirmRequest(List<ImportRecordRequest> records, List<SubscriptionCreateRequest> newSubscriptions)`
  - `record ImportConfirmResponse(int recordedCount, int skippedCount, int createdSubscriptions)`
  - `ImportConfirmResponse CsvImportService.confirm(ImportConfirmRequest request)`
  - Repository: `boolean existsBySubscriptionIdAndBillingDateAndAmount(Long subscriptionId, LocalDate billingDate, BigDecimal amount)`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test
@DisplayName("선택한 결제를 BillingRecord로 저장한다")
void recordsSelectedPayments() {
    given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription);
    given(billingRecordRepository.existsBySubscriptionIdAndBillingDateAndAmount(1L, date, amount)).willReturn(false);

    var response = service.confirm(new ImportConfirmRequest(
            List.of(new ImportRecordRequest(1L, date, amount, "넷플릭스")), List.of()));

    assertThat(response.recordedCount()).isEqualTo(1);
    then(billingRecordRepository).should().save(any(BillingRecord.class));
}

@Test
@DisplayName("이미 기록된 결제는 건너뛴다")
void skipsDuplicates() {
    given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription);
    given(billingRecordRepository.existsBySubscriptionIdAndBillingDateAndAmount(1L, date, amount)).willReturn(true);

    var response = service.confirm(new ImportConfirmRequest(
            List.of(new ImportRecordRequest(1L, date, amount, "넷플릭스")), List.of()));

    assertThat(response.recordedCount()).isZero();
    assertThat(response.skippedCount()).isEqualTo(1);
    then(billingRecordRepository).should(never()).save(any());
}

@Test
@DisplayName("신규 후보를 구독으로 등록한다")
void createsNewSubscriptions() {
    given(commandService.createSubscription(any())).willReturn(mock(SubscriptionResponse.class));

    var response = service.confirm(new ImportConfirmRequest(List.of(), List.of(createRequest)));

    assertThat(response.createdSubscriptions()).isEqualTo(1);
}

@Test
@DisplayName("가져온 기록의 memo에 가맹점을 남긴다")
void storesMerchantInMemo() {
    // save 캡처 후 memo == "가져오기: 넷플릭스" 검증
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.service.CsvImportServiceConfirmTest'`
Expected: 컴파일 실패

- [ ] **Step 3: 최소 구현**

`@Transactional`을 붙이고, 기록 루프에서 `findOwnedById` → 중복 확인 → `BillingRecord.builder()...memo("가져오기: " + merchant).status(PAID)` 저장. 신규 구독은 `commandService.createSubscription(request)` 호출. `null` 필드가 있으면 `INVALID_IMPORT_ROW`.

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.service.CsvImportServiceConfirmTest'`
Expected: PASS

---

### Task 9: 컨트롤러와 설정

**Files:**
- Create: `src/main/java/com/subflow/subscription/importer/controller/CsvImportController.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/subflow/subscription/importer/controller/CsvImportControllerTest.java`

**Interfaces:**
- Consumes: `CsvImportService`
- Produces: `POST /api/imports/preview`, `POST /api/imports/confirm`

- [ ] **Step 1: 실패하는 테스트 작성**

기존 컨트롤러 테스트처럼 `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())`를 사용한다.

```java
@Test
@DisplayName("multipart 파일을 받아 미리보기를 반환한다")
void previewsCsv() throws Exception {
    given(service.preview(any(), isNull(), isNull(), isNull(), eq(true))).willReturn(previewResponse());
    mockMvc.perform(multipart("/api/imports/preview")
                    .file(new MockMultipartFile("file", "sample.csv", "text/csv", csvBytes())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rowCount").value(8))
            .andExpect(jsonPath("$.rows[0].merchant").value("넷플릭스"));
}

@Test
@DisplayName("컬럼 매핑 인덱스를 전달하면 서비스로 넘긴다")
void passesColumnMapping() throws Exception {
    mockMvc.perform(multipart("/api/imports/preview")
                    .file(new MockMultipartFile("file", "sample.csv", "text/csv", csvBytes()))
                    .param("dateColumn", "0").param("merchantColumn", "1").param("amountColumn", "2"))
            .andExpect(status().isOk());
    then(service).should().preview(any(), eq(0), eq(1), eq(2), eq(true));
}

@Test
@DisplayName("빈 파일이면 400을 반환한다")
void rejectsEmptyFile() throws Exception {
    given(service.preview(any(), any(), any(), any(), anyBoolean()))
            .willThrow(new BusinessException(ErrorCode.EMPTY_IMPORT_FILE));
    mockMvc.perform(multipart("/api/imports/preview")
                    .file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0])))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("가져올 내역이 없는 파일입니다."));
}

@Test
@DisplayName("확정 요청을 처리한다")
void confirmsImport() throws Exception {
    given(service.confirm(any())).willReturn(new ImportConfirmResponse(3, 1, 1));
    mockMvc.perform(post("/api/imports/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(confirmRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recordedCount").value(3));
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.controller.CsvImportControllerTest'`
Expected: 컴파일 실패

- [ ] **Step 3: 최소 구현**

- `@RestController @RequestMapping("/api/imports")`
- `preview`: `@RequestParam("file") MultipartFile`, `@RequestParam(required=false) Integer dateColumn/merchantColumn/amountColumn`, `@RequestParam(defaultValue="true") boolean hasHeader`
- `confirm`: `@Valid @RequestBody ImportConfirmRequest`
- `application.yml`에 multipart 제한 추가

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.subflow.subscription.importer.controller.CsvImportControllerTest'`
Expected: PASS

---

### Task 10: 화면 연결과 수동 검증

**Files:**
- Modify: `src/main/resources/templates/index.mustache`
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/static/css/app.css`

**Interfaces:**
- Consumes: `POST /api/imports/preview`, `POST /api/imports/confirm`

- [ ] **Step 1: 마크업 추가**

`내 구독 서비스` 패널 헤더에 `내역 가져오기` 버튼을 추가하고, 모달 3단계(파일 선택 / 미리보기 / 결과)를 기존 `sf-*` 스타일로 작성한다. 미리보기에는 매핑 셀렉트(필요 시), 행 체크박스, 후보 카드, 일회성 접힘 영역을 둔다.

- [ ] **Step 2: JS 추가**

`app.js`에 다음 함수를 추가한다.

- `openImportModal()` / `resetImportModal()`
- `previewImport()` — `FormData`로 파일과 매핑 인덱스 전송, `needsMapping`이면 매핑 UI 표시 후 재호출
- `renderImportPreview(data)`, `renderCandidates(data)`
- `confirmImport()` — 체크된 행과 후보를 모아 `POST /api/imports/confirm` 후 `location.reload()`

- [ ] **Step 3: CSS 추가**

미리보기 테이블, 후보 카드, 단계 표시 스타일을 `app.css`에 추가하고 템플릿의 `app.css?v=` 값을 갱신한다.

- [ ] **Step 4: 앱 실행 후 샘플 3종 수동 검증**

Run: `./gradlew bootRun`

| 파일 | 기대 결과 |
| --- | --- |
| `docs/samples/standard-utf8.csv` | 넷플릭스 3건 AUTO 매칭, Spotify 후보, 일회성 2건 |
| `docs/samples/card-cp949.csv` | 인코딩 `MS949` 감지, 상단 안내문 무시, 넷플릭스 매칭 |
| `docs/samples/bank-semicolon.csv` | 세미콜론 감지, 입금(급여) 행 `skipped`, SPOTIFY/NETFLIX 매칭·후보 |

같은 파일을 두 번 적용한 뒤 결제 기록이 늘지 않는지 확인한다.

- [ ] **Step 5: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 전체 PASS

---

## Self-Review 결과

- 스펙 1~15장의 요구사항이 Task 1~10에 각각 대응됨
- `ColumnDetection`의 `mapping`은 `needsMapping=true`일 때 `null`로 통일
- `hasHeader=false`이면 `ColumnDetector`가 헤더 탐색을 생략하고 값 추론만 수행한다
- `alreadyRecorded`는 Task 7에서 메모리 비교로 구현
- 커밋 단계는 사용자 지침에 따라 계획에서 제외
