package com.subflow.subscription.service;

import com.subflow.subscription.catalog.ServiceCatalog;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.InsightResponse;
import com.subflow.subscription.dto.ServiceCatalogResponse;
import com.subflow.subscription.dto.ServicePlanResponse;
import com.subflow.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Map.entry;

/**
 * 현재 구독을 카탈로그와 비교해 절약 제안·중복 구독을 계산한다. 저장은 하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsightService {

    private static final Map<String, String> OVERLAP_GROUPS = Map.ofEntries(
            entry("spotify", "음악 스트리밍"),
            entry("apple-music", "음악 스트리밍"),
            entry("youtube-music", "음악 스트리밍"),
            entry("melon", "음악 스트리밍"),
            entry("genie", "음악 스트리밍"),
            entry("flo", "음악 스트리밍"),
            entry("bugs", "음악 스트리밍"),
            entry("icloud", "클라우드 저장소"),
            entry("google-one", "클라우드 저장소"),
            entry("dropbox", "클라우드 저장소"),
            entry("naver-plus", "클라우드 저장소"),
            entry("naver-mybox", "클라우드 저장소"),
            entry("chatgpt", "AI 어시스턴트"),
            entry("claude", "AI 어시스턴트"),
            entry("perplexity", "AI 어시스턴트"),
            entry("gemini", "AI 어시스턴트"),
            entry("microsoft-copilot", "AI 어시스턴트"),
            entry("cursor", "AI 어시스턴트"),
            entry("github-copilot", "AI 어시스턴트")
    );

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionFinder subscriptionFinder;

    public InsightResponse getInsights() {
        String ownerEmail = subscriptionFinder.currentOwnerEmail();
        List<Subscription> subscriptions = subscriptionRepository
                .findAllByOwnerEmailOrderByNextBillingDateAsc(ownerEmail).stream()
                .filter(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE)
                .toList();

        List<InsightResponse.AnnualSaving> annualSavings = new ArrayList<>();
        List<InsightResponse.StudentSaving> studentSavings = new ArrayList<>();
        Map<String, Set<String>> groupServiceIds = new LinkedHashMap<>();
        Map<String, BigDecimal> groupTotals = new LinkedHashMap<>();
        Map<String, Set<String>> groupServiceNames = new LinkedHashMap<>();

        for (Subscription subscription : subscriptions) {
            Optional<ServiceCatalogResponse> matched = findService(subscription.getServiceName());
            if (matched.isEmpty()) {
                continue;
            }
            ServiceCatalogResponse service = matched.get();

            annualSaving(subscription, service).ifPresent(annualSavings::add);
            studentSaving(subscription, service).ifPresent(studentSavings::add);
            collectDuplicateGroup(service, subscription, groupServiceIds, groupTotals, groupServiceNames);
        }

        List<InsightResponse.DuplicateGroup> duplicateGroups = groupServiceIds.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(entry -> new InsightResponse.DuplicateGroup(
                        entry.getKey(),
                        List.copyOf(groupServiceNames.get(entry.getKey())),
                        groupTotals.get(entry.getKey())))
                .sorted(Comparator.comparing(InsightResponse.DuplicateGroup::monthlyTotal).reversed())
                .toList();

        return new InsightResponse(
                annualSavings,
                sum(annualSavings.stream().map(InsightResponse.AnnualSaving::saving).toList()),
                studentSavings,
                sum(studentSavings.stream().map(InsightResponse.StudentSaving::saving).toList()),
                duplicateGroups);
    }

    private Optional<InsightResponse.AnnualSaving> annualSaving(Subscription subscription,
                                                                ServiceCatalogResponse service) {
        Optional<ServicePlanResponse> yearly = service.getPlans().stream()
                .filter(plan -> plan.getBillingCycle() == BillingCycle.YEARLY)
                .findFirst();
        if (yearly.isEmpty() || subscription.getMonthlyExpense() == null) {
            return Optional.empty();
        }
        BigDecimal yearlyTotal = subscription.getMonthlyExpense().multiply(BigDecimal.valueOf(12));
        BigDecimal saving = yearlyTotal.subtract(yearly.get().getFee());
        if (saving.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new InsightResponse.AnnualSaving(
                service.getName(),
                yearly.get().getDisplayName(),
                subscription.getMonthlyExpense(),
                yearly.get().getFee(),
                saving));
    }

    private Optional<InsightResponse.StudentSaving> studentSaving(Subscription subscription,
                                                                  ServiceCatalogResponse service) {
        Optional<ServicePlanResponse> student = service.getPlans().stream()
                .filter(ServicePlanResponse::isStudent)
                .findFirst();
        if (student.isEmpty() || subscription.getMonthlyExpense() == null) {
            return Optional.empty();
        }
        BigDecimal saving = subscription.getMonthlyExpense().subtract(student.get().getFee());
        if (saving.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new InsightResponse.StudentSaving(
                service.getName(),
                student.get().getDisplayName(),
                subscription.getMonthlyExpense(),
                student.get().getFee(),
                saving));
    }

    private void collectDuplicateGroup(ServiceCatalogResponse service, Subscription subscription,
                                       Map<String, Set<String>> groupServiceIds,
                                       Map<String, BigDecimal> groupTotals,
                                       Map<String, Set<String>> groupServiceNames) {
        String group = OVERLAP_GROUPS.get(service.getId());
        if (group == null) {
            return;
        }
        groupServiceIds.computeIfAbsent(group, key -> new LinkedHashSet<>()).add(service.getId());
        groupServiceNames.computeIfAbsent(group, key -> new LinkedHashSet<>()).add(subscription.getServiceName());
        groupTotals.merge(group, subscription.getMonthlyExpense(), BigDecimal::add);
    }

    private Optional<ServiceCatalogResponse> findService(String serviceName) {
        String normalized = normalize(serviceName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return ServiceCatalog.services().stream()
                .filter(service -> normalized.contains(normalize(service.getName())))
                .max(Comparator.comparingInt(service -> normalize(service.getName()).length()));
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\-_/()+.]+", "");
    }
}
