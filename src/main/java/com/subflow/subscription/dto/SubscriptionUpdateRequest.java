package com.subflow.subscription.dto;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class SubscriptionUpdateRequest {

    @NotBlank(message = "서비스명을 입력해 주세요.")
    private String serviceName;

    @NotNull(message = "카테고리를 선택해 주세요.")
    private Category category;

    @NotNull(message = "구독료를 입력해 주세요.")
    @PositiveOrZero(message = "구독료는 0원 이상이어야 합니다.")
    private BigDecimal fee;

    @NotNull(message = "결제 주기를 선택해 주세요.")
    private BillingCycle billingCycle;

    @Min(value = 1, message = "결제일은 1일 이상이어야 합니다.")
    @Max(value = 31, message = "결제일은 31일 이하여야 합니다.")
    private Integer billingDay;

    @Min(value = 0, message = "알림 설정일은 0일 이상이어야 합니다.")
    @Max(value = 30, message = "알림 설정일은 30일 이하여야 합니다.")
    private Integer remindDaysBefore;

    private String memo;

    @Builder
    public SubscriptionUpdateRequest(String serviceName, Category category, BigDecimal fee,
                                     BillingCycle billingCycle, Integer billingDay,
                                     Integer remindDaysBefore, String memo) {
        this.serviceName = serviceName;
        this.category = category;
        this.fee = fee;
        this.billingCycle = billingCycle;
        this.billingDay = billingDay;
        this.remindDaysBefore = remindDaysBefore;
        this.memo = memo;
    }
}
