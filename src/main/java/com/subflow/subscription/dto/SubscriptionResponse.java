package com.subflow.subscription.dto;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
@Builder
public class SubscriptionResponse {

    private Long id;
    private String serviceName;
    private Category category;
    private String categoryDescription;
    private BigDecimal fee;
    private BillingCycle billingCycle;
    private String billingCycleDescription;
    private Integer billingDay;
    private LocalDate startDate;
    private LocalDate nextBillingDate;
    private BigDecimal monthlyExpense;
    private SubscriptionStatus status;
    private String statusDescription;
    private long dDay;
    private LocalDate cancellationScheduledAt;
    private Integer remindDaysBefore;
    private String memo;
    private LocalDateTime createdAt;

    public static SubscriptionResponse from(Subscription subscription) {
        return from(subscription, LocalDate.now());
    }

    public static SubscriptionResponse from(Subscription subscription, LocalDate baseDate) {
        long dDay = ChronoUnit.DAYS.between(baseDate, subscription.getNextBillingDate());

        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .serviceName(subscription.getServiceName())
                .category(subscription.getCategory())
                .categoryDescription(subscription.getCategory().getDescription())
                .fee(subscription.getFee())
                .billingCycle(subscription.getBillingCycle())
                .billingCycleDescription(subscription.getBillingCycle().getDescription())
                .billingDay(subscription.getBillingDay())
                .startDate(subscription.getStartDate())
                .nextBillingDate(subscription.getNextBillingDate())
                .monthlyExpense(subscription.getMonthlyExpense())
                .status(subscription.getStatus())
                .statusDescription(subscription.getStatus().getDescription())
                .dDay(dDay)
                .cancellationScheduledAt(subscription.getCancellationScheduledAt())
                .remindDaysBefore(subscription.getRemindDaysBefore())
                .memo(subscription.getMemo())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
