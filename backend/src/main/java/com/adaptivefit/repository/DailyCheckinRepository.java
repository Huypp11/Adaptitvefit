package com.adaptivefit.repository;

import com.adaptivefit.entity.DailyCheckin;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, Long> {
    boolean existsByUserIdAndCheckinDate(Long userId, LocalDate date);
    Optional<DailyCheckin> findByUserIdAndCheckinDate(Long userId, LocalDate date);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DailyCheckin c where c.user.id = :userId and c.checkinDate = :date")
    Optional<DailyCheckin> findByUserIdAndCheckinDateForUpdate(@Param("userId") Long userId,
                                                              @Param("date") LocalDate date);
    List<DailyCheckin> findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(Long userId, LocalDate from, LocalDate to);
}
