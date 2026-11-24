package com.subflow.subscription.service;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.subscription.catalog.ServiceCatalog;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.InsightResponse;
import com.subflow.subscription.dto.ServiceCatalogResponse;
import com.subflow.subscription.dto.ServicePlanResponse;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import com.subflow.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.time.Year;
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
    private final CurrentUserProvider currentUserProvider;

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

        List<InsightResponse.BenefitRecommendation> benefits = buildBenefits(subscriptions);

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
                duplicateGroups,
                benefits);
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

    private List<InsightResponse.BenefitRecommendation> buildBenefits(List<Subscription> subscriptions) {
        Optional<User> currentUser = currentUserProvider.findCurrentUser();
        if (currentUser.isEmpty()) {
            return List.of();
        }
        User user = currentUser.get();
        List<InsightResponse.BenefitRecommendation> benefits = new ArrayList<>();

        addFamilyPlanBenefits(user, subscriptions, benefits);
        addYouthBenefit(user, benefits);
        addMembershipBenefits(user, benefits);
        addStudentBenefit(user, benefits);
        return benefits;
    }

    private void addFamilyPlanBenefits(User user, List<Subscription> subscriptions,
                                       List<InsightResponse.BenefitRecommendation> benefits) {
        int householdSize = user.getHouseholdSize() == null ? 1 : user.getHouseholdSize();
        if (householdSize < 2) {
            return;
        }
        for (Subscription subscription : subscriptions) {
            Optional<ServiceCatalogResponse> matched = findService(subscription.getServiceName());
            if (matched.isEmpty()) {
                continue;
            }
            Optional<ServicePlanResponse> familyPlan = matched.get().getPlans().stream()
                    .filter(plan -> plan.getBillingCycle() == BillingCycle.MONTHLY)
                    .filter(plan -> plan.getName().contains("패밀리")
                            || plan.getName().contains("가족")
                            || plan.getName().contains("Family"))
                    .findFirst();
            if (familyPlan.isEmpty()) {
                continue;
            }
            BigDecimal individualTotal = subscription.getMonthlyExpense()
                    .multiply(BigDecimal.valueOf(householdSize));
            BigDecimal saving = individualTotal.subtract(familyPlan.get().getFee());
            if (saving.signum() <= 0) {
                continue;
            }
            benefits.add(new InsightResponse.BenefitRecommendation(
                    "FAMILY_PLAN",
                    matched.get().getName() + " 가족 요금제",
                    "가구원 " + householdSize + "명이 각자 결제 중이라면 "
                            + familyPlan.get().getDisplayName() + "으로 월 "
                            + saving.stripTrailingZeros().toPlainString() + "원 절약할 수 있습니다.",
                    saving));
        }
    }

    private void addYouthBenefit(User user, List<InsightResponse.BenefitRecommendation> benefits) {
        if (user.getBirthYear() == null) {
            return;
        }
        int age = Year.now().getValue() - user.getBirthYear();
        if (age >= 19 && age <= 34) {
            benefits.add(new InsightResponse.BenefitRecommendation(
                    "YOUTH",
                    "청년 대상 혜택",
                    "만 " + age + "세는 통신사 청년 요금제와 문화·교통 할인을 받을 수 있습니다. 가입 조건을 확인해 보세요.",
                    null));
        }
    }

    private void addMembershipBenefits(User user, List<InsightResponse.BenefitRecommendation> benefits) {
        Set<Membership> memberships = user.membershipSet();
        if (memberships.isEmpty()) {
            return;
        }
        boolean telecom = memberships.stream().anyMatch(membership ->
                membership == Membership.SKT || membership == Membership.KT
                        || membership == Membership.LG_U_PLUS || membership == Membership.MVNO);
        boolean card = memberships.stream().anyMatch(membership ->
                membership.name().endsWith("_CARD") || membership == Membership.KAKAO_BANK
                        || membership == Membership.TOSS_BANK);
        boolean shopping = memberships.stream().anyMatch(membership ->
                membership == Membership.COUPANG_WOW || membership == Membership.NAVER_PLUS
                        || membership == Membership.BAEMIN_CLUB);

        if (telecom) {
            benefits.add(new InsightResponse.BenefitRecommendation(
                    "TELECOM_MEMBERSHIP",
                    "통신사 멤버십 할인",
                    "통신사 멤버십으로 구독형 서비스를 할인받을 수 있는지 확인해 보세요.",
                    null));
        }
        if (card) {
            benefits.add(new InsightResponse.BenefitRecommendation(
                    "CARD_MEMBERSHIP",
                    "카드사 구독 할인",
                    "보유 카드의 구독·정기결제 할인 혜택을 확인해 보세요.",
                    null));
        }
        if (shopping) {
            benefits.add(new InsightResponse.BenefitRecommendation(
                    "SHOPPING_MEMBERSHIP",
                    "쇼핑 멤버십 혜택",
                    "쇼핑 멤버십에 포함된 영상·배송 혜택이 구독과 겹치는지 확인해 보세요.",
                    null));
        }
    }

    private void addStudentBenefit(User user, List<InsightResponse.BenefitRecommendation> benefits) {
        Occupation occupation = user.getOccupation();
        if (occupation != null && occupation.isStudent()) {
            benefits.add(new InsightResponse.BenefitRecommendation(
                    "STUDENT_VERIFICATION",
                    "학생 인증 할인",
                    "학생 인증이 필요한 서비스는 Spotify, YouTube, Apple Music 외에도 Adobe, Microsoft 365 등이 있습니다.",
                    null));
        }
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
