package com.subflow.subscription.importer.dto;

import com.subflow.subscription.importer.matcher.ImportCandidate;
import com.subflow.subscription.importer.parser.ColumnMapping;

import java.util.List;

public record ImportPreviewResponse(
        String encoding,
        char delimiter,
        int headerRowIndex,
        boolean needsMapping,
        ColumnMapping columns,
        List<String> headers,
        List<List<String>> sampleRows,
        int rowCount,
        List<ImportPreviewRow> rows,
        List<ImportCandidate> candidates,
        int oneOffCount,
        List<SkippedRow> skipped
) {
}
