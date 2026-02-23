package com.microsoft.cloudoptimizer.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks implemented recommendations for savings validation.
 *
 * When a user marks a recommendation as implemented, we track it here
 * and later validate whether the expected savings were actually realized.
 */
@Entity
@Table(name = "implemented_recommendations", indexes = {
    @Index(name = "idx_impl_rec_tenant", columnList = "tenantId"),
    @Index(name = "idx_impl_rec_resource", columnList = "provider, resourceId"),
    @Index(name = "idx_impl_rec_validation", columnList = "validationStatus, scheduledValidationAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImplementedRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the original recommendation.
     */
    @Column(nullable = false)
    private Long recommendationId;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 512)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CloudProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResourceType resourceType;

    /**
     * When the recommendation was marked as implemented.
     */
    @Column(nullable = false)
    private LocalDateTime implementedAt;

    /**
     * User who marked the recommendation as implemented.
     */
    @Column(length = 128)
    private String implementedBy;

    /**
     * Expected monthly savings from the original recommendation.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal expectedMonthlySavings;

    /**
     * Actual monthly savings after validation.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal actualMonthlySavings;

    /**
     * When the implementation should be validated (typically 30 days after implementation).
     */
    private LocalDateTime scheduledValidationAt;

    /**
     * When the actual validation occurred.
     */
    private LocalDateTime actualValidatedAt;

    /**
     * Current validation status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ValidationStatus validationStatus;

    /**
     * Notes from the validation process.
     */
    @Column(length = 1024)
    private String validationNotes;

    /**
     * Average daily cost before implementation (baseline).
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal costBeforeDaily;

    /**
     * Average daily cost after implementation (for comparison).
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal costAfterDaily;

    /**
     * The action that was implemented.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RecommendationAction action;

    /**
     * Description of what was implemented.
     */
    @Column(length = 512)
    private String implementationSummary;

    @PrePersist
    protected void onCreate() {
        if (implementedAt == null) {
            implementedAt = LocalDateTime.now();
        }
        if (validationStatus == null) {
            validationStatus = ValidationStatus.PENDING;
        }
        if (scheduledValidationAt == null) {
            scheduledValidationAt = implementedAt.plusDays(30);
        }
    }
}
