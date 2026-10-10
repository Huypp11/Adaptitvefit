package com.adaptivefit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "exercises")
@Getter @Setter @NoArgsConstructor
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Column(name = "duration_max", nullable = false)
    private Integer durationMax;

    @Column(nullable = false)
    private Integer difficulty;

    @Column(nullable = false)
    private String intensity;

    private String equipment;

    @Column(nullable = false)
    private String location;

    @Column(name = "muscle_group")
    private String muscleGroup;

    private String goal;

    @Column(name = "energy_requirement", nullable = false)
    private Integer energyRequirement;

    @Column(name = "recovery_suitable", nullable = false)
    private Boolean recoverySuitable;

    @Column(length = 2000)
    private String instructions;

    @Column(name = "media_url")
    private String mediaUrl;

    /** MET (Metabolic Equivalent of Task) dung de uoc tinh calo; null thi frontend dung gia tri theo cuong do. */
    private Double met;

    /** Cac buoc thuc hien, moi dong mot buoc. */
    @Column(length = 4000)
    private String steps;

    /** Duong dan anh minh hoa, moi dong mot duong dan. */
    @Column(name = "media_urls", length = 1000)
    private String mediaUrls;

    @Column(length = 1000)
    private String tips;

    @Column(nullable = false)
    private Boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercise_tags", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "tag", nullable = false, length = 40)
    private Set<String> tags = new HashSet<>();
}
