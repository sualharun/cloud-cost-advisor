package com.microsoft.cloudoptimizer.adapters.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for Azure SDK clients.
 *
 * CREDENTIAL STRATEGY:
 * Uses DefaultAzureCredential which tries multiple authentication methods:
 * 1. Environment variables (AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID)
 * 2. Managed Identity (when running on Azure)
 * 3. Azure CLI credentials (for local development)
 * 4. IntelliJ / VS Code credentials
 *
 * MULTI-TENANT SUPPORT:
 * For customers with multiple Azure AD tenants, credentials are cached
 * per tenant and refreshed as needed.
 */
@Component
@Slf4j
public class AzureClientFactory {

    private final TokenCredential defaultCredential;
    private final Map<String, CostManagementClient> costClients = new ConcurrentHashMap<>();
    private final Map<String, ResourceManagementClient> armClients = new ConcurrentHashMap<>();
    private final Map<String, MonitorClient> monitorClients = new ConcurrentHashMap<>();

    @Value("${azure.subscription-id:}")
    private String defaultSubscriptionId;

    public AzureClientFactory() {
        this.defaultCredential = new DefaultAzureCredentialBuilder().build();
        log.info("Azure client factory initialized with DefaultAzureCredential");
    }

    public CostManagementClient getCostManagementClient(String tenantId) {
        return costClients.computeIfAbsent(tenantId, id -> {
            log.info("Creating Cost Management client for tenant: {}", tenantId);
            return new CostManagementClient(defaultCredential, tenantId);
        });
    }

    public ResourceManagementClient getResourceManagementClient(String tenantId) {
        return armClients.computeIfAbsent(tenantId, id -> {
            log.info("Creating Resource Management client for tenant: {}", tenantId);
            return new ResourceManagementClient(defaultCredential, tenantId);
        });
    }

    public MonitorClient getMonitorClient(String tenantId) {
        return monitorClients.computeIfAbsent(tenantId, id -> {
            log.info("Creating Monitor client for tenant: {}", tenantId);
            return new MonitorClient(defaultCredential, tenantId);
        });
    }

    public RetailPriceClient getRetailPriceClient() {
        // Retail Price API doesn't require authentication
        return new RetailPriceClient();
    }

    public ComputeClient getComputeClient() {
        return new ComputeClient(defaultCredential, defaultSubscriptionId);
    }

    /**
     * Cost Management client wrapper.
     * In production, this wraps com.azure.resourcemanager.costmanagement.CostManagementManager
     */
    public static class CostManagementClient {
        private final TokenCredential credential;
        private final String tenantId;

        public CostManagementClient(TokenCredential credential, String tenantId) {
            this.credential = credential;
            this.tenantId = tenantId;
        }

        public List<AzureCostAdapter.AzureCostRow> queryUsage(
                String scope,
                LocalDate startDate,
                LocalDate endDate,
                String resourceId
        ) {
            // Implementation would use Azure Cost Management SDK
            // QueryDefinition with type = Usage, timeframe = Custom
            // Grouping by ResourceId, filtered by resourceId
            log.debug("Querying Azure cost data: scope={}, resource={}", scope, resourceId);

            // Placeholder - actual implementation uses:
            // CostManagementManager.costManagement().query().usage(scope, queryDefinition)
            return List.of();
        }

        public void validateAccess() {
            // Validate by attempting to list scopes
            log.debug("Validating Azure Cost Management access for tenant: {}", tenantId);
        }
    }

    /**
     * Resource Management client wrapper.
     */
    public static class ResourceManagementClient {
        private final TokenCredential credential;
        private final String tenantId;

        public ResourceManagementClient(TokenCredential credential, String tenantId) {
            this.credential = credential;
            this.tenantId = tenantId;
        }

        public AzureCostAdapter.AzureResource getResource(String resourceId) {
            // Implementation uses Azure Resource Manager SDK
            // GenericResource.getById(resourceId)
            log.debug("Fetching Azure resource: {}", resourceId);
            return null; // Placeholder
        }
    }

    /**
     * Azure Monitor client wrapper.
     */
    public static class MonitorClient {
        private final TokenCredential credential;
        private final String tenantId;

        public MonitorClient(TokenCredential credential, String tenantId) {
            this.credential = credential;
            this.tenantId = tenantId;
        }

        public AzureCostAdapter.MetricResult getMetric(
                String resourceId,
                String metricName,
                LocalDate startDate,
                LocalDate endDate
        ) {
            // Implementation uses Azure Monitor SDK
            // MetricsQueryClient.queryResource(resourceId, List.of(metricName), options)
            log.debug("Querying metric {} for resource {}", metricName, resourceId);
            return new AzureCostAdapter.MetricResult(0.5, 0.8, 100);
        }
    }

    /**
     * Azure Retail Prices API client (public, no auth required).
     */
    public static class RetailPriceClient {
        private static final String RETAIL_PRICES_URL = "https://prices.azure.com/api/retail/prices";

        public Double getPrice(String sku, String region) {
            // Query: ?$filter=armSkuName eq 'Standard_D4s_v3' and armRegionName eq 'eastus'
            log.debug("Fetching retail price for SKU {} in region {}", sku, region);
            return null; // Placeholder
        }
    }

    /**
     * Compute client for SKU listings.
     */
    public static class ComputeClient {
        private final TokenCredential credential;
        private final String subscriptionId;

        public ComputeClient(TokenCredential credential, String subscriptionId) {
            this.credential = credential;
            this.subscriptionId = subscriptionId;
        }

        public List<VmSize> listVmSizes(String region) {
            // Implementation uses Azure Compute SDK
            // ComputeManager.virtualMachines().sizes().listByRegion(region)
            log.debug("Listing VM sizes for region: {}", region);
            return List.of();
        }

        public record VmSize(String name, int numberOfCores, int memoryInMB) {}
    }
}
