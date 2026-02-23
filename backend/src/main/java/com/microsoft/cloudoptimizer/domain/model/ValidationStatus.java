package com.microsoft.cloudoptimizer.domain.model;

/**
 * Status of savings validation for implemented recommendations.
 */
public enum ValidationStatus {
    /**
     * Waiting for validation (typically 30 days after implementation).
     */
    PENDING,

    /**
     * Savings have been validated and confirmed.
     */
    VALIDATED,

    /**
     * Validation failed - expected savings not realized.
     */
    FAILED,

    /**
     * Partial savings realized (some but not all expected savings).
     */
    PARTIAL
}
