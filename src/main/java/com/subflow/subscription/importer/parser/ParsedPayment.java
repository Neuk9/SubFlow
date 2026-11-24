package com.subflow.subscription.importer.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedPayment(int rowNumber, LocalDate billingDate, String merchant, BigDecimal amount) {
}
