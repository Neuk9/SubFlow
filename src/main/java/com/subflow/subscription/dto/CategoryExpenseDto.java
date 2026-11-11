package com.subflow.subscription.dto;

import com.subflow.subscription.domain.Category;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CategoryExpenseDto {

    private Category category;
    private String categoryDescription;
    private BigDecimal amount;
    private long count;
    private double percentage;
}
