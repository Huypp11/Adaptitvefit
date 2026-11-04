package com.adaptivefit.repository;

import com.adaptivefit.entity.RecommendationHistory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from RecommendationHistory r
            where r.checkin.id = :checkinId and r.user.id = :userId
            """)
    Optional<RecommendationHistory> findForUpdate(@Param("checkinId") Long checkinId,
                                                  @Param("userId") Long userId);

    Optional<RecommendationHistory> findByIdAndUserId(Long id, Long userId);

    Page<RecommendationHistory> findByUserId(Long userId, Pageable pageable);
}
