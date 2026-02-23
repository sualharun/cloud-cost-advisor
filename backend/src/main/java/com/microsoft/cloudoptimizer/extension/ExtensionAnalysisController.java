package com.microsoft.cloudoptimizer.extension;

import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import com.microsoft.cloudoptimizer.recommendation.RecommendationEngine;
import com.microsoft.cloudoptimizer.security.TenantContext;
import com.microsoft.cloudoptimizer.service.RecommendationService;
import com.microsoft.cloudoptimizer.simulation.CostSimulationService;
import com.microsoft.cloudoptimizer.tradeoff.ResourceAlternativeService;
import com.microsoft.cloudoptimizer.tradeoff.dto.AlternativeComparisonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API for browser extension integration.
 *
 * ENDPOINT DESIGN:
 * - All endpoints require JWT authentication
 * - Rate limited per tenant (100 requests/minute)
 * - Responses optimized for low latency (cached where possible)
 *
 * SECURITY CONSIDERATIONS:
 * - Extension sends JWT obtained from Azure AD
 * - Resource IDs are validated against tenant's accessible resources
 * - No sensitive data returned beyond cost/recommendation data
 * 
 * NOTE: This controller is disabled in 'local' profile. Use MockExtensionController instead.
 */
@RestController
@RequestMapping("/api/extension")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Extension API", description = "APIs for browser extension integration")
@SecurityRequirement(name = "bearer-jwt")
@org.springframework.context.annotation.Profile("!local")
public class ExtensionAnalysisController {

    private final RecommendationEngine recommendationEngine;
    private final ExtensionCacheService cacheService;
    private final RecommendationService recommendationService;
    private final CostSimulationService costSimulationService;
    private final ResourceAlternativeService resourceAlternativeService;

    /**
     * Primary endpoint for resource analysis.
     * Called when extension detects a resource page.
     */
    @PostMapping("/analyze")
    @Operation(summary = "Analyze a cloud resource",
               description = "Analyzes a cloud resource and returns cost predictions and recommendations")
    public ResponseEntity<ExtensionAnalysisResponse> analyzeResource(
            @Valid @RequestBody ExtensionAnalysisRequest request
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Extension analysis request: provider={}, resourceId={}",
                request.provider(), request.resourceId());

        // Check cache first
        var cached = cacheService.getCachedAnalysis(tenantId, request.provider(), request.resourceId());
        if (cached != null) {
            log.debug("Returning cached analysis for: {}", request.resourceId());
            return ResponseEntity.ok(cached);
        }

        // Perform analysis
        var analysisRequest = new RecommendationEngine.ResourceAnalysisRequest(
                request.resourceType(),
                request.region(),
                request.detectedConfig()
        );

        var result = recommendationEngine.analyzeResource(
                tenantId,
                request.provider(),
                request.resourceId(),
                analysisRequest
        );

        // Map to extension response format
        var response = mapToExtensionResponse(result);

