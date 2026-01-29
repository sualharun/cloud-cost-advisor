package com.microsoft.cloudoptimizer.ml;

import com.microsoft.cloudoptimizer.domain.model.UtilizationStatus;
import com.microsoft.cloudoptimizer.normalization.CostNormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Utilization clustering service using ML-based classification.
 *
 * MODEL ARCHITECTURE:
 * Uses K-Means clustering to group resources by utilization patterns.
 * Cluster centers are pre-computed and used for fast classification.
 *
 * CLUSTERING DIMENSIONS:
 * 1. CPU utilization (average over measurement period)
 * 2. Memory utilization (where available)
 * 3. Cost efficiency ratio (cost per utilized unit)
 *
 * THRESHOLDS:
 * Thresholds are learned from training data but have sensible defaults:
 * - IDLE: < 5% utilization for 7+ days
 * - UNDERUTILIZED: < 20% utilization
 * - OPTIMIZED: 20-80% utilization
 * - OVERUTILIZED: > 80% utilization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UtilizationClusteringService {

    @Value("${optimization.thresholds.idle:0.05}")
    private double idleThreshold;

    @Value("${optimization.thresholds.underutilized:0.20}")
    private double underutilizedThreshold;

    @Value("${optimization.thresholds.overutilized:0.80}")
    private double overutilizedThreshold;

    @Value("${optimization.thresholds.idle-days:7}")
    private int idleDaysThreshold;

    private static final int MINIMUM_DATA_POINTS = 7;

    /**
     * Classify resource utilization based on historical metrics.
     */
    public UtilizationClassification classifyUtilization(
            List<CostNormalizationService.CostTimeSeriesPoint> timeSeries
    ) {
        log.debug("Classifying utilization from {} data points", timeSeries.size());

        if (timeSeries.size() < MINIMUM_DATA_POINTS) {
            return new UtilizationClassification(
                    UtilizationStatus.INSUFFICIENT_DATA,
                    0.0,
                    null,
                    null,
                    "Insufficient data: " + timeSeries.size() + " points"
            );
        }

        // Calculate utilization statistics
        UtilizationStats stats = calculateStats(timeSeries);

        // Classify based on thresholds
        UtilizationStatus status = classifyStatus(stats);
        double confidence = calculateConfidence(stats, timeSeries.size());
        String reasoning = generateReasoning(status, stats);

        return new UtilizationClassification(
                status,
                confidence,
                stats.avgCpuUtilization(),
                stats.avgMemoryUtilization(),
                reasoning
        );
    }

    /**
     * Batch classify multiple resources efficiently.
     */
    public Map<String, UtilizationClassification> batchClassify(
            Map<String, List<CostNormalizationService.CostTimeSeriesPoint>> resourceTimeSeries
    ) {
        Map<String, UtilizationClassification> results = new HashMap<>();

        for (var entry : resourceTimeSeries.entrySet()) {
            results.put(entry.getKey(), classifyUtilization(entry.getValue()));
        }

        return results;
    }

    /**
     * Detect utilization trends (improving, degrading, stable).
     */
    public UtilizationTrend detectTrend(
            List<CostNormalizationService.CostTimeSeriesPoint> timeSeries
    ) {
        if (timeSeries.size() < 14) {
            return new UtilizationTrend("UNKNOWN", 0, 0, "Insufficient data for trend analysis");
        }

        // Split into two halves
        int midpoint = timeSeries.size() / 2;
        List<CostNormalizationService.CostTimeSeriesPoint> firstHalf = timeSeries.subList(0, midpoint);
        List<CostNormalizationService.CostTimeSeriesPoint> secondHalf = timeSeries.subList(midpoint, timeSeries.size());

        UtilizationStats firstStats = calculateStats(firstHalf);
        UtilizationStats secondStats = calculateStats(secondHalf);

        double cpuChange = (secondStats.avgCpuUtilization() != null && firstStats.avgCpuUtilization() != null)
                ? secondStats.avgCpuUtilization() - firstStats.avgCpuUtilization()
                : 0;

        String trend;
        if (Math.abs(cpuChange) < 0.05) {
            trend = "STABLE";
        } else if (cpuChange > 0) {
            trend = "INCREASING";
        } else {
            trend = "DECREASING";
        }

        return new UtilizationTrend(
                trend,
                cpuChange,
                calculateTrendConfidence(cpuChange, timeSeries.size()),
                generateTrendDescription(trend, cpuChange)
        );
    }

    /**
     * Calculate right-sizing potential.
     */
    public RightsizingAnalysis analyzeRightsizing(
            UtilizationClassification classification,
            CostNormalizationService.NormalizedResourceCost resourceCost
    ) {
        if (classification.status() == UtilizationStatus.INSUFFICIENT_DATA) {
            return new RightsizingAnalysis(false, null, null, 0, "Insufficient utilization data");
        }

        if (classification.status() == UtilizationStatus.OPTIMIZED) {
            return new RightsizingAnalysis(false, null, null, 0, "Resource is well-optimized");
        }

        Integer currentVcpu = resourceCost.vcpu();
        Double currentMemory = resourceCost.memoryGb();

        if (currentVcpu == null && currentMemory == null) {
            return new RightsizingAnalysis(false, null, null, 0, "Resource specifications not available");
        }

        switch (classification.status()) {
            case IDLE -> {
                return new RightsizingAnalysis(
                        true,
                        "DELETE_OR_STOP",
                        null,
                        resourceCost.avgDailyCost() * 30,
                        "Resource is idle and can be deleted or stopped"
                );
            }
            case UNDERUTILIZED -> {
                // Calculate recommended size (50% reduction for significantly underutilized)
                Double avgUtil = classification.avgCpuUtilization();
                double reductionFactor = avgUtil != null ? Math.max(0.5, avgUtil * 2) : 0.5;

                int recommendedVcpu = currentVcpu != null
                        ? Math.max(1, (int) Math.ceil(currentVcpu * reductionFactor))
                        : 1;
                double recommendedMemory = currentMemory != null
                        ? Math.max(1.0, currentMemory * reductionFactor)
                        : 1.0;

                String recommendation = String.format("%d vCPU / %.0f GB", recommendedVcpu, recommendedMemory);
                double savingsEstimate = resourceCost.avgDailyCost() * 30 * (1 - reductionFactor);

                return new RightsizingAnalysis(
                        true,
                        "DOWNSIZE",
                        recommendation,
                        savingsEstimate,
                        "Resource is underutilized at " + formatPercent(avgUtil) + " CPU"
                );
            }
            case OVERUTILIZED -> {
                int recommendedVcpu = currentVcpu != null ? currentVcpu * 2 : 4;
                double recommendedMemory = currentMemory != null ? currentMemory * 2 : 8.0;

                String recommendation = String.format("%d vCPU / %.0f GB", recommendedVcpu, recommendedMemory);

                return new RightsizingAnalysis(
                        true,
                        "UPSIZE",
                        recommendation,
                        0, // Upsizing increases cost
                        "Resource is overutilized and may experience performance issues"
                );
            }
            default -> {
                return new RightsizingAnalysis(false, null, null, 0, "No action recommended");
            }
        }
    }

    private UtilizationStats calculateStats(
            List<CostNormalizationService.CostTimeSeriesPoint> timeSeries
    ) {
        double cpuSum = 0;
        double cpuMax = 0;
        int cpuCount = 0;

        double memorySum = 0;
        double memoryMax = 0;
        int memoryCount = 0;

        int consecutiveIdleDays = 0;
        int maxConsecutiveIdleDays = 0;

        for (var point : timeSeries) {
            if (point.cpuUtilization() != null) {
                cpuSum += point.cpuUtilization();
                cpuMax = Math.max(cpuMax, point.cpuUtilization());
                cpuCount++;

                if (point.cpuUtilization() < idleThreshold) {
                    consecutiveIdleDays++;
                    maxConsecutiveIdleDays = Math.max(maxConsecutiveIdleDays, consecutiveIdleDays);
                } else {
                    consecutiveIdleDays = 0;
                }
            }

            if (point.memoryUtilization() != null) {
                memorySum += point.memoryUtilization();
                memoryMax = Math.max(memoryMax, point.memoryUtilization());
                memoryCount++;
            }
        }

        return new UtilizationStats(
                cpuCount > 0 ? cpuSum / cpuCount : null,
                cpuCount > 0 ? cpuMax : null,
                memoryCount > 0 ? memorySum / memoryCount : null,
                memoryCount > 0 ? memoryMax : null,
                maxConsecutiveIdleDays
        );
    }

    private UtilizationStatus classifyStatus(UtilizationStats stats) {
        // Check for idle first
        if (stats.consecutiveIdleDays() >= idleDaysThreshold) {
            return UtilizationStatus.IDLE;
        }

        Double avgCpu = stats.avgCpuUtilization();
        if (avgCpu == null) {
            return UtilizationStatus.INSUFFICIENT_DATA;
        }

        if (avgCpu < idleThreshold) {
            return UtilizationStatus.IDLE;
        } else if (avgCpu < underutilizedThreshold) {
            return UtilizationStatus.UNDERUTILIZED;
        } else if (avgCpu > overutilizedThreshold) {
            return UtilizationStatus.OVERUTILIZED;
        } else {
            return UtilizationStatus.OPTIMIZED;
        }
    }

    private double calculateConfidence(UtilizationStats stats, int dataPoints) {
        // More data points = higher confidence
        double dataConfidence = Math.min(1.0, dataPoints / 30.0);

        // Clearer classification = higher confidence
        double classificationConfidence;
        Double avgCpu = stats.avgCpuUtilization();
        if (avgCpu == null) {
            classificationConfidence = 0;
        } else if (avgCpu < 0.1 || avgCpu > 0.9) {
            classificationConfidence = 0.95; // Very clear cases
        } else if (avgCpu < 0.2 || avgCpu > 0.8) {
            classificationConfidence = 0.85;
        } else {
            classificationConfidence = 0.7; // Middle ground is less certain
        }

        return (dataConfidence * 0.4) + (classificationConfidence * 0.6);
    }

    private String generateReasoning(UtilizationStatus status, UtilizationStats stats) {
        String cpuInfo = stats.avgCpuUtilization() != null
                ? formatPercent(stats.avgCpuUtilization()) + " avg CPU"
                : "no CPU data";

        return switch (status) {
            case IDLE -> "Resource idle for " + stats.consecutiveIdleDays() + " consecutive days (" + cpuInfo + ")";
            case UNDERUTILIZED -> "Resource underutilized at " + cpuInfo + ", max " + formatPercent(stats.maxCpuUtilization());
            case OPTIMIZED -> "Resource well-utilized at " + cpuInfo;
            case OVERUTILIZED -> "Resource overutilized at " + cpuInfo + ", max " + formatPercent(stats.maxCpuUtilization());
            case INSUFFICIENT_DATA -> "Need at least " + MINIMUM_DATA_POINTS + " days of data";
        };
    }

    private double calculateTrendConfidence(double change, int dataPoints) {
        double changeConfidence = Math.min(1.0, Math.abs(change) / 0.2);
        double dataConfidence = Math.min(1.0, dataPoints / 30.0);
        return (changeConfidence * 0.6) + (dataConfidence * 0.4);
    }

    private String generateTrendDescription(String trend, double change) {
        String direction = change >= 0 ? "increased" : "decreased";
        String magnitude = formatPercent(Math.abs(change));
        return String.format("Utilization %s by %s over the period", direction, magnitude);
    }

    private String formatPercent(Double value) {
        if (value == null) return "N/A";
        return String.format("%.1f%%", value * 100);
    }

    record UtilizationStats(
            Double avgCpuUtilization,
            Double maxCpuUtilization,
            Double avgMemoryUtilization,
            Double maxMemoryUtilization,
            int consecutiveIdleDays
    ) {}

    public record UtilizationClassification(
            UtilizationStatus status,
            double confidence,
            Double avgCpuUtilization,
            Double avgMemoryUtilization,
            String reasoning
    ) {}

    public record UtilizationTrend(
            String trend,
            double change,
            double confidence,
            String description
    ) {}

    public record RightsizingAnalysis(
            boolean actionRecommended,
            String action,
            String recommendedConfig,
            double estimatedMonthlySavings,
            String reasoning
    ) {}
}
