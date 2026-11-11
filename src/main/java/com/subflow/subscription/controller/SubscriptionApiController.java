package com.subflow.subscription.controller;

import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.calculator.ExpenseCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.SubscriptionCancelRequest;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.dto.SubscriptionSummaryResponse;
import com.subflow.subscription.dto.SubscriptionUpdateRequest;
import com.subflow.subscription.service.SubscriptionService;
import com.subflow.subscription.service.SubscriptionStatisticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionApiController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionStatisticsService statisticsService;
    private final BillingDateCalculator billingDateCalculator;
    private final ExpenseCalculator expenseCalculator;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody SubscriptionCreateRequest request) {
        SubscriptionResponse response = subscriptionService.createSubscription(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptions(
            @RequestParam(required = false) SubscriptionStatus status) {
        return ResponseEntity.ok(subscriptionService.getSubscriptions(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> getSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getSubscription(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> updateSubscription(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionUpdateRequest request) {
        return ResponseEntity.ok(subscriptionService.updateSubscription(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscription(@PathVariable Long id) {
        subscriptionService.deleteSubscription(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @PathVariable Long id,
            @RequestBody(required = false) SubscriptionCancelRequest request) {
        return ResponseEntity.ok(subscriptionService.cancelSubscription(id, request));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<SubscriptionResponse> pauseSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.pauseSubscription(id));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<SubscriptionResponse> resumeSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.resumeSubscription(id));
    }

    @GetMapping("/summary")
    public ResponseEntity<SubscriptionSummaryResponse> getSummary() {
        return ResponseEntity.ok(statisticsService.getSummary());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<SubscriptionResponse>> getUpcoming(
            @RequestParam(defaultValue = "7") int daysAhead) {
        return ResponseEntity.ok(statisticsService.getUpcomingSubscriptions(daysAhead));
    }

    /**
     * [시연 포인트용 프리뷰 API]
     * 구독 등록 폼에서 금액/주기/시작일 입력 시 실시간으로 계산되는 다음 결제일과 월 환산 지출을 반환합니다.
     */
    @GetMapping("/calculate-preview")
    public ResponseEntity<Map<String, Object>> calculatePreview(
            @RequestParam BigDecimal fee,
            @RequestParam BillingCycle billingCycle,
            @RequestParam(required = false) Integer billingDay,
            @RequestParam(required = false) LocalDate startDate) {

        LocalDate today = LocalDate.now();
        LocalDate start = (startDate != null) ? startDate : today;
        Integer day = (billingDay != null) ? billingDay : start.getDayOfMonth();

        LocalDate nextBillingDate = billingDateCalculator.calculateNextBillingDate(billingCycle, start, day, today);
        BigDecimal monthlyExpense = expenseCalculator.calculateMonthlyExpense(fee, billingCycle);
        long dDay = expenseCalculator.calculateDaysUntilNextBilling(nextBillingDate, today);

        return ResponseEntity.ok(Map.of(
                "nextBillingDate", nextBillingDate.toString(),
                "monthlyExpense", monthlyExpense,
                "dDay", dDay
        ));
    }
}
