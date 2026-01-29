package com.microsoft.cloudoptimizer.recommendation;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.*;
import com.microsoft.cloudoptimizer.domain.repository.RecommendationRepository;
import com.microsoft.cloudoptimizer.ml.ForecastingService;
import com.microsoft.cloudoptimizer.ml.UtilizationClusteringService;
import com.microsoft.cloudoptimizer.normalization.CostNormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Core recommendation engine that orchestrates ML insights into actionable recommendations.
 *
 * DECISION FLOW:
 * 1. Gather normalized cost and utilization data
 * 2. Run forecasting to predict future costs
 * 3. Classify utilization patterns
 * 4. Generate recommendations based on patterns
 * 5. Rank recommendations by savings potential and confidence
 * 6. Filter based on minimum thresholds
 *
 * RECOMMENDATION DEDUPLICATION:
 * Only one active recommendation per resource per action type.
 * New recommendations update existing ones rather than creating duplicates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationEngine {

    private final CostNormalizationService normalizationService;
    private final ForecastingService forecastingService;
    private final UtilizationClusteringService clusteringService;
    private final RecommendationRepository recommendationRepository;
    private final Map<CloudProvider, CloudCostAdapter> costAdapters;

    private static final int ANALYSIS_LOOKBACK_DAYS = 30;
    private static final double MINIMUM_SAVINGS_THRESHOLD = 10.0; // $10/month minimum
    private static final double MINIMUM_CONFIDENCE_THRESHOLD = 0.6;

    /**
     * Generate recommendations for a specific resource.
     * Called by the browser extension for real-time analysis.
     */
    @Transactional
    public ResourceAnalysisResult analyzeResource(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            ResourceAnalysisRequest request
    ) {
        log.info("Analyzing resource: {} for tenant: {}", resourceId, tenantId);

        try {
            // Get or fetch cost data
            var costSummary = normalizationService.getResourceCostSummary(
                    tenantId, provider, resourceId,
                    java.time.LocalDate.now().minusDays(ANALYSIS_LOOKBACK_DAYS),
                    java.time.LocalDate.now()
            );

            // If no historical data, use extension-provided config for basic analysis
            if (costSummary == null && request.detectedConfig() != null) {
                return analyzeWithoutHistory(tenantId, provider, resourceId, request);
            }

            if (costSummary == null) {
                return ResourceAnalysisResult.noData(resourceId);
            }

            // Get time series for ML analysis
            var timeSeries = normalizationService.getCostTimeSeries(
                    tenantId, provider, resourceId, ANALYSIS_LOOKBACK_DAYS
            );

            // Run forecasting
            var forecast = forecastingService.forecastCost(timeSeries, 30);

            // Classify utilization
            var classification = clusteringService.classifyUtilization(timeSeries);

            // Analyze rightsizing potential
            var rightsizing = clusteringService.analyzeRightsizing(classification, costSummary);

            // Generate recommendations
            List<Recommendation> recommendations = generateRecommendations(
                    tenantId, provider, resourceId, costSummary,
                    forecast, classification, rightsizing
            );

            // Persist recommendations
            for (var rec : recommendations) {
                persistRecommendation(rec);
            }

            log.info("Generated {} recommendations for resource: {}", recommendations.size(), resourceId);

            return new ResourceAnalysisResult(
                    true,
                    resourceId,
                    costSummary.resourceName(),
                    costSummary.resourceType(),
                    forecast.monthlyCostForecast(),
                    forecast.confidence(),
                    classification.status(),
                    classification.avgCpuUtilization(),
                    recommendations.stream()
                            .map(this::toRecommendationDto)
                            .toList(),
                    null
            );

        } catch (Exception e) {
            log.error("Resource analysis failed for: {}", resourceId, e);
            return ResourceAnalysisResult.error(resourceId, e.getMessage());
        }
    }

    /**
     * Analyze resource when we don't have historical cost data.
     * Uses pricing APIs and extension-provided config.
     */
    private ResourceAnalysisResult analyzeWithoutHistory(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            ResourceAnalysisRequest request
    ) {
        log.info("Analyzing resource without history: {}", resourceId);

        var adapter = costAdapters.get(provider);
        if (adapter == null) {
            return ResourceAnalysisResult.error(resourceId, "Unsupported provider");
        }

        // Fetch current resource metadata
        var metadataOpt = adapter.fetchResourceMetadata(tenantId, resourceId);
        if (metadataOpt.isEmpty()) {
            return ResourceAnalysisResult.noData(resourceId);
        }

        var metadata = metadataOpt.get();

        // Estimate cost from pricing API
        var priceOpt = adapter.getSkuPricing(metadata.sku(), metadata.region());
        double estimatedMonthlyCost = priceOpt.map(hourly -> hourly * 24 * 30).orElse(0.0);

        // Check if downsizing opportunities exist
        List<RecommendationDto> recommendations = new ArrayList<>();

        if (request.detectedConfig() != null && metadata.resourceType().isOptimizable()) {
            // Suggest reviewing resource based on detected config
            recommendations.add(new RecommendationDto(
                    RecommendationAction.DOWNSIZE_INSTANCE,
                    "Review resource sizing",
                    "No utilization data available. Consider reviewing if current size is appropriate.",
                    null,
                    0.0,
                    0.5,
                    "LOW"
            ));
        }

        return new ResourceAnalysisResult(
                true,
                resourceId,
                metadata.resourceName(),
                metadata.resourceType(),
                estimatedMonthlyCost,
                0.5, // Low confidence without history
                UtilizationStatus.INSUFFICIENT_DATA,
                null,
                recommendations,
                "Limited analysis - no historical data available"
        );
    }

    private List<Recommendation> generateRecommendations(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            CostNormalizationService.NormalizedResourceCost costSummary,
            ForecastingService.ForecastResult forecast,
            UtilizationClusteringService.UtilizationClassification classification,
            UtilizationClusteringService.RightsizingAnalysis rightsizing
    ) {
        List<Recommendation> recommendations = new ArrayList<>();

        // Rightsizing recommendation
        if (rightsizing.actionRecommended() &&
            rightsizing.estimatedMonthlySavings() >= MINIMUM_SAVINGS_THRESHOLD &&
            classification.confidence() >= MINIMUM_CONFIDENCE_THRESHOLD) {

            RecommendationAction action = switch (rightsizing.action()) {
                case "DELETE_OR_STOP" -> RecommendationAction.DELETE_RESOURCE;
                case "DOWNSIZE" -> RecommendationAction.DOWNSIZE_INSTANCE;
                case "UPSIZE" -> RecommendationAction.UPSIZE_INSTANCE;
                default -> RecommendationAction.NO_ACTION;
            };

            if (action != RecommendationAction.NO_ACTION) {
                recommendations.add(Recommendation.builder()
                        .tenantId(tenantId)
                        .provider(provider)
                        .resourceId(resourceId)
                        .resourceName(costSummary.resourceName())
                        .resourceType(costSummary.resourceType())
                        .action(action)
                        .summary(rightsizing.reasoning())
                        .details(generateDetailedDescription(classification, rightsizing, costSummary))
                        .currentConfig(formatConfig(costSummary.vcpu(), costSummary.memoryGb()))
                        .suggestedConfig(rightsizing.recommendedConfig())
                        .estimatedMonthlySavings(BigDecimal.valueOf(rightsizing.estimatedMonthlySavings()))
                        .savingsPercentage(calculateSavingsPercentage(rightsizing.estimatedMonthlySavings(), costSummary.avgDailyCost() * 30))
                        .confidence(classification.confidence())
                        .riskLevel(calculateRiskLevel(action))
                        .status(RecommendationStatus.ACTIVE)
                        .build());
            }
        }

        // Reserved instance recommendation
        if (classification.status() == UtilizationStatus.OPTIMIZED &&
            forecast.success() &&
            forecast.confidence() >= 0.8) {

            double potentialSavings = forecast.monthlyCostForecast() * 0.3; // RI typically saves ~30%
            if (potentialSavings >= MINIMUM_SAVINGS_THRESHOLD) {
                recommendations.add(Recommendation.builder()
                        .tenantId(tenantId)
                        .provider(provider)
                        .resourceId(resourceId)
                        .resourceName(costSummary.resourceName())
                        .resourceType(costSummary.resourceType())
                        .action(RecommendationAction.PURCHASE_RESERVATION)
                        .summary("Consider reserved instance for stable workload")
                        .details("Resource shows stable utilization patterns. Reserved instances typically offer 30-70% savings for 1-3 year commitments.")
                        .estimatedMonthlySavings(BigDecimal.valueOf(potentialSavings))
                        .savingsPercentage(30.0)
                        .confidence(forecast.confidence())
                        .riskLevel("LOW")
                        .status(RecommendationStatus.ACTIVE)
                        .build());
            }
        }

        // Schedule shutdown for dev/test workloads (based on naming patterns)
        if (isLikelyDevTest(resourceId, costSummary.resourceName()) &&
            classification.status() != UtilizationStatus.OVERUTILIZED) {

            double potentialSavings = costSummary.avgDailyCost() * 30 * 0.5; // 12 hours off = 50% savings
            if (potentialSavings >= MINIMUM_SAVINGS_THRESHOLD) {
                recommendations.add(Recommendation.builder()
                        .tenantId(tenantId)
                        .provider(provider)
                        .resourceId(resourceId)
                        .resourceName(costSummary.resourceName())
                        .resourceType(costSummary.resourceType())
                        .action(RecommendationAction.SCHEDULE_SHUTDOWN)
                        .summary("Schedule automatic shutdown during off-hours")
                        .details("Resource appears to be non-production. Consider scheduling shutdown during nights and weekends.")
                        .estimatedMonthlySavings(BigDecimal.valueOf(potentialSavings))
                        .savingsPercentage(50.0)
                        .confidence(0.7)
                        .riskLevel("LOW")
                        .status(RecommendationStatus.ACTIVE)
                        .build());
            }
        }

        return recommendations;
    }

    private void persistRecommendation(Recommendation recommendation) {
        // Check for existing active recommendation
        var existing = recommendationRepository.findByTenantIdAndProviderAndResourceIdAndActionAndStatus(
                recommendation.getTenantId(),
                recommendation.getProvider(),
                recommendation.getResourceId(),
                recommendation.getAction(),
                RecommendationStatus.ACTIVE
        );

        if (existing.isPresent()) {
            // Update existing recommendation
            var existingRec = existing.get();
            existingRec.setSummary(recommendation.getSummary());
            existingRec.setDetails(recommendation.getDetails());
            existingRec.setEstimatedMonthlySavings(recommendation.getEstimatedMonthlySavings());
            existingRec.setConfidence(recommendation.getConfidence());
            existingRec.setGeneratedAt(LocalDateTime.now());
            existingRec.setExpiresAt(LocalDateTime.now().plusDays(7));
            recommendationRepository.save(existingRec);
        } else {
            recommendationRepository.save(recommendation);
        }
    }

    private RecommendationDto toRecommendationDto(Recommendation rec) {
        return new RecommendationDto(
                rec.getAction(),
                rec.getSummary(),
                rec.getDetails(),
                rec.getSuggestedConfig(),
                rec.getEstimatedMonthlySavings() != null ? rec.getEstimatedMonthlySavings().doubleValue() : 0,
                rec.getConfidence(),
                rec.getRiskLevel()
        );
    }

    private String generateDetailedDescription(
            UtilizationClusteringService.UtilizationClassification classification,
            UtilizationClusteringService.RightsizingAnalysis rightsizing,
            CostNormalizationService.NormalizedResourceCost costSummary
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analysis based on ").append(costSummary.dataPointCount()).append(" days of data.\n");
        sb.append("Current utilization: ").append(classification.reasoning()).append("\n");
        sb.append("Current daily cost: $").append(String.format("%.2f", costSummary.avgDailyCost())).append("\n");
        if (rightsizing.recommendedConfig() != null) {
            sb.append("Recommended configuration: ").append(rightsizing.recommendedConfig()).append("\n");
        }
        sb.append("Estimated monthly savings: $").append(String.format("%.2f", rightsizing.estimatedMonthlySavings()));
        return sb.toString();
    }

    private String formatConfig(Integer vcpu, Double memoryGb) {
        if (vcpu == null && memoryGb == null) return null;
        StringBuilder sb = new StringBuilder();
        if (vcpu != null) sb.append(vcpu).append(" vCPU");
        if (memoryGb != null) {
            if (vcpu != null) sb.append(" / ");
            sb.append(String.format("%.0f", memoryGb)).append(" GB");
        }
        return sb.toString();
    }

    private double calculateSavingsPercentage(double savings, double currentCost) {
        if (currentCost <= 0) return 0;
        return (savings / currentCost) * 100;
    }

    private String calculateRiskLevel(RecommendationAction action) {
        return switch (action) {
            case DELETE_RESOURCE -> "HIGH";
            case DOWNSIZE_INSTANCE, UPSIZE_INSTANCE, CHANGE_REGION -> "MEDIUM";
            case PURCHASE_RESERVATION, CHANGE_STORAGE_TIER, SCHEDULE_SHUTDOWN -> "LOW";
            default -> "LOW";
        };
    }

    private boolean isLikelyDevTest(String resourceId, String resourceName) {
        String combined = (resourceId + " " + (resourceName != null ? resourceName : "")).toLowerCase();
        return combined.contains("dev") ||
               combined.contains("test") ||
               combined.contains("staging") ||
               combined.contains("qa") ||
               combined.contains("sandbox") ||
               combined.contains("nonprod");
    }

    // DTOs for API responses

    public record ResourceAnalysisRequest(
            ResourceType resourceType,
            String region,
            Map<String, Object> detectedConfig
    ) {}

    public record ResourceAnalysisResult(
            boolean success,
            String resourceId,
            String resourceName,
            ResourceType resourceType,
            double monthlyCostForecast,
            double confidence,
            UtilizationStatus utilizationStatus,
            Double avgCpuUtilization,
            List<RecommendationDto> recommendations,
            String message
    ) {
        static ResourceAnalysisResult noData(String resourceId) {
            return new ResourceAnalysisResult(
                    false, resourceId, null, null, 0, 0,
                    UtilizationStatus.INSUFFICIENT_DATA, null,
                    List.of(), "No cost data available for this resource"
            );
        }

        static ResourceAnalysisResult error(String resourceId, String message) {
            return new ResourceAnalysisResult(
                    false, resourceId, null, null, 0, 0,
                    null, null, List.of(), message
            );
        }
    }

    public record RecommendationDto(
            RecommendationAction action,
            String summary,
            String details,
            String suggestedConfig,
            double estimatedMonthlySavings,
            double confidence,
            String riskLevel
    ) {}
}
