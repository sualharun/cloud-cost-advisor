package com.microsoft.cloudoptimizer.domain.repository;

import com.microsoft.cloudoptimizer.domain.model.TenantPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantPreferencesRepository extends JpaRepository<TenantPreferences, Long> {

    /**
     * Find preferences by tenant ID.
     */
    Optional<TenantPreferences> findByTenantId(String tenantId);

    /**
     * Check if preferences exist for a tenant.
     */
    boolean existsByTenantId(String tenantId);
}
