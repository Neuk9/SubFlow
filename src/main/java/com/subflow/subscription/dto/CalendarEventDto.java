package com.subflow.subscription.dto;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class CalendarEventDto {
    private Long subscriptionId;
    private String serviceName;
    private Category category;
    private String categoryDescription;
    private BigDecimal fee;
    private BillingCycle billingCycle;
    private LocalDate billingDate;
    private int day;
    private String color;

    public static String getColorForCategory(Category category) {
        if (category == null) return "#6c757d";
        return switch (category) {
            case OTT -> "#e50914";          // Red
            case MUSIC -> "#1db954";        // Green
            case PRODUCTIVITY -> "#0d6efd"; // Blue
            case SHOPPING -> "#fd7e14";     // Orange
            case CLOUD -> "#0dcaf0";        // Cyan
            case GAME -> "#6f42c1";         // Purple
            case EDUCATION -> "#20c997";    // Teal
            case OTHER -> "#6c757d";        // Gray
        };
    }
}
