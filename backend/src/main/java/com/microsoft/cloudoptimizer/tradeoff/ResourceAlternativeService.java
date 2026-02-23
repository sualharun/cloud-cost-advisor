package com.microsoft.cloudoptimizer.tradeoff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.*;
import com.microsoft.cloudoptimizer.domain.repository.*;
import com.microsoft.cloudoptimizer.tradeoff.dto.AlternativeComparisonResponse;
import com.microsoft.cloudoptimizer.tradeoff.dto.AlternativeComparisonResponse.*;
import com.microsoft.cloudoptimizer.tradeoff.dto.TradeoffInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for comparing resource alternatives with tradeoff analysis.
 *
 * Orchestrates tradeoff score providers to compute weighted scores
 * and rank alternatives based on tenant preferences.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceAlternativeService {

    private final ResourceAlternativeRepository alternativeRepository;
    private final TradeoffDimensionRepository dimensionRepository;
    private final AlternativeTradeoffScoreRepository scoreRepository;
    private final TenantPreferencesRepository preferencesRepository;
    private final Map<CloudProvider, CloudCostAdapter> costAdapters;
    private final List<TradeoffScoreProvider> scoreProviders;
    private final ObjectMapper objectMapper;

    private static final double HOURS_PER_MONTH = 730;

    /**
     * Compare alternatives for a resource with tradeoff analysis.
     */
    public AlternativeComparisonResponse compareAlternatives(String tenantId,
                                                              CloudProvider provider,
                                                              String resourceId,
                                                              String currentSku,
                                                              String region) {
        log.info("Comparing alternatives for {} {} in {}", provider, currentSku, region);

        // Get adapter and fetch current resource metadata
        var adapter = costAdapters.get(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }

        var metadataOpt = adapter.fetchResourceMetadata(tenantId, resourceId);
        CloudCostAdapter.ResourceMetadata metadata;
        if (metadataOpt.isPresent()) {
            metadata = metadataOpt.get();
        } else {
            // Create synthetic metadata for comparison
            var priceOpt = adapter.getSkuPricing(currentSku, region);
            metadata = new CloudCostAdapter.ResourceMetadata(
                    resourceId, resourceId, ResourceType.COMPUTE,
                    currentSku, region, 4, 16.0, null, Map.of(), "Running"
            );
        }

        // Get tenant preferences
        var preferences = getOrCreatePreferences(tenantId);
        Map<String, Double> weights = parseWeights(preferences.getTradeoffWeights());

        // Get active dimensions
        var dimensions = dimensionRepository.findByActiveTrueOrderByDisplayOrderAsc();
        Map<String, TradeoffDimension> dimensionMap = dimensions.stream()
                .collect(Collectors.toMap(TradeoffDimension::getName, d -> d));

        // Get available alternatives
        List<ResourceAlternative> alternatives = alternativeRepository
                .findByProviderAndCurrentSkuAndActiveTrue(provider, currentSku);

        // Include cross-cloud alternatives if enabled
        if (preferences.getIncludeMultiCloudAlternatives()) {
            var crossCloud = alternativeRepository
                    .findByProviderAndCurrentSkuAndAlternativeProviderNotAndActiveTrue(
                            provider, currentSku, provider);
            alternatives.addAll(crossCloud);
        }

        if (alternatives.isEmpty()) {
            log.info("No alternatives found for {}", currentSku);
        }

        // Calculate current monthly cost
        double currentMonthlyCost = adapter.getSkuPricing(currentSku, region)
                .map(hourly -> hourly * HOURS_PER_MONTH)
                .orElse(0.0);

        // Build current resource info
        var currentInfo = new CurrentResourceInfo(
                resourceId,
                provider.name(),
                currentSku,
                metadata.resourceName(),
                metadata.vcpu() != null ? metadata.vcpu() : 0,
                metadata.memoryGb() != null ? metadata.memoryGb() : 0,
                currentMonthlyCost,
                region
        );

        // Calculate tradeoffs for each alternative
        List<AlternativeWithTradeoffs> alternativeResults = alternatives.stream()
                .map(alt -> calculateAlternativeTradeoffs(alt, metadata, region, weights,
                        dimensionMap, currentMonthlyCost))
                .filter(alt -> alt.estimatedMonthlySavings() >= preferences.getMinimumSavingsThreshold())
                .sorted(Comparator.comparingDouble(AlternativeWithTradeoffs::overallScore).reversed())
                .toList();

        // Build preferences info
        var prefsInfo = new UserPreferencesInfo(
                weights,
                preferences.getIncludeMultiCloudAlternatives(),
                preferences.getMinimumSavingsThreshold()
        );

        return new AlternativeComparisonResponse(currentInfo, alternativeResults, prefsInfo);
    }

    /**
     * Calculate tradeoff scores for a single alternative.
     */
    private AlternativeWithTradeoffs calculateAlternativeTradeoffs(
            ResourceAlternative alternative,
            CloudCostAdapter.ResourceMetadata currentMetadata,
            String region,
            Map<String, Double> weights,
            Map<String, TradeoffDimension> dimensionMap,
            double currentMonthlyCost) {

        // Get alternative cost
        var adapter = costAdapters.get(alternative.getAlternativeProvider());
        double altMonthlyCost = adapter != null ?
                adapter.getSkuPricing(alternative.getAlternativeSku(), region)
                        .map(hourly -> hourly * HOURS_PER_MONTH)
                        .orElse(currentMonthlyCost) :
                alternative.getEstimatedHourlyPrice() != null ?
                        alternative.getEstimatedHourlyPrice().doubleValue() * HOURS_PER_MONTH :
                        currentMonthlyCost;

        double savings = currentMonthlyCost - altMonthlyCost;
        double savingsPercentage = currentMonthlyCost > 0 ?
                (savings / currentMonthlyCost) * 100 : 0;

        // Calculate tradeoff scores from each provider
        List<TradeoffInfo> tradeoffs = new ArrayList<>();
        double weightedScoreSum = 0;
        double totalWeight = 0;

        for (TradeoffScoreProvider provider : scoreProviders) {
            var dimension = dimensionMap.get(provider.getDimensionName());
            if (dimension == null || !dimension.getActive()) continue;

            var score = provider.calculateScore(alternative, currentMetadata, region);
            double weight = weights.getOrDefault(dimension.getName(), dimension.getDefaultWeight());

            String direction = TradeoffInfo.calculateDirection(score.score(),
                    dimension.getHigherIsBetter());

            tradeoffs.add(new TradeoffInfo(
                    dimension.getName(),
                    dimension.getDisplayName(),
                    score.score(),
                    score.currentValue(),
                    score.alternativeValue(),
                    score.explanation(),
                    direction
            ));

            weightedScoreSum += score.score() * weight;
            totalWeight += weight;
        }

        double overallScore = totalWeight > 0 ? weightedScoreSum / totalWeight : 0.5;

        return new AlternativeWithTradeoffs(
                alternative.getId(),
                alternative.getAlternativeSku(),
                alternative.getDisplayName() != null ?
                        alternative.getDisplayName() : alternative.getAlternativeSku(),
                alternative.getAlternativeProvider().name(),
                alternative.getVcpu() != null ? alternative.getVcpu() : 0,
                alternative.getMemoryGb() != null ? alternative.getMemoryGb() : 0,
                altMonthlyCost,
                savings,
                savingsPercentage,
                overallScore,
                alternative.getCategory(),
                tradeoffs
        );
    }

    /**
     * Get or create default preferences for a tenant.
     */
    public TenantPreferences getOrCreatePreferences(String tenantId) {
        return preferencesRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    var prefs = TenantPreferences.builder()
                            .tenantId(tenantId)
                            .minimumSavingsThreshold(10.0)
                            .includeMultiCloudAlternatives(false)
                            .build();
                    return preferencesRepository.save(prefs);
                });
    }

    /**
     * Update tenant preferences.
     */
    public TenantPreferences updatePreferences(String tenantId, Map<String, Double> weights,
                                                Boolean includeMultiCloud,
                                                Double minimumSavingsThreshold) {
        var prefs = getOrCreatePreferences(tenantId);

        if (weights != null) {
            try {
                prefs.setTradeoffWeights(objectMapper.writeValueAsString(weights));
            } catch (Exception e) {
                log.error("Failed to serialize weights", e);
            }
        }

        if (includeMultiCloud != null) {
            prefs.setIncludeMultiCloudAlternatives(includeMultiCloud);
        }

        if (minimumSavingsThreshold != null) {
            prefs.setMinimumSavingsThreshold(minimumSavingsThreshold);
        }

        return preferencesRepository.save(prefs);
    }

    /**
     * Get available tradeoff dimensions.
     */
    public List<TradeoffDimension> getActiveDimensions() {
        return dimensionRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    private Map<String, Double> parseWeights(String json) {
        if (json == null || json.isBlank()) {
            return getDefaultWeights();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse weights JSON, using defaults", e);
            return getDefaultWeights();
        }
    }

    private Map<String, Double> getDefaultWeights() {
        var dimensions = dimensionRepository.findByActiveTrueOrderByDisplayOrderAsc();
        Map<String, Double> weights = new HashMap<>();
        for (var dim : dimensions) {
            weights.put(dim.getName(), dim.getDefaultWeight());
        }
        return weights;
    }
}
