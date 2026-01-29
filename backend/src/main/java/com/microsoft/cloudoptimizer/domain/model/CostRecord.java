package com.microsoft.cloudoptimizer.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Normalized cost record - the core abstraction for cloud-agnostic cost analysis.
 *
 * DESIGN RATIONALE:
 * This entity represents the "lingua franca" of multi-cloud cost data.
 * All provider-specific billing data is transformed into this schema before
 * ML processing. This ensures:
 *
 * 1. ML models remain provider-agnostic
 * 2. Cross-cloud comparisons are meaningful
 * 3. Recommendation logic operates on a consistent schema
 *
 * The schema captures resource characteristics (compute capacity, memory),
 * utilization metrics, and cost data. These dimensions enable:
 * - Rightsizing recommendations (vcpu/memory vs utilization)
 * - Trend forecasting (daily cost time series)
 * - Anomaly detection (cost vs expected patterns)
 *
 * IMMUTABILITY NOTE:
 * Cost records are append-only. Historical data is never modified.
 * This preserves audit trails and ensures forecast consistency.
 */
@Entity
@Table(name = "cost_records", indexes = {
    @Index(name = "idx_cost_record_provider_resource", columnList = "provider, resourceId"),
    @Index(name = "idx_cost_record_date", columnList = "recordDate"),
    @Index(name = "idx_cost_record_tenant", columnList = "tenantId, recordDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CostRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Multi-tenant isolation identifier.
     * Maps to Azure AD tenant or organizational unit.
     */
    @Column(nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CloudProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResourceType resourceType;

    /**
     * Provider-native resource identifier.
     * - Azure: /subscriptions/{sub}/resourceGroups/{rg}/providers/...
     * - AWS: arn:aws:ec2:region:account:instance/i-xxx
     * - GCP: projects/{project}/zones/{zone}/instances/{name}
     */
    @Column(nullable = false, length = 512)
    private String resourceId;

    /**
     * Human-readable resource name for display purposes.
     */
    @Column(length = 256)
    private String resourceName;

    /**
     * Provider-specific SKU identifier.
     * Used for pricing lookups and rightsizing.
     */
    @Column(length = 128)
    private String sku;

    /**
     * Number of virtual CPUs allocated.
     * Null for non-compute resources.
     */
    private Integer vcpu;

    /**
     * Memory allocation in GB.
     * Null for non-compute resources.
     */
    private Double memoryGb;

    /**
     * Storage capacity in GB.
     * Applicable to storage, database, and some compute resources.
     */
    private Double storageGb;

    /**
     * Average CPU utilization (0.0-1.0) over the measurement period.
     * Sourced from provider metrics (Azure Monitor, CloudWatch, Cloud Monitoring).
     */
    private Double avgCpuUtilization;

    /**
     * Average memory utilization (0.0-1.0) over the measurement period.
     */
    private Double avgMemoryUtilization;

    /**
     * Average network throughput in MB/s.
     */
    private Double avgNetworkMbps;

    /**
     * Deployment region in provider-native format.
     * Normalization to canonical region names happens at query time.
     */
    @Column(nullable = false, length = 64)
    private String region;

    /**
     * Total cost for this resource on the record date.
     * Currency: USD (normalized during ingestion).
     */
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal dailyCost;

    /**
     * Date this cost record represents.
     */
    @Column(nullable = false)
    private LocalDate recordDate;

    /**
     * Timestamp when this record was ingested.
     */
    @Column(nullable = false)
    private LocalDateTime ingestedAt;

    /**
     * Source of the cost data for audit purposes.
     */
    @Column(length = 64)
    private String dataSource;

    @PrePersist
    protected void onCreate() {
        if (ingestedAt == null) {
            ingestedAt = LocalDateTime.now();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CostRecord that = (CostRecord) o;
        return Objects.equals(tenantId, that.tenantId) &&
               provider == that.provider &&
               Objects.equals(resourceId, that.resourceId) &&
               Objects.equals(recordDate, that.recordDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, provider, resourceId, recordDate);
    }
}
