package com.subflow.subscription.importer.parser;

public record ColumnDetection(ColumnMapping mapping, int headerRowIndex, boolean needsMapping) {
}
