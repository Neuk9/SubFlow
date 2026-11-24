package com.subflow.subscription.importer.parser;

import java.util.List;

public record CsvDocument(String encoding, char delimiter, List<CsvRow> rows) {
}
