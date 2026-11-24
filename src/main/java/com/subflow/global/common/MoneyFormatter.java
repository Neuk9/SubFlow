package com.subflow.global.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * 화면 표시용 금액 포맷터. API의 숫자 필드는 그대로 두고 뷰에서만 사용한다.
 */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.KOREA);
        format.setMaximumFractionDigits(0);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount);
    }
}
