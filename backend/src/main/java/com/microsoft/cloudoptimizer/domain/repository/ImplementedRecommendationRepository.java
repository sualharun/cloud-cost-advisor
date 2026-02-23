package com.microsoft.cloudoptimizer.domain.repository;

import com.microsoft.cloudoptimizer.domain.model.ImplementedRecommendation;
import com.microsoft.cloudoptimizer.domain.model.ValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImplementedRecommendationRepository extends JpaRepository<ImplementedRecommendation, Long> {

    /**
     * Find all implemented recommendations for a tenant, ordered by implementation date.
     */
    List<ImplementedRecommendation> findByTenantIdOrderByImplementedAtDesc(String tenantId);

    /**
     * Find implemented recommendations pending validation that are past their scheduled date.
     */
    List<ImplementedRecommendation> findByValidationStatusAndScheduledValidationAtBefore(
            ValidationStatus status, LocalDateTime before);

    /**
     * Find implementation record by the original recommendation ID.
     */
    Optional<ImplementedRecommendation> findByRecommendationIdAndTenantId(
            Long recommendationId, String tenantId);

    /**
     * Calculate total validated savings for a tenant.
     */
    @Query("SELECT COALESCE(SUM(ir.actualMonthlySavings), 0) FROM ImplementedRecommendation ir " +
           "WHERE ir.tenantId = :tenantId AND ir.validationStatus = 'VALIDATED'")
    BigDecimal calculateTotalValidatedSavings(@Param("tenantId") String tenantId);

    /**
     * Calculate total expected savings for a tenant (for pending implementations).
     */
    @Query("SELECT COALESCE(SUM(ir.expectedMonthlySavings), 0) FROM ImplementedRecommendation ir " +
           "WHERE ir.tenantId = :tenantId")
    BigDecimal calculateTotalExpectedSavings(@Param("tenantId") String tenantId);

    /**
     * Count implementations by validation status for a tenant.
     */
    @Query("SELECT ir.validationStatus, COUNT(ir) FROM ImplementedRecommendation ir " +
           "WHERE ir.tenantId = :tenantId GROUP BY ir.validationStatus")
    List<Object[]> countByValidationStatus(@Param("tenantId") String tenantId);

    /**
     * Count total implementations for a tenant.
     */
    long countByTenantId(String tenantId);

    /**
     * Count validated implementations for a tenant.
     */
    long countByTenantIdAndValidationStatus(String tenantId, ValidationStatus status);

    /**
     * Find implementations for a specific resource.
     */
    List<ImplementedRecommendation> findByTenantIdAndResourceIdOrderByImplementedAtDesc(
            String tenantId, String resourceId);
}
