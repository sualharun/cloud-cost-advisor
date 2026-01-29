package com.microsoft.cloudoptimizer.adapters.azure;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.CostRecord;
import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Azure Cost Management adapter.
 *
 * DATA SOURCES:
 * 1. Azure Cost Management API - Detailed usage and cost data
 * 2. Azure Resource Manager API - Resource metadata
 * 3. Azure Monitor API - Utilization metrics
 * 4. Azure Retail Prices API - SKU pricing
 *
 * AUTHENTICATION:
 * Uses Azure Identity library with DefaultAzureCredential.
 * Supports: Managed Identity, Service Principal, Azure CLI, etc.
 *
 * COST EXPORT INTEGRATION:
 * For large-scale deployments, configure Azure Cost Management exports
 * to Blob Storage. This adapter can read from exports for batch processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!local")
public class AzureCostAdapter implements CloudCostAdapter {

    private final AzureClientFactory clientFactory;
    private final AzureResourceTypeMapper resourceTypeMapper;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    @Override
    public List<CostRecord> fetchCostData(
            String tenantId,
            String resourceId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        log.info("Fetching Azure cost data for resource: {} in tenant: {}", resourceId, tenantId);

        try {
            var costClient = clientFactory.getCostManagementClient(tenantId);
            var scope = extractScope(resourceId);

            // Azure Cost Management query
            var queryResult = costClient.queryUsage(
                    scope,
                    startDate,
                    endDate,
                    resourceId
            );

            return queryResult.stream()
                    .map(row -> mapToCostRecord(tenantId, row))
                    .toList();

        } catch (Exception e) {
            log.error("Failed to fetch Azure cost data for resource: {}", resourceId, e);
            throw new CloudAdapterException("Azure cost fetch failed", e);
        }
    }

