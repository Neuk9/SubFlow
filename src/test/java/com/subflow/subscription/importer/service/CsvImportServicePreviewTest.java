package com.subflow.subscription.importer.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.BillingRecord;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.importer.matcher.ImportCandidate;
import com.subflow.subscription.importer.matcher.MatchType;
import com.subflow.subscription.importer.dto.ImportPreviewResponse;
import com.subflow.subscription.importer.dto.SkippedRow;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.service.SubscriptionFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CsvImportServicePreviewTest {

    private static final String OWNER = "me@example.com";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingRecordRepository billingRecordRepository;

    @Mock
    private SubscriptionFinder subscriptionFinder;

    @InjectMocks
    private CsvImportService service;

    @Test
    @DisplayName("미리보기에서 매칭 행과 신규 후보를 함께 반환한다")
    void buildsPreview() throws IOException {
        givenOwner(List.of(subscription(1L, "넷플릭스 프리미엄", "17000", 25)), List.of());

        ImportPreviewResponse response = service.preview(
                csvFromResource("standard-utf8.csv"), null, null, null, true);

        assertThat(response.needsMapping()).isFalse();
        assertThat(response.rows())
                .filteredOn(row -> row.merchant().equals("넷플릭스"))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.matchType()).isEqualTo(MatchType.AUTO));
        assertThat(response.candidates())
                .extracting(ImportCandidate::suggestedName)
                .contains("Spotify");
        assertThat(response.oneOffCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 기록된 결제는 alreadyRecorded로 표시한다")
    void marksAlreadyRecorded() throws IOException {
        Subscription netflix = subscription(1L, "넷플릭스 프리미엄", "17000", 25);
        BillingRecord record = BillingRecord.builder()
                .subscription(netflix)
                .billingDate(LocalDate.of(2026, 9, 25))
                .amount(new BigDecimal("17000"))
                .build();
        givenOwner(List.of(netflix), List.of(record));

        ImportPreviewResponse response = service.preview(
                csvFromResource("standard-utf8.csv"), null, null, null, true);

        assertThat(response.rows())
                .filteredOn(row -> row.merchant().equals("넷플릭스")
                        && row.billingDate().equals(LocalDate.of(2026, 9, 25)))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.alreadyRecorded()).isTrue());
    }

    @Test
    @DisplayName("5,000건을 넘으면 IMPORT_LIMIT_EXCEEDED를 던진다")
    void rejectsTooManyRows() {
        MockMultipartFile file = csvWithRows(5001);

        assertThatThrownBy(() -> service.preview(file, null, null, null, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMPORT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("4,999건은 통과한다")
    void allowsJustUnderLimit() {
        givenOwner(List.of(), List.of());

        ImportPreviewResponse response = service.preview(csvWithRows(4999), null, null, null, true);

        assertThat(response.rowCount()).isEqualTo(4999);
    }

    @Test
    @DisplayName("0 이하 금액 행은 skipped로 분류한다")
    void skipsNonPositiveAmount() {
        givenOwner(List.of(), List.of());

        ImportPreviewResponse response = service.preview(
                csv("거래일,이용처,승인금액\n2026-11-05,급여,0\n"), null, null, null, true);

        assertThat(response.skipped()).extracting(SkippedRow::rowNumber).contains(2);
    }

    @Test
    @DisplayName("컬럼을 못 찾으면 가장 넓은 행을 헤더 후보로 반환한다")
    void needsMappingUsesWidestRowAsHeaders() {
        MockMultipartFile file = csv(
                "신한카드 이용대금 명세서\n알수없음,알수없음,알수없음\n2026.08.15,스포티파이,\"10,900\"\n");

        ImportPreviewResponse response = service.preview(file, null, null, null, true);

        assertThat(response.needsMapping()).isTrue();
        assertThat(response.headers()).hasSize(3);
    }

    @Test
    @DisplayName("해지 예정 구독도 결제 매칭 대상에 포함한다")
    void matchesExpiringSubscription() throws IOException {
        Subscription netflix = subscription(1L, "넷플릭스 프리미엄", "17000", 25);
        netflix.scheduleCancellation(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));
        givenOwner(List.of(netflix), List.of());

        ImportPreviewResponse response = service.preview(
                csvFromResource("standard-utf8.csv"), null, null, null, true);

        assertThat(response.rows())
                .filteredOn(row -> row.merchant().equals("넷플릭스"))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.matchType()).isEqualTo(MatchType.AUTO));
    }

    @Test
    @DisplayName("중복 행이 많아도 원본 행 수가 5,000을 넘으면 거부한다")
    void limitUsesRawRowCount() {
        StringBuilder builder = new StringBuilder("거래일,이용처,승인금액\n");
        for (int i = 0; i < 5001; i++) {
            builder.append("2026-01-01,SAME,1000\n");
        }

        assertThatThrownBy(() -> service.preview(csv(builder.toString()), null, null, null, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMPORT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("깨진 CSV는 UNSUPPORTED_CSV를 던진다")
    void rejectsMalformedCsv() {
        MockMultipartFile file = csv("거래일,이용처,승인금액\n2026-01-01,\"닫히지 않은 값,1000\n");

        assertThatThrownBy(() -> service.preview(file, null, null, null, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CSV);
    }

    @Test
    @DisplayName("컬럼 매핑이 중복되면 INVALID_COLUMN_MAPPING을 던진다")
    void rejectsDuplicateMapping() {
        assertThatThrownBy(() -> service.preview(csv("a,b,c\n1,2,3\n"), 0, 0, 1, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_COLUMN_MAPPING);
    }

    @Test
    @DisplayName("컬럼 매핑 인덱스가 범위를 벗어나면 INVALID_COLUMN_MAPPING을 던진다")
    void rejectsOutOfRangeMapping() {
        assertThatThrownBy(() -> service.preview(csv("a,b,c\n1,2,3\n"), 0, 1, 9, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_COLUMN_MAPPING);
    }

    @Test
    @DisplayName("금액은 소수점 2자리로 정규화한다")
    void normalizesAmountScale() {
        givenOwner(List.of(), List.of());

        ImportPreviewResponse response = service.preview(
                csv("거래일,이용처,승인금액\n2026-01-01,STORE,10900.999\n"), null, null, null, true);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).amount()).isEqualByComparingTo("10901.00");
    }

    @Test
    @DisplayName("빈 파일은 EMPTY_IMPORT_FILE을 던진다")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.preview(csv(""), null, null, null, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMPTY_IMPORT_FILE);
    }

    private void givenOwner(List<Subscription> subscriptions, List<BillingRecord> records) {
        given(subscriptionFinder.currentOwnerEmail()).willReturn(OWNER);
        given(subscriptionRepository.findAllByOwnerEmailOrderByNextBillingDateAsc(OWNER))
                .willReturn(subscriptions);
        given(billingRecordRepository.findAllBySubscriptionOwnerEmail(OWNER)).willReturn(records);
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile csvFromResource(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/csv/" + name)) {
            assertThat(in).as("테스트 리소스 %s", name).isNotNull();
            return new MockMultipartFile("file", name, "text/csv", in.readAllBytes());
        }
    }

    private MockMultipartFile csvWithRows(int count) {
        StringBuilder builder = new StringBuilder("거래일,이용처,승인금액\n");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            lines.add("2026-01-01,STORE %05d,1000".formatted(i));
        }
        builder.append(String.join("\n", lines)).append('\n');
        return csv(builder.toString());
    }

    private Subscription subscription(Long id, String serviceName, String fee, int billingDay) {
        Subscription subscription = Subscription.builder()
                .serviceName(serviceName)
                .category(Category.OTT)
                .fee(new BigDecimal(fee))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(billingDay)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2026, 12, billingDay))
                .monthlyExpense(new BigDecimal(fee))
                .build();
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }
}
