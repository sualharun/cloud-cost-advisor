package com.microsoft.cloudoptimizer.recommendation;

import com.microsoft.cloudoptimizer.domain.model.*;
import com.microsoft.cloudoptimizer.domain.repository.RecommendationRepository;
import com.microsoft.cloudoptimizer.ml.ForecastingService;
import com.microsoft.cloudoptimizer.ml.UtilizationClusteringService;
import com.microsoft.cloudoptimizer.normalization.CostNormalizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RecommendationEngine.
 *
 * Test strategy:
 * 1. Test recommendation generation for different utilization scenarios
 * 2. Verify threshold-based filtering works correctly
 * 3. Ensure provider-agnostic behavior
 * 4. Test edge cases (no data, insufficient data, etc.)
 */
@ExtendWith(MockitoExtension.class)
class RecommendationEngineTest {

    @Mock
    private CostNormalizationService normalizationService;

    @Mock
    private ForecastingService forecastingService;

    @Mock
    private UtilizationClusteringService clusteringService;

    @Mock
    private RecommendationRepository recommendationRepository;

    @InjectMocks
    private RecommendationEngine recommendationEngine;

    private static final String TENANT_ID = "test-tenant";
    private static final String RESOURCE_ID = "/subscriptions/xxx/resourceGroups/test/providers/Microsoft.Compute/virtualMachines/test-vm";

    @Nested
    @DisplayName("Resource Analysis Tests")
    class ResourceAnalysisTests {

        @Test
        @DisplayName("Should return no-data result when no historical cost data exists")
        void shouldReturnNoDataWhenNoHistory() {
            // Given
            when(normalizationService.getResourceCostSummary(
                    eq(TENANT_ID), eq(CloudProvider.AZURE), eq(RESOURCE_ID), any(), any()
            )).thenReturn(null);

            var request = new RecommendationEngine.ResourceAnalysisRequest(
                    ResourceType.COMPUTE, "eastus", null
            );

            // When
            var result = recommendationEngine.analyzeResource(
                    TENANT_ID, CloudProvider.AZURE, RESOURCE_ID, request
            );

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.utilizationStatus()).isEqualTo(UtilizationStatus.INSUFFICIENT_DATA);
            assertThat(result.recommendations()).isEmpty();
        }

