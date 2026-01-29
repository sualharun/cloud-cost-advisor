package com.microsoft.cloudoptimizer.config;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.CostRecord;
import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Local development configuration.
 *
 * ENABLED WHEN: spring.profiles.active=local OR APP_ENV=LOCAL
 *
 * This configuration:
 * 1. Disables JWT authentication
 * 2. Enables permissive CORS for chrome-extension:// and localhost
 * 3. Provides mock cloud adapters with realistic test data
 * 4. Uses H2 in-memory database
 *
 * NO CLOUD CREDENTIALS REQUIRED!
 */
@Configuration
@ConditionalOnProperty(name = "app.env", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalDevConfig {

    @Bean
    @Primary
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("🔓 LOCAL MODE: Security disabled for development");

        http
            .cors(cors -> cors.configurationSource(localCorsConfig()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource localCorsConfig() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "chrome-extension://*",
            "moz-extension://*",
            "http://localhost:*",
            "https://localhost:*",
            "file://*",
            "*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        log.info("🌐 LOCAL MODE: CORS enabled for chrome-extension://* and localhost:*");
        return source;
    }

    /**
     * Mock Azure Cost Adapter - returns realistic test data.
     */
    @Bean
    @Primary
    public CloudCostAdapter mockAzureAdapter() {
        log.info("📊 LOCAL MODE: Using MockAzureCostAdapter");
        return new MockCloudCostAdapter(CloudProvider.AZURE);
    }

    /**
     * Mock AWS Cost Adapter.
     */
    @Bean
    public CloudCostAdapter mockAwsAdapter() {
        log.info("📊 LOCAL MODE: Using MockAwsCostAdapter");
        return new MockCloudCostAdapter(CloudProvider.AWS);
    }

    /**
     * Mock GCP Cost Adapter.
     */
    @Bean
    public CloudCostAdapter mockGcpAdapter() {
        log.info("📊 LOCAL MODE: Using MockGcpCostAdapter");
        return new MockCloudCostAdapter(CloudProvider.GCP);
    }

    /**
     * Mock adapter that returns realistic test data.
     */
    static class MockCloudCostAdapter implements CloudCostAdapter {
        private final CloudProvider provider;
        private final Random random = new Random(42); // Fixed seed for consistent results

        MockCloudCostAdapter(CloudProvider provider) {
            this.provider = provider;
        }

        @Override
        public CloudProvider getProvider() {
            return provider;
        }

        @Override
        public List<CostRecord> fetchCostData(String tenantId, String resourceId,
                                               LocalDate startDate, LocalDate endDate) {
            log.debug("MOCK: Fetching cost data for {} resource: {}", provider, resourceId);

            List<CostRecord> records = new ArrayList<>();
            LocalDate current = startDate;

            // Determine resource characteristics from ID
            boolean isUnderutilized = resourceId.contains("underutilized") || resourceId.contains("test-vm-01");
            boolean isIdle = resourceId.contains("idle");
            double baseCost = resourceId.contains("db") ? 10.0 : 4.5; // DBs cost more

            while (!current.isAfter(endDate)) {
                double dailyCost = baseCost + (random.nextDouble() * 2 - 1);
                double cpuUtil = isIdle ? 0.02 + random.nextDouble() * 0.03
                               : isUnderutilized ? 0.10 + random.nextDouble() * 0.08
                               : 0.45 + random.nextDouble() * 0.30;

                records.add(CostRecord.builder()
                    .tenantId(tenantId)
                    .provider(provider)
                    .resourceType(resourceId.contains("db") ? ResourceType.DATABASE : ResourceType.COMPUTE)
                    .resourceId(resourceId)
                    .resourceName(extractName(resourceId))
                    .sku(getSku())
                    .region(getRegion())
                    .vcpu(4)
                    .memoryGb(16.0)
                    .avgCpuUtilization(cpuUtil)
                    .avgMemoryUtilization(cpuUtil * 0.8)
                    .dailyCost(BigDecimal.valueOf(dailyCost))
                    .recordDate(current)
                    .ingestedAt(LocalDateTime.now())
                    .dataSource("mock-local-dev")
                    .build());

                current = current.plusDays(1);
            }

            return records;
        }

        @Override
        public Optional<ResourceMetadata> fetchResourceMetadata(String tenantId, String resourceId) {
            log.debug("MOCK: Fetching metadata for {} resource: {}", provider, resourceId);

            return Optional.of(new ResourceMetadata(
                resourceId,
                extractName(resourceId),
                resourceId.contains("db") ? ResourceType.DATABASE : ResourceType.COMPUTE,
                getSku(),
                getRegion(),
                4,
                16.0,
                100.0,
                Map.of("environment", "development", "owner", "local-dev"),
                "Running"
            ));
        }

        @Override
        public boolean validateCredentials(String tenantId) {
            return true; // Always valid in mock mode
        }

        @Override
        public Optional<Double> getSkuPricing(String sku, String region) {
            // Return realistic hourly prices
            return Optional.of(switch (sku) {
                case "Standard_D4s_v3", "m5.xlarge" -> 0.192;
                case "Standard_D2s_v3", "m5.large" -> 0.096;
                case "Standard_B2s", "t3.small" -> 0.0416;
                default -> 0.10;
            });
        }

        @Override
        public List<SkuInfo> listAvailableSkus(ResourceType resourceType, String region) {
            return List.of(
                new SkuInfo("Standard_D2s_v3", "D2s v3 (2 vCPU, 8 GB)", 2, 8.0, 0.096, "D-series"),
                new SkuInfo("Standard_D4s_v3", "D4s v3 (4 vCPU, 16 GB)", 4, 16.0, 0.192, "D-series"),
                new SkuInfo("Standard_B2s", "B2s (2 vCPU, 4 GB)", 2, 4.0, 0.0416, "B-series"),
                new SkuInfo("Standard_B1s", "B1s (1 vCPU, 1 GB)", 1, 1.0, 0.0104, "B-series")
            );
        }

        @Override
        public Optional<UtilizationMetrics> fetchUtilizationMetrics(String tenantId, String resourceId,
                                                                     LocalDate startDate, LocalDate endDate) {
            boolean isUnderutilized = resourceId.contains("underutilized");
            boolean isIdle = resourceId.contains("idle");

            double avgCpu = isIdle ? 0.03 : isUnderutilized ? 0.12 : 0.55;
            double maxCpu = isIdle ? 0.08 : isUnderutilized ? 0.25 : 0.85;

            return Optional.of(new UtilizationMetrics(
                avgCpu, maxCpu,
                avgCpu * 0.7, maxCpu * 0.8,
                50.0, 100.0,
                (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) * 288
            ));
        }

        private String extractName(String resourceId) {
            int lastSlash = resourceId.lastIndexOf('/');
            return lastSlash >= 0 ? resourceId.substring(lastSlash + 1) : resourceId;
        }

        private String getSku() {
            return switch (provider) {
                case AZURE -> "Standard_D4s_v3";
                case AWS -> "m5.xlarge";
                case GCP -> "n2-standard-4";
            };
        }

        private String getRegion() {
            return switch (provider) {
                case AZURE -> "eastus";
                case AWS -> "us-east-1";
                case GCP -> "us-east1";
            };
        }
    }
}