    @Override
    public Optional<ResourceMetadata> fetchResourceMetadata(
            String tenantId,
            String resourceId
    ) {
        log.debug("Fetching Azure resource metadata: {}", resourceId);

        try {
            var armClient = clientFactory.getResourceManagementClient(tenantId);
            var resource = armClient.getResource(resourceId);

            if (resource == null) {
                return Optional.empty();
            }

            return Optional.of(new ResourceMetadata(
                    resourceId,
                    resource.name(),
                    resourceTypeMapper.mapResourceType(resource.type()),
                    resource.sku() != null ? resource.sku().name() : null,
                    resource.location(),
                    extractVcpu(resource),
                    extractMemoryGb(resource),
                    extractStorageGb(resource),
                    resource.tags() != null ? resource.tags() : Map.of(),
                    resource.provisioningState()
            ));

        } catch (Exception e) {
            log.error("Failed to fetch Azure resource metadata: {}", resourceId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean validateCredentials(String tenantId) {
        try {
            var client = clientFactory.getCostManagementClient(tenantId);
            // Attempt a minimal API call to validate credentials
            client.validateAccess();
            return true;
        } catch (Exception e) {
            log.warn("Azure credential validation failed for tenant: {}", tenantId, e);
            return false;
        }
    }

    @Override
    public Optional<Double> getSkuPricing(String sku, String region) {
        try {
            var priceClient = clientFactory.getRetailPriceClient();
            var price = priceClient.getPrice(sku, region);
            return Optional.ofNullable(price);
        } catch (Exception e) {
            log.warn("Failed to get Azure SKU pricing: {} in {}", sku, region, e);
            return Optional.empty();
        }
    }

    @Override
    public List<SkuInfo> listAvailableSkus(ResourceType resourceType, String region) {
        try {
            var computeClient = clientFactory.getComputeClient();
            return computeClient.listVmSizes(region).stream()
                    .map(size -> new SkuInfo(
                            size.name(),
                            size.name(),
                            size.numberOfCores(),
                            size.memoryInMB() / 1024.0,
                            getSkuPricing(size.name(), region).orElse(0.0),
                            extractFamily(size.name())
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to list Azure SKUs for region: {}", region, e);
            return List.of();
        }
    }

    @Override
    public Optional<UtilizationMetrics> fetchUtilizationMetrics(
            String tenantId,
            String resourceId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        log.debug("Fetching Azure metrics for resource: {}", resourceId);

        try {
            var monitorClient = clientFactory.getMonitorClient(tenantId);

            var cpuMetrics = monitorClient.getMetric(
                    resourceId,
                    "Percentage CPU",
                    startDate,
                    endDate
            );

            var memoryMetrics = monitorClient.getMetric(
                    resourceId,
                    "Available Memory Bytes",
                    startDate,
                    endDate
            );

            var networkMetrics = monitorClient.getMetric(
                    resourceId,
                    "Network In Total",
                    startDate,
                    endDate
            );

            return Optional.of(new UtilizationMetrics(
                    cpuMetrics.average() / 100.0,
                    cpuMetrics.maximum() / 100.0,
                    calculateMemoryUtilization(memoryMetrics),
                    calculateMaxMemoryUtilization(memoryMetrics),
                    networkMetrics.average(),
                    null,
                    cpuMetrics.dataPointCount()
            ));

        } catch (Exception e) {
            log.error("Failed to fetch Azure utilization metrics: {}", resourceId, e);
            return Optional.empty();
        }
    }

    private CostRecord mapToCostRecord(String tenantId, AzureCostRow row) {
        return CostRecord.builder()
                .tenantId(tenantId)
                .provider(CloudProvider.AZURE)
                .resourceType(resourceTypeMapper.mapResourceType(row.resourceType()))
                .resourceId(row.resourceId())
                .resourceName(row.resourceName())
                .sku(row.meterName())
                .region(row.resourceLocation())
                .dailyCost(BigDecimal.valueOf(row.cost()))
                .recordDate(row.usageDate())
                .ingestedAt(LocalDateTime.now())
                .dataSource("azure-cost-management-api")
                .build();
    }

    private String extractScope(String resourceId) {
        // Extract subscription scope from resource ID
        // Format: /subscriptions/{subscriptionId}/...
        var parts = resourceId.split("/");
        if (parts.length >= 3) {
            return "/subscriptions/" + parts[2];
        }
        throw new IllegalArgumentException("Invalid Azure resource ID: " + resourceId);
    }

    private Integer extractVcpu(AzureResource resource) {
        if (resource.properties() == null) return null;
        var vmSize = resource.properties().get("vmSize");
        if (vmSize == null) return null;
        // Parse vCPU from VM size name (e.g., Standard_D4s_v3 -> 4)
        return parseVcpuFromSizeName(vmSize.toString());
    }

    private Double extractMemoryGb(AzureResource resource) {
        if (resource.properties() == null) return null;
        // Memory is typically fetched from SKU capabilities
        return null;
    }

    private Double extractStorageGb(AzureResource resource) {
        if (resource.properties() == null) return null;
        var diskSizeGb = resource.properties().get("diskSizeGB");
        return diskSizeGb != null ? Double.parseDouble(diskSizeGb.toString()) : null;
    }

    private Integer parseVcpuFromSizeName(String sizeName) {
        // Standard_D4s_v3 -> 4
        var pattern = java.util.regex.Pattern.compile("\\d+");
        var matcher = pattern.matcher(sizeName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return null;
    }

    private String extractFamily(String sizeName) {
        // Standard_D4s_v3 -> D-series
        if (sizeName.contains("_D")) return "D-series";
        if (sizeName.contains("_E")) return "E-series";
        if (sizeName.contains("_F")) return "F-series";
        if (sizeName.contains("_B")) return "B-series";
        if (sizeName.contains("_M")) return "M-series";
        return "General";
    }

    private Double calculateMemoryUtilization(MetricResult memoryMetrics) {
        // Convert available memory to utilization percentage
        // This requires knowing total memory which comes from SKU
        return 0.5; // Placeholder - actual implementation needs total memory
    }

    private Double calculateMaxMemoryUtilization(MetricResult memoryMetrics) {
        return 0.7; // Placeholder
    }

    // Inner record types for Azure-specific data
    record AzureCostRow(
            String resourceId,
            String resourceName,
            String resourceType,
            String resourceLocation,
            String meterName,
            LocalDate usageDate,
            double cost
    ) {}

    record AzureResource(
            String id,
            String name,
            String type,
            String location,
            AzureSku sku,
            Map<String, Object> properties,
            Map<String, String> tags,
            String provisioningState
    ) {}

    record AzureSku(String name, String tier, String size) {}

    record MetricResult(double average, double maximum, int dataPointCount) {}

    static class CloudAdapterException extends RuntimeException {
        CloudAdapterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
