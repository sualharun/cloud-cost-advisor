package com.microsoft.cloudoptimizer.tradeoff;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.ResourceAlternative;

/**
 * Interface for calculating tradeoff scores for resource alternatives.
 *
 * Each implementation handles a specific dimension (cost, performance, etc.)
 * and calculates a normalized score (0-1) for that dimension.
 */
public interface TradeoffScoreProvider {

    /**
     * Returns the dimension name this provider handles.
     */
    String getDimensionName();

    /**
     * Calculate the tradeoff score for an alternative.
     *
     * @param alternative The resource alternative being evaluated
     * @param currentMetadata Metadata about the current resource
     * @param region The region for pricing lookups
     * @return A TradeoffScore with score (0-1), explanation, and current/alternative values
     */
    TradeoffScore calculateScore(ResourceAlternative alternative,
                                  CloudCostAdapter.ResourceMetadata currentMetadata,
                                  String region);

    /**
     * Result of a tradeoff score calculation.
     */
    record TradeoffScore(
            double score,           // 0-1, higher is better
            String explanation,     // Human-readable explanation
            String currentValue,    // Value for current resource
            String alternativeValue,// Value for alternative
            double confidence       // 0-1, confidence in this score
    ) {
        /**
         * Create an unknown/error score.
         */
        public static TradeoffScore unknown(String reason) {
            return new TradeoffScore(0.5, reason, "Unknown", "Unknown", 0.0);
        }
    }
}
