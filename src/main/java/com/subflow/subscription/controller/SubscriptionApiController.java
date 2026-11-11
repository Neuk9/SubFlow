package com.subflow.subscription.controller;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.CalendarEventDto;
import com.subflow.subscription.dto.PreviewResponse;
import com.subflow.subscription.dto.SubscriptionCancelRequest;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.dto.SubscriptionSummaryResponse;
import com.subflow.subscription.dto.SubscriptionUpdateRequest;
import com.subflow.subscription.service.SubscriptionCalendarService;
import com.subflow.subscription.service.SubscriptionPreviewService;
import com.subflow.subscription.service.SubscriptionCommandService;
import com.subflow.subscription.service.SubscriptionQueryService;
import com.subflow.subscription.service.SubscriptionStatisticsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionApiController {

    private final SubscriptionCommandService subscriptionCommandService;
    private final SubscriptionQueryService subscriptionQueryService;
    private final SubscriptionStatisticsService statisticsService;
    private final SubscriptionCalendarService calendarService;
    private final SubscriptionPreviewService previewService;
    private final Clock clock;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody SubscriptionCreateRequest request) {
        SubscriptionResponse response = subscriptionCommandService.createSubscription(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptions(
            @RequestParam(required = false) SubscriptionStatus status) {
        return ResponseEntity.ok(subscriptionQueryService.getSubscriptions(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> getSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionQueryService.getSubscription(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> updateSubscription(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionUpdateRequest request) {
        return ResponseEntity.ok(subscriptionCommandService.updateSubscription(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscription(@PathVariable Long id) {
        subscriptionCommandService.deleteSubscription(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @PathVariable Long id,
            @RequestBody(required = false) SubscriptionCancelRequest request) {
        return ResponseEntity.ok(subscriptionCommandService.cancelSubscription(id, request));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<SubscriptionResponse> pauseSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionCommandService.pauseSubscription(id));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<SubscriptionResponse> resumeSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionCommandService.resumeSubscription(id));
    }

    @GetMapping("/summary")
    public ResponseEntity<SubscriptionSummaryResponse> getSummary() {
        return ResponseEntity.ok(statisticsService.getSummary());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<SubscriptionResponse>> getUpcoming(
            @RequestParam(defaultValue = "7")
            @Min(value = 0, message = "조회 일수는 0일 이상이어야 합니다.")
            @Max(value = 365, message = "조회 일수는 365일 이하여야 합니다.")
            int daysAhead) {
        return ResponseEntity.ok(statisticsService.getUpcomingSubscriptions(daysAhead));
    }

    @GetMapping("/calendar")
    public ResponseEntity<List<CalendarEventDto>> getCalendarEvents(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now(clock);
        int targetYear = (year != null) ? year : now.getYear();
        int targetMonth = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(calendarService.getMonthlyEvents(targetYear, targetMonth));
    }

    /**
     * 구독 등록 폼에서 금액/주기/시작일 입력 시 다음 결제일과 월 환산 지출을 미리 계산합니다.
     */
    @GetMapping("/calculate-preview")
    public ResponseEntity<PreviewResponse> calculatePreview(
            @RequestParam BigDecimal fee,
            @RequestParam BillingCycle billingCycle,
            @RequestParam(required = false) Integer billingDay,
            @RequestParam(required = false) LocalDate startDate) {
        return ResponseEntity.ok(previewService.calculatePreview(fee, billingCycle, billingDay, startDate));
    }
}
