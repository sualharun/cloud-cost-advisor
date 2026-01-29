package com.microsoft.cloudoptimizer.extension;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mock Extension API Controller for Local Development
 * 
 * This controller provides mock responses for the browser extension during local testing.
 * It bypasses authentication and returns realistic test data.
 */
@RestController
@RequestMapping("/api/extension")
@Profile("local")
public class MockExtensionController {

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeResource(@RequestBody Map<String, Object> request) {
        System.out.println("🧪 Mock Extension API - Analyzing resource: " + request.get("resourceId"));
        
        String resourceId = (String) request.get("resourceId");
        String resourceType = (String) request.get("resourceType");
        String provider = (String) request.get("provider");
        
        // Build mock response
        var response = new MockAnalysisResponse(
            resourceId != null ? resourceId : "unknown-resource",
            extractResourceName(resourceId),
            143.22,
            0.91,
            "UNDERUTILIZED",
            "warning",
            0.15,
            createRecommendations(resourceId, resourceType)
        );
        
        System.out.println("✅ Returning mock analysis with " + response.recommendations.size() + " recommendations");
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/analyze/batch")
    public ResponseEntity<?> analyzeBatch(@RequestBody Map<String, Object> request) {
        System.out.println("🧪 Mock Extension API - Batch analysis");
        return ResponseEntity.ok(Map.of("results", List.of()));
    }
    
    private String extractResourceName(String resourceId) {
        if (resourceId == null) return "unknown";
        
        // Extract last part of resource ID
        if (resourceId.contains("/")) {
            String[] parts = resourceId.split("/");
            return parts[parts.length - 1];
        }
        return resourceId;
    }
    
    private List<MockRecommendation> createRecommendations(String resourceId, String resourceType) {
        List<MockRecommendation> recommendations = new ArrayList<>();
        
        // Determine recommendation based on resource ID or type
        boolean isStorage = resourceType != null && resourceType.equals("STORAGE");
        boolean isIdle = resourceId != null && resourceId.toLowerCase().contains("idle");
        boolean isOptimized = resourceId != null && resourceId.toLowerCase().contains("optimized");
        
        if (isOptimized) {
            // No recommendations - resource is well-configured
            return recommendations;
        }
        
        if (isIdle) {
            recommendations.add(new MockRecommendation(
                "DELETE_RESOURCE",
                "Delete idle resource",
                "Resource has been idle for 7+ days with no activity",
                null,
                300.00,
                0.92,
                "LOW"
            ));
        } else if (isStorage) {
            recommendations.add(new MockRecommendation(
                "CHANGE_STORAGE_TIER",
                "Move to cool storage tier",
                "Access patterns suggest infrequent access - move to cool tier",
                "Cool Storage",
                45.00,
                0.85,
                "LOW"
            ));
        } else {
            // Default: underutilized compute
            recommendations.add(new MockRecommendation(
                "DOWNSIZE_INSTANCE",
                "Downsize instance",
                "Resource is underutilized at 15% CPU usage. Consider downsizing.",
                "2 vCPU / 8 GB (current: 4 vCPU / 16 GB)",
                62.40,
                0.91,
                "MEDIUM"
            ));
        }
        
        return recommendations;
    }
    
    // Response DTOs
    record MockAnalysisResponse(
        String resourceId,
        String resourceName,
        double monthlyCostForecast,
        double confidence,
        String utilizationStatus,
        String severityLevel,
        double avgCpuUtilization,
        List<MockRecommendation> recommendations
    ) {}
    
    record MockRecommendation(
        String action,
        String actionDisplayName,
        String summary,
        String suggestedConfig,
        double estimatedSavings,
        double confidence,
        String riskLevel
    ) {}
}
