package com.microsoft.cloudoptimizer.domain.repository;

import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import com.microsoft.cloudoptimizer.domain.model.ResourceAlternative;
import com.microsoft.cloudoptimizer.domain.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceAlternativeRepository extends JpaRepository<ResourceAlternative, Long> {

    /**
     * Find active alternatives for a specific current SKU.
     */
    List<ResourceAlternative> findByProviderAndCurrentSkuAndActiveTrue(
            CloudProvider provider, String currentSku);

    /**
     * Find alternatives by resource type.
     */
    List<ResourceAlternative> findByResourceTypeAndActiveTrue(ResourceType resourceType);

    /**
     * Find all alternatives for a provider.
     */
    List<ResourceAlternative> findByProviderAndActiveTrue(CloudProvider provider);

    /**
     * Check if alternatives exist for a SKU.
     */
    boolean existsByProviderAndCurrentSkuAndActiveTrue(CloudProvider provider, String currentSku);

    /**
     * Find cross-cloud alternatives (where alternativeProvider differs).
     */
    List<ResourceAlternative> findByProviderAndCurrentSkuAndAlternativeProviderNotAndActiveTrue(
            CloudProvider provider, String currentSku, CloudProvider excludeProvider);
}
