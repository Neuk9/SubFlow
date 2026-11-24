package com.subflow.subscription.importer.matcher;

import com.subflow.subscription.brand.ServiceBrand;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.importer.parser.ParsedPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 매칭되지 않은 결제를 가맹점별로 묶어 반복 결제(신규 구독 후보)를 찾는다.
 */
public class CandidateFinder {

    private static final int MAX_INTERVAL_DEVIATION_DAYS = 3;
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.05");

    public List<ImportCandidate> find(List<ParsedPayment> unmatched, List<Subscription> existingSubscriptions) {
        Map<String, List<ParsedPayment>> groups = unmatched.stream()
                .filter(payment -> !MerchantNormalizer.normalize(payment.merchant()).isEmpty())
                .collect(Collectors.groupingBy(
                        payment -> MerchantNormalizer.normalize(payment.merchant()),
                        TreeMap::new,
                        Collectors.toList()));

        List<ImportCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<ParsedPayment>> entry : groups.entrySet()) {
            List<ParsedPayment> payments = entry.getValue().stream()
                    .sorted(Comparator.comparing(ParsedPayment::billingDate))
                    .toList();

            if (payments.size() < 2) {
                continue;
            }

            List<Long> intervals = intervals(payments);
            long medianInterval = median(intervals);
            if (!allWithin(intervals, medianInterval, MAX_INTERVAL_DEVIATION_DAYS)) {
                continue;
            }

            List<BigDecimal> amounts = payments.stream().map(ParsedPayment::amount).toList();
            BigDecimal medianAmount = medianAmount(amounts);
            if (!allWithinRatio(amounts, medianAmount)) {
                continue;
            }

            BillingCycle cycle = cycleOf(medianInterval);
            if (cycle == null) {
                continue;
            }

            ServiceBrand brand = ServiceBrand.resolve(payments.get(0).merchant());
            if (isExisting(brand, entry.getKey(), medianAmount, existingSubscriptions)) {
                continue;
            }

            ParsedPayment latest = payments.get(payments.size() - 1);
            candidates.add(new ImportCandidate(
                    payments.get(0).merchant(),
                    brand != null ? brand.getDisplayName() : cleanMerchant(payments.get(0).merchant()),
                    CategoryGuesser.guess(brand, payments.get(0).merchant()),
                    cycle,
                    medianAmount,
                    latest.billingDate().getDayOfMonth(),
                    payments.get(0).billingDate(),
                    payments.size(),
                    payments.stream().map(ParsedPayment::rowNumber).toList(),
                    brand != null ? brand.getSlug() : null,
                    brand != null && brand.isImage()));
        }
        return candidates;
    }

    private List<Long> intervals(List<ParsedPayment> payments) {
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < payments.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(
                    payments.get(i - 1).billingDate(), payments.get(i).billingDate()));
        }
        return intervals;
    }

    private long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private BigDecimal medianAmount(List<BigDecimal> amounts) {
        List<BigDecimal> sorted = amounts.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private boolean allWithin(List<Long> values, long target, long tolerance) {
        return values.stream().allMatch(value -> Math.abs(value - target) <= tolerance);
    }

    private boolean allWithinRatio(List<BigDecimal> amounts, BigDecimal median) {
        BigDecimal tolerance = median.multiply(AMOUNT_TOLERANCE);
        return amounts.stream().allMatch(amount -> amount.subtract(median).abs().compareTo(tolerance) <= 0);
    }

    private BillingCycle cycleOf(long days) {
        if (days >= 6 && days <= 8) {
            return BillingCycle.WEEKLY;
        }
        if (days >= 26 && days <= 35) {
            return BillingCycle.MONTHLY;
        }
        if (days >= 350 && days <= 380) {
            return BillingCycle.YEARLY;
        }
        return null;
    }

    private boolean isExisting(ServiceBrand brand, String normalizedMerchant, BigDecimal amount,
                               List<Subscription> existingSubscriptions) {
        for (Subscription subscription : existingSubscriptions) {
            if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
                continue;
            }
            if (brand != null && brand == ServiceBrand.resolve(subscription.getServiceName())) {
                return true;
            }
            String normalizedName = MerchantNormalizer.normalize(subscription.getServiceName());
            if (!normalizedName.isEmpty() && normalizedName.equals(normalizedMerchant)
                    && subscription.getFee() != null
                    && subscription.getFee().subtract(amount).abs()
                    .compareTo(subscription.getFee().multiply(AMOUNT_TOLERANCE)) <= 0) {
                return true;
            }
        }
        return false;
    }

    private String cleanMerchant(String merchant) {
        return merchant
                .replaceAll("\\(주\\)|주식회사|㈜", "")
                .replaceAll("(?i)\\b(inc|ltd|corp|ab|co)\\b\\.?", "")
                .trim();
    }
}
