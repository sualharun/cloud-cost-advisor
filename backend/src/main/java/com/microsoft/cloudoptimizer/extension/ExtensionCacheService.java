package com.microsoft.cloudoptimizer.extension;

import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Caching service for extension analysis results.
 *
 * CACHING STRATEGY:
 * - Analysis results are cached for 5 minutes
 * - Cache key: tenant + provider + resourceId
 * - Cache is warmed during batch ingestion
 * - Stale entries are evicted on schedule
 *
 * This reduces backend load when users navigate between
 * resource pages frequently.
 */
@Service
@Slf4j
public class ExtensionCacheService {

    private static final String CACHE_NAME = "extension-analysis";

    @Cacheable(value = CACHE_NAME, key = "#tenantId + ':' + #provider + ':' + #resourceId",
               unless = "#result == null")
    public ExtensionAnalysisController.ExtensionAnalysisResponse getCachedAnalysis(
            String tenantId,
            CloudProvider provider,
            String resourceId
    ) {
        // Return null to trigger actual analysis
        // Cache framework will store the result on next put
        return null;
    }

    @CachePut(value = CACHE_NAME, key = "#tenantId + ':' + #provider + ':' + #resourceId")
    public ExtensionAnalysisController.ExtensionAnalysisResponse cacheAnalysis(
            String tenantId,
            CloudProvider provider,
            String resourceId,
            ExtensionAnalysisController.ExtensionAnalysisResponse response
    ) {
        log.debug("Caching analysis for resource: {}", resourceId);
        return response;
    }

    @CacheEvict(value = CACHE_NAME, key = "#tenantId + ':' + #provider + ':' + #resourceId")
    public void evictAnalysis(String tenantId, CloudProvider provider, String resourceId) {
        log.debug("Evicting cached analysis for resource: {}", resourceId);
    }

    @CacheEvict(value = CACHE_NAME, allEntries = true)
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void evictAllExpired() {
        log.debug("Evicting all expired cache entries");
    }

    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAllForTenant(String tenantId) {
        log.info("Evicting all cache entries for tenant: {}", tenantId);
        // Note: This evicts all entries; for tenant-specific eviction,
        // consider using a more sophisticated cache implementation
    }
}
