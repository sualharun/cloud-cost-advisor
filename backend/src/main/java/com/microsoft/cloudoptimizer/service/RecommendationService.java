package com.microsoft.cloudoptimizer.service;

import com.microsoft.cloudoptimizer.domain.model.*;
import com.microsoft.cloudoptimizer.domain.repository.ImplementedRecommendationRepository;
import com.microsoft.cloudoptimizer.domain.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing recommendation lifecycle operations.
 *
 * Handles dismissal, implementation tracking, and status transitions
 * for cost optimization recommendations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final ImplementedRecommendationRepository implementedRecommendationRepository;

    /**
     * Dismiss a recommendation for a tenant.
     *
     * @param tenantId The tenant identifier
     * @param recommendationId The recommendation ID to dismiss
     * @param reason The dismissal reason provided by user
     * @param actionedBy The user who dismissed the recommendation
     * @return The dismissed recommendation, or empty if not found
     */
    @Transactional
    public Optional<Recommendation> dismiss(String tenantId, Long recommendationId,
                                            String reason, String actionedBy) {
        log.info("Dismissing recommendation {} for tenant {} by {}",
                recommendationId, tenantId, actionedBy);

        var recommendation = recommendationRepository.findByIdAndTenantId(recommendationId, tenantId);

        if (recommendation.isEmpty()) {
            log.warn("Recommendation {} not found for tenant {}", recommendationId, tenantId);
            return Optional.empty();
        }

        var rec = recommendation.get();

        // Only active recommendations can be dismissed
        if (rec.getStatus() != RecommendationStatus.ACTIVE) {
            log.warn("Cannot dismiss recommendation {} with status {}",
                    recommendationId, rec.getStatus());
            return Optional.empty();
        }

        rec.setStatus(RecommendationStatus.DISMISSED);
        rec.setFeedback(reason);
        rec.setActionedBy(actionedBy);
        rec.setActionedAt(LocalDateTime.now());

        var saved = recommendationRepository.save(rec);
        log.info("Recommendation {} dismissed successfully", recommendationId);

        return Optional.of(saved);
    }

    /**
     * Restore a dismissed recommendation back to active status.
     *
     * @param tenantId The tenant identifier
     * @param recommendationId The recommendation ID to undismiss
     * @return The restored recommendation, or empty if not found
     */
    @Transactional
    public Optional<Recommendation> undismiss(String tenantId, Long recommendationId) {
        log.info("Undismissing recommendation {} for tenant {}", recommendationId, tenantId);

        var recommendation = recommendationRepository.findByIdAndTenantId(recommendationId, tenantId);

        if (recommendation.isEmpty()) {
            log.warn("Recommendation {} not found for tenant {}", recommendationId, tenantId);
            return Optional.empty();
        }

        var rec = recommendation.get();

        // Only dismissed recommendations can be undismissed
        if (rec.getStatus() != RecommendationStatus.DISMISSED) {
            log.warn("Cannot undismiss recommendation {} with status {}",
                    recommendationId, rec.getStatus());
            return Optional.empty();
        }

        rec.setStatus(RecommendationStatus.ACTIVE);
        rec.setFeedback(null);
        rec.setActionedBy(null);
        rec.setActionedAt(null);
        // Refresh expiration
        rec.setExpiresAt(LocalDateTime.now().plusDays(7));

        var saved = recommendationRepository.save(rec);
        log.info("Recommendation {} restored to active status", recommendationId);

        return Optional.of(saved);
    }

    /**
     * Find all dismissed recommendations for a tenant.
     *
     * @param tenantId The tenant identifier
     * @return List of dismissed recommendations ordered by dismissal date
     */
    public List<Recommendation> findDismissedByTenant(String tenantId) {
        log.debug("Finding dismissed recommendations for tenant {}", tenantId);
        return recommendationRepository.findByTenantIdAndStatusOrderByActionedAtDesc(
                tenantId, RecommendationStatus.DISMISSED);
    }

    /**
     * Find a recommendation by ID and tenant.
     *
     * @param tenantId The tenant identifier
     * @param recommendationId The recommendation ID
     * @return The recommendation if found
     */
    public Optional<Recommendation> findByIdAndTenant(String tenantId, Long recommendationId) {
        return recommendationRepository.findByIdAndTenantId(recommendationId, tenantId);
    }

    /**
     * Check if a resource has any active, non-dismissed recommendations.
     *
     * @param tenantId The tenant identifier
     * @param resourceId The resource identifier
     * @return true if there are active recommendations
     */
    public boolean hasActiveRecommendations(String tenantId, String resourceId) {
        // This could be optimized with a specific query if needed
        return recommendationRepository.findByTenantIdAndStatusOrderByEstimatedMonthlySavingsDesc(
                tenantId, RecommendationStatus.ACTIVE)
                .stream()
                .anyMatch(r -> r.getResourceId().equals(resourceId));
    }

    /**
     * Mark a recommendation as implemented and create tracking record.
     *
     * @param tenantId The tenant identifier
     * @param recommendationId The recommendation ID to mark as implemented
     * @param implementedBy The user who implemented
     * @return The implementation record, or empty if recommendation not found
     */
    @Transactional
    public Optional<ImplementedRecommendation> markImplemented(String tenantId, Long recommendationId,
                                                                String implementedBy) {
        log.info("Marking recommendation {} as implemented for tenant {} by {}",
                recommendationId, tenantId, implementedBy);

        var recommendation = recommendationRepository.findByIdAndTenantId(recommendationId, tenantId);

        if (recommendation.isEmpty()) {
            log.warn("Recommendation {} not found for tenant {}", recommendationId, tenantId);
            return Optional.empty();
        }

        var rec = recommendation.get();

        // Check if already implemented
        var existing = implementedRecommendationRepository.findByRecommendationIdAndTenantId(
                recommendationId, tenantId);
        if (existing.isPresent()) {
            log.warn("Recommendation {} already implemented", recommendationId);
            return existing;
        }

        // Update recommendation status
        rec.setStatus(RecommendationStatus.IMPLEMENTED);
        rec.setActionedBy(implementedBy);
        rec.setActionedAt(LocalDateTime.now());
        recommendationRepository.save(rec);

        // Create implementation tracking record
        var implRec = ImplementedRecommendation.builder()
                .recommendationId(recommendationId)
                .tenantId(tenantId)
                .resourceId(rec.getResourceId())
                .provider(rec.getProvider())
                .resourceType(rec.getResourceType())
                .implementedAt(LocalDateTime.now())
                .implementedBy(implementedBy)
                .expectedMonthlySavings(rec.getEstimatedMonthlySavings())
                .validationStatus(ValidationStatus.PENDING)
                .scheduledValidationAt(LocalDateTime.now().plusDays(30))
                .action(rec.getAction())
                .implementationSummary(rec.getSummary())
                .build();

        var saved = implementedRecommendationRepository.save(implRec);
        log.info("Created implementation record {} for recommendation {}", saved.getId(), recommendationId);

        return Optional.of(saved);
    }

    /**
     * Get savings metrics for a tenant.
     */
    public SavingsMetrics getSavingsMetrics(String tenantId) {
        var totalExpected = implementedRecommendationRepository.calculateTotalExpectedSavings(tenantId);
        var totalValidated = implementedRecommendationRepository.calculateTotalValidatedSavings(tenantId);
        var implementedCount = implementedRecommendationRepository.countByTenantId(tenantId);
        var validatedCount = implementedRecommendationRepository.countByTenantIdAndValidationStatus(
                tenantId, ValidationStatus.VALIDATED);

        double successRate = implementedCount > 0 ?
                (double) validatedCount / implementedCount * 100 : 0.0;

        return new SavingsMetrics(
                totalExpected != null ? totalExpected.doubleValue() : 0.0,
                totalValidated != null ? totalValidated.doubleValue() : 0.0,
                (int) implementedCount,
                successRate
        );
    }

    /**
     * Get implemented recommendations for a tenant.
     */
    public List<ImplementedRecommendation> getImplementedRecommendations(String tenantId) {
        return implementedRecommendationRepository.findByTenantIdOrderByImplementedAtDesc(tenantId);
    }

    /**
     * DTO for savings metrics.
     */
    public record SavingsMetrics(
            double totalExpectedSavings,
            double totalValidatedSavings,
            int implementedCount,
            double validationSuccessRate
    ) {}
}
