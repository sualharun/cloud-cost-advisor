package com.microsoft.cloudoptimizer.domain.model;

/**
 * Actionable optimization recommendations.
 *
 * Each action type corresponds to a specific cost-saving operation
 * that can be performed on a cloud resource.
 */
public enum RecommendationAction {
    /**
     * Reduce instance size (vCPU/memory).
     * Applicable when resource is consistently underutilized.
     */
    DOWNSIZE_INSTANCE("Downsize instance", "Reduce compute capacity to match actual usage"),

    /**
     * Increase instance size.
     * Recommended when approaching capacity to avoid throttling.
     */
    UPSIZE_INSTANCE("Upsize instance", "Increase capacity to prevent performance degradation"),

    /**
     * Convert to reserved/committed use pricing.
     * Applicable for stable, predictable workloads.
     */
    PURCHASE_RESERVATION("Purchase reservation", "Convert to 1 or 3 year commitment for savings"),

    /**
     * Terminate unused resource.
     * Recommended for idle resources with no recent activity.
     */
    DELETE_RESOURCE("Delete resource", "Remove idle or orphaned resource"),

    /**
     * Migrate to different region.
     * When significant price differences exist between regions.
     */
    CHANGE_REGION("Change region", "Migrate to lower-cost region"),

    /**
     * Modify storage tier.
     * Move infrequently accessed data to cold/archive storage.
     */
    CHANGE_STORAGE_TIER("Change storage tier", "Move to appropriate access tier"),

    /**
     * Use spot/preemptible instances.
     * For fault-tolerant, interruptible workloads.
     */
    USE_SPOT_INSTANCES("Use spot instances", "Switch to spot/preemptible for cost savings"),

    /**
     * Schedule start/stop for non-production workloads.
     */
    SCHEDULE_SHUTDOWN("Schedule shutdown", "Auto-stop during off-hours"),

    /**
     * Consolidate multiple small resources.
     */
    CONSOLIDATE_RESOURCES("Consolidate resources", "Merge underutilized resources"),

    /**
     * Switch to alternative service.
     * When a different service provides better cost efficiency.
     */
    MIGRATE_SERVICE("Migrate service", "Switch to more cost-effective service"),

    /**
     * No action needed.
     */
    NO_ACTION("No action", "Resource is well-optimized");

    private final String displayName;
    private final String description;

    RecommendationAction(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
