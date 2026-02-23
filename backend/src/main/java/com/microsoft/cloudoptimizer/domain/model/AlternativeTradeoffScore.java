package com.microsoft.cloudoptimizer.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores calculated tradeoff scores for a resource alternative on a specific dimension.
 *
 * Scores are calculated by TradeoffScoreProviders and cached here.
 */
@Entity
@Table(name = "alternative_tradeoff_scores", indexes = {
    @Index(name = "idx_alt_score_alternative", columnList = "alternativeId"),
    @Index(name = "idx_alt_score_dimension", columnList = "dimensionId"),
    @Index(name = "idx_alt_score_alt_dim", columnList = "alternativeId, dimensionId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlternativeTradeoffScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the resource alternative.
     */
    @Column(nullable = false)
    private Long alternativeId;

    /**
     * Reference to the tradeoff dimension.
     */
    @Column(nullable = false)
    private Long dimensionId;

    /**
     * Calculated score (0-1 scale).
     * 0 = worst, 1 = best
     */
    @Column(nullable = false)
    private Double score;

    /**
     * Human-readable explanation of the score.
     */
    @Column(length = 512)
    private String explanation;

    /**
     * Value for the current resource (for comparison display).
     */
    @Column(length = 128)
    private String currentValue;

    /**
     * Value for the alternative resource.
     */
    @Column(length = 128)
    private String alternativeValue;

    /**
     * When this score was last calculated.
     */
    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    /**
     * Source of the score calculation (provider class name).
     */
    @Column(length = 128)
    private String source;

    /**
     * Confidence in this score (0-1).
     */
    private Double confidence;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (lastUpdated == null) {
            lastUpdated = LocalDateTime.now();
        }
    }
}
