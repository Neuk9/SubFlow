package com.subflow.subscription.domain;

import com.subflow.global.common.BaseTimeEntity;
import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(name = "billing_day")
    private Integer billingDay;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate nextBillingDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyExpense;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "cancellation_scheduled_at")
    private LocalDate cancellationScheduledAt;

    @Column(name = "remind_days_before", nullable = false)
    private Integer remindDaysBefore;

    @Column(name = "owner_email", nullable = false, length = 255)
    private String ownerEmail;

    @Column(length = 2000)
    private String memo;

    @Builder
    public Subscription(String serviceName, Category category, BigDecimal fee,
                        BillingCycle billingCycle, Integer billingDay, LocalDate startDate,
                        LocalDate nextBillingDate, BigDecimal monthlyExpense,
                        Integer remindDaysBefore, String memo, String ownerEmail) {
        this.serviceName = serviceName;
        this.category = category != null ? category : Category.OTHER;
        this.fee = fee;
        this.billingCycle = billingCycle;
        this.billingDay = billingDay;
        this.startDate = startDate;
        this.nextBillingDate = nextBillingDate;
        this.monthlyExpense = monthlyExpense;
        this.status = SubscriptionStatus.ACTIVE;
        this.remindDaysBefore = remindDaysBefore != null ? remindDaysBefore : 3;
        this.memo = memo;
        this.ownerEmail = ownerEmail;
    }

    public void update(String serviceName, Category category, BigDecimal fee,
                       BillingCycle billingCycle, Integer billingDay, LocalDate nextBillingDate,
                       BigDecimal monthlyExpense, Integer remindDaysBefore, String memo) {
        this.serviceName = serviceName;
        this.category = category != null ? category : this.category;
        this.fee = fee;
        this.billingCycle = billingCycle;
        this.billingDay = billingDay;
        this.nextBillingDate = nextBillingDate;
        this.monthlyExpense = monthlyExpense;
        this.remindDaysBefore = remindDaysBefore != null ? remindDaysBefore : this.remindDaysBefore;
        this.memo = memo;
    }

    public void renewBilling(LocalDate newNextBillingDate) {
        if (this.status == SubscriptionStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED);
        }
        this.nextBillingDate = newNextBillingDate;
    }

    public void scheduleCancellation(LocalDate scheduledDate) {
        requireNotCanceled("해지 예약");
        if (this.status == SubscriptionStatus.EXPIRING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 해지가 예약된 구독입니다.");
        }
        if (scheduledDate == null || scheduledDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "해지 예정일은 오늘 이후여야 합니다.");
        }
        this.cancellationScheduledAt = scheduledDate;
        this.status = SubscriptionStatus.EXPIRING;
    }

    public void cancelImmediately() {
        requireNotCanceled("해지");
        this.status = SubscriptionStatus.CANCELED;
        this.cancellationScheduledAt = LocalDate.now();
    }

    public void pause() {
        requireNotCanceled("일시 정지");
        this.status = SubscriptionStatus.PAUSED;
    }

    public void resume(LocalDate calculatedNextBillingDate) {
        requireNotCanceled("재개");
        this.status = SubscriptionStatus.ACTIVE;
        this.nextBillingDate = calculatedNextBillingDate;
    }

    private void requireNotCanceled(String action) {
        if (this.status == SubscriptionStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED,
                    String.format("해지된 구독은 %s할 수 없습니다.", action));
        }
    }
}
