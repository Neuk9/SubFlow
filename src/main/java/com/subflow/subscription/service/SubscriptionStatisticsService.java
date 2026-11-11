package com.subflow.subscription.service;

import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.CategoryExpenseDto;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.dto.SubscriptionSummaryResponse;
import com.subflow.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionStatisticsService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        BigDecimal totalMonthly = subscriptionRepository.sumMonthlyExpenseByStatus(SubscriptionStatus.ACTIVE);
        if (totalMonthly == null) {
            totalMonthly = BigDecimal.ZERO;
        }
        BigDecimal totalYearly = totalMonthly.multiply(BigDecimal.valueOf(12));
        long activeCount = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);

        LocalDate sevenDaysLater = today.plusDays(7);
        List<Subscription> upcomingList = subscriptionRepository.findUpcomingSubscriptions(
                SubscriptionStatus.ACTIVE, today, sevenDaysLater);
        long upcomingCount = upcomingList.size();

        List<Object[]> categoryData = subscriptionRepository.aggregateMonthlyExpenseByCategory(SubscriptionStatus.ACTIVE);
        List<CategoryExpenseDto> categoryExpenses = new ArrayList<>();

        for (Object[] row : categoryData) {
            Category category = (Category) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();

            double percentage = 0.0;
            if (totalMonthly.compareTo(BigDecimal.ZERO) > 0) {
                percentage = amount.multiply(BigDecimal.valueOf(100))
                        .divide(totalMonthly, 1, RoundingMode.HALF_UP)
                        .doubleValue();
            }

            categoryExpenses.add(CategoryExpenseDto.builder()
                    .category(category)
                    .categoryDescription(category.getDescription())
                    .amount(amount)
                    .count(count)
                    .percentage(percentage)
                    .build());
        }

        return SubscriptionSummaryResponse.builder()
                .totalMonthlyExpense(totalMonthly)
                .totalYearlyExpense(totalYearly)
                .activeCount(activeCount)
                .upcomingCountWithin7Days(upcomingCount)
                .categoryExpenses(categoryExpenses)
                .build();
    }

    public List<SubscriptionResponse> getUpcomingSubscriptions(int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(daysAhead);
        return subscriptionRepository.findUpcomingSubscriptions(SubscriptionStatus.ACTIVE, today, targetDate).stream()
                .map(s -> SubscriptionResponse.from(s, today))
                .toList();
    }
}
