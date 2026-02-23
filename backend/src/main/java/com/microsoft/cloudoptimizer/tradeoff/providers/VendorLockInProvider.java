package com.microsoft.cloudoptimizer.tradeoff.providers;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.ResourceAlternative;
import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import com.microsoft.cloudoptimizer.tradeoff.TradeoffScoreProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Calculates vendor lock-in tradeoff score based on service portability.
 *
 * Higher score = more portable/standard (less vendor lock-in)
 * Score is based on whether services use standard APIs vs proprietary ones.
 */
@Component
@Slf4j
public class VendorLockInProvider implements TradeoffScoreProvider {

    // Services that are generally portable/standard
    private static final Set<String> PORTABLE_SKUS = Set.of(
            // Standard VMs are portable
            "Standard_D", "Standard_B", "Standard_E", "Standard_F",
            "m5", "m6i", "t3", "c5", "c6i",
            "n2-standard", "n2d-standard", "e2-standard",
            // Standard databases
            "Basic", "Standard", "GeneralPurpose"
    );

    // Services with significant vendor lock-in
    private static final Set<String> PROPRIETARY_KEYWORDS = Set.of(
            // Azure specific
            "Cosmos", "Synapse", "Functions",
            // AWS specific
            "Aurora", "DynamoDB", "Lambda",
            // GCP specific
            "Spanner", "BigQuery", "CloudRun"
    );

    @Override
    public String getDimensionName() {
        return "vendor_lock_in";
    }

    @Override
    public TradeoffScore calculateScore(ResourceAlternative alternative,
                                         CloudCostAdapter.ResourceMetadata currentMetadata,
                                         String region) {
        ResourceType resourceType = alternative.getResourceType();
        String alternativeSku = alternative.getAlternativeSku();
        boolean isCrossCloud = alternative.getProvider() != alternative.getAlternativeProvider();

        double score;
        String explanation;

        // Cross-cloud alternatives reduce lock-in
        if (isCrossCloud) {
            score = 0.9;
            explanation = "Multi-cloud option - reduces vendor dependency";
        } else if (resourceType == ResourceType.COMPUTE || resourceType == ResourceType.STORAGE) {
            // Standard compute and storage are generally portable
            score = isPortableSku(alternativeSku) ? 0.8 : 0.6;
            explanation = score >= 0.8 ?
                    "Standard service - easily portable to other clouds" :
                    "Some proprietary features - moderate portability";
        } else if (resourceType == ResourceType.DATABASE) {
            // Databases vary widely in portability
            score = isProprietarySku(alternativeSku) ? 0.3 : 0.6;
            explanation = score <= 0.3 ?
                    "Proprietary database - high vendor lock-in" :
                    "Standard database - moderate portability";
        } else if (resourceType == ResourceType.SERVERLESS) {
            // Serverless is typically highly proprietary
            score = 0.3;
            explanation = "Serverless service - proprietary, limited portability";
        } else {
            score = 0.5;
            explanation = "Standard vendor lock-in considerations apply";
        }

        return new TradeoffScore(
                score,
                explanation,
                isCrossCloud ? alternative.getProvider().name() : "Same cloud",
                isCrossCloud ? alternative.getAlternativeProvider().name() : "Standard service",
                0.75
        );
    }

    private boolean isPortableSku(String sku) {
        if (sku == null) return true;
        return PORTABLE_SKUS.stream().anyMatch(sku::contains);
    }

    private boolean isProprietarySku(String sku) {
        if (sku == null) return false;
        return PROPRIETARY_KEYWORDS.stream().anyMatch(sku::contains);
    }
}
