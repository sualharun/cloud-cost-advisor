package com.microsoft.cloudoptimizer.tradeoff.providers;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.ResourceAlternative;
import com.microsoft.cloudoptimizer.tradeoff.TradeoffScoreProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Calculates performance tradeoff score based on vCPU and memory capacity.
 *
 * Higher score = better or equal performance
 * Score is based on ratio of alternative capacity to current capacity.
 */
@Component
@Slf4j
public class PerformanceTradeoffProvider implements TradeoffScoreProvider {

    @Override
    public String getDimensionName() {
        return "performance";
    }

    @Override
    public TradeoffScore calculateScore(ResourceAlternative alternative,
                                         CloudCostAdapter.ResourceMetadata currentMetadata,
                                         String region) {
        Integer currentVcpu = currentMetadata.vcpu();
        Double currentMemory = currentMetadata.memoryGb();
        Integer altVcpu = alternative.getVcpu();
        Double altMemory = alternative.getMemoryGb();

        if (currentVcpu == null || currentMemory == null ||
            altVcpu == null || altMemory == null) {
            return TradeoffScore.unknown("Missing performance specifications");
        }

        // Calculate capacity ratios
        double vcpuRatio = (double) altVcpu / currentVcpu;
        double memoryRatio = altMemory / currentMemory;

        // Combined performance score (weighted average)
        // vCPU typically more important for compute-bound workloads
        double combinedRatio = (vcpuRatio * 0.6) + (memoryRatio * 0.4);

        // Convert to 0-1 score
        // Ratio >= 1.0 = score of 1.0 (same or better performance)
        // Ratio = 0.5 = score of 0.5 (half performance)
        // Ratio = 0.0 = score of 0.0
        double score = Math.min(1.0, combinedRatio);

        String explanation;
        if (combinedRatio >= 1.0) {
            explanation = "Same or better performance capacity";
        } else if (combinedRatio >= 0.75) {
            explanation = String.format("%.0f%% of current performance capacity", combinedRatio * 100);
        } else if (combinedRatio >= 0.5) {
            explanation = String.format("Reduced to %.0f%% of current capacity - verify workload fits",
                    combinedRatio * 100);
        } else {
            explanation = String.format("Significantly reduced (%.0f%%) - may impact workload",
                    combinedRatio * 100);
        }

        return new TradeoffScore(
                score,
                explanation,
                String.format("%d vCPU / %.0f GB", currentVcpu, currentMemory),
                String.format("%d vCPU / %.0f GB", altVcpu, altMemory),
                0.85
        );
    }
}
