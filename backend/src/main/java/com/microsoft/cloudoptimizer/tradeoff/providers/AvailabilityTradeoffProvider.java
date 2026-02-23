package com.microsoft.cloudoptimizer.tradeoff.providers;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.ResourceAlternative;
import com.microsoft.cloudoptimizer.tradeoff.TradeoffScoreProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Calculates availability tradeoff score based on SLA tier differences.
 *
 * Higher score = same or better availability
 * Score is based on SKU family SLA tiers.
 */
@Component
@Slf4j
public class AvailabilityTradeoffProvider implements TradeoffScoreProvider {

    // SLA tiers for different SKU families (simplified)
    // Higher number = better SLA
    private static final Map<String, Integer> SLA_TIERS = Map.ofEntries(
            // Azure D-series (general purpose)
            Map.entry("Standard_D", 3),
            Map.entry("Standard_Ds", 3),
            Map.entry("D-series", 3),
            // Azure B-series (burstable)
            Map.entry("Standard_B", 2),
            Map.entry("B-series", 2),
            // Azure E-series (memory optimized)
            Map.entry("Standard_E", 3),
            Map.entry("E-series", 3),
            // Azure F-series (compute optimized)
            Map.entry("Standard_F", 3),
            Map.entry("F-series", 3),
            // Azure L-series (storage optimized)
            Map.entry("Standard_L", 3),
            Map.entry("L-series", 3),
            // AWS general purpose
            Map.entry("m5", 3),
            Map.entry("m6i", 3),
            Map.entry("t3", 2),
            Map.entry("t3a", 2),
            // AWS compute optimized
            Map.entry("c5", 3),
            Map.entry("c6i", 3),
            // GCP general purpose
            Map.entry("n2-standard", 3),
            Map.entry("n2d-standard", 3),
            Map.entry("e2-standard", 2)
    );

    @Override
    public String getDimensionName() {
        return "availability";
    }

    @Override
    public TradeoffScore calculateScore(ResourceAlternative alternative,
                                         CloudCostAdapter.ResourceMetadata currentMetadata,
                                         String region) {
        String currentSku = currentMetadata.sku();
        String alternativeSku = alternative.getAlternativeSku();
        String currentFamily = alternative.getSkuFamily();

        int currentTier = getSlaTier(currentSku);
        int alternativeTier = getSlaTier(alternativeSku);

        // Score based on tier comparison
        double score;
        String explanation;

        if (alternativeTier >= currentTier) {
            score = 1.0;
            explanation = "Same or better SLA tier";
        } else if (alternativeTier == currentTier - 1) {
            score = 0.75;
            explanation = "Slightly lower SLA tier - burstable instance";
        } else {
            score = 0.5;
            explanation = "Lower SLA tier - review availability requirements";
        }

        String currentDesc = getTierDescription(currentTier);
        String altDesc = getTierDescription(alternativeTier);

        return new TradeoffScore(
                score,
                explanation,
                currentDesc,
                altDesc,
                0.8
        );
    }

    private int getSlaTier(String sku) {
        if (sku == null) return 2;

        for (Map.Entry<String, Integer> entry : SLA_TIERS.entrySet()) {
            if (sku.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 2; // Default to middle tier
    }

    private String getTierDescription(int tier) {
        return switch (tier) {
            case 1 -> "Basic (99.0% SLA)";
            case 2 -> "Standard (99.5% SLA)";
            case 3 -> "Premium (99.9% SLA)";
            case 4 -> "Mission Critical (99.99% SLA)";
            default -> "Unknown tier";
        };
    }
}
