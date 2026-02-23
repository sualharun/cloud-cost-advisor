package com.microsoft.cloudoptimizer.simulation;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service for simulating cost impacts of configuration changes.
 *
 * Uses mock pricing data from CloudCostAdapter to estimate costs
 * for different resource configurations without making real changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostSimulationService {

    private final Map<CloudProvider, CloudCostAdapter> costAdapters;

    private static final double HOURS_PER_MONTH = 730; // Average hours in a month
    private static final double RI_1_YEAR_DISCOUNT = 0.30;  // 30% savings for 1-year RI
    private static final double RI_3_YEAR_DISCOUNT = 0.60;  // 60% savings for 3-year RI
    private static final double SPOT_DISCOUNT = 0.70;       // ~70% savings for spot instances
    private static final double DEV_HOURS_DISCOUNT = 0.50;  // 50% savings for 12hr/day schedule

    /**
     * Simulate cost change from switching to a different SKU.
     */
    public SimulationResult simulateSkuChange(String tenantId, CloudProvider provider,
                                               String resourceId, String currentSku,
                                               String targetSku, String region) {
        log.info("Simulating SKU change: {} -> {} in {}", currentSku, targetSku, region);

        var adapter = costAdapters.get(provider);
        if (adapter == null) {
            return SimulationResult.error("Unsupported provider: " + provider);
        }

        Optional<Double> currentPriceOpt = adapter.getSkuPricing(currentSku, region);
        Optional<Double> targetPriceOpt = adapter.getSkuPricing(targetSku, region);

        if (currentPriceOpt.isEmpty()) {
            return SimulationResult.error("Unknown SKU: " + currentSku);
        }
        if (targetPriceOpt.isEmpty()) {
            return SimulationResult.error("Unknown target SKU: " + targetSku);
        }

        double currentHourly = currentPriceOpt.get();
        double targetHourly = targetPriceOpt.get();

        double currentMonthly = currentHourly * HOURS_PER_MONTH;
        double targetMonthly = targetHourly * HOURS_PER_MONTH;
        double savings = currentMonthly - targetMonthly;
        double savingsPercentage = (savings / currentMonthly) * 100;

        return new SimulationResult(
                resourceId,
                currentMonthly,
                targetMonthly,
                savings,
                savingsPercentage,
                0.9, // High confidence for direct SKU pricing
                SimulationType.SKU_CHANGE,
                String.format("Change from %s to %s", currentSku, targetSku)
        );
    }

    /**
     * Simulate cost savings from purchasing a Reserved Instance.
     */
    public SimulationResult simulateReservation(String tenantId, CloudProvider provider,
                                                 String resourceId, String sku, String region,
                                                 ReservationTerm term) {
        log.info("Simulating reservation for {} with {} term", sku, term);

        var adapter = costAdapters.get(provider);
        if (adapter == null) {
            return SimulationResult.error("Unsupported provider: " + provider);
        }

        Optional<Double> priceOpt = adapter.getSkuPricing(sku, region);
        if (priceOpt.isEmpty()) {
            return SimulationResult.error("Unknown SKU: " + sku);
        }

        double onDemandHourly = priceOpt.get();
        double onDemandMonthly = onDemandHourly * HOURS_PER_MONTH;

        double discount = switch (term) {
            case ONE_YEAR -> RI_1_YEAR_DISCOUNT;
            case THREE_YEAR -> RI_3_YEAR_DISCOUNT;
        };

        double reservedMonthly = onDemandMonthly * (1 - discount);
        double savings = onDemandMonthly - reservedMonthly;
        double savingsPercentage = discount * 100;

        return new SimulationResult(
                resourceId,
                onDemandMonthly,
                reservedMonthly,
                savings,
                savingsPercentage,
                0.95, // Very high confidence for known RI discounts
                SimulationType.RESERVATION,
                String.format("%s Reserved Instance for %s", term.getDisplayName(), sku)
        );
    }

    /**
     * Simulate cost savings from using Spot/Preemptible instances.
     */
    public SimulationResult simulateSpot(String tenantId, CloudProvider provider,
                                          String resourceId, String sku, String region) {
        log.info("Simulating spot instance for {}", sku);

        var adapter = costAdapters.get(provider);
        if (adapter == null) {
            return SimulationResult.error("Unsupported provider: " + provider);
        }

        Optional<Double> priceOpt = adapter.getSkuPricing(sku, region);
        if (priceOpt.isEmpty()) {
            return SimulationResult.error("Unknown SKU: " + sku);
        }

        double onDemandHourly = priceOpt.get();
        double onDemandMonthly = onDemandHourly * HOURS_PER_MONTH;

        // Spot pricing varies, use average discount
        double spotMonthly = onDemandMonthly * (1 - SPOT_DISCOUNT);
        double savings = onDemandMonthly - spotMonthly;

        return new SimulationResult(
                resourceId,
                onDemandMonthly,
                spotMonthly,
                savings,
                SPOT_DISCOUNT * 100,
                0.7, // Lower confidence due to spot price variability
                SimulationType.SPOT,
                String.format("Spot instance for %s (prices may vary)", sku)
        );
    }

    /**
     * Simulate cost savings from scheduling resource shutdown.
     */
    public SimulationResult simulateScheduling(String tenantId, CloudProvider provider,
                                                String resourceId, String sku, String region,
                                                ScheduleType schedule) {
        log.info("Simulating {} schedule for {}", schedule, sku);

        var adapter = costAdapters.get(provider);
        if (adapter == null) {
            return SimulationResult.error("Unsupported provider: " + provider);
        }

        Optional<Double> priceOpt = adapter.getSkuPricing(sku, region);
        if (priceOpt.isEmpty()) {
            return SimulationResult.error("Unknown SKU: " + sku);
        }

        double hourlyPrice = priceOpt.get();
        double fullTimeMonthly = hourlyPrice * HOURS_PER_MONTH;

        double runningHoursRatio = switch (schedule) {
            case BUSINESS_HOURS -> 0.298; // 10hr/day * 5 days/week = 50hr/168hr
            case DEV_HOURS -> 0.357;       // 12hr/day * 5 days/week = 60hr/168hr
            case WEEKDAYS_ONLY -> 0.714;   // 24hr * 5 days/week = 120hr/168hr
        };

        double scheduledMonthly = fullTimeMonthly * runningHoursRatio;
        double savings = fullTimeMonthly - scheduledMonthly;
        double savingsPercentage = (1 - runningHoursRatio) * 100;

        return new SimulationResult(
                resourceId,
                fullTimeMonthly,
                scheduledMonthly,
                savings,
                savingsPercentage,
                0.85,
                SimulationType.SCHEDULING,
                String.format("%s schedule: %s", schedule.getDisplayName(), schedule.getDescription())
        );
    }

    /**
     * Simulate cost change from a generic configuration map.
     * Used when the frontend provides arbitrary config changes.
     */
    public SimulationResult simulateConfigChange(String tenantId, CloudProvider provider,
                                                  String resourceId, String currentSku,
                                                  String region, Map<String, Object> proposedConfig) {
        // Extract target SKU from proposed config if available
        String targetSku = null;
        if (proposedConfig != null) {
            if (proposedConfig.containsKey("sku")) {
                targetSku = String.valueOf(proposedConfig.get("sku"));
            } else if (proposedConfig.containsKey("vmSize")) {
                targetSku = String.valueOf(proposedConfig.get("vmSize"));
            } else if (proposedConfig.containsKey("instanceType")) {
                targetSku = String.valueOf(proposedConfig.get("instanceType"));
            }
        }

        if (targetSku != null && !targetSku.equals(currentSku)) {
            return simulateSkuChange(tenantId, provider, resourceId, currentSku, targetSku, region);
        }

        // Check for reservation simulation
        if (proposedConfig != null && proposedConfig.containsKey("reservationTerm")) {
            String termStr = String.valueOf(proposedConfig.get("reservationTerm"));
            ReservationTerm term = termStr.contains("3") ?
                    ReservationTerm.THREE_YEAR : ReservationTerm.ONE_YEAR;
            return simulateReservation(tenantId, provider, resourceId, currentSku, region, term);
        }

        // Check for spot simulation
        if (proposedConfig != null && Boolean.TRUE.equals(proposedConfig.get("useSpot"))) {
            return simulateSpot(tenantId, provider, resourceId, currentSku, region);
        }

        // Check for scheduling simulation
        if (proposedConfig != null && proposedConfig.containsKey("schedule")) {
            String scheduleStr = String.valueOf(proposedConfig.get("schedule"));
            ScheduleType schedule = switch (scheduleStr.toLowerCase()) {
                case "business_hours", "business" -> ScheduleType.BUSINESS_HOURS;
                case "weekdays", "weekdays_only" -> ScheduleType.WEEKDAYS_ONLY;
                default -> ScheduleType.DEV_HOURS;
            };
            return simulateScheduling(tenantId, provider, resourceId, currentSku, region, schedule);
        }

        return SimulationResult.error("Unable to determine simulation type from config");
    }

    // DTOs and Enums

    public record SimulationResult(
            String resourceId,
            double currentMonthlyCost,
            double projectedMonthlyCost,
            double savingsAmount,
            double savingsPercentage,
            double confidence,
            SimulationType simulationType,
            String description
    ) {
        static SimulationResult error(String message) {
            return new SimulationResult(null, 0, 0, 0, 0, 0, null, message);
        }

        public boolean isSuccess() {
            return resourceId != null && simulationType != null;
        }
    }

    public enum SimulationType {
        SKU_CHANGE,
        RESERVATION,
        SPOT,
        SCHEDULING,
        REGION_CHANGE
    }

    public enum ReservationTerm {
        ONE_YEAR("1-Year"),
        THREE_YEAR("3-Year");

        private final String displayName;

        ReservationTerm(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum ScheduleType {
        BUSINESS_HOURS("Business Hours", "9 AM - 7 PM, Mon-Fri"),
        DEV_HOURS("Dev Hours", "8 AM - 8 PM, Mon-Fri"),
        WEEKDAYS_ONLY("Weekdays Only", "24/7, Mon-Fri only");

        private final String displayName;
        private final String description;

        ScheduleType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }
}