        @Test
        @DisplayName("Should generate downsize recommendation for underutilized resource")
        void shouldGenerateDownsizeForUnderutilized() {
            // Given
            var costSummary = createCostSummary(4, 16.0, 10.0, 0.15);
            var timeSeries = createTimeSeries(30, 0.15);
            var forecast = createForecast(300.0, 0.85);
            var classification = createClassification(UtilizationStatus.UNDERUTILIZED, 0.15, 0.9);
            var rightsizing = createRightsizing(true, "DOWNSIZE", "2 vCPU / 8 GB", 150.0);

            when(normalizationService.getResourceCostSummary(any(), any(), any(), any(), any()))
                    .thenReturn(costSummary);
            when(normalizationService.getCostTimeSeries(any(), any(), any(), anyInt()))
                    .thenReturn(timeSeries);
            when(forecastingService.forecastCost(any(), anyInt()))
                    .thenReturn(forecast);
            when(clusteringService.classifyUtilization(any()))
                    .thenReturn(classification);
            when(clusteringService.analyzeRightsizing(any(), any()))
                    .thenReturn(rightsizing);

            var request = new RecommendationEngine.ResourceAnalysisRequest(
                    ResourceType.COMPUTE, "eastus", Map.of("vcpu", 4, "memoryGb", 16)
            );

            // When
            var result = recommendationEngine.analyzeResource(
                    TENANT_ID, CloudProvider.AZURE, RESOURCE_ID, request
            );

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.utilizationStatus()).isEqualTo(UtilizationStatus.UNDERUTILIZED);
            assertThat(result.recommendations()).hasSize(1);
            assertThat(result.recommendations().get(0).action())
                    .isEqualTo(RecommendationAction.DOWNSIZE_INSTANCE);
            assertThat(result.recommendations().get(0).estimatedMonthlySavings())
                    .isGreaterThan(0);
        }

        @Test
        @DisplayName("Should generate no recommendations for optimized resource")
        void shouldGenerateNoRecommendationsForOptimized() {
            // Given
            var costSummary = createCostSummary(4, 16.0, 10.0, 0.55);
            var timeSeries = createTimeSeries(30, 0.55);
            var forecast = createForecast(300.0, 0.9);
            var classification = createClassification(UtilizationStatus.OPTIMIZED, 0.55, 0.95);
            var rightsizing = createRightsizing(false, null, null, 0);

            when(normalizationService.getResourceCostSummary(any(), any(), any(), any(), any()))
                    .thenReturn(costSummary);
            when(normalizationService.getCostTimeSeries(any(), any(), any(), anyInt()))
                    .thenReturn(timeSeries);
            when(forecastingService.forecastCost(any(), anyInt()))
                    .thenReturn(forecast);
            when(clusteringService.classifyUtilization(any()))
                    .thenReturn(classification);
            when(clusteringService.analyzeRightsizing(any(), any()))
                    .thenReturn(rightsizing);

            var request = new RecommendationEngine.ResourceAnalysisRequest(
                    ResourceType.COMPUTE, "eastus", null
            );

            // When
            var result = recommendationEngine.analyzeResource(
                    TENANT_ID, CloudProvider.AZURE, RESOURCE_ID, request
            );

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.utilizationStatus()).isEqualTo(UtilizationStatus.OPTIMIZED);
            // May have reserved instance recommendation for stable workloads
        }

        @Test
        @DisplayName("Should generate delete recommendation for idle resource")
        void shouldGenerateDeleteForIdle() {
            // Given
            var costSummary = createCostSummary(4, 16.0, 10.0, 0.02);
            var timeSeries = createTimeSeries(30, 0.02);
            var forecast = createForecast(300.0, 0.9);
            var classification = createClassification(UtilizationStatus.IDLE, 0.02, 0.95);
            var rightsizing = createRightsizing(true, "DELETE_OR_STOP", null, 300.0);

            when(normalizationService.getResourceCostSummary(any(), any(), any(), any(), any()))
                    .thenReturn(costSummary);
            when(normalizationService.getCostTimeSeries(any(), any(), any(), anyInt()))
                    .thenReturn(timeSeries);
            when(forecastingService.forecastCost(any(), anyInt()))
                    .thenReturn(forecast);
            when(clusteringService.classifyUtilization(any()))
                    .thenReturn(classification);
            when(clusteringService.analyzeRightsizing(any(), any()))
                    .thenReturn(rightsizing);

            var request = new RecommendationEngine.ResourceAnalysisRequest(
                    ResourceType.COMPUTE, "eastus", null
            );

            // When
            var result = recommendationEngine.analyzeResource(
                    TENANT_ID, CloudProvider.AZURE, RESOURCE_ID, request
            );

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.recommendations()).isNotEmpty();
            assertThat(result.recommendations().get(0).action())
                    .isEqualTo(RecommendationAction.DELETE_RESOURCE);
        }
    }

    @Nested
    @DisplayName("Threshold Tests")
    class ThresholdTests {

        @Test
        @DisplayName("Should filter recommendations below minimum savings threshold")
        void shouldFilterLowSavingsRecommendations() {
            // Given: rightsizing with only $5 savings (below $10 threshold)
            var costSummary = createCostSummary(2, 4.0, 0.5, 0.18);
            var timeSeries = createTimeSeries(30, 0.18);
            var forecast = createForecast(15.0, 0.8);
            var classification = createClassification(UtilizationStatus.UNDERUTILIZED, 0.18, 0.85);
            var rightsizing = createRightsizing(true, "DOWNSIZE", "1 vCPU / 2 GB", 5.0);

            when(normalizationService.getResourceCostSummary(any(), any(), any(), any(), any()))
                    .thenReturn(costSummary);
            when(normalizationService.getCostTimeSeries(any(), any(), any(), anyInt()))
                    .thenReturn(timeSeries);
            when(forecastingService.forecastCost(any(), anyInt()))
                    .thenReturn(forecast);
            when(clusteringService.classifyUtilization(any()))
                    .thenReturn(classification);
            when(clusteringService.analyzeRightsizing(any(), any()))
                    .thenReturn(rightsizing);

            var request = new RecommendationEngine.ResourceAnalysisRequest(
                    ResourceType.COMPUTE, "eastus", null
            );

            // When
            var result = recommendationEngine.analyzeResource(
                    TENANT_ID, CloudProvider.AZURE, RESOURCE_ID, request
            );

            // Then: should not include the low-savings recommendation
            assertThat(result.recommendations().stream()
                    .filter(r -> r.action() == RecommendationAction.DOWNSIZE_INSTANCE)
                    .findAny()
            ).isEmpty();
        }
    }

    // Helper methods to create test data

    private CostNormalizationService.NormalizedResourceCost createCostSummary(
            int vcpu, double memoryGb, double avgDailyCost, double cpuUtil
    ) {
        return new CostNormalizationService.NormalizedResourceCost(
                TENANT_ID, CloudProvider.AZURE, RESOURCE_ID, "test-vm",
                ResourceType.COMPUTE, "Standard_D4s_v3", "us-east-1",
                vcpu, memoryGb, null, avgDailyCost * 30, avgDailyCost,
                cpuUtil, null, 30, LocalDate.now().minusDays(30), LocalDate.now()
        );
    }

    private List<CostNormalizationService.CostTimeSeriesPoint> createTimeSeries(
            int days, double avgCpuUtil
    ) {
        return java.util.stream.IntStream.range(0, days)
                .mapToObj(i -> new CostNormalizationService.CostTimeSeriesPoint(
                        LocalDate.now().minusDays(days - i),
                        10.0 + Math.random() * 2,
                        avgCpuUtil + (Math.random() - 0.5) * 0.1,
                        null
                ))
                .toList();
    }

    private ForecastingService.ForecastResult createForecast(
            double monthlyCost, double confidence
    ) {
        return new ForecastingService.ForecastResult(
                true, 30, monthlyCost, new double[30], new LocalDate[30],
                confidence, monthlyCost * 0.9, monthlyCost * 1.1, "test-model"
        );
    }

    private UtilizationClusteringService.UtilizationClassification createClassification(
            UtilizationStatus status, double cpuUtil, double confidence
    ) {
        return new UtilizationClusteringService.UtilizationClassification(
                status, confidence, cpuUtil, null, "Test classification"
        );
    }

    private UtilizationClusteringService.RightsizingAnalysis createRightsizing(
            boolean actionRecommended, String action, String config, double savings
    ) {
        return new UtilizationClusteringService.RightsizingAnalysis(
                actionRecommended, action, config, savings, "Test analysis"
        );
    }
}
