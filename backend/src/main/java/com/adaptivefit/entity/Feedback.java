package com.adaptivefit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks")
@Getter @Setter @NoArgsConstructor
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", unique = true)
    private WorkoutSession session;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", unique = true)
    private RecommendationHistory recommendation;

    @Column
    private Boolean completed;

    private Integer difficulty;
    private Integer fatigue;
    private Integer satisfaction;
    private Boolean liked;

    @Column(name = "not_completed_reason")
    private String notCompletedReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_status")
    private RecommendationFeedbackStatus recommendationStatus;

    @Column(name = "suitability_rating")
    private Integer suitabilityRating;

    @Column(name = "recommendation_difficulty")
    private Integer recommendationDifficulty;

    @Column(name = "recommendation_reason")
    private String recommendationReason;

    @Column(name = "recommendation_comment")
    private String recommendationComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
