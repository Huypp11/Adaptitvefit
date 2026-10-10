package com.adaptivefit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_checkins")
@Getter @Setter @NoArgsConstructor
public class DailyCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "sleep_hours", nullable = false)
    private Double sleepHours;

    @Column(nullable = false) private Integer stress;
    @Column(nullable = false) private Integer energy;
    @Column(nullable = false) private Integer mood;

    @Column(name = "free_time_min", nullable = false)
    private Integer freeTimeMin;

    @Column(nullable = false)
    private Integer motivation;

    @Column(name = "exercised_today", nullable = false)
    private Boolean exercisedToday;

    @Column(name = "duration_min")
    private Integer durationMin;

    private Integer difficulty;

    @Column(name = "missed_reason")
    private String missedReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
