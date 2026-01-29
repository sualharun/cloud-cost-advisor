package com.microsoft.cloudoptimizer.ml;

import com.microsoft.cloudoptimizer.normalization.CostNormalizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Local forecasting service that uses simple statistical methods.
 *
 * NO AZURE ML REQUIRED!
 *
 * This service provides realistic forecasts using:
 * 1. Exponential smoothing for trend detection
 * 2. Simple moving average for baseline
 * 3. Variance-based confidence calculation
 *
 * Perfect for local development and testing.
 */
@Service
@Primary
@ConditionalOnProperty(name = "app.env", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalForecastingService {

    private static final int MINIMUM_DATA_POINTS = 7;

    public LocalForecastingService() {
        log.info("📈 LOCAL MODE: Using LocalForecastingService (no Azure ML required)");
    }

    /**
     * Generate cost forecast using simple statistical methods.
     */
    public ForecastingService.ForecastResult forecastCost(
            List<CostNormalizationService.CostTimeSeriesPoint> timeSeries,
            int forecastDays
    ) {
        log.debug("LOCAL FORECAST: Generating {}-day forecast from {} data points",
                forecastDays, timeSeries.size());

        if (timeSeries.size() < MINIMUM_DATA_POINTS) {
            return ForecastingService.ForecastResult.insufficientData();
        }

        // Extract cost values
        double[] costs = timeSeries.stream()
                .mapToDouble(CostNormalizationService.CostTimeSeriesPoint::cost)
                .toArray();

        // Calculate exponential moving average (EMA)
        double alpha = 0.3; // Smoothing factor
        double ema = costs[0];
        for (int i = 1; i < costs.length; i++) {
            ema = alpha * costs[i] + (1 - alpha) * ema;
        }

        // Calculate trend (slope)
        int recentPeriod = Math.min(14, costs.length);
        double recentSum = 0, earlierSum = 0;
        for (int i = costs.length - recentPeriod; i < costs.length; i++) {
            recentSum += costs[i];
        }
        recentSum /= recentPeriod;

        int earlierPeriod = Math.min(recentPeriod, costs.length - recentPeriod);
        if (earlierPeriod > 0) {
            for (int i = costs.length - recentPeriod - earlierPeriod; i < costs.length - recentPeriod; i++) {
                earlierSum += costs[i];
            }
            earlierSum /= earlierPeriod;
        } else {
            earlierSum = recentSum;
        }

        double dailyTrend = (recentSum - earlierSum) / recentPeriod;

        // Generate forecast
        double[] dailyForecasts = new double[forecastDays];
        LocalDate[] forecastDates = new LocalDate[forecastDays];
        LocalDate startDate = timeSeries.get(timeSeries.size() - 1).date().plusDays(1);

        double runningForecast = ema;
        for (int i = 0; i < forecastDays; i++) {
            runningForecast = Math.max(0, runningForecast + dailyTrend);
            dailyForecasts[i] = runningForecast;
            forecastDates[i] = startDate.plusDays(i);
        }

        // Calculate monthly forecast (first 30 days)
        double monthlyForecast = 0;
        for (int i = 0; i < Math.min(30, forecastDays); i++) {
            monthlyForecast += dailyForecasts[i];
        }

        // Calculate confidence based on data quality
        double variance = calculateVariance(costs);
        double stdDev = Math.sqrt(variance);
        double coeffOfVariation = ema > 0 ? stdDev / ema : 1.0;

        // More stable data = higher confidence
        double confidence = Math.max(0.5, Math.min(0.95, 1.0 - coeffOfVariation));

        // Adjust confidence based on data volume
        confidence *= Math.min(1.0, timeSeries.size() / 30.0);

        log.debug("LOCAL FORECAST: Monthly forecast = ${}, confidence = {}%",
                String.format("%.2f", monthlyForecast),
                String.format("%.0f", confidence * 100));

        return new ForecastingService.ForecastResult(
                true,
                forecastDays,
                monthlyForecast,
                dailyForecasts,
                forecastDates,
                confidence,
                monthlyForecast * (1 - (1 - confidence) * 0.5),
                monthlyForecast * (1 + (1 - confidence) * 0.5),
                "local-statistical"
        );
    }

    private double calculateVariance(double[] values) {
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;

        double variance = 0;
        for (double v : values) {
            variance += (v - mean) * (v - mean);
        }
        return variance / values.length;
    }
}
