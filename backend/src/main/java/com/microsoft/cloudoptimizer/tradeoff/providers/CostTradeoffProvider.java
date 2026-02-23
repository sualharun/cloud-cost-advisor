package com.microsoft.cloudoptimizer.tradeoff.providers;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.ResourceAlternative;
import com.microsoft.cloudoptimizer.tradeoff.TradeoffScoreProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Calculates cost tradeoff score based on pricing differences.
 *
 * Higher score = more savings
 * Score is based on percentage savings compared to current SKU.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CostTradeoffProvider implements TradeoffScoreProvider {

    private final Map<CloudProvider, CloudCostAdapter> costAdapters;

    private static final double HOURS_PER_MONTH = 730;

    @Override
    public String getDimensionName() {
        return "cost";
    }

    @Override
    public TradeoffScore calculateScore(ResourceAlternative alternative,
                                         CloudCostAdapter.ResourceMetadata currentMetadata,
                                         String region) {
        var adapter = costAdapters.get(alternative.getProvider());
        if (adapter == null) {
            return TradeoffScore.unknown("No adapter for provider");
        }

        var currentPriceOpt = adapter.getSkuPricing(currentMetadata.sku(), region);
        var altPriceOpt = adapter.getSkuPricing(alternative.getAlternativeSku(), region);

        if (currentPriceOpt.isEmpty() || altPriceOpt.isEmpty()) {
            return TradeoffScore.unknown("Pricing data not available");
        }

        double currentHourly = currentPriceOpt.get();
        double alternativeHourly = altPriceOpt.get();

        double currentMonthly = currentHourly * HOURS_PER_MONTH;
        double alternativeMonthly = alternativeHourly * HOURS_PER_MONTH;

        // Calculate savings percentage
        double savingsPercentage = (currentMonthly - alternativeMonthly) / currentMonthly;

        // Convert to 0-1 score (cap at 1.0 for very large savings)
        // 100% savings = 1.0, 0% savings = 0.5, -100% cost increase = 0.0
        double score;
        if (savingsPercentage >= 0) {
            score = 0.5 + (savingsPercentage * 0.5);
        } else {
            score = 0.5 + (savingsPercentage * 0.5);
        }
        score = Math.max(0, Math.min(1, score));

        String explanation;
        if (savingsPercentage > 0) {
            explanation = String.format("%.0f%% cost savings ($%.2f/mo -> $%.2f/mo)",
                    savingsPercentage * 100, currentMonthly, alternativeMonthly);
        } else if (savingsPercentage < 0) {
            explanation = String.format("%.0f%% cost increase ($%.2f/mo -> $%.2f/mo)",
                    Math.abs(savingsPercentage) * 100, currentMonthly, alternativeMonthly);
        } else {
            explanation = "Same cost";
        }

        return new TradeoffScore(
                score,
                explanation,
                String.format("$%.2f/mo", currentMonthly),
                String.format("$%.2f/mo", alternativeMonthly),
                0.9 // High confidence for direct pricing data
        );
    }
}
