package com.microsoft.cloudoptimizer.domain.model;

/**
 * Normalized resource types across cloud providers.
 *
 * Maps provider-specific resource types to a common taxonomy.
 * This abstraction enables provider-agnostic ML models.
 *
 * Provider mappings:
 * - COMPUTE: Azure VMs, EC2 Instances, GCE Instances
 * - STORAGE: Azure Blob/Files, S3, GCS
 * - DATABASE: Azure SQL/Cosmos, RDS/DynamoDB, Cloud SQL/Spanner
 * - NETWORK: VNets/LBs, VPCs/ELBs, VPCs/Cloud Load Balancing
 * - KUBERNETES: AKS, EKS, GKE
 * - SERVERLESS: Azure Functions, Lambda, Cloud Functions
 * - ANALYTICS: Synapse, Redshift/Athena, BigQuery
 */
public enum ResourceType {
    COMPUTE("Compute instances", true),
    STORAGE("Object and file storage", true),
    DATABASE("Managed databases", true),
    NETWORK("Networking resources", false),
    KUBERNETES("Managed Kubernetes", true),
    SERVERLESS("Serverless functions", true),
    ANALYTICS("Data analytics services", true),
    CACHING("Caching services", true),
    MESSAGING("Message queues and event streams", false),
    MONITORING("Observability services", false),
    UNKNOWN("Unclassified resources", false);

    private final String description;
    private final boolean optimizable;

    ResourceType(String description, boolean optimizable) {
        this.description = description;
        this.optimizable = optimizable;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Indicates if this resource type supports rightsizing recommendations.
     */
    public boolean isOptimizable() {
        return optimizable;
    }
}
