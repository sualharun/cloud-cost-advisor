package com.microsoft.cloudoptimizer.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores tenant-specific preferences for tradeoff analysis.
 *
 * Allows tenants to customize dimension weights based on their priorities.
 */
@Entity
@Table(name = "tenant_preferences", indexes = {
    @Index(name = "idx_tenant_pref_tenant", columnList = "tenantId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique tenant identifier.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String tenantId;

    /**
     * JSON map of dimension name to weight (0-1).
     * Example: {"cost": 0.4, "performance": 0.3, "availability": 0.15, ...}
     */
    @Column(columnDefinition = "TEXT")
    private String tradeoffWeights;

    /**
     * Minimum monthly savings threshold to show recommendations (in USD).
     */
    @Column(nullable = false)
    private Double minimumSavingsThreshold;

    /**
     * Whether to include multi-cloud alternatives in comparisons.
     */
    @Column(nullable = false)
    private Boolean includeMultiCloudAlternatives;

    /**
     * Minimum confidence level for recommendations.
     */
    private Double minimumConfidenceThreshold;

    /**
     * Preferred notification frequency.
     */
    @Column(length = 32)
    private String notificationFrequency;

    /**
     * Whether recommendations should auto-dismiss after implementation.
     */
    private Boolean autoDismissImplemented;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (minimumSavingsThreshold == null) {
            minimumSavingsThreshold = 10.0;
        }
        if (includeMultiCloudAlternatives == null) {
            includeMultiCloudAlternatives = false;
        }
        if (minimumConfidenceThreshold == null) {
            minimumConfidenceThreshold = 0.6;
        }
        if (autoDismissImplemented == null) {
            autoDismissImplemented = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
