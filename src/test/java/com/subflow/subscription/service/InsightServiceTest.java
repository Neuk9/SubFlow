package com.subflow.subscription.service;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.dto.InsightResponse;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import com.subflow.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    private static final String OWNER = "me@example.com";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionFinder subscriptionFinder;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private InsightService insightService;

    @Test
    @DisplayName("연간 요금제가 있으면 전환 절약액을 계산한다")
    void calculatesAnnualSaving() {
        givenOwner(subscription("노션 Plus", "15000"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.annualSavings()).hasSize(1);
        assertThat(response.annualSavings().get(0).serviceName()).isEqualTo("노션");
        assertThat(response.annualSavings().get(0).saving()).isEqualByComparingTo("60000");
        assertThat(response.annualSavingTotal()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("학생 요금제가 더 싸면 절약액을 계산한다")
    void calculatesStudentSaving() {
        givenOwner(subscription("스포티파이 개인", "10900"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.studentSavings()).hasSize(1);
        assertThat(response.studentSavings().get(0).studentFee()).isEqualByComparingTo("5900");
        assertThat(response.studentSavings().get(0).saving()).isEqualByComparingTo("5000");
        assertThat(response.studentSavingTotal()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("같은 겹침 그룹의 구독이 2개 이상이면 중복으로 표시한다")
    void detectsDuplicates() {
        givenOwner(
                subscription("스포티파이 개인", "10900"),
                subscription("애플 뮤직 개인", "11000"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.duplicateGroups()).hasSize(1);
        InsightResponse.DuplicateGroup group = response.duplicateGroups().get(0);
        assertThat(group.group()).isEqualTo("음악 스트리밍");
        assertThat(group.serviceNames()).containsExactlyInAnyOrder("스포티파이 개인", "애플 뮤직 개인");
        assertThat(group.monthlyTotal()).isEqualByComparingTo("21900");
    }

    @Test
    @DisplayName("가구원 수가 2명 이상이면 가족 요금제 절약을 제안한다")
    void recommendsFamilyPlan() {
        givenOwner(user(2002, Occupation.UNIVERSITY, 3, Set.of()),
                subscription("유튜브 프리미엄", "14900"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.benefits())
                .filteredOn(benefit -> benefit.code().equals("FAMILY_PLAN"))
                .isNotEmpty()
                .allSatisfy(benefit -> assertThat(benefit.estimatedMonthlySaving()).isEqualByComparingTo("21800"));
    }

    @Test
    @DisplayName("만 19~34세면 청년 혜택을 제안한다")
    void recommendsYouthBenefit() {
        givenOwner(user(Year.now().getValue() - 25, Occupation.WORKER, 1, Set.of()));

        InsightResponse response = insightService.getInsights();

        assertThat(response.benefits()).extracting(InsightResponse.BenefitRecommendation::code)
                .contains("YOUTH");
    }

    @Test
    @DisplayName("통신사 멤버십이 있으면 멤버십 할인을 제안한다")
    void recommendsTelecomBenefit() {
        givenOwner(user(2002, Occupation.WORKER, 1, Set.of(Membership.SKT)));

        InsightResponse response = insightService.getInsights();

        assertThat(response.benefits()).extracting(InsightResponse.BenefitRecommendation::code)
                .contains("TELECOM_MEMBERSHIP");
    }

    @Test
    @DisplayName("프로필이 없으면 혜택을 제안하지 않는다")
    void noBenefitsWithoutProfile() {
        givenOwner((User) null, subscription("유튜브 프리미엄", "14900"));

        assertThat(insightService.getInsights().benefits()).isEmpty();
    }

    @Test
    @DisplayName("이미 연간 구독 중이면 연간 전환 절약을 추천하지 않는다")
    void skipsAnnualSavingForYearlySubscription() {
        Subscription yearly = Subscription.builder()
                .serviceName("Microsoft 365 Personal (연간)")
                .category(Category.PRODUCTIVITY)
                .fee(new BigDecimal("119000"))
                .billingCycle(BillingCycle.YEARLY)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2027, 1, 1))
                .monthlyExpense(new BigDecimal("9917"))
                .build();
        givenOwner(yearly);

        assertThat(insightService.getInsights().annualSavings()).isEmpty();
    }

    @Test
    @DisplayName("이미 가족 요금제를 이용 중이면 가족 요금제를 다시 추천하지 않는다")
    void skipsFamilyPlanWhenAlreadySubscribed() {
        givenOwner(user(2002, Occupation.UNIVERSITY, 3, Set.of()),
                subscription("유튜브 프리미엄 패밀리", "22900"));

        assertThat(insightService.getInsights().benefits())
                .filteredOn(benefit -> benefit.code().equals("FAMILY_PLAN"))
                .isEmpty();
    }

    @Test
    @DisplayName("카탈로그 요금제 표시명(Adobe All Apps, Google AI Pro)도 인사이트에 매칭된다")
    void matchesPlanDisplayNamesInCatalog() {
        givenOwner(
                subscription("Adobe All Apps", "62000"),
                subscription("Google AI Pro", "29000"),
                subscription("ChatGPT Plus", "29000"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.annualSavings()).extracting(InsightResponse.AnnualSaving::serviceName)
                .contains("Adobe Creative Cloud");
        assertThat(response.duplicateGroups()).extracting(InsightResponse.DuplicateGroup::group)
                .contains("AI 어시스턴트");
    }

    @Test
    @DisplayName("카탈로그에 없는 서비스는 인사이트에서 제외한다")
    void ignoresUnknownService() {
        givenOwner(subscription("동네 빵집 정기배송", "9000"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.annualSavings()).isEmpty();
        assertThat(response.studentSavings()).isEmpty();
        assertThat(response.duplicateGroups()).isEmpty();
    }

    private void givenOwner(Subscription... subscriptions) {
        givenOwner(null, subscriptions);
    }

    private void givenOwner(User user, Subscription... subscriptions) {
        given(subscriptionFinder.currentOwnerEmail()).willReturn(OWNER);
        given(subscriptionRepository.findAllByOwnerEmailOrderByNextBillingDateAsc(OWNER))
                .willReturn(List.of(subscriptions));
        given(currentUserProvider.findCurrentUser()).willReturn(Optional.ofNullable(user));
    }

    private User user(int birthYear, Occupation occupation, Integer householdSize, Set<Membership> memberships) {
        User user = User.builder().googleSub("g-1").email(OWNER).name("늑구").build();
        user.completeOnboarding(birthYear, occupation, householdSize, Set.of(), memberships);
        return user;
    }

    private Subscription subscription(String serviceName, String monthlyExpense) {
        return Subscription.builder()
                .serviceName(serviceName)
                .category(Category.OTHER)
                .fee(new BigDecimal(monthlyExpense))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(15)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2026, 12, 15))
                .monthlyExpense(new BigDecimal(monthlyExpense))
                .build();
    }
}
