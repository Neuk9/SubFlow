package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.dto.CalendarEventDto;
import com.subflow.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SubscriptionCalendarServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingDateCalculator billingDateCalculator;

    @InjectMocks
    private SubscriptionCalendarService calendarService;

    private Subscription monthly(int billingDay) {
        return Subscription.builder()
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(billingDay)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2026, 12, 25))
                .monthlyExpense(BigDecimal.valueOf(17000))
                .build();
    }

    @Test
    @DisplayName("매월 구독은 해당 월 결제일 하루만 이벤트가 생성된다")
    void monthlyProducesSingleEvent() {
        given(subscriptionRepository.findAll()).willReturn(List.of(monthly(25)));

        List<CalendarEventDto> events = calendarService.getMonthlyEvents(2026, 3);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getBillingDate()).isEqualTo(LocalDate.of(2026, 3, 25));
    }

    @Test
    @DisplayName("31일 결제 구독은 짧은 달에서 말일로 보정된다")
    void monthlyAdjustsToEndOfMonth() {
        given(subscriptionRepository.findAll()).willReturn(List.of(monthly(31)));

        List<CalendarEventDto> events = calendarService.getMonthlyEvents(2026, 2);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getBillingDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("주간 구독은 해당 월의 결제일이 모두 생성된다")
    void weeklyProducesAllEvents() {
        Subscription weekly = Subscription.builder()
                .serviceName("주간")
                .category(Category.OTHER)
                .fee(BigDecimal.valueOf(1000))
                .billingCycle(BillingCycle.WEEKLY)
                .startDate(LocalDate.of(2026, 3, 2))
                .nextBillingDate(LocalDate.of(2026, 3, 2))
                .monthlyExpense(BigDecimal.valueOf(1000))
                .build();
        given(subscriptionRepository.findAll()).willReturn(List.of(weekly));

        List<CalendarEventDto> events = calendarService.getMonthlyEvents(2026, 3);

        assertThat(events).hasSize(5);
        assertThat(events.get(0).getBillingDate()).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    @DisplayName("잘못된 월은 예외가 발생한다")
    void invalidMonthThrows() {
        assertThatThrownBy(() -> calendarService.getMonthlyEvents(2026, 13))
                .isInstanceOf(BusinessException.class);
    }
}
