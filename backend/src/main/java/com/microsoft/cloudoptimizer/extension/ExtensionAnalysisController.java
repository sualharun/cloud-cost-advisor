package com.microsoft.cloudoptimizer.extension;

import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import com.microsoft.cloudoptimizer.recommendation.RecommendationEngine;
import com.microsoft.cloudoptimizer.security.TenantContext;
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

        // TODO: Implement dismissal logic
        // recommendationService.dismiss(tenantId, recommendationId, request.reason());

        return ResponseEntity.ok().build();
    }

    /**
     * Mark recommendation as implemented.
     * Triggers validation after implementation.
     */
    @PostMapping("/recommendations/{recommendationId}/implement")
    @Operation(summary = "Mark recommendation as implemented")
    public ResponseEntity<Void> markImplemented(
            @PathVariable Long recommendationId
    ) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Marking recommendation implemented: {} for tenant: {}", recommendationId, tenantId);

        // TODO: Implement tracking logic
        // recommendationService.markImplemented(tenantId, recommendationId);

        return ResponseEntity.ok().build();
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

        // TODO: Implement simulation using pricing APIs
        // For now, return a placeholder
        return ResponseEntity.ok(new SimulationResponse(
                request.resourceId(),
                0.0, // currentMonthlyCost
                0.0, // projectedMonthlyCost
                0.0, // savingsAmount
                0.0, // savingsPercentage
                0.7  // confidence
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
}
