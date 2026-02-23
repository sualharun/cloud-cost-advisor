package com.microsoft.cloudoptimizer.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a dimension for evaluating resource alternatives.
 *
 * Examples: cost, performance, availability, migration_effort, vendor_lock_in, environmental_impact
 */
@Entity
@Table(name = "tradeoff_dimensions", indexes = {
    @Index(name = "idx_tradeoff_dim_name", columnList = "name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeoffDimension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique dimension identifier (e.g., "cost", "performance").
     */
    @Column(nullable = false, unique = true, length = 64)
    private String name;

    /**
     * Human-readable display name.
     */
    @Column(nullable = false, length = 128)
    private String displayName;

    /**
     * Description of what this dimension measures.
     */
    @Column(length = 512)
    private String description;

    /**
     * Default weight for this dimension (0-1).
     * Sum of all dimensions should equal 1 for normalization.
     */
    @Column(nullable = false)
    private Double defaultWeight;

    /**
     * Whether higher scores are better for this dimension.
     * true = higher is better (e.g., performance)
     * false = lower is better (e.g., cost - though we typically invert to savings)
     */
    @Column(nullable = false)
    private Boolean higherIsBetter;

    /**
     * Whether this dimension is currently active.
     */
    @Column(nullable = false)
    private Boolean active;

    /**
     * Display order for UI.
     */
    private Integer displayOrder;

    /**
     * Icon identifier for UI.
     */
    @Column(length = 64)
    private String iconName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
        if (higherIsBetter == null) {
            higherIsBetter = true;
        }
    }
}
