package com.adaptivefit.repository;

import com.adaptivefit.entity.Intervention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterventionRepository extends JpaRepository<Intervention, Long> {
    List<Intervention> findAllByOrderByPriorityAsc();
}
