package com.subflow.subscription.importer.dto;

public record ImportConfirmResponse(int recordedCount, int skippedCount, int createdSubscriptions) {
}
