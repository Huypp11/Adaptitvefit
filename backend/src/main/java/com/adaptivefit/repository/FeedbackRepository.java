package com.adaptivefit.repository;

import com.adaptivefit.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    boolean existsBySessionId(Long sessionId);
}
