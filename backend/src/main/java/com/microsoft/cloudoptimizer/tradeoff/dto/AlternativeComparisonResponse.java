package com.microsoft.cloudoptimizer.tradeoff.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO for the alternatives comparison API response.
 */
public record AlternativeComparisonResponse(
        CurrentResourceInfo currentResource,
        List<AlternativeWithTradeoffs> alternatives,
        UserPreferencesInfo userPreferences
) {
    /**
     * Information about the current resource.
     */
    public record CurrentResourceInfo(
            String resourceId,
            String provider,
            String sku,
            String displayName,
            int vcpu,
            double memoryGb,
            double estimatedMonthlyCost,
            String region
    ) {}

    /**
     * Alternative with its tradeoff scores.
     */
    public record AlternativeWithTradeoffs(
            Long alternativeId,
            String sku,
            String displayName,
            String provider,
            int vcpu,
            double memoryGb,
            double estimatedMonthlyCost,
            double estimatedMonthlySavings,
            double savingsPercentage,
            double overallScore,
            String category, // "downsize", "upsize", "different_family", "cross_cloud"
            List<TradeoffInfo> tradeoffs
    ) {}

    /**
     * User's tradeoff preferences.
     */
    public record UserPreferencesInfo(
            Map<String, Double> weights,
            boolean includeMultiCloud,
            double minimumSavingsThreshold
    ) {}
}
