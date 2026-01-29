package com.microsoft.cloudoptimizer.adapters;

import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.CostRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port interface for cloud cost data ingestion.
 *
 * ADAPTER PATTERN:
 * Each cloud provider implements this interface to normalize their
 * proprietary billing data into our unified CostRecord schema.
 *
 * IMPLEMENTATION REQUIREMENTS:
 * 1. Handle rate limiting and retry logic
 * 2. Implement credential refresh for long-running jobs
 * 3. Log all API calls for audit
 * 4. Convert all currencies to USD
 * 5. Map provider SKUs to our resource types
 */
public interface CloudCostAdapter {

    /**
     * Returns the cloud provider this adapter handles.
     */
    CloudProvider getProvider();

    /**
     * Fetch cost data for a specific resource.
     *
     * @param tenantId Customer tenant identifier
     * @param resourceId Provider-native resource identifier
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return List of normalized cost records
     */
    List<CostRecord> fetchCostData(
            String tenantId,
            String resourceId,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Fetch resource metadata for real-time analysis.
     * Called when the browser extension requests immediate analysis.
     *
     * @param tenantId Customer tenant identifier
     * @param resourceId Provider-native resource identifier
     * @return Resource metadata including current configuration
     */
    Optional<ResourceMetadata> fetchResourceMetadata(
            String tenantId,
            String resourceId
    );

    /**
     * Validate that credentials are configured and valid.
     */
    boolean validateCredentials(String tenantId);

    /**
     * Get current pricing for a resource SKU in a region.
     *
     * @param sku Provider-specific SKU identifier
     * @param region Provider-specific region identifier
     * @return Hourly price in USD
     */
    Optional<Double> getSkuPricing(String sku, String region);

    /**
     * List available SKUs for rightsizing recommendations.
     *
     * @param resourceType The normalized resource type
     * @param region Target region
     * @return Available SKUs with specifications
     */
    List<SkuInfo> listAvailableSkus(
            com.microsoft.cloudoptimizer.domain.model.ResourceType resourceType,
            String region
    );

    /**
     * Fetch utilization metrics for a resource.
     */
    Optional<UtilizationMetrics> fetchUtilizationMetrics(
            String tenantId,
            String resourceId,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Resource metadata DTO.
     */
    record ResourceMetadata(
            String resourceId,
            String resourceName,
            com.microsoft.cloudoptimizer.domain.model.ResourceType resourceType,
            String sku,
            String region,
            Integer vcpu,
            Double memoryGb,
            Double storageGb,
            Map<String, String> tags,
            String status
    ) {}

    /**
     * SKU information for rightsizing.
     */
    record SkuInfo(
            String sku,
            String displayName,
            Integer vcpu,
            Double memoryGb,
            Double hourlyPrice,
            String family
    ) {}

    /**
     * Utilization metrics DTO.
     */
    record UtilizationMetrics(
            Double avgCpuUtilization,
            Double maxCpuUtilization,
            Double avgMemoryUtilization,
            Double maxMemoryUtilization,
            Double avgNetworkMbps,
            Double avgDiskIops,
            int dataPointCount
    ) {}
}
