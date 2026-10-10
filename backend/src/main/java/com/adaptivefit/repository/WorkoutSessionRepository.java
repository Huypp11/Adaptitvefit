package com.adaptivefit.repository;

import com.adaptivefit.entity.WorkoutSession;
import com.adaptivefit.entity.WorkoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndStatus(Long userId, WorkoutStatus status);

    Page<WorkoutSession> findByUserId(Long userId, Pageable pageable);
}
