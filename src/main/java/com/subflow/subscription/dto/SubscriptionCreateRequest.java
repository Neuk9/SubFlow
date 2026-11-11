package com.subflow.subscription.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class SubscriptionCreateRequest {

    @NotBlank(message = "서비스명을 입력해 주세요.")
    private final String serviceName;

    @NotNull(message = "카테고리를 선택해 주세요.")
    private final Category category;

    @NotNull(message = "구독료를 입력해 주세요.")
    @PositiveOrZero(message = "구독료는 0원 이상이어야 합니다.")
    private final BigDecimal fee;

    @NotNull(message = "결제 주기를 선택해 주세요.")
    private final BillingCycle billingCycle;

    @Min(value = 1, message = "결제일은 1일 이상이어야 합니다.")
    @Max(value = 31, message = "결제일은 31일 이하여야 합니다.")
    private final Integer billingDay;

    private final LocalDate startDate;

    @Min(value = 0, message = "알림 설정일은 0일 이상이어야 합니다.")
    @Max(value = 30, message = "알림 설정일은 30일 이하여야 합니다.")
    private final Integer remindDaysBefore;

    private final String memo;

    @Builder
    @JsonCreator
    public SubscriptionCreateRequest(
            @JsonProperty("serviceName") String serviceName,
            @JsonProperty("category") Category category,
            @JsonProperty("fee") BigDecimal fee,
            @JsonProperty("billingCycle") BillingCycle billingCycle,
            @JsonProperty("billingDay") Integer billingDay,
            @JsonProperty("startDate") LocalDate startDate,
            @JsonProperty("remindDaysBefore") Integer remindDaysBefore,
            @JsonProperty("memo") String memo) {
        this.serviceName = serviceName;
        this.category = category;
        this.fee = fee;
        this.billingCycle = billingCycle;
        this.billingDay = billingDay;
        this.startDate = startDate;
        this.remindDaysBefore = (remindDaysBefore != null) ? remindDaysBefore : 3;
        this.memo = memo;
    }
}
