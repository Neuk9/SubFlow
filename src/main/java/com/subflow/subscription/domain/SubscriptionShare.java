package com.subflow.subscription.domain;

import com.subflow.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "subscription_shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionShare extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(name = "member_name", nullable = false, length = 50)
    private String memberName;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    @Column(name = "member_email", length = 255)
    private String memberEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShareStatus status;

    @Builder
    public SubscriptionShare(Subscription subscription, String memberName, BigDecimal shareAmount,
                             String memberEmail, ShareStatus status) {
        this.subscription = subscription;
        this.memberName = memberName;
        this.shareAmount = shareAmount;
        this.memberEmail = memberEmail;
        this.status = status != null ? status : ShareStatus.ACTIVE;
    }

    public void accept() {
        this.status = ShareStatus.ACTIVE;
    }

    public void decline() {
        this.status = ShareStatus.DECLINED;
    }
}
