package com.microsoft.cloudoptimizer.adapters.aws;

import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Maps AWS service codes to normalized ResourceType enum.
 *
 * AWS uses product codes in Cost Explorer that identify the service.
 * This mapper normalizes these to our unified resource taxonomy.
 */
@Component
public class AwsResourceTypeMapper {

    private static final Map<String, ResourceType> AWS_SERVICE_MAP = Map.ofEntries(
            // Compute
            Map.entry("amazonec2", ResourceType.COMPUTE),
            Map.entry("elasticcomputecloud", ResourceType.COMPUTE),
            Map.entry("awslambda", ResourceType.SERVERLESS),

            // Storage
            Map.entry("amazons3", ResourceType.STORAGE),
            Map.entry("amazonglacier", ResourceType.STORAGE),
            Map.entry("amazonebs", ResourceType.STORAGE),
            Map.entry("amazonefs", ResourceType.STORAGE),

            // Database
            Map.entry("amazonrds", ResourceType.DATABASE),
            Map.entry("amazonauroradb", ResourceType.DATABASE),
            Map.entry("amazondocdb", ResourceType.DATABASE),
            Map.entry("amazondynamodb", ResourceType.DATABASE),
            Map.entry("amazonredshift", ResourceType.ANALYTICS),
            Map.entry("amazonelasticache", ResourceType.CACHING),

            // Kubernetes
            Map.entry("amazoneks", ResourceType.KUBERNETES),
            Map.entry("amazonecs", ResourceType.KUBERNETES),

            // Analytics
            Map.entry("amazonathena", ResourceType.ANALYTICS),
            Map.entry("amazonemr", ResourceType.ANALYTICS),
            Map.entry("amazonkinesis", ResourceType.ANALYTICS),

            // Networking
            Map.entry("amazonvpc", ResourceType.NETWORK),
            Map.entry("awselasticloadbalancing", ResourceType.NETWORK),
            Map.entry("amazonroute53", ResourceType.NETWORK),
            Map.entry("amazoncloudfront", ResourceType.NETWORK),

            // Messaging
            Map.entry("amazonsqs", ResourceType.MESSAGING),
            Map.entry("amazonsns", ResourceType.MESSAGING),
            Map.entry("amazonmq", ResourceType.MESSAGING),

            // Monitoring
            Map.entry("amazoncloudwatch", ResourceType.MONITORING),
            Map.entry("awsxray", ResourceType.MONITORING)
    );

    /**
     * Maps AWS product code to normalized type.
     *
     * @param awsProductCode AWS product/service code
     * @return Normalized ResourceType
     */
    public ResourceType mapResourceType(String awsProductCode) {
        if (awsProductCode == null) {
            return ResourceType.UNKNOWN;
        }

        // Normalize: remove spaces, lowercase
        String normalized = awsProductCode.toLowerCase().replace(" ", "").replace("-", "");
        return AWS_SERVICE_MAP.getOrDefault(normalized, ResourceType.UNKNOWN);
    }

    /**
     * Check if product code is supported for optimization.
     */
    public boolean isSupported(String awsProductCode) {
        ResourceType type = mapResourceType(awsProductCode);
        return type != ResourceType.UNKNOWN && type.isOptimizable();
    }
}
