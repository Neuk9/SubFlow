package com.subflow.subscription.importer.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;

/**
 * CSV 셀 값을 날짜/금액으로 변환한다.
 */
public final class ValueParser {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            formatter("uuuu-MM-dd HH:mm:ss"),
            formatter("uuuu-MM-dd"),
            formatter("uuuu.MM.dd"),
            formatter("uuuu/MM/dd"),
            formatter("uuuuMMdd")
    );

    private ValueParser() {
    }

    public static Optional<LocalDate> parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(value, formatter));
            } catch (DateTimeParseException ignored) {
                // 다음 형식을 시도한다.
            }
        }
        return Optional.empty();
    }

    public static Optional<BigDecimal> parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim()
                .replace("\"", "")
                .replace(",", "")
                .replace("원", "")
                .replace(" ", "");

        boolean negative = value.startsWith("-");
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("-") || value.startsWith("+")) {
            value = value.substring(1);
        }
        if (!value.matches("\\d{1,15}(\\.\\d{1,4})?")) {
            return Optional.empty();
        }
        try {
            BigDecimal amount = new BigDecimal(value);
            if (negative) {
                amount = amount.negate();
            }
            return amount.signum() == 0 ? Optional.empty() : Optional.of(amount);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }
}
