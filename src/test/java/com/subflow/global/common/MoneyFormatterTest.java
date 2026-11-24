package com.subflow.global.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyFormatterTest {

    @Test
    @DisplayName("소수점이 있는 금액을 천 단위 콤마로 포맷한다")
    void formatsWithThousandsSeparator() {
        assertThat(MoneyFormatter.format(new BigDecimal("49790.00"))).isEqualTo("49,790");
        assertThat(MoneyFormatter.format(new BigDecimal("17000.00"))).isEqualTo("17,000");
        assertThat(MoneyFormatter.format(new BigDecimal("120000.00"))).isEqualTo("120,000");
    }

    @Test
    @DisplayName("0과 null은 0으로 표시한다")
    void formatsZeroAndNull() {
        assertThat(MoneyFormatter.format(BigDecimal.ZERO)).isEqualTo("0");
        assertThat(MoneyFormatter.format(null)).isEqualTo("0");
    }

    @Test
    @DisplayName("소수점 이하는 반올림해서 표시하지 않는다")
    void roundsFraction() {
        assertThat(MoneyFormatter.format(new BigDecimal("1234567.89"))).isEqualTo("1,234,568");
    }
}
