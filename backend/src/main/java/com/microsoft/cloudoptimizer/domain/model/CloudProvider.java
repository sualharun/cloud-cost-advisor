package com.microsoft.cloudoptimizer.domain.model;

/**
 * Supported cloud providers.
 *
 * Each provider has distinct billing APIs, resource schemas, and pricing models.
 * The normalization layer abstracts these differences.
 */
public enum CloudProvider {
    AZURE("Azure", "portal.azure.com"),
    AWS("Amazon Web Services", "console.aws.amazon.com"),
    GCP("Google Cloud Platform", "console.cloud.google.com");

    private final String displayName;
    private final String consoleDomain;

    CloudProvider(String displayName, String consoleDomain) {
        this.displayName = displayName;
        this.consoleDomain = consoleDomain;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getConsoleDomain() {
        return consoleDomain;
    }

    public static CloudProvider fromDomain(String domain) {
        for (CloudProvider provider : values()) {
            if (domain.contains(provider.consoleDomain)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown cloud provider domain: " + domain);
    }
}
