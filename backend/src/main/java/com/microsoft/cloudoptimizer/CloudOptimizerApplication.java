package com.microsoft.cloudoptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Multi-Cloud AI Cost Optimization Advisor
 *
 * Enterprise-grade cost optimization platform with browser extension integration.
 * Provides ML-powered insights for Azure, AWS, and GCP resources.
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class CloudOptimizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudOptimizerApplication.class, args);
    }
}
