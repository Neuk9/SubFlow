package com.subflow.subscription.controller;

import com.subflow.global.exception.GlobalExceptionHandler;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.PreviewResponse;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.service.SubscriptionCalendarService;
import com.subflow.subscription.service.SubscriptionPreviewService;
import com.subflow.subscription.service.SubscriptionService;
import com.subflow.subscription.service.SubscriptionStatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubscriptionApiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionStatisticsService statisticsService;

    @Mock
    private SubscriptionCalendarService calendarService;

    @Mock
    private SubscriptionPreviewService previewService;

    @InjectMocks
    private SubscriptionApiController subscriptionApiController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(subscriptionApiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("구독 등록 요청이 유효하면 201 Created와 함께 응답이 반환된다")
    void createSubscriptionReturns201() throws Exception {
        SubscriptionResponse response = SubscriptionResponse.builder()
                .id(1L)
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .categoryDescription("OTT/영상")
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingCycleDescription("매월")
                .billingDay(25)
                .nextBillingDate(LocalDate.of(2026, 3, 25))
                .monthlyExpense(BigDecimal.valueOf(17000))
                .status(SubscriptionStatus.ACTIVE)
                .statusDescription("이용 중")
                .dDay(14)
                .build();

        given(subscriptionService.createSubscription(any(SubscriptionCreateRequest.class))).willReturn(response);

        String json = """
                {
                    "serviceName": "넷플릭스",
                    "category": "OTT",
                    "fee": 17000,
                    "billingCycle": "MONTHLY",
                    "billingDay": 25,
                    "startDate": "2026-03-01",
                    "remindDaysBefore": 3
                }
                """;

        mockMvc.perform(post("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.serviceName").value("넷플릭스"))
                .andExpect(jsonPath("$.nextBillingDate").value("2026-03-25"))
                .andExpect(jsonPath("$.monthlyExpense").value(17000));
    }

    @Test
    @DisplayName("프리뷰 계산 API가 다음 결제일과 월 환산 지출을 올바르게 반환한다")
    void previewCalculationReturnsValues() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate calculatedDate = today.plusDays(10);

        given(previewService.calculatePreview(any(), any(), any(), any()))
                .willReturn(PreviewResponse.builder()
                        .nextBillingDate(calculatedDate)
                        .monthlyExpense(BigDecimal.valueOf(10000))
                        .dDay(10L)
                        .build());

        mockMvc.perform(get("/api/subscriptions/calculate-preview")
                        .param("fee", "120000")
                        .param("billingCycle", "YEARLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyExpense").value(10000))
                .andExpect(jsonPath("$.dDay").value(10));
    }
}
