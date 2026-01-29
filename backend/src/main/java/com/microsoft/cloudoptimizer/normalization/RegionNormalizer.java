package com.microsoft.cloudoptimizer.normalization;

import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Normalizes cloud provider regions to a canonical format.
 *
 * PURPOSE:
 * Each cloud provider uses different naming conventions for regions:
 * - Azure: "East US", "West Europe"
 * - AWS: "us-east-1", "eu-west-1"
 * - GCP: "us-east1", "europe-west1"
 *
 * This normalizer maps all regions to a canonical format for:
 * 1. Cross-cloud cost comparisons
 * 2. Regional pricing lookups
 * 3. Geographic compliance reporting
 *
 * CANONICAL FORMAT:
 * {continent}-{direction}{number}
 * Examples: us-east-1, eu-west-1, asia-southeast-1
 */
@Component
public class RegionNormalizer {

    // Azure region mappings
    private static final Map<String, String> AZURE_TO_CANONICAL = Map.ofEntries(
            Map.entry("eastus", "us-east-1"),
            Map.entry("eastus2", "us-east-2"),
            Map.entry("westus", "us-west-1"),
            Map.entry("westus2", "us-west-2"),
            Map.entry("westus3", "us-west-3"),
            Map.entry("centralus", "us-central-1"),
            Map.entry("northcentralus", "us-north-1"),
            Map.entry("southcentralus", "us-south-1"),
            Map.entry("westeurope", "eu-west-1"),
            Map.entry("northeurope", "eu-north-1"),
            Map.entry("uksouth", "uk-south-1"),
            Map.entry("ukwest", "uk-west-1"),
            Map.entry("francecentral", "eu-central-1"),
            Map.entry("germanywestcentral", "eu-central-2"),
            Map.entry("southeastasia", "asia-southeast-1"),
            Map.entry("eastasia", "asia-east-1"),
            Map.entry("japaneast", "asia-northeast-1"),
            Map.entry("japanwest", "asia-northeast-2"),
            Map.entry("australiaeast", "australia-east-1"),
            Map.entry("australiasoutheast", "australia-southeast-1"),
            Map.entry("brazilsouth", "sa-east-1"),
            Map.entry("canadacentral", "ca-central-1"),
            Map.entry("canadaeast", "ca-east-1"),
            Map.entry("koreacentral", "asia-northeast-3"),
            Map.entry("indiacentral", "asia-south-1")
    );

    // AWS regions are already in canonical-like format
    private static final Map<String, String> AWS_TO_CANONICAL = Map.ofEntries(
            Map.entry("us-east-1", "us-east-1"),
            Map.entry("us-east-2", "us-east-2"),
            Map.entry("us-west-1", "us-west-1"),
            Map.entry("us-west-2", "us-west-2"),
            Map.entry("eu-west-1", "eu-west-1"),
            Map.entry("eu-west-2", "eu-west-2"),
            Map.entry("eu-west-3", "eu-west-3"),
            Map.entry("eu-central-1", "eu-central-1"),
            Map.entry("eu-north-1", "eu-north-1"),
            Map.entry("ap-southeast-1", "asia-southeast-1"),
            Map.entry("ap-southeast-2", "australia-east-1"),
            Map.entry("ap-northeast-1", "asia-northeast-1"),
            Map.entry("ap-northeast-2", "asia-northeast-3"),
            Map.entry("ap-south-1", "asia-south-1"),
            Map.entry("sa-east-1", "sa-east-1"),
            Map.entry("ca-central-1", "ca-central-1")
    );

    // GCP region mappings
    private static final Map<String, String> GCP_TO_CANONICAL = Map.ofEntries(
            Map.entry("us-east1", "us-east-1"),
            Map.entry("us-east4", "us-east-2"),
            Map.entry("us-west1", "us-west-1"),
            Map.entry("us-west2", "us-west-2"),
            Map.entry("us-west3", "us-west-3"),
            Map.entry("us-west4", "us-west-4"),
            Map.entry("us-central1", "us-central-1"),
            Map.entry("europe-west1", "eu-west-1"),
            Map.entry("europe-west2", "eu-west-2"),
            Map.entry("europe-west3", "eu-central-1"),
            Map.entry("europe-west4", "eu-west-3"),
            Map.entry("europe-north1", "eu-north-1"),
            Map.entry("asia-east1", "asia-east-1"),
            Map.entry("asia-east2", "asia-east-2"),
            Map.entry("asia-southeast1", "asia-southeast-1"),
            Map.entry("asia-southeast2", "asia-southeast-2"),
            Map.entry("asia-northeast1", "asia-northeast-1"),
            Map.entry("asia-northeast2", "asia-northeast-2"),
            Map.entry("asia-northeast3", "asia-northeast-3"),
            Map.entry("asia-south1", "asia-south-1"),
            Map.entry("australia-southeast1", "australia-east-1"),
            Map.entry("southamerica-east1", "sa-east-1"),
            Map.entry("northamerica-northeast1", "ca-central-1")
    );

    /**
     * Convert provider-specific region to canonical format.
     */
    public String toCanonicalRegion(CloudProvider provider, String providerRegion) {
        if (providerRegion == null) {
            return "unknown";
        }

        String normalized = providerRegion.toLowerCase().replace(" ", "").replace("-", "");

        return switch (provider) {
            case AZURE -> AZURE_TO_CANONICAL.getOrDefault(normalized, providerRegion.toLowerCase());
            case AWS -> AWS_TO_CANONICAL.getOrDefault(providerRegion.toLowerCase(), providerRegion.toLowerCase());
            case GCP -> GCP_TO_CANONICAL.getOrDefault(providerRegion.toLowerCase(), providerRegion.toLowerCase());
        };
    }

    /**
     * Get geographic zone for compliance and latency analysis.
     */
    public GeographicZone getGeographicZone(String canonicalRegion) {
        if (canonicalRegion.startsWith("us-")) {
            return GeographicZone.NORTH_AMERICA;
        } else if (canonicalRegion.startsWith("eu-") || canonicalRegion.startsWith("uk-")) {
            return GeographicZone.EUROPE;
        } else if (canonicalRegion.startsWith("asia-")) {
            return GeographicZone.ASIA_PACIFIC;
        } else if (canonicalRegion.startsWith("australia-")) {
            return GeographicZone.AUSTRALIA;
        } else if (canonicalRegion.startsWith("sa-")) {
            return GeographicZone.SOUTH_AMERICA;
        } else if (canonicalRegion.startsWith("ca-")) {
            return GeographicZone.NORTH_AMERICA;
        }
        return GeographicZone.UNKNOWN;
    }

    public enum GeographicZone {
        NORTH_AMERICA,
        SOUTH_AMERICA,
        EUROPE,
        ASIA_PACIFIC,
        AUSTRALIA,
        AFRICA,
        MIDDLE_EAST,
        UNKNOWN
    }
}
