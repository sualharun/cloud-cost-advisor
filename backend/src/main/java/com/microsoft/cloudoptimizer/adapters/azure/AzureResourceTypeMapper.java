package com.microsoft.cloudoptimizer.adapters.azure;

import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Maps Azure resource types to normalized ResourceType enum.
 *
 * Azure resource types follow the format:
 * Microsoft.{Provider}/{resourceType}
 *
 * Examples:
 * - Microsoft.Compute/virtualMachines -> COMPUTE
 * - Microsoft.Storage/storageAccounts -> STORAGE
 * - Microsoft.Sql/servers/databases -> DATABASE
 */
@Component
public class AzureResourceTypeMapper {

    private static final Map<String, ResourceType> AZURE_TYPE_MAP = Map.ofEntries(
            // Compute
            Map.entry("microsoft.compute/virtualmachines", ResourceType.COMPUTE),
            Map.entry("microsoft.compute/virtualmachinescalesets", ResourceType.COMPUTE),
            Map.entry("microsoft.classiccompute/virtualmachines", ResourceType.COMPUTE),

            // Storage
            Map.entry("microsoft.storage/storageaccounts", ResourceType.STORAGE),
            Map.entry("microsoft.classicstorage/storageaccounts", ResourceType.STORAGE),

            // Database
            Map.entry("microsoft.sql/servers/databases", ResourceType.DATABASE),
            Map.entry("microsoft.sql/managedinstances", ResourceType.DATABASE),
            Map.entry("microsoft.documentdb/databaseaccounts", ResourceType.DATABASE),
            Map.entry("microsoft.dbformysql/servers", ResourceType.DATABASE),
            Map.entry("microsoft.dbforpostgresql/servers", ResourceType.DATABASE),
            Map.entry("microsoft.dbformariadb/servers", ResourceType.DATABASE),
            Map.entry("microsoft.cache/redis", ResourceType.CACHING),

            // Kubernetes
            Map.entry("microsoft.containerservice/managedclusters", ResourceType.KUBERNETES),

            // Serverless
            Map.entry("microsoft.web/sites", ResourceType.SERVERLESS),
            Map.entry("microsoft.web/functions", ResourceType.SERVERLESS),

            // Analytics
            Map.entry("microsoft.synapse/workspaces", ResourceType.ANALYTICS),
            Map.entry("microsoft.databricks/workspaces", ResourceType.ANALYTICS),
            Map.entry("microsoft.hdinsight/clusters", ResourceType.ANALYTICS),

            // Networking
            Map.entry("microsoft.network/virtualnetworks", ResourceType.NETWORK),
            Map.entry("microsoft.network/loadbalancers", ResourceType.NETWORK),
            Map.entry("microsoft.network/applicationgateways", ResourceType.NETWORK),
            Map.entry("microsoft.network/publicipaddresses", ResourceType.NETWORK),

            // Messaging
            Map.entry("microsoft.servicebus/namespaces", ResourceType.MESSAGING),
            Map.entry("microsoft.eventhub/namespaces", ResourceType.MESSAGING),

            // Monitoring
            Map.entry("microsoft.insights/components", ResourceType.MONITORING),
            Map.entry("microsoft.operationalinsights/workspaces", ResourceType.MONITORING)
    );

    /**
     * Maps Azure resource type to normalized type.
     *
     * @param azureResourceType Azure resource type (e.g., "Microsoft.Compute/virtualMachines")
     * @return Normalized ResourceType
     */
    public ResourceType mapResourceType(String azureResourceType) {
        if (azureResourceType == null) {
            return ResourceType.UNKNOWN;
        }

        String normalized = azureResourceType.toLowerCase();
        return AZURE_TYPE_MAP.getOrDefault(normalized, ResourceType.UNKNOWN);
    }

    /**
     * Check if resource type is supported for optimization.
     */
    public boolean isSupported(String azureResourceType) {
        ResourceType type = mapResourceType(azureResourceType);
        return type != ResourceType.UNKNOWN && type.isOptimizable();
    }
}
