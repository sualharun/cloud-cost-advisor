package com.microsoft.cloudoptimizer.tradeoff.providers;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.ResourceAlternative;
import com.microsoft.cloudoptimizer.tradeoff.TradeoffScoreProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Calculates migration effort tradeoff score based on compatibility.
 *
 * Higher score = easier migration (less effort required)
 * Score is based on whether SKUs are in the same family, provider, etc.
 */
@Component
@Slf4j
public class MigrationEffortTradeoffProvider implements TradeoffScoreProvider {

    @Override
    public String getDimensionName() {
        return "migration_effort";
    }

    @Override
    public TradeoffScore calculateScore(ResourceAlternative alternative,
                                         CloudCostAdapter.ResourceMetadata currentMetadata,
                                         String region) {
        String currentSku = currentMetadata.sku();
        String alternativeSku = alternative.getAlternativeSku();
        boolean sameProvider = alternative.getProvider() == alternative.getAlternativeProvider();
        String currentFamily = extractSkuFamily(currentSku);
        String alternativeFamily = extractSkuFamily(alternativeSku);

        double score;
        String explanation;
        String currentDesc;
        String altDesc;

        if (sameProvider && currentFamily != null && currentFamily.equals(alternativeFamily)) {
            // Same provider, same family - very easy migration
            score = 1.0;
            explanation = "Same SKU family - minimal effort, typically no downtime resize";
            currentDesc = "Same family resize";
            altDesc = "In-place resize";
        } else if (sameProvider) {
            // Same provider, different family - moderate effort
            score = 0.75;
            explanation = "Different SKU family - may require VM deallocation";
            currentDesc = currentFamily != null ? currentFamily : "Current family";
            altDesc = alternativeFamily != null ? alternativeFamily : "Different family";
        } else {
            // Cross-cloud migration - significant effort
            score = 0.3;
            explanation = "Cross-cloud migration - significant planning and effort required";
            currentDesc = alternative.getProvider().name();
            altDesc = alternative.getAlternativeProvider().name();
        }

        return new TradeoffScore(
                score,
                explanation,
                currentDesc,
                altDesc,
                0.9 // High confidence as this is deterministic
        );
    }

    private String extractSkuFamily(String sku) {
        if (sku == null) return null;

        // Azure: Standard_D4s_v3 -> D
        if (sku.startsWith("Standard_")) {
            String[] parts = sku.substring(9).split("[_0-9]");
            if (parts.length > 0) {
                return parts[0];
            }
        }

        // AWS: m5.xlarge -> m5
        if (sku.contains(".")) {
            return sku.split("\\.")[0];
        }

        // GCP: n2-standard-4 -> n2-standard
        if (sku.contains("-")) {
            int lastDash = sku.lastIndexOf('-');
            String suffix = sku.substring(lastDash + 1);
            if (suffix.matches("\\d+")) {
                return sku.substring(0, lastDash);
            }
        }

        return sku;
    }
}
