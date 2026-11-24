package com.subflow.subscription.importer.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.BillingRecord;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.importer.dto.ImportConfirmRequest;
import com.subflow.subscription.importer.dto.ImportConfirmResponse;
import com.subflow.subscription.importer.dto.ImportRecordRequest;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.service.SubscriptionCommandService;
import com.subflow.subscription.service.SubscriptionFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CsvImportServiceConfirmTest {

    private static final String OWNER = "me@example.com";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 25);
    private static final BigDecimal AMOUNT = new BigDecimal("17000");

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingRecordRepository billingRecordRepository;

    @Mock
    private SubscriptionFinder subscriptionFinder;

    @Mock
    private SubscriptionCommandService subscriptionCommandService;

    @InjectMocks
    private CsvImportService service;

    @Test
    @DisplayName("선택한 결제를 BillingRecord로 저장한다")
    void recordsSelectedPayments() {
        givenOwner(subscription());
        given(billingRecordRepository.existsBySubscriptionIdAndBillingDateAndAmount(1L, DATE, AMOUNT))
                .willReturn(false);

        ImportConfirmResponse response = service.confirm(
                new ImportConfirmRequest(List.of(new ImportRecordRequest(1L, DATE, AMOUNT, "넷플릭스")), List.of()));

        assertThat(response.recordedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isZero();
        then(billingRecordRepository).should().save(any(BillingRecord.class));
    }

    @Test
    @DisplayName("이미 기록된 결제는 건너뛴다")
    void skipsDuplicates() {
        givenOwner(subscription());
        given(billingRecordRepository.existsBySubscriptionIdAndBillingDateAndAmount(1L, DATE, AMOUNT))
                .willReturn(true);

        ImportConfirmResponse response = service.confirm(
                new ImportConfirmRequest(List.of(new ImportRecordRequest(1L, DATE, AMOUNT, "넷플릭스")), List.of()));

        assertThat(response.recordedCount()).isZero();
        assertThat(response.skippedCount()).isEqualTo(1);
        then(billingRecordRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("신규 후보를 구독으로 등록한다")
    void createsNewSubscriptions() {
        SubscriptionCreateRequest createRequest = SubscriptionCreateRequest.builder()
                .serviceName("Spotify")
                .category(Category.MUSIC)
                .fee(new BigDecimal("10900"))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(15)
                .startDate(LocalDate.of(2026, 8, 15))
                .build();

        ImportConfirmResponse response = service.confirm(
                new ImportConfirmRequest(List.of(), List.of(createRequest)));

        assertThat(response.createdSubscriptions()).isEqualTo(1);
        then(subscriptionCommandService).should().createSubscription(createRequest);
    }

    @Test
    @DisplayName("가져온 기록의 memo에 가맹점을 남긴다")
    void storesMerchantInMemo() {
        givenOwner(subscription());
        given(billingRecordRepository.existsBySubscriptionIdAndBillingDateAndAmount(1L, DATE, AMOUNT))
                .willReturn(false);

        service.confirm(new ImportConfirmRequest(
                List.of(new ImportRecordRequest(1L, DATE, AMOUNT, "넷플릭스")), List.of()));

        ArgumentCaptor<BillingRecord> captor = ArgumentCaptor.forClass(BillingRecord.class);
        then(billingRecordRepository).should().save(captor.capture());
        assertThat(captor.getValue().getMemo()).isEqualTo("가져오기: 넷플릭스");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("필수 값이 빠진 확정 요청은 INVALID_IMPORT_ROW를 던진다")
    void rejectsInvalidRequest() {
        assertThatThrownBy(() -> service.confirm(
                new ImportConfirmRequest(List.of(new ImportRecordRequest(null, DATE, AMOUNT, "넷플릭스")), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMPORT_ROW);
    }

    @Test
    @DisplayName("소유한 구독 목록을 한 번만 조회한다")
    void loadsOwnedSubscriptionsOnce() {
        givenOwner(subscription());
        given(billingRecordRepository.existsBySubscriptionIdAndBillingDateAndAmount(1L, DATE, AMOUNT))
                .willReturn(false);

        service.confirm(new ImportConfirmRequest(
                List.of(new ImportRecordRequest(1L, DATE, AMOUNT, "넷플릭스"),
                        new ImportRecordRequest(1L, DATE.plusMonths(1), AMOUNT, "넷플릭스")), List.of()));

        then(subscriptionRepository).should(times(1))
                .findAllByOwnerEmailOrderByNextBillingDateAsc(OWNER);
        then(subscriptionFinder).should(never()).findOwnedById(any());
    }

    @Test
    @DisplayName("소유하지 않은 구독 ID면 SUBSCRIPTION_NOT_FOUND를 던진다")
    void rejectsUnownedSubscription() {
        givenOwner(subscription());

        assertThatThrownBy(() -> service.confirm(new ImportConfirmRequest(
                List.of(new ImportRecordRequest(99L, DATE, AMOUNT, "넷플릭스")), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    private void givenOwner(Subscription subscription) {
        given(subscriptionFinder.currentOwnerEmail()).willReturn(OWNER);
        given(subscriptionRepository.findAllByOwnerEmailOrderByNextBillingDateAsc(OWNER))
                .willReturn(List.of(subscription));
    }

    private Subscription subscription() {
        Subscription subscription = Subscription.builder()
                .serviceName("넷플릭스 프리미엄")
                .category(Category.OTT)
                .fee(new BigDecimal("17000"))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2026, 12, 25))
                .monthlyExpense(new BigDecimal("17000"))
                .build();
        ReflectionTestUtils.setField(subscription, "id", 1L);
        return subscription;
    }
}
