package com.microsoft.cloudoptimizer.normalization;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.CostRecord;
import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import com.microsoft.cloudoptimizer.domain.repository.CostRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central service for normalizing cost data across cloud providers.
 *
 * NORMALIZATION PRINCIPLES:
 * 1. Currency: All costs converted to USD
 * 2. Time: UTC timezone, daily granularity
 * 3. Resource Types: Mapped to unified taxonomy
 * 4. Metrics: Scaled to consistent units (GB, vCPU, Mbps)
 *
 * This service acts as the boundary between provider-specific adapters
 * and the provider-agnostic ML/recommendation layers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostNormalizationService {

    private final Map<CloudProvider, CloudCostAdapter> adapters;
    private final CostRecordRepository costRecordRepository;
    private final RegionNormalizer regionNormalizer;

    /**
     * Fetch and normalize cost data for a resource.
     *
     * @param tenantId Customer tenant
     * @param provider Cloud provider
     * @param resourceId Provider-native resource ID
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return Normalized cost records
     */
    @Transactional
    public List<CostRecord> fetchAndNormalizeCosts(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        log.info("Normalizing costs for {} resource: {}", provider, resourceId);

        CloudCostAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }

        // Fetch raw cost data from provider
        List<CostRecord> rawRecords = adapter.fetchCostData(
                tenantId, resourceId, startDate, endDate
        );

        // Apply normalizations
        List<CostRecord> normalizedRecords = rawRecords.stream()
                .map(this::normalizeRecord)
                .toList();

        // Persist normalized records
        costRecordRepository.saveAll(normalizedRecords);

        log.info("Normalized {} cost records for resource: {}", normalizedRecords.size(), resourceId);
        return normalizedRecords;
    }

    /**
     * Get normalized cost summary for a resource.
     * Includes aggregated metrics across the date range.
     */
    public NormalizedResourceCost getResourceCostSummary(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<CostRecord> records = costRecordRepository
                .findByTenantIdAndProviderAndResourceIdAndRecordDateBetween(
                        tenantId, provider, resourceId, startDate, endDate
                );

        if (records.isEmpty()) {
            return null;
        }

        // Calculate aggregates
        double totalCost = records.stream()
                .mapToDouble(r -> r.getDailyCost().doubleValue())
                .sum();

        double avgDailyCost = totalCost / records.size();

        Double avgCpuUtilization = records.stream()
                .filter(r -> r.getAvgCpuUtilization() != null)
                .mapToDouble(CostRecord::getAvgCpuUtilization)
                .average()
                .orElse(Double.NaN);

        Double avgMemoryUtilization = records.stream()
                .filter(r -> r.getAvgMemoryUtilization() != null)
                .mapToDouble(CostRecord::getAvgMemoryUtilization)
                .average()
                .orElse(Double.NaN);

        CostRecord latest = records.get(records.size() - 1);

        return new NormalizedResourceCost(
                tenantId,
                provider,
                resourceId,
                latest.getResourceName(),
                latest.getResourceType(),
                latest.getSku(),
                regionNormalizer.toCanonicalRegion(provider, latest.getRegion()),
                latest.getVcpu(),
                latest.getMemoryGb(),
                latest.getStorageGb(),
                totalCost,
                avgDailyCost,
                Double.isNaN(avgCpuUtilization) ? null : avgCpuUtilization,
                Double.isNaN(avgMemoryUtilization) ? null : avgMemoryUtilization,
                records.size(),
                startDate,
                endDate
        );
    }

    /**
     * Get time series data for forecasting.
     */
    public List<CostTimeSeriesPoint> getCostTimeSeries(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            int daysBack
    ) {
        LocalDate sinceDate = LocalDate.now().minusDays(daysBack);

        List<CostRecord> records = costRecordRepository.getCostTimeSeries(
                tenantId, provider, resourceId, sinceDate
        );

        return records.stream()
                .map(r -> new CostTimeSeriesPoint(
                        r.getRecordDate(),
                        r.getDailyCost().doubleValue(),
                        r.getAvgCpuUtilization(),
                        r.getAvgMemoryUtilization()
                ))
                .toList();
    }

    /**
     * Check if sufficient data exists for ML analysis.
     */
    public boolean hasSufficientData(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            int minimumDays
    ) {
        LocalDate sinceDate = LocalDate.now().minusDays(minimumDays);

        List<CostRecord> records = costRecordRepository.getCostTimeSeries(
                tenantId, provider, resourceId, sinceDate
        );

        // Need at least 70% coverage for reliable analysis
        return records.size() >= (minimumDays * 0.7);
    }

    private CostRecord normalizeRecord(CostRecord record) {
        // Normalize region to canonical format
        String canonicalRegion = regionNormalizer.toCanonicalRegion(
                record.getProvider(), record.getRegion()
        );
        record.setRegion(canonicalRegion);

        // Ensure utilization metrics are in 0-1 range
        if (record.getAvgCpuUtilization() != null) {
            record.setAvgCpuUtilization(
                    Math.min(1.0, Math.max(0.0, record.getAvgCpuUtilization()))
            );
        }

        if (record.getAvgMemoryUtilization() != null) {
            record.setAvgMemoryUtilization(
                    Math.min(1.0, Math.max(0.0, record.getAvgMemoryUtilization()))
            );
        }

        return record;
    }

    /**
     * Normalized resource cost summary for ML and recommendations.
     */
    public record NormalizedResourceCost(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            String resourceName,
            ResourceType resourceType,
            String sku,
            String canonicalRegion,
            Integer vcpu,
            Double memoryGb,
            Double storageGb,
            double totalCost,
            double avgDailyCost,
            Double avgCpuUtilization,
            Double avgMemoryUtilization,
            int dataPointCount,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {}

    /**
     * Time series point for forecasting input.
     */
    public record CostTimeSeriesPoint(
            LocalDate date,
            double cost,
            Double cpuUtilization,
            Double memoryUtilization
    ) {}
}
