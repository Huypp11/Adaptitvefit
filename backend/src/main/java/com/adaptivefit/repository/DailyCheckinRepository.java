package com.adaptivefit.repository;

import com.adaptivefit.entity.DailyCheckin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, Long> {
    boolean existsByUserIdAndCheckinDate(Long userId, LocalDate date);
    Optional<DailyCheckin> findByUserIdAndCheckinDate(Long userId, LocalDate date);
    List<DailyCheckin> findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(Long userId, LocalDate from, LocalDate to);
}
