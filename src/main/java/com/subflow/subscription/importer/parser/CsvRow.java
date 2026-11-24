package com.subflow.subscription.importer.parser;

import java.util.List;

public record CsvRow(int rowNumber, List<String> values) {
}