        // Cache successful results
        if (result.success()) {
            cacheService.cacheAnalysis(tenantId, request.provider(), request.resourceId(), response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Batch analysis for multiple resources.
     * Called when extension loads a resource list page.
     */
    @PostMapping("/analyze/batch")
    @Operation(summary = "Analyze multiple resources",
               description = "Batch analysis for resource list views")
    public ResponseEntity<BatchAnalysisResponse> analyzeResourceBatch(
            @Valid @RequestBody BatchAnalysisRequest request
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Extension batch analysis: provider={}, count={}",
                request.provider(), request.resources().size());

        List<ExtensionAnalysisResponse> results = request.resources().stream()
                .map(resource -> {
                    var analysisRequest = new RecommendationEngine.ResourceAnalysisRequest(
                            resource.resourceType(),
                            resource.region(),
                            resource.detectedConfig()
                    );
                    var result = recommendationEngine.analyzeResource(
                            tenantId,
                            request.provider(),
                            resource.resourceId(),
                            analysisRequest
                    );
                    return mapToExtensionResponse(result);
                })
                .toList();

        // Calculate summary
        double totalSavings = results.stream()
                .flatMap(r -> r.recommendations().stream())
                .mapToDouble(ExtensionRecommendation::estimatedSavings)
                .sum();

        int underutilizedCount = (int) results.stream()
                .filter(r -> "UNDERUTILIZED".equals(r.utilizationStatus()) ||
                             "IDLE".equals(r.utilizationStatus()))
                .count();

        return ResponseEntity.ok(new BatchAnalysisResponse(
                results,
                new BatchSummary(
                        results.size(),
                        underutilizedCount,
                        totalSavings
                )
        ));
    }

    /**
     * Dismiss a recommendation.
     * Prevents it from showing again for this resource.
     */
    @PostMapping("/recommendations/{recommendationId}/dismiss")
    @Operation(summary = "Dismiss a recommendation")
    public ResponseEntity<Void> dismissRecommendation(
            @PathVariable Long recommendationId,
            @RequestBody DismissRequest request
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Dismissing recommendation: {} for tenant: {}", recommendationId, tenantId);

        var result = recommendationService.dismiss(
                tenantId,
                recommendationId,
                request.reason(),
                TenantContext.getCurrentUser()
        );

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Undismiss a recommendation.
     * Restores a previously dismissed recommendation to active status.
     */
    @PostMapping("/recommendations/{recommendationId}/undismiss")
    @Operation(summary = "Restore a dismissed recommendation")
    public ResponseEntity<Void> undismissRecommendation(
            @PathVariable Long recommendationId
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Undismissing recommendation: {} for tenant: {}", recommendationId, tenantId);

        var result = recommendationService.undismiss(tenantId, recommendationId);

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Get all dismissed recommendations for the tenant.
     */
    @GetMapping("/recommendations/dismissed")
    @Operation(summary = "Get dismissed recommendations")
    public ResponseEntity<List<DismissedRecommendationResponse>> getDismissedRecommendations() {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Getting dismissed recommendations for tenant: {}", tenantId);

        var dismissed = recommendationService.findDismissedByTenant(tenantId);

        var response = dismissed.stream()
                .map(r -> new DismissedRecommendationResponse(
                        r.getId(),
                        r.getResourceId(),
                        r.getResourceName(),
                        r.getAction().name(),
                        r.getAction().getDisplayName(),
                        r.getSummary(),
                        r.getEstimatedMonthlySavings() != null ?
                                r.getEstimatedMonthlySavings().doubleValue() : 0.0,
                        r.getFeedback(),
                        r.getActionedBy(),
                        r.getActionedAt()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Mark recommendation as implemented.
     * Triggers validation after implementation.
     */
    @PostMapping("/recommendations/{recommendationId}/implement")
    @Operation(summary = "Mark recommendation as implemented")
    public ResponseEntity<ImplementationResponse> markImplemented(
            @PathVariable Long recommendationId
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Marking recommendation implemented: {} for tenant: {}", recommendationId, tenantId);

        var result = recommendationService.markImplemented(
                tenantId,
                recommendationId,
                TenantContext.getCurrentUser()
        );

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var impl = result.get();
        return ResponseEntity.ok(new ImplementationResponse(
                impl.getId(),
                impl.getRecommendationId(),
                impl.getResourceId(),
                impl.getExpectedMonthlySavings() != null ?
                        impl.getExpectedMonthlySavings().doubleValue() : 0.0,
                impl.getImplementedAt(),
                impl.getScheduledValidationAt(),
                impl.getValidationStatus().name()
        ));
    }

    /**
     * Get savings metrics for the tenant.
     */
    @GetMapping("/metrics/savings")
    @Operation(summary = "Get savings metrics",
               description = "Returns aggregated savings metrics for implemented recommendations")
    public ResponseEntity<SavingsMetricsResponse> getSavingsMetrics() {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Getting savings metrics for tenant: {}", tenantId);

        var metrics = recommendationService.getSavingsMetrics(tenantId);

        return ResponseEntity.ok(new SavingsMetricsResponse(
                metrics.totalExpectedSavings(),
                metrics.totalValidatedSavings(),
                metrics.implementedCount(),
                metrics.validationSuccessRate()
        ));
    }

    /**
     * Simulate cost impact of a configuration change.
     */
    @PostMapping("/simulate")
    @Operation(summary = "Simulate cost impact",
               description = "Estimate cost for a proposed configuration change")
    public ResponseEntity<SimulationResponse> simulateChange(
            @Valid @RequestBody SimulationRequest request
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Simulating change for resource: {}", request.resourceId());

        // Extract current SKU and region from proposed config or use defaults
        String currentSku = extractString(request.proposedConfig(), "currentSku", "Standard_D4s_v3");
        String region = extractString(request.proposedConfig(), "region", "eastus");

        var result = costSimulationService.simulateConfigChange(
                tenantId,
                request.provider(),
                request.resourceId(),
                currentSku,
                region,
                request.proposedConfig()
        );

        return ResponseEntity.ok(new SimulationResponse(
                request.resourceId(),
                result.currentMonthlyCost(),
                result.projectedMonthlyCost(),
                result.savingsAmount(),
                result.savingsPercentage(),
                result.confidence()
        ));
    }

    /**
     * Simulate cost savings from purchasing a Reserved Instance.
     */
    @PostMapping("/simulate/reservation")
    @Operation(summary = "Simulate reservation savings")
    public ResponseEntity<SimulationResponse> simulateReservation(
            @Valid @RequestBody ReservationSimulationRequest request
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Simulating reservation for resource: {}", request.resourceId());

        var term = "3".equals(request.term()) ?
                CostSimulationService.ReservationTerm.THREE_YEAR :
                CostSimulationService.ReservationTerm.ONE_YEAR;

        var result = costSimulationService.simulateReservation(
                tenantId,
                request.provider(),
                request.resourceId(),
                request.sku(),
                request.region(),
                term
        );

        return ResponseEntity.ok(new SimulationResponse(
                request.resourceId(),
                result.currentMonthlyCost(),
                result.projectedMonthlyCost(),
                result.savingsAmount(),
                result.savingsPercentage(),
                result.confidence()
        ));
    }

    /**
     * Simulate cost savings from using Spot/Preemptible instances.
     */
    @PostMapping("/simulate/spot")
    @Operation(summary = "Simulate spot instance savings")
    public ResponseEntity<SimulationResponse> simulateSpot(
            @Valid @RequestBody SpotSimulationRequest request
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Simulating spot instance for resource: {}", request.resourceId());

        var result = costSimulationService.simulateSpot(
                tenantId,
                request.provider(),
                request.resourceId(),
                request.sku(),
                request.region()
        );

        return ResponseEntity.ok(new SimulationResponse(
                request.resourceId(),
                result.currentMonthlyCost(),
                result.projectedMonthlyCost(),
                result.savingsAmount(),
                result.savingsPercentage(),
                result.confidence()
        ));
    }

    /**
     * Simulate cost savings from scheduling resource shutdown.
     */
    @PostMapping("/simulate/scheduling")
    @Operation(summary = "Simulate scheduling savings")
    public ResponseEntity<SimulationResponse> simulateScheduling(
            @Valid @RequestBody SchedulingSimulationRequest request
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Simulating scheduling for resource: {}", request.resourceId());

        var scheduleType = switch (request.schedule().toLowerCase()) {
            case "business_hours", "business" -> CostSimulationService.ScheduleType.BUSINESS_HOURS;
            case "weekdays", "weekdays_only" -> CostSimulationService.ScheduleType.WEEKDAYS_ONLY;
            default -> CostSimulationService.ScheduleType.DEV_HOURS;
        };

        var result = costSimulationService.simulateScheduling(
                tenantId,
                request.provider(),
                request.resourceId(),
                request.sku(),
                request.region(),
                scheduleType
        );

        return ResponseEntity.ok(new SimulationResponse(
                request.resourceId(),
                result.currentMonthlyCost(),
                result.projectedMonthlyCost(),
                result.savingsAmount(),
                result.savingsPercentage(),
                result.confidence()
        ));
    }

    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        return String.valueOf(map.get(key));
    }

    // ==================== Tradeoff/Alternatives Endpoints ====================

    /**
     * Compare alternatives for a resource with tradeoff analysis.
     */
    @GetMapping("/alternatives")
    @Operation(summary = "Get resource alternatives with tradeoff analysis",
               description = "Returns ranked alternatives with scores across multiple dimensions")
    public ResponseEntity<AlternativeComparisonResponse> getAlternatives(
            @RequestParam @NotNull CloudProvider provider,
            @RequestParam @NotBlank String resourceId,
            @RequestParam @NotBlank String sku,
            @RequestParam(defaultValue = "eastus") String region
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Getting alternatives for {} {} in {}", provider, sku, region);

        var response = resourceAlternativeService.compareAlternatives(
                tenantId, provider, resourceId, sku, region);

        return ResponseEntity.ok(response);
    }

    /**
     * Get current tradeoff weight preferences for the tenant.
     */
    @GetMapping("/preferences/tradeoff-weights")
    @Operation(summary = "Get tradeoff weight preferences")
    public ResponseEntity<TradeoffPreferencesResponse> getTradeoffPreferences() {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Getting tradeoff preferences for tenant: {}", tenantId);

        var prefs = resourceAlternativeService.getOrCreatePreferences(tenantId);
        var dimensions = resourceAlternativeService.getActiveDimensions();

        var dimensionInfos = dimensions.stream()
                .map(d -> new TradeoffDimensionInfo(
                        d.getName(),
                        d.getDisplayName(),
                        d.getDescription(),
                        d.getDefaultWeight(),
                        d.getIconName()
                ))
                .toList();

        return ResponseEntity.ok(new TradeoffPreferencesResponse(
                prefs.getTradeoffWeights(),
                prefs.getIncludeMultiCloudAlternatives(),
                prefs.getMinimumSavingsThreshold(),
                dimensionInfos
        ));
    }

    /**
     * Update tradeoff weight preferences for the tenant.
     */
    @PutMapping("/preferences/tradeoff-weights")
    @Operation(summary = "Update tradeoff weight preferences")
    public ResponseEntity<TradeoffPreferencesResponse> updateTradeoffPreferences(
            @RequestBody TradeoffPreferencesUpdateRequest request
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Updating tradeoff preferences for tenant: {}", tenantId);

        var prefs = resourceAlternativeService.updatePreferences(
                tenantId,
                request.weights(),
                request.includeMultiCloud(),
                request.minimumSavingsThreshold()
        );

        var dimensions = resourceAlternativeService.getActiveDimensions();
        var dimensionInfos = dimensions.stream()
                .map(d -> new TradeoffDimensionInfo(
                        d.getName(),
                        d.getDisplayName(),
                        d.getDescription(),
                        d.getDefaultWeight(),
                        d.getIconName()
                ))
                .toList();

        return ResponseEntity.ok(new TradeoffPreferencesResponse(
                prefs.getTradeoffWeights(),
                prefs.getIncludeMultiCloudAlternatives(),
                prefs.getMinimumSavingsThreshold(),
                dimensionInfos
        ));
    }

    private ExtensionAnalysisResponse mapToExtensionResponse(
            RecommendationEngine.ResourceAnalysisResult result
    ) {
        if (!result.success()) {
            return ExtensionAnalysisResponse.noData(result.resourceId(), result.message());
        }

        return new ExtensionAnalysisResponse(
                result.resourceId(),
                result.resourceName(),
                result.monthlyCostForecast(),
                result.confidence(),
                result.utilizationStatus() != null ? result.utilizationStatus().name() : null,
                result.utilizationStatus() != null ? result.utilizationStatus().getSeverityLevel() : "neutral",
                result.avgCpuUtilization(),
                result.recommendations().stream()
                        .map(r -> new ExtensionRecommendation(
                                r.action().name(),
                                r.action().getDisplayName(),
                                r.summary(),
                                r.suggestedConfig(),
                                r.estimatedMonthlySavings(),
                                r.confidence(),
                                r.riskLevel()
                        ))
                        .toList(),
                null
        );
    }

    // Request/Response DTOs

    public record ExtensionAnalysisRequest(
            @NotNull CloudProvider provider,
            @NotBlank String resourceId,
            ResourceType resourceType,
            String region,
            Map<String, Object> detectedConfig
    ) {}

    public record BatchAnalysisRequest(
            @NotNull CloudProvider provider,
            @NotNull List<ResourceInfo> resources
    ) {}

    public record ResourceInfo(
            @NotBlank String resourceId,
            ResourceType resourceType,
            String region,
            Map<String, Object> detectedConfig
    ) {}

    public record BatchAnalysisResponse(
            List<ExtensionAnalysisResponse> results,
            BatchSummary summary
    ) {}

    public record BatchSummary(
            int totalAnalyzed,
            int underutilizedCount,
            double totalPotentialSavings
    ) {}

    public record ExtensionAnalysisResponse(
            String resourceId,
            String resourceName,
            double monthlyCostForecast,
            double confidence,
            String utilizationStatus,
            String severityLevel,
            Double avgCpuUtilization,
            List<ExtensionRecommendation> recommendations,
            String message
    ) {
        static ExtensionAnalysisResponse noData(String resourceId, String message) {
            return new ExtensionAnalysisResponse(
                    resourceId, null, 0, 0, "INSUFFICIENT_DATA",
                    "neutral", null, List.of(), message
            );
        }
    }

    public record ExtensionRecommendation(
            String action,
            String actionDisplayName,
            String summary,
            String suggestedConfig,
            double estimatedSavings,
            double confidence,
            String riskLevel
    ) {}

    public record DismissRequest(
            String reason
    ) {}

    public record SimulationRequest(
            @NotBlank String resourceId,
            @NotNull CloudProvider provider,
            Map<String, Object> proposedConfig
    ) {}

    public record SimulationResponse(
            String resourceId,
            double currentMonthlyCost,
            double projectedMonthlyCost,
            double savingsAmount,
            double savingsPercentage,
            double confidence
    ) {}

    public record DismissedRecommendationResponse(
            Long id,
            String resourceId,
            String resourceName,
            String action,
            String actionDisplayName,
            String summary,
            double estimatedMonthlySavings,
            String dismissReason,
            String dismissedBy,
            LocalDateTime dismissedAt
    ) {}

    public record ImplementationResponse(
            Long implementationId,
            Long recommendationId,
            String resourceId,
            double expectedMonthlySavings,
            LocalDateTime implementedAt,
            LocalDateTime scheduledValidationAt,
            String validationStatus
    ) {}

    public record SavingsMetricsResponse(
            double totalExpectedSavings,
            double totalValidatedSavings,
            int implementedCount,
            double validationSuccessRate
    ) {}

    public record ReservationSimulationRequest(
            @NotBlank String resourceId,
            @NotNull CloudProvider provider,
            @NotBlank String sku,
            @NotBlank String region,
            @NotBlank String term // "1" or "3" for 1-year or 3-year
    ) {}

    public record SpotSimulationRequest(
            @NotBlank String resourceId,
            @NotNull CloudProvider provider,
            @NotBlank String sku,
            @NotBlank String region
    ) {}

    public record SchedulingSimulationRequest(
            @NotBlank String resourceId,
            @NotNull CloudProvider provider,
            @NotBlank String sku,
            @NotBlank String region,
            @NotBlank String schedule // "business_hours", "dev_hours", "weekdays_only"
    ) {}

    public record TradeoffPreferencesResponse(
            String weights, // JSON string of dimension weights
            Boolean includeMultiCloud,
            Double minimumSavingsThreshold,
            List<TradeoffDimensionInfo> dimensions
    ) {}

    public record TradeoffDimensionInfo(
            String name,
            String displayName,
            String description,
            Double defaultWeight,
            String iconName
    ) {}

    public record TradeoffPreferencesUpdateRequest(
            Map<String, Double> weights,
            Boolean includeMultiCloud,
            Double minimumSavingsThreshold
    ) {}
}
