package com.adaptivefit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "user_profiles")
@Getter @Setter @NoArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Integer age;

    @Column(name = "height_cm", nullable = false, precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "fitness_level", nullable = false)
    private String fitnessLevel;

    @Column(nullable = false)
    private String goal;

    @Column(name = "available_minutes", nullable = false)
    private Integer availableMinutes;

    @Column(name = "preferred_workout")
    private String preferredWorkout;

    @Column(nullable = false)
    private String location;

    private String equipment;
}
