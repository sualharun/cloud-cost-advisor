package com.microsoft.cloudoptimizer.domain.model;

/**
 * Lifecycle states for recommendations.
 */
public enum RecommendationStatus {
    /**
     * Recommendation is current and actionable.
     */
    ACTIVE,

    /**
     * User has implemented the recommendation.
     */
    IMPLEMENTED,

    /**
     * User has dismissed the recommendation.
     */
    DISMISSED,

    /**
     * Recommendation is no longer valid (data changed).
     */
    EXPIRED,

    /**
     * System is verifying if implementation was successful.
     */
    VALIDATING
}
