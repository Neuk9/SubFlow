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
        return category == null ? Category.OTHER.getColor() : category.getColor();
    }
}
