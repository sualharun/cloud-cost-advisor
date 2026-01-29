package com.microsoft.cloudoptimizer.domain.repository;

import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.CostRecord;
import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CostRecordRepository extends JpaRepository<CostRecord, Long> {

    /**
     * Find all cost records for a specific resource within a date range.
     */
    List<CostRecord> findByTenantIdAndProviderAndResourceIdAndRecordDateBetween(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Find the most recent cost record for a resource.
     */
    @Query("SELECT c FROM CostRecord c WHERE c.tenantId = :tenantId " +
           "AND c.provider = :provider AND c.resourceId = :resourceId " +
           "ORDER BY c.recordDate DESC LIMIT 1")
    CostRecord findLatestByResource(
            @Param("tenantId") String tenantId,
            @Param("provider") CloudProvider provider,
            @Param("resourceId") String resourceId
    );

    /**
     * Calculate total cost for a tenant over a date range.
     */
    @Query("SELECT SUM(c.dailyCost) FROM CostRecord c WHERE c.tenantId = :tenantId " +
           "AND c.recordDate BETWEEN :startDate AND :endDate")
    BigDecimal calculateTotalCost(
            @Param("tenantId") String tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Find resources with low utilization.
     */
    @Query("SELECT c FROM CostRecord c WHERE c.tenantId = :tenantId " +
           "AND c.resourceType = :resourceType " +
           "AND c.avgCpuUtilization < :threshold " +
           "AND c.recordDate >= :sinceDate " +
           "ORDER BY c.dailyCost DESC")
    List<CostRecord> findUnderutilizedResources(
            @Param("tenantId") String tenantId,
            @Param("resourceType") ResourceType resourceType,
            @Param("threshold") Double threshold,
            @Param("sinceDate") LocalDate sinceDate
    );

    /**
     * Get cost time series for forecasting.
     */
    @Query("SELECT c FROM CostRecord c WHERE c.tenantId = :tenantId " +
           "AND c.provider = :provider AND c.resourceId = :resourceId " +
           "AND c.recordDate >= :sinceDate " +
           "ORDER BY c.recordDate ASC")
    List<CostRecord> getCostTimeSeries(
            @Param("tenantId") String tenantId,
            @Param("provider") CloudProvider provider,
            @Param("resourceId") String resourceId,
            @Param("sinceDate") LocalDate sinceDate
    );

    /**
     * Get average utilization metrics for a resource.
     */
    @Query("SELECT AVG(c.avgCpuUtilization), AVG(c.avgMemoryUtilization), AVG(c.dailyCost) " +
           "FROM CostRecord c WHERE c.tenantId = :tenantId " +
           "AND c.provider = :provider AND c.resourceId = :resourceId " +
           "AND c.recordDate >= :sinceDate")
    Object[] getAverageMetrics(
            @Param("tenantId") String tenantId,
            @Param("provider") CloudProvider provider,
            @Param("resourceId") String resourceId,
            @Param("sinceDate") LocalDate sinceDate
    );

    /**
     * Check if cost data exists for a resource.
     */
    boolean existsByTenantIdAndProviderAndResourceId(
            String tenantId,
            CloudProvider provider,
            String resourceId
    );
}
