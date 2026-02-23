package com.microsoft.cloudoptimizer.domain.repository;

import com.microsoft.cloudoptimizer.domain.model.TradeoffDimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeoffDimensionRepository extends JpaRepository<TradeoffDimension, Long> {

    /**
     * Find a dimension by its unique name.
     */
    Optional<TradeoffDimension> findByName(String name);

    /**
     * Find all active dimensions ordered by display order.
     */
    List<TradeoffDimension> findByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Check if a dimension exists by name.
     */
    boolean existsByName(String name);
}
