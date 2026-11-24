package com.subflow.subscription.importer.matcher;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.importer.parser.ParsedPayment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateFinderTest {

    private final CandidateFinder finder = new CandidateFinder();

    @Test
    @DisplayName("매월 반복되는 미등록 가맹점을 월간 후보로 제안한다")
    void findsMonthlyCandidate() {
        List<ParsedPayment> rows = List.of(
                payment(2, "2026-08-15", "SPOTIFY AB", "10900"),
                payment(3, "2026-09-15", "SPOTIFY AB", "10900"),
                payment(4, "2026-10-15", "SPOTIFY AB", "10900"));

        List<ImportCandidate> result = finder.find(rows, List.of());

        assertThat(result).hasSize(1);
        ImportCandidate candidate = result.get(0);
        assertThat(candidate.suggestedName()).isEqualTo("Spotify");
        assertThat(candidate.suggestedCategory()).isEqualTo(Category.MUSIC);
        assertThat(candidate.suggestedBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(candidate.suggestedBillingDay()).isEqualTo(15);
        assertThat(candidate.suggestedFee()).isEqualByComparingTo("10900");
        assertThat(candidate.occurrences()).isEqualTo(3);
        assertThat(candidate.rowNumbers()).containsExactly(2, 3, 4);
    }

    @Test
    @DisplayName("1회만 등장하면 후보로 제안하지 않는다")
    void ignoresOneOff() {
        List<ParsedPayment> rows = List.of(payment(2, "2026-11-20", "스타벅스", "6500"));

        assertThat(finder.find(rows, List.of())).isEmpty();
    }

    @Test
    @DisplayName("금액 편차가 크면 후보로 제안하지 않는다")
    void ignoresVaryingAmounts() {
        List<ParsedPayment> rows = List.of(
                payment(2, "2026-08-15", "SPOTIFY AB", "10900"),
                payment(3, "2026-09-15", "SPOTIFY AB", "20000"),
                payment(4, "2026-10-15", "SPOTIFY AB", "10900"));

        assertThat(finder.find(rows, List.of())).isEmpty();
    }

    @Test
    @DisplayName("이미 등록된 구독과 같은 브랜드면 후보에서 제외한다")
    void excludesExistingSubscription() {
        List<ParsedPayment> rows = List.of(
                payment(2, "2026-08-15", "NETFLIX.COM", "17000"),
                payment(3, "2026-09-15", "NETFLIX.COM", "17000"));
        Subscription existing = subscription(1L, "넷플릭스 프리미엄", "17000");

        assertThat(finder.find(rows, List.of(existing))).isEmpty();
    }

    @Test
    @DisplayName("주기가 일정하지 않으면 후보로 제안하지 않는다")
    void ignoresIrregularInterval() {
        List<ParsedPayment> rows = List.of(
                payment(2, "2026-08-01", "SPOTIFY AB", "10900"),
                payment(3, "2026-08-20", "SPOTIFY AB", "10900"),
                payment(4, "2026-11-10", "SPOTIFY AB", "10900"));

        assertThat(finder.find(rows, List.of())).isEmpty();
    }

    private ParsedPayment payment(int rowNumber, String date, String merchant, String amount) {
        return new ParsedPayment(rowNumber, LocalDate.parse(date), merchant, new BigDecimal(amount));
    }

    private Subscription subscription(Long id, String serviceName, String fee) {
        Subscription subscription = Subscription.builder()
                .serviceName(serviceName)
                .category(Category.OTT)
                .fee(new BigDecimal(fee))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(15)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2026, 12, 15))
                .monthlyExpense(new BigDecimal(fee))
                .build();
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }
}
