package com.microsoft.cloudoptimizer.domain.repository;

import com.microsoft.cloudoptimizer.domain.model.AlternativeTradeoffScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlternativeTradeoffScoreRepository extends JpaRepository<AlternativeTradeoffScore, Long> {

    /**
     * Find all scores for a specific alternative.
     */
    List<AlternativeTradeoffScore> findByAlternativeId(Long alternativeId);

    /**
     * Find score for a specific alternative and dimension.
     */
    Optional<AlternativeTradeoffScore> findByAlternativeIdAndDimensionId(
            Long alternativeId, Long dimensionId);

    /**
     * Find scores for multiple alternatives.
     */
    List<AlternativeTradeoffScore> findByAlternativeIdIn(List<Long> alternativeIds);

    /**
     * Delete all scores for an alternative.
     */
    @Modifying
    void deleteByAlternativeId(Long alternativeId);

    /**
     * Find stale scores that need recalculation.
     */
    @Query("SELECT s FROM AlternativeTradeoffScore s WHERE s.lastUpdated < :threshold")
    List<AlternativeTradeoffScore> findStaleScores(@Param("threshold") LocalDateTime threshold);
}
