package com.microsoft.cloudoptimizer.tradeoff.dto;

/**
 * DTO for tradeoff dimension information in API responses.
 */
public record TradeoffInfo(
        String dimension,
        String displayName,
        double score,
        String currentValue,
        String alternativeValue,
        String explanation,
        String direction // "improvement", "degradation", "neutral"
) {
    /**
     * Determine the direction based on score.
     */
    public static String calculateDirection(double score, boolean higherIsBetter) {
        if (higherIsBetter) {
            if (score >= 0.7) return "improvement";
            if (score <= 0.4) return "degradation";
        } else {
            if (score <= 0.3) return "improvement";
            if (score >= 0.6) return "degradation";
        }
        return "neutral";
    }
}
