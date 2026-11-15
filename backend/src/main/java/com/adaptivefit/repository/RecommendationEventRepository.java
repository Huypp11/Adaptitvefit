package com.adaptivefit.repository;

import com.adaptivefit.entity.RecommendationEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecommendationEventRepository extends JpaRepository<RecommendationEvent, Long> {
    boolean existsByUser_IdAndIdempotencyKey(Long userId, String idempotencyKey);
    Optional<RecommendationEvent> findByUser_IdAndWorkoutSession_IdAndEventType(
            Long userId, Long workoutSessionId, String eventType);
    @EntityGraph(attributePaths = {"user", "workoutSession"})
    List<RecommendationEvent> findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(LocalDateTime since);
    long countByUser_Id(Long userId);
    void deleteByUser_Id(Long userId);
    long deleteByOccurredAtBefore(LocalDateTime cutoff);
}
