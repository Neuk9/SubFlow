package com.subflow.subscription.importer.matcher;

import com.subflow.subscription.brand.ServiceBrand;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 결제 한 건을 기존 구독과 비교해 점수를 매긴다.
 */
public class SubscriptionMatcher {

    private static final double BRAND_MATCH = 0.6;
    private static final double NAME_CONTAINS = 0.5;
    private static final double AMOUNT_EXACT = 0.3;
    private static final double AMOUNT_NEAR = 0.2;
    private static final double DAY_NEAR = 0.1;
    private static final double AUTO_THRESHOLD = 0.7;
    private static final double MIN_THRESHOLD = 0.4;
    private static final double EPSILON = 1e-9;

    public Optional<MatchResult> match(LocalDate billingDate, String merchant, BigDecimal amount,
                                       List<Subscription> subscriptions) {
        String normalizedMerchant = MerchantNormalizer.normalize(merchant);
        ServiceBrand merchantBrand = ServiceBrand.resolve(merchant);

        List<ScoredSubscription> scored = subscriptions.stream()
                .filter(subscription -> subscription.getStatus() != SubscriptionStatus.CANCELED)
                .map(subscription -> new ScoredSubscription(
                        subscription,
                        score(billingDate, normalizedMerchant, merchantBrand, amount, subscription)))
                .filter(scoredSubscription -> scoredSubscription.score() >= MIN_THRESHOLD)
                .sorted(Comparator.comparingDouble(ScoredSubscription::score).reversed())
                .toList();

        if (scored.isEmpty()) {
            return Optional.empty();
        }

        ScoredSubscription top = scored.get(0);
        boolean tied = scored.size() > 1 && Math.abs(scored.get(1).score() - top.score()) < EPSILON;
        if (tied) {
            return Optional.of(new MatchResult(null, null, top.score(), MatchType.REVIEW));
        }
        MatchType type = top.score() >= AUTO_THRESHOLD ? MatchType.AUTO : MatchType.REVIEW;
        return Optional.of(new MatchResult(
                top.subscription().getId(),
                top.subscription().getServiceName(),
                top.score(),
                type));
    }

    private double score(LocalDate billingDate, String normalizedMerchant, ServiceBrand merchantBrand,
                         BigDecimal amount, Subscription subscription) {
        double score = 0;

        ServiceBrand subscriptionBrand = ServiceBrand.resolve(subscription.getServiceName());
        if (merchantBrand != null && merchantBrand == subscriptionBrand) {
            score += BRAND_MATCH;
        }

        String normalizedName = MerchantNormalizer.normalize(subscription.getServiceName());
        if (!normalizedMerchant.isEmpty() && !normalizedName.isEmpty()
                && (normalizedMerchant.contains(normalizedName) || normalizedName.contains(normalizedMerchant))) {
            score += NAME_CONTAINS;
        }

        double amountPoints = amountScore(amount, subscription);
        score += amountPoints;
        score += dayScore(billingDate, subscription);

        if (amountPoints == 0) {
            // 금액 신호가 없으면 같은 브랜드라도 자동 확정하지 않는다. (요금제 오매칭 방지)
            return Math.min(score, AUTO_THRESHOLD - 0.01);
        }
        return Math.min(1.0, score);
    }

    private double amountScore(BigDecimal amount, Subscription subscription) {
        if (amount == null) {
            return 0;
        }
        if (sameAmount(amount, subscription.getFee()) || sameAmount(amount, subscription.getMonthlyExpense())) {
            return AMOUNT_EXACT;
        }
        if (withinRatio(amount, subscription.getFee()) || withinRatio(amount, subscription.getMonthlyExpense())) {
            return AMOUNT_NEAR;
        }
        return 0;
    }

    private boolean sameAmount(BigDecimal amount, BigDecimal base) {
        return base != null && amount.compareTo(base) == 0;
    }

    private boolean withinRatio(BigDecimal amount, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return false;
        }
        BigDecimal tolerance = base.abs().multiply(new BigDecimal("0.05"));
        return amount.subtract(base).abs().compareTo(tolerance) <= 0;
    }

    private double dayScore(LocalDate billingDate, Subscription subscription) {
        Integer billingDay = subscription.getBillingDay();
        if (billingDay == null) {
            return 0;
        }
        int difference = Math.abs(billingDate.getDayOfMonth() - billingDay);
        int circular = Math.min(difference, 31 - difference);
        return circular <= 2 ? DAY_NEAR : 0;
    }

    private record ScoredSubscription(Subscription subscription, double score) {
    }
}
