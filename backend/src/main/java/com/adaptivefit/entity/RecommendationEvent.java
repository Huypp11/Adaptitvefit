package com.adaptivefit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_recommendation_event_idempotency",
                columnNames = {"user_id", "idempotency_key"}))
@Getter @Setter @NoArgsConstructor
public class RecommendationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id")
    private RecommendationHistory recommendation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_session_id")
    private WorkoutSession workoutSession;

    @Column(name = "tracking_id", nullable = false, length = 80)
    private String trackingId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "recommendation_mode", nullable = false, length = 24)
    private String recommendationMode;

    @Column(name = "idempotency_key", nullable = false, length = 191)
    private String idempotencyKey;

    @Column(name = "payload_json", length = 2000)
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
