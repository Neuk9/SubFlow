package com.subflow.subscription.domain;

import com.subflow.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "billing_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(nullable = false)
    private LocalDate billingDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingStatus status;

    @Column(length = 255)
    private String memo;

    @Builder
    public BillingRecord(Subscription subscription, LocalDate billingDate, BigDecimal amount, BillingStatus status, String memo) {
        this.subscription = subscription;
        this.billingDate = billingDate;
        this.amount = amount;
        this.status = status != null ? status : BillingStatus.PAID;
        this.memo = memo;
    }
}
