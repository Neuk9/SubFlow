package com.subflow.subscription.importer.validation;

/**
 * CSV 파싱 자원 상한.
 */
public record CsvLimits(int maxRows, int maxColumns, int maxCellLength) {

    public static final CsvLimits DEFAULT = new CsvLimits(5_000, 100, 10_000);
}
