package com.microsoft.cloudoptimizer.adapters.gcp;

import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Maps GCP service names to normalized ResourceType enum.
 *
 * GCP uses service names in billing exports that identify the product.
 * This mapper normalizes these to our unified resource taxonomy.
 */
@Component
public class GcpResourceTypeMapper {

    private static final Map<String, ResourceType> GCP_SERVICE_MAP = Map.ofEntries(
            // Compute
            Map.entry("compute engine", ResourceType.COMPUTE),
            Map.entry("cloud functions", ResourceType.SERVERLESS),
            Map.entry("cloud run", ResourceType.SERVERLESS),

            // Storage
            Map.entry("cloud storage", ResourceType.STORAGE),
            Map.entry("persistent disk", ResourceType.STORAGE),
            Map.entry("filestore", ResourceType.STORAGE),

            // Database
            Map.entry("cloud sql", ResourceType.DATABASE),
            Map.entry("cloud spanner", ResourceType.DATABASE),
            Map.entry("cloud bigtable", ResourceType.DATABASE),
            Map.entry("firestore", ResourceType.DATABASE),
            Map.entry("memorystore", ResourceType.CACHING),

            // Kubernetes
            Map.entry("kubernetes engine", ResourceType.KUBERNETES),
            Map.entry("anthos", ResourceType.KUBERNETES),

            // Analytics
            Map.entry("bigquery", ResourceType.ANALYTICS),
            Map.entry("dataflow", ResourceType.ANALYTICS),
            Map.entry("dataproc", ResourceType.ANALYTICS),
            Map.entry("pub/sub", ResourceType.MESSAGING),

            // Networking
            Map.entry("cloud networking", ResourceType.NETWORK),
            Map.entry("cloud load balancing", ResourceType.NETWORK),
            Map.entry("cloud cdn", ResourceType.NETWORK),
            Map.entry("cloud dns", ResourceType.NETWORK),
            Map.entry("vpc network", ResourceType.NETWORK),

            // Monitoring
            Map.entry("cloud monitoring", ResourceType.MONITORING),
            Map.entry("cloud logging", ResourceType.MONITORING),
            Map.entry("cloud trace", ResourceType.MONITORING)
    );

    /**
     * Maps GCP service name to normalized type.
     *
     * @param gcpServiceName GCP service/product name
     * @return Normalized ResourceType
     */
    public ResourceType mapResourceType(String gcpServiceName) {
        if (gcpServiceName == null) {
            return ResourceType.UNKNOWN;
        }

        String normalized = gcpServiceName.toLowerCase().trim();
        return GCP_SERVICE_MAP.getOrDefault(normalized, ResourceType.UNKNOWN);
    }

    /**
     * Check if service is supported for optimization.
     */
    public boolean isSupported(String gcpServiceName) {
        ResourceType type = mapResourceType(gcpServiceName);
        return type != ResourceType.UNKNOWN && type.isOptimizable();
    }
}
