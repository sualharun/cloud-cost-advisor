package com.microsoft.cloudoptimizer.config;

import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.ResourceAlternative;
import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import com.microsoft.cloudoptimizer.domain.model.TradeoffDimension;
import com.microsoft.cloudoptimizer.domain.repository.ResourceAlternativeRepository;
import com.microsoft.cloudoptimizer.domain.repository.TradeoffDimensionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Loads initial tradeoff dimensions and resource alternatives on startup.
 *
 * Only seeds data if the tables are empty.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeoffDataLoader implements CommandLineRunner {

    private final TradeoffDimensionRepository dimensionRepository;
    private final ResourceAlternativeRepository alternativeRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedDimensionsIfEmpty();
        seedAlternativesIfEmpty();
    }

    private void seedDimensionsIfEmpty() {
        if (dimensionRepository.count() > 0) {
            log.info("Tradeoff dimensions already exist, skipping seed");
            return;
        }

        log.info("Seeding tradeoff dimensions...");

        // Cost dimension - weight 0.35
        dimensionRepository.save(TradeoffDimension.builder()
                .name("cost")
                .displayName("Cost Savings")
                .description("Potential cost reduction compared to current configuration")
                .defaultWeight(0.35)
                .higherIsBetter(true)
                .active(true)
                .displayOrder(1)
                .iconName("dollar")
                .build());

        // Performance dimension - weight 0.25
        dimensionRepository.save(TradeoffDimension.builder()
                .name("performance")
                .displayName("Performance")
                .description("Compute capacity (vCPU, memory) compared to current")
                .defaultWeight(0.25)
                .higherIsBetter(true)
                .active(true)
                .displayOrder(2)
                .iconName("speedometer")
                .build());

        // Availability dimension - weight 0.15
        dimensionRepository.save(TradeoffDimension.builder()
                .name("availability")
                .displayName("Availability/SLA")
                .description("Service level agreement and uptime guarantees")
                .defaultWeight(0.15)
                .higherIsBetter(true)
                .active(true)
                .displayOrder(3)
                .iconName("shield")
                .build());

        // Migration effort dimension - weight 0.15
        dimensionRepository.save(TradeoffDimension.builder()
                .name("migration_effort")
                .displayName("Migration Effort")
                .description("Ease of migration from current to alternative")
                .defaultWeight(0.15)
                .higherIsBetter(true)
                .active(true)
                .displayOrder(4)
                .iconName("wrench")
                .build());

        // Vendor lock-in dimension - weight 0.05
        dimensionRepository.save(TradeoffDimension.builder()
                .name("vendor_lock_in")
                .displayName("Portability")
                .description("Ability to move to other providers in the future")
                .defaultWeight(0.05)
                .higherIsBetter(true)
                .active(true)
                .displayOrder(5)
                .iconName("unlock")
                .build());

        // Environmental impact dimension - weight 0.05
        dimensionRepository.save(TradeoffDimension.builder()
                .name("environmental_impact")
                .displayName("Sustainability")
                .description("Carbon footprint and environmental considerations")
                .defaultWeight(0.05)
                .higherIsBetter(true)
                .active(true)
                .displayOrder(6)
                .iconName("leaf")
                .build());

        log.info("Seeded 6 tradeoff dimensions");
    }

    private void seedAlternativesIfEmpty() {
        if (alternativeRepository.count() > 0) {
            log.info("Resource alternatives already exist, skipping seed");
            return;
        }

        log.info("Seeding resource alternatives...");

        // Azure D4s_v3 alternatives
        seedAzureD4sV3Alternatives();

        // Azure D2s_v3 alternatives
        seedAzureD2sV3Alternatives();

        // AWS m5.xlarge alternatives
        seedAwsM5XlargeAlternatives();

        log.info("Seeded resource alternatives");
    }

    private void seedAzureD4sV3Alternatives() {
        // Standard_D4s_v3 (4 vCPU, 16 GB) -> Standard_D2s_v3 (2 vCPU, 8 GB)
        alternativeRepository.save(ResourceAlternative.builder()
                .provider(CloudProvider.AZURE)
                .resourceType(ResourceType.COMPUTE)
                .currentSku("Standard_D4s_v3")
                .alternativeSku("Standard_D2s_v3")
                .alternativeProvider(CloudProvider.AZURE)
                .displayName("D2s v3 (2 vCPU, 8 GB)")
                .estimatedHourlyPrice(BigDecimal.valueOf(0.096))
                .vcpu(2)
                .memoryGb(8.0)
                .skuFamily("D-series")
                .active(true)
                .category("downsize")
                .build());

        // Standard_D4s_v3 -> Standard_B2s (burstable)
        alternativeRepository.save(ResourceAlternative.builder()
                .provider(CloudProvider.AZURE)
                .resourceType(ResourceType.COMPUTE)
                .currentSku("Standard_D4s_v3")
                .alternativeSku("Standard_B2s")
                .alternativeProvider(CloudProvider.AZURE)
                .displayName("B2s (2 vCPU, 4 GB, Burstable)")
                .estimatedHourlyPrice(BigDecimal.valueOf(0.0416))
                .vcpu(2)
                .memoryGb(4.0)
                .skuFamily("B-series")
                .active(true)
                .category("different_family")
                .build());

        // Standard_D4s_v3 -> Standard_B1s (small burstable)
        alternativeRepository.save(ResourceAlternative.builder()
                .provider(CloudProvider.AZURE)
                .resourceType(ResourceType.COMPUTE)
                .currentSku("Standard_D4s_v3")
                .alternativeSku("Standard_B1s")
                .alternativeProvider(CloudProvider.AZURE)
                .displayName("B1s (1 vCPU, 1 GB, Burstable)")
                .estimatedHourlyPrice(BigDecimal.valueOf(0.0104))
                .vcpu(1)
                .memoryGb(1.0)
                .skuFamily("B-series")
                .active(true)
                .category("different_family")
                .build());

        // Standard_D4s_v3 -> AWS m5.large (cross-cloud)
        alternativeRepository.save(ResourceAlternative.builder()
                .provider(CloudProvider.AZURE)
                .resourceType(ResourceType.COMPUTE)
                .currentSku("Standard_D4s_v3")
                .alternativeSku("m5.large")
                .alternativeProvider(CloudProvider.AWS)
                .displayName("AWS m5.large (2 vCPU, 8 GB)")
                .estimatedHourlyPrice(BigDecimal.valueOf(0.096))
                .vcpu(2)
                .memoryGb(8.0)
                .skuFamily("m5")
                .active(true)
                .category("cross_cloud")
                .build());
    }

    private void seedAzureD2sV3Alternatives() {
        // Standard_D2s_v3 -> Standard_B2s
        alternativeRepository.save(ResourceAlternative.builder()
                .provider(CloudProvider.AZURE)
                .resourceType(ResourceType.COMPUTE)
                .currentSku("Standard_D2s_v3")
                .alternativeSku("Standard_B2s")
                .alternativeProvider(CloudProvider.AZURE)
                .displayName("B2s (2 vCPU, 4 GB, Burstable)")
                .estimatedHourlyPrice(BigDecimal.valueOf(0.0416))
                .vcpu(2)
                .memoryGb(4.0)
                .skuFamily("B-series")
                .active(true)
                .category("different_family")
                .build());

        // Standard_D2s_v3 -> Standard_B1s
        alternativeRepository.save(ResourceAlternative.builder()
                .provider(CloudProvider.AZURE)
                .resourceType(ResourceType.COMPUTE)
                .currentSku("Standard_D2s_v3")
                .alternativeSku("Standard_B1s")
                .alternativeProvider(CloudProvider.AZURE)
                .displayName("B1s (1 vCPU, 1 GB, Burstable)")
                .estimatedHourlyPrice(BigDecimal.valueOf(0.0104))
                .vcpu(1)
                .memoryGb(1.0)
                .skuFamily("B-series")
                .active(true)
                .category("different_family")
                .build());
    }

    private void seedAwsM5XlargeAlternatives() {
        // m5.xlarge (4 vCPU, 16 GB) -> m5.large (2 vCPU, 8 GB)
        alternativeRepository.save(ResourceAlternative.builder()
                .provider(CloudProvider.AWS)
                .resourceType(ResourceType.COMPUTE)
                .currentSku("m5.xlarge")
                .alternativeSku("m5.large")
                .alternativeProvider(CloudProvider.AWS)
                .displayName("m5.large (2 vCPU, 8 GB)")
                .estimatedHourlyPrice(BigDecimal.valueOf(0.096))
                .vcpu(2)
                .memoryGb(8.0)
                .skuFamily("m5")
                .active(true)
                .category("downsize")
                .build());

        // m5.xlarge -> t3.large (burstable)
        alternativeRepository.save(ResourceAlternative.builder()
                .provider(CloudProvider.AWS)
                .resourceType(ResourceType.COMPUTE)
                .currentSku("m5.xlarge")
                .alternativeSku("t3.large")
                .alternativeProvider(CloudProvider.AWS)
                .displayName("t3.large (2 vCPU, 8 GB, Burstable)")
                .estimatedHourlyPrice(BigDecimal.valueOf(0.0832))
                .vcpu(2)
                .memoryGb(8.0)
                .skuFamily("t3")
                .active(true)
                .category("different_family")
                .build());

        // m5.xlarge -> t3.small
        alternativeRepository.save(ResourceAlternative.builder()
                .provider(CloudProvider.AWS)
                .resourceType(ResourceType.COMPUTE)
                .currentSku("m5.xlarge")
                .alternativeSku("t3.small")
                .alternativeProvider(CloudProvider.AWS)
                .displayName("t3.small (2 vCPU, 2 GB, Burstable)")
                .estimatedHourlyPrice(BigDecimal.valueOf(0.0208))
                .vcpu(2)
                .memoryGb(2.0)
                .skuFamily("t3")
                .active(true)
                .category("different_family")
                .build());
    }
}
