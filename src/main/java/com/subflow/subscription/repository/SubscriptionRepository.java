package com.subflow.subscription.repository;

import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByOwnerEmailOrderByNextBillingDateAsc(String ownerEmail);

    List<Subscription> findAllByOwnerEmailAndStatusOrderByNextBillingDateAsc(String ownerEmail, SubscriptionStatus status);

    Optional<Subscription> findByIdAndOwnerEmail(Long id, String ownerEmail);

    List<Subscription> findAllByStatusOrderByNextBillingDateAsc(SubscriptionStatus status);

    List<Subscription> findAllByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus status, LocalDate date);

    List<Subscription> findAllByStatusAndCancellationScheduledAtLessThanEqual(SubscriptionStatus status, LocalDate date);

    @Query("""
            SELECT s FROM Subscription s
            WHERE s.ownerEmail = :ownerEmail
              AND s.status = :status
              AND s.nextBillingDate BETWEEN :startDate AND :endDate
            ORDER BY s.nextBillingDate ASC
            """)
    List<Subscription> findUpcomingSubscriptions(@Param("ownerEmail") String ownerEmail,
                                                 @Param("status") SubscriptionStatus status,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(s.monthlyExpense), 0) FROM Subscription s WHERE s.ownerEmail = :ownerEmail AND s.status = :status")
    BigDecimal sumMonthlyExpense(@Param("ownerEmail") String ownerEmail,
                                 @Param("status") SubscriptionStatus status);

    @Query("""
            SELECT s.category, COALESCE(SUM(s.monthlyExpense), 0), COUNT(s)
            FROM Subscription s
            WHERE s.ownerEmail = :ownerEmail AND s.status = :status
            GROUP BY s.category
            """)
    List<Object[]> aggregateMonthlyExpenseByCategory(@Param("ownerEmail") String ownerEmail,
                                                     @Param("status") SubscriptionStatus status);

    long countByOwnerEmailAndStatus(String ownerEmail, SubscriptionStatus status);
}
