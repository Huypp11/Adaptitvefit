package com.adaptivefit.repository;

import com.adaptivefit.entity.RiskPrediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RiskPredictionRepository extends JpaRepository<RiskPrediction, Long> {
    Optional<RiskPrediction> findByCheckinId(Long checkinId);
    List<RiskPrediction> findByCheckinIdIn(Collection<Long> checkinIds);
}
