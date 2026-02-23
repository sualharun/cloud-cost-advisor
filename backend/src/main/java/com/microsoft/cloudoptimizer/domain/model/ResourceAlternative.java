package com.microsoft.cloudoptimizer.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents an alternative SKU/configuration for a resource.
 *
 * Maps current SKUs to possible alternatives for rightsizing recommendations.
 */
@Entity
@Table(name = "resource_alternatives", indexes = {
    @Index(name = "idx_res_alt_provider_current", columnList = "provider, currentSku"),
    @Index(name = "idx_res_alt_resource_type", columnList = "resourceType")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceAlternative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Cloud provider for the current resource.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CloudProvider provider;

    /**
     * Resource type (COMPUTE, DATABASE, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResourceType resourceType;

    /**
     * Current SKU that this alternative applies to.
     */
    @Column(nullable = false, length = 128)
    private String currentSku;

    /**
     * Alternative SKU being proposed.
     */
    @Column(nullable = false, length = 128)
    private String alternativeSku;

    /**
     * If multi-cloud, the target provider.
     * Null if same provider.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private CloudProvider alternativeProvider;

    /**
     * Display name for the alternative.
     */
    @Column(length = 256)
    private String displayName;

    /**
     * Estimated hourly price in USD.
     */
    @Column(precision = 10, scale = 4)
    private BigDecimal estimatedHourlyPrice;

    /**
     * Number of vCPUs.
     */
    private Integer vcpu;

    /**
     * Memory in GB.
     */
    private Double memoryGb;

    /**
     * Storage in GB (if applicable).
     */
    private Double storageGb;

    /**
     * SKU family (e.g., "D-series", "B-series").
     */
    @Column(length = 64)
    private String skuFamily;

    /**
     * Whether this alternative is active.
     */
    @Column(nullable = false)
    private Boolean active;

    /**
     * Alternative category (downsize, upsize, different_family, cross_cloud).
     */
    @Column(length = 32)
    private String category;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
        if (alternativeProvider == null) {
            alternativeProvider = provider;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
