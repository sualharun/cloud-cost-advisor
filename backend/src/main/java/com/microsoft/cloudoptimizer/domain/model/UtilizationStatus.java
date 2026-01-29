package com.microsoft.cloudoptimizer.domain.model;

/**
 * Resource utilization classification derived from ML clustering analysis.
 *
 * Thresholds are configurable per resource type but defaults are:
 * - UNDERUTILIZED: avg utilization < 20% over 14 days
 * - OPTIMIZED: avg utilization 20-80%
 * - OVERUTILIZED: avg utilization > 80%
 * - IDLE: avg utilization < 5% or no activity for 7+ days
 */
public enum UtilizationStatus {
    /**
     * Resource is significantly over-provisioned.
     * Primary candidate for downsizing.
     */
    UNDERUTILIZED("Under-utilized", "warning", 0.9),

    /**
     * Resource is appropriately sized for workload.
     */
    OPTIMIZED("Well-optimized", "success", 0.1),

    /**
     * Resource is nearing capacity limits.
     * May need scaling up to prevent performance issues.
     */
    OVERUTILIZED("Over-utilized", "danger", 0.7),

    /**
     * Resource shows minimal to no activity.
     * Candidate for deletion or reserved instance conversion.
     */
    IDLE("Idle", "critical", 0.95),

    /**
     * Insufficient data for classification.
     * Requires more observation time.
     */
    INSUFFICIENT_DATA("Insufficient data", "neutral", 0.0);

    private final String displayLabel;
    private final String severityLevel;
    private final double savingsPotential;

    UtilizationStatus(String displayLabel, String severityLevel, double savingsPotential) {
        this.displayLabel = displayLabel;
        this.severityLevel = severityLevel;
        this.savingsPotential = savingsPotential;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    /**
     * UI severity indicator: success, warning, danger, critical, neutral
     */
    public String getSeverityLevel() {
        return severityLevel;
    }

    /**
     * Typical savings potential (0.0-1.0) for this status.
     * Used for prioritization when no specific recommendation exists.
     */
    public double getSavingsPotential() {
        return savingsPotential;
    }
}
