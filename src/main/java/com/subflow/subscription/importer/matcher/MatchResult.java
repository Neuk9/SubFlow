package com.subflow.subscription.importer.matcher;

public record MatchResult(Long subscriptionId, String serviceName, double score, MatchType type) {
}
