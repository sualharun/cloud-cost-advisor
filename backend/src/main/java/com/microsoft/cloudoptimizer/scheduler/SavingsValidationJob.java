package com.microsoft.cloudoptimizer.scheduler;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.ImplementedRecommendation;
import com.microsoft.cloudoptimizer.domain.model.ValidationStatus;
import com.microsoft.cloudoptimizer.domain.repository.ImplementedRecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Scheduled job to validate savings from implemented recommendations.
 *
 * Runs daily to check implementations that are past their scheduled validation date
 * and compares actual costs before and after implementation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SavingsValidationJob {

    private final ImplementedRecommendationRepository implementedRecommendationRepository;
    private final Map<CloudProvider, CloudCostAdapter> costAdapters;

    private static final double VALIDATION_THRESHOLD = 0.5; // 50% of expected savings to be "validated"
    private static final double PARTIAL_THRESHOLD = 0.25;   // 25% of expected savings to be "partial"
    private static final int COMPARISON_DAYS = 14;          // Days to compare before/after

    /**
     * Run validation daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void validatePendingImplementations() {
        log.info("Starting savings validation job");

        var pendingValidations = implementedRecommendationRepository
                .findByValidationStatusAndScheduledValidationAtBefore(
                        ValidationStatus.PENDING,
                        LocalDateTime.now()
                );

        log.info("Found {} implementations pending validation", pendingValidations.size());

        int validated = 0;
        int partial = 0;
        int failed = 0;

        for (var impl : pendingValidations) {
            try {
                validateImplementation(impl);
                switch (impl.getValidationStatus()) {
                    case VALIDATED -> validated++;
                    case PARTIAL -> partial++;
                    case FAILED -> failed++;
                    default -> {}
                }
            } catch (Exception e) {
                log.error("Failed to validate implementation {}: {}", impl.getId(), e.getMessage());
            }
        }

        log.info("Validation complete: {} validated, {} partial, {} failed",
                validated, partial, failed);
    }

    /**
     * Validate a single implementation by comparing costs before and after.
     */
    private void validateImplementation(ImplementedRecommendation impl) {
        log.debug("Validating implementation {} for resource {}",
                impl.getId(), impl.getResourceId());

        var adapter = costAdapters.get(impl.getProvider());
        if (adapter == null) {
            log.warn("No adapter found for provider {} - skipping validation", impl.getProvider());
            markAsFailed(impl, "No adapter available for provider");
            return;
        }

        LocalDate implementedDate = impl.getImplementedAt().toLocalDate();
        LocalDate now = LocalDate.now();

        // Get cost data before implementation
        LocalDate beforeStart = implementedDate.minusDays(COMPARISON_DAYS);
        LocalDate beforeEnd = implementedDate.minusDays(1);

        var costsBefore = adapter.fetchCostData(
                impl.getTenantId(),
                impl.getResourceId(),
                beforeStart,
                beforeEnd
        );

        if (costsBefore.isEmpty()) {
            log.warn("No cost data before implementation for {}", impl.getResourceId());
            markAsFailed(impl, "No cost data available before implementation");
            return;
        }

        // Get cost data after implementation
        LocalDate afterStart = implementedDate.plusDays(7); // Skip first week for stabilization
        LocalDate afterEnd = now.minusDays(1);

        // Ensure we have at least 7 days of data after
        if (afterEnd.isBefore(afterStart.plusDays(7))) {
            log.debug("Not enough post-implementation data yet for {}", impl.getResourceId());
            return; // Skip for now, will retry later
        }

        var costsAfter = adapter.fetchCostData(
                impl.getTenantId(),
                impl.getResourceId(),
                afterStart,
                afterEnd
        );

        if (costsAfter.isEmpty()) {
            // Resource might have been deleted - that's a success for DELETE recommendations
            if (impl.getAction() != null && impl.getAction().name().contains("DELETE")) {
                markAsValidated(impl,
                        impl.getExpectedMonthlySavings(),
                        BigDecimal.ZERO,
                        impl.getExpectedMonthlySavings(),
                        "Resource deleted - full savings realized");
            } else {
                markAsFailed(impl, "No cost data available after implementation");
            }
            return;
        }

        // Calculate average daily costs
        BigDecimal avgDailyBefore = costsBefore.stream()
                .map(c -> c.getDailyCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(costsBefore.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgDailyAfter = costsAfter.stream()
                .map(c -> c.getDailyCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(costsAfter.size()), 2, RoundingMode.HALF_UP);

        // Calculate actual monthly savings
        BigDecimal dailySavings = avgDailyBefore.subtract(avgDailyAfter);
        BigDecimal actualMonthlySavings = dailySavings.multiply(BigDecimal.valueOf(30));

        // Compare to expected
        BigDecimal expectedSavings = impl.getExpectedMonthlySavings() != null ?
                impl.getExpectedMonthlySavings() : BigDecimal.ZERO;

        double savingsRatio = expectedSavings.doubleValue() > 0 ?
                actualMonthlySavings.doubleValue() / expectedSavings.doubleValue() : 0;

        log.info("Validation for {}: before=${}/day, after=${}/day, savings=${}/mo ({}% of expected)",
                impl.getResourceId(),
                avgDailyBefore,
                avgDailyAfter,
                actualMonthlySavings,
                Math.round(savingsRatio * 100));

        impl.setCostBeforeDaily(avgDailyBefore);
        impl.setCostAfterDaily(avgDailyAfter);
        impl.setActualMonthlySavings(actualMonthlySavings);
        impl.setActualValidatedAt(LocalDateTime.now());

        if (savingsRatio >= VALIDATION_THRESHOLD) {
            impl.setValidationStatus(ValidationStatus.VALIDATED);
            impl.setValidationNotes(String.format(
                    "Savings validated: $%.2f/mo actual vs $%.2f/mo expected (%.0f%%)",
                    actualMonthlySavings.doubleValue(),
                    expectedSavings.doubleValue(),
                    savingsRatio * 100
            ));
        } else if (savingsRatio >= PARTIAL_THRESHOLD) {
            impl.setValidationStatus(ValidationStatus.PARTIAL);
            impl.setValidationNotes(String.format(
                    "Partial savings: $%.2f/mo actual vs $%.2f/mo expected (%.0f%%)",
                    actualMonthlySavings.doubleValue(),
                    expectedSavings.doubleValue(),
                    savingsRatio * 100
            ));
        } else {
            impl.setValidationStatus(ValidationStatus.FAILED);
            impl.setValidationNotes(String.format(
                    "Insufficient savings: $%.2f/mo actual vs $%.2f/mo expected (%.0f%%)",
                    actualMonthlySavings.doubleValue(),
                    expectedSavings.doubleValue(),
                    savingsRatio * 100
            ));
        }

        implementedRecommendationRepository.save(impl);
    }

    private void markAsValidated(ImplementedRecommendation impl,
                                  BigDecimal costBefore, BigDecimal costAfter,
                                  BigDecimal actualSavings, String notes) {
        impl.setCostBeforeDaily(costBefore);
        impl.setCostAfterDaily(costAfter);
        impl.setActualMonthlySavings(actualSavings);
        impl.setActualValidatedAt(LocalDateTime.now());
        impl.setValidationStatus(ValidationStatus.VALIDATED);
        impl.setValidationNotes(notes);
        implementedRecommendationRepository.save(impl);
    }

    private void markAsFailed(ImplementedRecommendation impl, String reason) {
        impl.setActualValidatedAt(LocalDateTime.now());
        impl.setValidationStatus(ValidationStatus.FAILED);
        impl.setValidationNotes(reason);
        implementedRecommendationRepository.save(impl);
    }
}
