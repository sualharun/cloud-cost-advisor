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

    @PostMapping("/recommendations/{id}/dismiss")
    public ResponseEntity<?> dismissRecommendation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        System.out.println("🧪 Mock Extension API - Dismissing recommendation: " + id);
        String reason = (String) request.getOrDefault("reason", "No reason provided");
        return ResponseEntity.ok(Map.of(
            "recommendationId", id,
            "status", "DISMISSED",
            "feedback", reason,
            "dismissedAt", java.time.LocalDateTime.now().toString(),
            "success", true
        ));
    }

    @PostMapping("/recommendations/{id}/undismiss")
    public ResponseEntity<?> undismissRecommendation(@PathVariable Long id) {
        System.out.println("🧪 Mock Extension API - Undismissing recommendation: " + id);
        return ResponseEntity.ok(Map.of(
            "recommendationId", id,
            "status", "ACTIVE",
            "restoredAt", java.time.LocalDateTime.now().toString(),
            "success", true
        ));
    }

    @GetMapping("/recommendations/dismissed")
    public ResponseEntity<?> getDismissedRecommendations() {
        System.out.println("🧪 Mock Extension API - Getting dismissed recommendations");
        return ResponseEntity.ok(List.of(
            Map.of(
                "recommendationId", 101,
                "resourceId", "/subscriptions/xxx/resourceGroups/rg/providers/Microsoft.Compute/virtualMachines/old-vm",
                "action", "DELETE_RESOURCE",
                "estimatedSavings", 150.00,
                "feedback", "Will be decommissioned next month",
                "dismissedAt", "2026-02-20T10:30:00"
            )
        ));
    }

    @PostMapping("/recommendations/{id}/implement")
    public ResponseEntity<?> markImplemented(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        System.out.println("🧪 Mock Extension API - Marking recommendation as implemented: " + id);
        return ResponseEntity.ok(Map.of(
            "implementedRecommendationId", 1,
            "recommendationId", id,
            "status", "IMPLEMENTED",
            "implementedAt", java.time.LocalDateTime.now().toString(),
            "expectedMonthlySavings", request.getOrDefault("expectedMonthlySavings", 62.40),
            "scheduledValidationAt", java.time.LocalDateTime.now().plusDays(30).toString(),
            "success", true
        ));
    }

    @GetMapping("/metrics/savings")
    public ResponseEntity<?> getSavingsMetrics() {
        System.out.println("🧪 Mock Extension API - Getting savings metrics");
        return ResponseEntity.ok(Map.of(
            "totalExpectedSavings", 312.80,
            "totalValidatedSavings", 285.50,
            "implementedCount", 5,
            "validationSuccessRate", 91.2,
            "pendingValidationCount", 2
        ));
    }

    @PostMapping("/simulate")
    public ResponseEntity<?> simulateChange(@RequestBody Map<String, Object> request) {
        System.out.println("🧪 Mock Extension API - Simulating SKU change: " + request);
        String currentSku = (String) request.getOrDefault("currentSku", "Standard_D4s_v3");
        String targetSku = (String) request.getOrDefault("targetSku", "Standard_D2s_v3");

        double currentPrice = getSkuPrice(currentSku);
        double targetPrice = getSkuPrice(targetSku);
        double monthlySavings = (currentPrice - targetPrice) * 730;

        return ResponseEntity.ok(Map.of(
            "currentSku", currentSku,
            "targetSku", targetSku,
            "currentMonthlyEstimate", currentPrice * 730,
            "targetMonthlyEstimate", targetPrice * 730,
            "monthlySavings", monthlySavings,
            "savingsPercentage", (monthlySavings / (currentPrice * 730)) * 100
        ));
    }

    @PostMapping("/simulate/reservation")
    public ResponseEntity<?> simulateReservation(@RequestBody Map<String, Object> request) {
        System.out.println("🧪 Mock Extension API - Simulating reservation: " + request);
        String sku = (String) request.getOrDefault("sku", "Standard_D4s_v3");
        int termYears = (int) request.getOrDefault("termYears", 1);

        double hourlyPrice = getSkuPrice(sku);
        double monthlyOnDemand = hourlyPrice * 730;
        double discount = termYears == 3 ? 0.60 : 0.30;
        double monthlyReserved = monthlyOnDemand * (1 - discount);

        return ResponseEntity.ok(Map.of(
            "sku", sku,
            "termYears", termYears,
            "monthlyOnDemandCost", monthlyOnDemand,
            "monthlyReservedCost", monthlyReserved,
            "monthlySavings", monthlyOnDemand - monthlyReserved,
            "savingsPercentage", discount * 100,
            "upfrontCost", monthlyReserved * 12 * termYears * 0.5
        ));
    }

    @PostMapping("/simulate/spot")
    public ResponseEntity<?> simulateSpot(@RequestBody Map<String, Object> request) {
        System.out.println("🧪 Mock Extension API - Simulating spot: " + request);
        String sku = (String) request.getOrDefault("sku", "Standard_D4s_v3");

        double hourlyPrice = getSkuPrice(sku);
        double monthlyOnDemand = hourlyPrice * 730;
        double spotDiscount = 0.70;
        double monthlySpot = monthlyOnDemand * (1 - spotDiscount);

        return ResponseEntity.ok(Map.of(
            "sku", sku,
            "monthlyOnDemandCost", monthlyOnDemand,
            "monthlySpotCost", monthlySpot,
            "monthlySavings", monthlyOnDemand - monthlySpot,
            "savingsPercentage", spotDiscount * 100,
            "interruptionRisk", "MEDIUM",
            "averageAvailability", 92.5
        ));
    }

    @PostMapping("/simulate/scheduling")
    public ResponseEntity<?> simulateScheduling(@RequestBody Map<String, Object> request) {
        System.out.println("🧪 Mock Extension API - Simulating scheduling: " + request);
        String sku = (String) request.getOrDefault("sku", "Standard_D4s_v3");
        int hoursPerDay = (int) request.getOrDefault("hoursPerDay", 10);
        int daysPerWeek = (int) request.getOrDefault("daysPerWeek", 5);

        double hourlyPrice = getSkuPrice(sku);
        double monthlyFull = hourlyPrice * 730;
        double monthlyScheduled = hourlyPrice * hoursPerDay * daysPerWeek * 4.33;

        return ResponseEntity.ok(Map.of(
            "sku", sku,
            "hoursPerDay", hoursPerDay,
            "daysPerWeek", daysPerWeek,
            "monthlyFullTimeCost", monthlyFull,
            "monthlyScheduledCost", monthlyScheduled,
            "monthlySavings", monthlyFull - monthlyScheduled,
            "savingsPercentage", ((monthlyFull - monthlyScheduled) / monthlyFull) * 100
        ));
    }

    @GetMapping("/alternatives")
    public ResponseEntity<?> getAlternatives(
            @RequestParam String provider,
            @RequestParam String resourceId,
            @RequestParam String currentSku,
            @RequestParam String region) {
        System.out.println("🧪 Mock Extension API - Getting alternatives for: " + currentSku);
        return ResponseEntity.ok(Map.of(
            "currentResource", Map.of(
                "resourceId", resourceId,
                "provider", provider,
                "sku", currentSku,
                "region", region,
                "estimatedMonthlyCost", getSkuPrice(currentSku) * 730
            ),
            "alternatives", List.of(
                Map.of(
                    "sku", "Standard_D2s_v3",
                    "displayName", "D2s v3 (2 vCPU, 8 GB)",
                    "estimatedMonthlyCost", 70.08,
                    "monthlySavings", 70.08,
                    "overallScore", 0.85,
                    "tradeoffs", List.of(
                        Map.of("dimension", "cost", "score", 0.95, "direction", "improvement"),
                        Map.of("dimension", "performance", "score", 0.70, "direction", "degradation"),
                        Map.of("dimension", "migration_effort", "score", 0.95, "direction", "neutral")
                    )
                ),
                Map.of(
                    "sku", "Standard_B2s",
                    "displayName", "B2s (2 vCPU, 4 GB)",
                    "estimatedMonthlyCost", 30.37,
                    "monthlySavings", 109.79,
                    "overallScore", 0.78,
                    "tradeoffs", List.of(
                        Map.of("dimension", "cost", "score", 0.98, "direction", "improvement"),
                        Map.of("dimension", "performance", "score", 0.50, "direction", "degradation"),
                        Map.of("dimension", "migration_effort", "score", 0.85, "direction", "neutral")
                    )
                )
            ),
            "userPreferences", Map.of(
                "cost", 0.35,
                "performance", 0.25,
                "availability", 0.15,
                "migration_effort", 0.15,
                "vendor_lock_in", 0.05,
                "environmental_impact", 0.05
            )
        ));
    }

    @GetMapping("/preferences/tradeoff-weights")
    public ResponseEntity<?> getTradeoffPreferences() {
        System.out.println("🧪 Mock Extension API - Getting tradeoff preferences");
        return ResponseEntity.ok(Map.of(
            "dimensions", List.of(
                Map.of("name", "cost", "displayName", "Cost Savings", "weight", 0.35, "description", "Prioritize cost reduction"),
                Map.of("name", "performance", "displayName", "Performance", "weight", 0.25, "description", "Maintain performance levels"),
                Map.of("name", "availability", "displayName", "Availability", "weight", 0.15, "description", "Service availability SLA"),
                Map.of("name", "migration_effort", "displayName", "Migration Effort", "weight", 0.15, "description", "Ease of migration"),
                Map.of("name", "vendor_lock_in", "displayName", "Vendor Lock-in", "weight", 0.05, "description", "Portability across clouds"),
                Map.of("name", "environmental_impact", "displayName", "Environmental Impact", "weight", 0.05, "description", "Carbon footprint")
            ),
            "minimumSavingsThreshold", 10.0,
            "includeMultiCloudAlternatives", false
        ));
    }

    @PutMapping("/preferences/tradeoff-weights")
    public ResponseEntity<?> updateTradeoffPreferences(@RequestBody Map<String, Object> request) {
        System.out.println("🧪 Mock Extension API - Updating tradeoff preferences: " + request);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Preferences updated successfully",
            "updatedAt", java.time.LocalDateTime.now().toString()
        ));
    }

    private double getSkuPrice(String sku) {
        return switch (sku) {
            case "Standard_D4s_v3", "m5.xlarge" -> 0.192;
            case "Standard_D2s_v3", "m5.large" -> 0.096;
            case "Standard_B2s", "t3.small" -> 0.0416;
            case "Standard_B1s", "t3.micro" -> 0.0104;
            default -> 0.10;
        };
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
