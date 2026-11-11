package com.subflow.subscription.repository;

import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByOrderByNextBillingDateAsc();

    List<Subscription> findAllByStatusOrderByNextBillingDateAsc(SubscriptionStatus status);

    List<Subscription> findAllByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus status, LocalDate date);

    List<Subscription> findAllByStatusAndCancellationScheduledAtLessThanEqual(SubscriptionStatus status, LocalDate date);

    @Query("SELECT s FROM Subscription s WHERE s.status = :status AND s.nextBillingDate BETWEEN :startDate AND :endDate ORDER BY s.nextBillingDate ASC")
    List<Subscription> findUpcomingSubscriptions(@Param("status") SubscriptionStatus status,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(s.monthlyExpense), 0) FROM Subscription s WHERE s.status = :status")
    BigDecimal sumMonthlyExpenseByStatus(@Param("status") SubscriptionStatus status);

    @Query("SELECT s.category, COALESCE(SUM(s.monthlyExpense), 0), COUNT(s) FROM Subscription s WHERE s.status = :status GROUP BY s.category")
    List<Object[]> aggregateMonthlyExpenseByCategory(@Param("status") SubscriptionStatus status);

    long countByStatus(SubscriptionStatus status);
}
