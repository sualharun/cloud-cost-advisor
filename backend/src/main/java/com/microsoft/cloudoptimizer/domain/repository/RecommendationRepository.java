package com.microsoft.cloudoptimizer.domain.repository;

import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.Recommendation;
import com.microsoft.cloudoptimizer.domain.model.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    /**
     * Find active recommendations for a specific resource.
     * Used by the browser extension for real-time display.
     */
    List<Recommendation> findByTenantIdAndProviderAndResourceIdAndStatus(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            RecommendationStatus status
    );

    /**
     * Find all active recommendations for a tenant.
     */
    List<Recommendation> findByTenantIdAndStatusOrderByEstimatedMonthlySavingsDesc(
            String tenantId,
            RecommendationStatus status
    );

    /**
     * Find recommendations with highest savings potential.
     */
    @Query("SELECT r FROM Recommendation r WHERE r.tenantId = :tenantId " +
           "AND r.status = :status " +
           "ORDER BY r.estimatedMonthlySavings DESC")
    List<Recommendation> findTopRecommendations(
            @Param("tenantId") String tenantId,
            @Param("status") RecommendationStatus status
    );

    /**
     * Expire old recommendations.
     */
    @Modifying
    @Query("UPDATE Recommendation r SET r.status = 'EXPIRED' " +
           "WHERE r.status = 'ACTIVE' AND r.expiresAt < :now")
    int expireOldRecommendations(@Param("now") LocalDateTime now);

    /**
     * Find existing recommendation to avoid duplicates.
     */
    Optional<Recommendation> findByTenantIdAndProviderAndResourceIdAndActionAndStatus(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            com.microsoft.cloudoptimizer.domain.model.RecommendationAction action,
            RecommendationStatus status
    );

    /**
     * Calculate total potential savings for a tenant.
     */
    @Query("SELECT SUM(r.estimatedMonthlySavings) FROM Recommendation r " +
           "WHERE r.tenantId = :tenantId AND r.status = 'ACTIVE'")
    java.math.BigDecimal calculateTotalPotentialSavings(@Param("tenantId") String tenantId);

    /**
     * Count recommendations by status for dashboard.
     */
    @Query("SELECT r.status, COUNT(r) FROM Recommendation r " +
           "WHERE r.tenantId = :tenantId GROUP BY r.status")
    List<Object[]> countByStatus(@Param("tenantId") String tenantId);
}
