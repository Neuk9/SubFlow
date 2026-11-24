package com.subflow.subscription.importer.parser;

import java.util.List;

/**
 * 헤더 키워드 또는 값 패턴으로 날짜·가맹점·금액 컬럼을 찾는다.
 */
public class ColumnDetector {

    private static final List<String> DATE_KEYWORDS =
            List.of("거래일", "이용일", "승인일", "결제일", "일자", "date");
    private static final List<String> MERCHANT_KEYWORDS =
            List.of("가맹점", "이용처", "내용", "적요", "상호", "merchant", "description");
    private static final List<String> AMOUNT_KEYWORDS =
            List.of("금액", "승인금액", "이용금액", "결제금액", "출금", "amount");

    private static final int SAMPLE_SIZE = 20;
    private static final double MIN_RATIO = 0.8;

    public ColumnDetection detect(CsvDocument document, boolean hasHeader) {
        if (hasHeader) {
            List<CsvRow> rows = document.rows();
            for (int i = 0; i < rows.size(); i++) {
                ColumnMapping mapping = mapHeaders(rows.get(i).values());
                if (mapping != null) {
                    return new ColumnDetection(mapping, i, false);
                }
            }
        }
        ColumnMapping inferred = infer(document.rows());
        if (inferred != null) {
            return new ColumnDetection(inferred, -1, false);
        }
        return new ColumnDetection(null, -1, true);
    }

    private ColumnMapping mapHeaders(List<String> headers) {
        int date = -1;
        int merchant = -1;
        int amount = -1;
        int withdrawal = -1;

        for (int i = 0; i < headers.size(); i++) {
            String header = normalize(headers.get(i));
            if (date < 0 && containsAny(header, DATE_KEYWORDS)) {
                date = i;
            }
            if (merchant < 0 && containsAny(header, MERCHANT_KEYWORDS)) {
                merchant = i;
            }
            if (containsAny(header, AMOUNT_KEYWORDS)) {
                if (header.contains("출금")) {
                    withdrawal = i;
                } else if (amount < 0) {
                    amount = i;
                }
            }
        }
        int resolvedAmount = withdrawal >= 0 ? withdrawal : amount;
        if (date < 0 || merchant < 0 || resolvedAmount < 0) {
            return null;
        }
        if (date == merchant || date == resolvedAmount || merchant == resolvedAmount) {
            return null;
        }
        return new ColumnMapping(date, merchant, resolvedAmount);
    }

    private ColumnMapping infer(List<CsvRow> rows) {
        List<CsvRow> sample = rows.stream().limit(SAMPLE_SIZE).toList();
        if (sample.isEmpty()) {
            return null;
        }
        int width = sample.stream().mapToInt(row -> row.values().size()).max().orElse(0);
        if (width < 3) {
            return null;
        }

        int date = bestColumn(sample, width, this::isDate, -1);
        int amount = bestColumn(sample, width, this::isAmount, date);
        if (date < 0 || amount < 0) {
            return null;
        }
        int merchant = longestTextColumn(sample, width, date, amount);
        if (merchant < 0) {
            return null;
        }
        return new ColumnMapping(date, merchant, amount);
    }

    private int bestColumn(List<CsvRow> rows, int width, ColumnPredicate predicate, int exclude) {
        int bestColumn = -1;
        double bestRatio = 0;
        for (int column = 0; column < width; column++) {
            if (column == exclude) {
                continue;
            }
            int nonEmpty = 0;
            int matches = 0;
            for (CsvRow row : rows) {
                String value = valueAt(row, column);
                if (value == null || value.isBlank()) {
                    continue;
                }
                nonEmpty++;
                if (predicate.test(value)) {
                    matches++;
                }
            }
            double ratio = nonEmpty == 0 ? 0 : (double) matches / nonEmpty;
            if (ratio >= MIN_RATIO && ratio > bestRatio) {
                bestRatio = ratio;
                bestColumn = column;
            }
        }
        return bestColumn;
    }

    private int longestTextColumn(List<CsvRow> rows, int width, int excludeA, int excludeB) {
        int bestColumn = -1;
        double bestAverage = 0;
        for (int column = 0; column < width; column++) {
            if (column == excludeA || column == excludeB) {
                continue;
            }
            int nonEmpty = 0;
            int totalLength = 0;
            for (CsvRow row : rows) {
                String value = valueAt(row, column);
                if (value == null || value.isBlank()) {
                    continue;
                }
                nonEmpty++;
                totalLength += value.trim().length();
            }
            double average = nonEmpty == 0 ? 0 : (double) totalLength / nonEmpty;
            if (average > bestAverage) {
                bestAverage = average;
                bestColumn = column;
            }
        }
        return bestColumn;
    }

    private String valueAt(CsvRow row, int column) {
        return column < row.values().size() ? row.values().get(column) : null;
    }

    private boolean isDate(String value) {
        return ValueParser.parseDate(value).isPresent();
    }

    private boolean isAmount(String value) {
        return ValueParser.parseAmount(value).isPresent();
    }

    private boolean containsAny(String header, List<String> keywords) {
        return keywords.stream().anyMatch(header::contains);
    }

    private String normalize(String header) {
        return header == null ? "" : header.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @FunctionalInterface
    private interface ColumnPredicate {
        boolean test(String value);
    }
}
