package com.adaptivefit.repository;

import com.adaptivefit.entity.Feedback;
import com.adaptivefit.entity.RecommendationFeedbackStatus;
import com.adaptivefit.entity.WorkoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    boolean existsBySessionId(Long sessionId);
    boolean existsByRecommendation_Id(Long recommendationId);
    Optional<Feedback> findByRecommendation_Id(Long recommendationId);

    long countByRecommendationStatus(RecommendationFeedbackStatus status);

    @Query("""
            select f.recommendationReason, count(f) from Feedback f
            where f.recommendation is not null and f.recommendationReason is not null
            group by f.recommendationReason
            """)
    List<Object[]> countRecommendationReasons();

    @Query("""
            select avg(f.suitabilityRating) from Feedback f
            where f.recommendation is not null and f.suitabilityRating is not null
            """)
    Double averageRecommendationSuitability();

    @Query("""
            select f from Feedback f
            left join fetch f.session s
            left join fetch f.recommendation r
            where s.user.id = :userId or r.user.id = :userId
            order by f.createdAt desc
            """)
    List<Feedback> findAllForUser(@Param("userId") Long userId);

    @Query("""
            select f from Feedback f join fetch f.session s
            where s.user.id = :userId and s.status = :status and s.finishedAt >= :since
            """)
    List<Feedback> findWorkoutFeedbackSince(@Param("userId") Long userId,
                                            @Param("status") WorkoutStatus status,
                                            @Param("since") LocalDateTime since);

    @Query("""
            select f.notCompletedReason from Feedback f join f.session s
            where s.user.id = :userId and s.status = :status
              and s.finishedAt >= :since and f.notCompletedReason is not null
              and s.finishedAt < :until
            order by f.createdAt desc
            """)
    List<String> findRecentAbandonmentReasons(@Param("userId") Long userId,
                                              @Param("status") WorkoutStatus status,
                                              @Param("since") LocalDateTime since,
                                              @Param("until") LocalDateTime until,
                                              Pageable pageable);

    Optional<Feedback> findBySession_Id(Long sessionId);
}
