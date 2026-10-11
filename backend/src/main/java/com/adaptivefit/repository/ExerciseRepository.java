package com.adaptivefit.repository;

import com.adaptivefit.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long>, JpaSpecificationExecutor<Exercise> {
    List<Exercise> findByIdInAndActiveTrue(Collection<Long> ids);

    List<Exercise> findByActiveTrue();
}
