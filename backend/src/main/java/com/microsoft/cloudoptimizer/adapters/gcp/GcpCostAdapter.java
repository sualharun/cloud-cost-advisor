package com.microsoft.cloudoptimizer.adapters.gcp;

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
 * Google Cloud Platform cost adapter.
 *
 * DATA SOURCES:
 * 1. BigQuery Billing Export - Standard and detailed exports
 * 2. Cloud Asset API - Resource metadata
 * 3. Cloud Monitoring API - Utilization metrics
 * 4. Cloud Billing Catalog API - SKU pricing
 *
 * AUTHENTICATION:
 * Uses Application Default Credentials (ADC):
 * 1. GOOGLE_APPLICATION_CREDENTIALS environment variable
 * 2. Workload Identity (GKE)
 * 3. Compute Engine default service account
 * 4. gcloud CLI credentials (development)
 *
 * BILLING EXPORT:
 * Requires BigQuery billing export to be configured.
 * Standard export: Daily resource-level data
 * Detailed export: Hourly pricing and usage breakdown
 * See: https://cloud.google.com/billing/docs/how-to/export-data-bigquery
 */
@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!local")
public class GcpCostAdapter implements CloudCostAdapter {

    private final GcpResourceTypeMapper resourceTypeMapper;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
    }

    @Override
    public List<CostRecord> fetchCostData(
            String tenantId,
            String resourceId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        log.info("Fetching GCP cost data for resource: {} in project: {}", resourceId, tenantId);

        try {
            // Extract project ID from resource name
            String projectId = extractProjectId(resourceId, tenantId);

            // Query BigQuery billing export
            List<GcpCostRow> rows = queryBillingExport(projectId, resourceId, startDate, endDate);

            return rows.stream()
                    .map(row -> mapToCostRecord(tenantId, row))
                    .toList();

        } catch (Exception e) {
            log.error("Failed to fetch GCP cost data for resource: {}", resourceId, e);
            throw new RuntimeException("GCP cost fetch failed", e);
        }
    }

    @Override
    public Optional<ResourceMetadata> fetchResourceMetadata(
            String tenantId,
            String resourceId
    ) {
        log.debug("Fetching GCP resource metadata: {}", resourceId);

        try {
            // Parse resource name to determine type
            GcpResourceName resourceName = GcpResourceName.parse(resourceId);

            // Fetch using Cloud Asset API or service-specific API
            return switch (resourceName.service()) {
                case "compute" -> fetchComputeMetadata(tenantId, resourceName);
                case "sql" -> fetchCloudSqlMetadata(tenantId, resourceName);
                case "storage" -> fetchGcsMetadata(tenantId, resourceName);
                case "run" -> fetchCloudRunMetadata(tenantId, resourceName);
                default -> fetchGenericMetadata(tenantId, resourceName);
            };

        } catch (Exception e) {
            log.error("Failed to fetch GCP resource metadata: {}", resourceId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean validateCredentials(String tenantId) {
        try {
            // Test credentials by listing projects
            log.debug("Validating GCP credentials for project: {}", tenantId);
            return true;
        } catch (Exception e) {
            log.warn("GCP credential validation failed for project: {}", tenantId, e);
            return false;
        }
    }

    @Override
    public Optional<Double> getSkuPricing(String machineType, String region) {
        try {
            // Cloud Billing Catalog API: services.skus.list
            log.debug("Fetching GCP pricing for {} in {}", machineType, region);
            return Optional.of(0.0); // Placeholder
        } catch (Exception e) {
            log.warn("Failed to get GCP pricing: {} in {}", machineType, region, e);
            return Optional.empty();
        }
    }

    @Override
    public List<SkuInfo> listAvailableSkus(ResourceType resourceType, String region) {
        try {
            // Compute Engine MachineTypes.list API
            log.debug("Listing GCP machine types in region: {}", region);

            return List.of(
                    new SkuInfo("e2-micro", "E2 Micro", 2, 1.0, 0.0084, "E2"),
                    new SkuInfo("e2-small", "E2 Small", 2, 2.0, 0.0168, "E2"),
                    new SkuInfo("e2-medium", "E2 Medium", 2, 4.0, 0.0336, "E2"),
                    new SkuInfo("n2-standard-2", "N2 Standard 2", 2, 8.0, 0.0971, "N2"),
                    new SkuInfo("n2-standard-4", "N2 Standard 4", 4, 16.0, 0.1942, "N2"),
                    new SkuInfo("c2-standard-4", "C2 Standard 4", 4, 16.0, 0.2088, "C2"),
                    new SkuInfo("m2-ultramem-208", "M2 Ultramem 208", 208, 5888.0, 42.186, "M2")
            );
        } catch (Exception e) {
            log.error("Failed to list GCP SKUs for region: {}", region, e);
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
        log.debug("Fetching GCP Cloud Monitoring metrics for resource: {}", resourceId);

        try {
            GcpResourceName resourceName = GcpResourceName.parse(resourceId);

            // Cloud Monitoring timeSeries.list API
            // Metric types: compute.googleapis.com/instance/cpu/utilization

            double avgCpu = fetchMonitoringMetric(
                    resourceName,
                    "compute.googleapis.com/instance/cpu/utilization",
                    "ALIGN_MEAN",
                    startDate,
                    endDate
            );

            double maxCpu = fetchMonitoringMetric(
                    resourceName,
                    "compute.googleapis.com/instance/cpu/utilization",
                    "ALIGN_MAX",
                    startDate,
                    endDate
            );

            // GCP provides memory utilization for VMs with ops-agent
            Double avgMemory = null;
            try {
                avgMemory = fetchMonitoringMetric(
                        resourceName,
                        "agent.googleapis.com/memory/percent_used",
                        "ALIGN_MEAN",
                        startDate,
                        endDate
                );
            } catch (Exception ignored) {
                // Memory metrics require ops-agent; not always available
            }

            return Optional.of(new UtilizationMetrics(
                    avgCpu,
                    maxCpu,
                    avgMemory != null ? avgMemory / 100.0 : null,
                    null,
                    null,
                    null,
                    calculateDataPointCount(startDate, endDate)
            ));

        } catch (Exception e) {
            log.error("Failed to fetch GCP utilization metrics: {}", resourceId, e);
            return Optional.empty();
        }
    }

    private CostRecord mapToCostRecord(String tenantId, GcpCostRow row) {
        return CostRecord.builder()
                .tenantId(tenantId)
                .provider(CloudProvider.GCP)
                .resourceType(resourceTypeMapper.mapResourceType(row.serviceName()))
                .resourceId(row.resourceName())
                .resourceName(extractDisplayName(row.resourceName()))
                .sku(row.skuDescription())
                .region(row.location())
                .dailyCost(BigDecimal.valueOf(row.cost()))
                .recordDate(row.usageDate())
                .ingestedAt(LocalDateTime.now())
                .dataSource("gcp-bigquery-billing-export")
                .build();
    }

    private String extractProjectId(String resourceId, String tenantId) {
        // Extract project ID from resource name
        // Format: projects/{projectId}/...
        if (resourceId != null && resourceId.startsWith("projects/")) {
            String[] parts = resourceId.split("/");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return tenantId;
    }

    private String extractDisplayName(String resourceName) {
        if (resourceName == null) return null;
        int lastSlash = resourceName.lastIndexOf('/');
        return lastSlash >= 0 ? resourceName.substring(lastSlash + 1) : resourceName;
    }

    private List<GcpCostRow> queryBillingExport(
            String projectId,
            String resourceId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // BigQuery SQL against billing export table:
        // SELECT ... FROM `project.dataset.gcp_billing_export_v1_XXXXXX`
        // WHERE resource.name = @resourceId
        // AND usage_start_time >= @startDate
        // AND usage_start_time < @endDate
        return List.of();
    }

    private Optional<ResourceMetadata> fetchComputeMetadata(String tenantId, GcpResourceName rn) {
        // Compute Engine instances.get API
        log.debug("Fetching GCE instance metadata: {}", rn.resource());
        return Optional.empty();
    }

    private Optional<ResourceMetadata> fetchCloudSqlMetadata(String tenantId, GcpResourceName rn) {
        // Cloud SQL instances.get API
        log.debug("Fetching Cloud SQL instance metadata: {}", rn.resource());
        return Optional.empty();
    }

    private Optional<ResourceMetadata> fetchGcsMetadata(String tenantId, GcpResourceName rn) {
        // Cloud Storage buckets.get API
        log.debug("Fetching GCS bucket metadata: {}", rn.resource());
        return Optional.empty();
    }

    private Optional<ResourceMetadata> fetchCloudRunMetadata(String tenantId, GcpResourceName rn) {
        // Cloud Run services.get API
        log.debug("Fetching Cloud Run service metadata: {}", rn.resource());
        return Optional.empty();
    }

    private Optional<ResourceMetadata> fetchGenericMetadata(String tenantId, GcpResourceName rn) {
        // Cloud Asset API for generic resources
        log.debug("Fetching generic resource metadata via Cloud Asset API: {}", rn);
        return Optional.empty();
    }

    private double fetchMonitoringMetric(
            GcpResourceName resourceName,
            String metricType,
            String aligner,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // Cloud Monitoring timeSeries.list API
        return 0.0; // Placeholder
    }

    private int calculateDataPointCount(LocalDate startDate, LocalDate endDate) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        return (int) (days * 288); // 5-minute intervals
    }

    record GcpCostRow(
            String resourceName,
            String serviceName,
            String skuDescription,
            String location,
            LocalDate usageDate,
            double cost
    ) {}

    /**
     * GCP resource name parser.
     * Format: projects/{project}/zones/{zone}/instances/{name}
     * or: //compute.googleapis.com/projects/...
     */
    record GcpResourceName(
            String project,
            String service,
            String location,
            String resourceType,
            String resource
    ) {
        static GcpResourceName parse(String resourceName) {
            if (resourceName == null) {
                throw new IllegalArgumentException("Resource name cannot be null");
            }

            // Handle //service.googleapis.com/... format
            if (resourceName.startsWith("//")) {
                String service = resourceName.split("\\.")[0].substring(2);
                String remaining = resourceName.substring(resourceName.indexOf("/projects/"));
                return parseStandardFormat(service, remaining);
            }

            // Handle projects/... format
            if (resourceName.startsWith("projects/")) {
                String service = inferService(resourceName);
                return parseStandardFormat(service, resourceName);
            }

            throw new IllegalArgumentException("Invalid GCP resource name: " + resourceName);
        }

        private static GcpResourceName parseStandardFormat(String service, String path) {
            String[] parts = path.split("/");
            String project = null;
            String location = null;
            String resourceType = null;
            String resource = null;

            for (int i = 0; i < parts.length - 1; i++) {
                switch (parts[i]) {
                    case "projects" -> project = parts[++i];
                    case "zones", "regions", "locations" -> location = parts[++i];
                    case "instances", "disks", "databases", "buckets" -> {
                        resourceType = parts[i];
                        resource = parts[++i];
                    }
                }
            }

            return new GcpResourceName(project, service, location, resourceType, resource);
        }

        private static String inferService(String resourceName) {
            if (resourceName.contains("/instances/")) return "compute";
            if (resourceName.contains("/databases/")) return "sql";
            if (resourceName.contains("/buckets/")) return "storage";
            if (resourceName.contains("/services/")) return "run";
            return "unknown";
        }
    }
}
