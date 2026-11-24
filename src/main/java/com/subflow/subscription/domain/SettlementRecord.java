package com.subflow.subscription.domain;

import com.subflow.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "settlement_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_records_share_period",
                columnNames = {"share_id", "period"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_id", nullable = false)
    private SubscriptionShare share;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false)
    private boolean settled;

    @Column(name = "settled_at")
    private LocalDate settledAt;

    @Column(name = "member_paid", nullable = false)
    private boolean memberPaid;

    @Column(name = "member_paid_at")
    private LocalDate memberPaidAt;

    @Builder
    public SettlementRecord(SubscriptionShare share, String period, boolean settled, LocalDate settledAt,
                            boolean memberPaid, LocalDate memberPaidAt) {
        this.share = share;
        this.period = period;
        this.settled = settled;
        this.settledAt = settledAt;
        this.memberPaid = memberPaid;
        this.memberPaidAt = memberPaidAt;
    }

    public void settle(LocalDate date) {
        this.settled = true;
        this.settledAt = date;
    }

    public void unsettle() {
        this.settled = false;
        this.settledAt = null;
    }

    public void markPaid(LocalDate date) {
        this.memberPaid = true;
        this.memberPaidAt = date;
    }

    public void cancelPaid() {
        this.memberPaid = false;
        this.memberPaidAt = null;
    }
}
