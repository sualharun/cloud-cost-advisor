package com.microsoft.cloudoptimizer.tradeoff.providers;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.ResourceAlternative;
import com.microsoft.cloudoptimizer.tradeoff.TradeoffScoreProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Calculates environmental impact tradeoff score based on carbon intensity.
 *
 * Higher score = lower environmental impact (greener choice)
 * Score is based on region carbon intensity and resource efficiency.
 */
@Component
@Slf4j
public class EnvironmentalImpactProvider implements TradeoffScoreProvider {

    // Estimated carbon intensity by region (lower is better)
    // Values are relative scores, not actual gCO2/kWh
    private static final Map<String, Double> REGION_CARBON_INTENSITY = Map.ofEntries(
            // Low carbon regions (renewable-heavy)
            Map.entry("northeurope", 0.2),
            Map.entry("swedencentral", 0.15),
            Map.entry("norwayeast", 0.1),
            Map.entry("eu-north-1", 0.2),
            Map.entry("europe-north1", 0.2),
            Map.entry("canadacentral", 0.25),
            Map.entry("ca-central-1", 0.25),
            // Medium carbon regions
            Map.entry("westeurope", 0.4),
            Map.entry("eu-west-1", 0.4),
            Map.entry("westus", 0.35),
            Map.entry("us-west-1", 0.35),
            Map.entry("eastus", 0.5),
            Map.entry("us-east-1", 0.5),
            Map.entry("us-east1", 0.5),
            // Higher carbon regions
            Map.entry("eastasia", 0.7),
            Map.entry("ap-east-1", 0.7),
            Map.entry("asia-east1", 0.7),
            Map.entry("australiaeast", 0.6),
            Map.entry("ap-southeast-2", 0.6)
    );

    @Override
    public String getDimensionName() {
        return "environmental_impact";
    }

    @Override
    public TradeoffScore calculateScore(ResourceAlternative alternative,
                                         CloudCostAdapter.ResourceMetadata currentMetadata,
                                         String region) {
        // Base score on region carbon intensity
        double regionIntensity = getRegionCarbonIntensity(region);
        double baseScore = 1.0 - regionIntensity;

        // Adjust for resource efficiency (smaller = better)
        Integer currentVcpu = currentMetadata.vcpu();
        Integer altVcpu = alternative.getVcpu();

        double efficiencyBonus = 0;
        if (currentVcpu != null && altVcpu != null && altVcpu < currentVcpu) {
            // Smaller instances are more efficient
            double reduction = (double) (currentVcpu - altVcpu) / currentVcpu;
            efficiencyBonus = reduction * 0.2; // Up to 20% bonus for rightsizing
        }

        double score = Math.min(1.0, baseScore + efficiencyBonus);

        // Generate explanation
        String explanation;
        if (regionIntensity <= 0.25) {
            explanation = "Low carbon region + ";
        } else if (regionIntensity <= 0.5) {
            explanation = "Medium carbon region + ";
        } else {
            explanation = "Higher carbon region + ";
        }

        if (efficiencyBonus > 0.1) {
            explanation += "significant efficiency improvement";
        } else if (efficiencyBonus > 0) {
            explanation += "moderate efficiency improvement";
        } else {
            explanation += "same resource size";
        }

        String regionName = region != null ? region : "unknown";
        String carbonLevel = regionIntensity <= 0.25 ? "Low" :
                            regionIntensity <= 0.5 ? "Medium" : "Higher";

        return new TradeoffScore(
                score,
                explanation,
                String.format("%s (%s carbon)", regionName, carbonLevel),
                efficiencyBonus > 0 ?
                        String.format("%d vCPU (more efficient)", altVcpu) :
                        "Same region",
                0.7 // Lower confidence as carbon data is estimated
        );
    }

    private double getRegionCarbonIntensity(String region) {
        if (region == null) return 0.5;

        String normalizedRegion = region.toLowerCase().replace("_", "").replace("-", "");

        // Try exact match first
        Double intensity = REGION_CARBON_INTENSITY.get(region.toLowerCase());
        if (intensity != null) return intensity;

        // Try partial match
        for (Map.Entry<String, Double> entry : REGION_CARBON_INTENSITY.entrySet()) {
            if (normalizedRegion.contains(entry.getKey().replace("-", "")) ||
                entry.getKey().replace("-", "").contains(normalizedRegion)) {
                return entry.getValue();
            }
        }

        return 0.5; // Default to medium
    }
}
