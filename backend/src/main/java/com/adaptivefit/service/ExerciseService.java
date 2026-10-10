package com.adaptivefit.service;

import com.adaptivefit.dto.ExerciseRequest;
import com.adaptivefit.dto.ExerciseResponse;
import com.adaptivefit.dto.PageResponse;
import com.adaptivefit.entity.Exercise;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.ExerciseRepository;
import com.adaptivefit.util.TextLists;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<ExerciseResponse> search(String q, String tag, String location, String intensity,
                                                 Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("name").ascending());
        Page<Exercise> result = repository.findAll(filter(q, tag, location, intensity, active), pageable);
        return PageResponse.of(result.map(ExerciseResponse::from));
    }

    /** Dung cho USER: chi tra bai tap dang bat. */
    @Transactional(readOnly = true)
    public ExerciseResponse getActive(Long id) {
        Exercise e = find(id);
        if (!Boolean.TRUE.equals(e.getActive())) {
            throw new NotFoundException("Không tìm thấy bài tập");
        }
        return ExerciseResponse.from(e);
    }

    @Transactional(readOnly = true)
    public ExerciseResponse get(Long id) {
        return ExerciseResponse.from(find(id));
    }

    @Transactional
    public ExerciseResponse create(ExerciseRequest req) {
        Exercise e = new Exercise();
        apply(e, req);
        return ExerciseResponse.from(repository.save(e));
    }

    @Transactional
    public ExerciseResponse update(Long id, ExerciseRequest req) {
        Exercise e = find(id);
        apply(e, req);
        return ExerciseResponse.from(repository.save(e));
    }

    /** Xoa mem: giu lai de lich su workout van tham chieu duoc. */
    @Transactional
    public void deactivate(Long id) {
        Exercise e = find(id);
        e.setActive(false);
        repository.save(e);
    }

    private Exercise find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy bài tập"));
    }

    private void apply(Exercise e, ExerciseRequest r) {
        if (r.durationMax() < r.durationMin()) {
            throw new BadRequestException("durationMax phải lớn hơn hoặc bằng durationMin");
        }
        e.setName(r.name().trim());
        e.setDescription(r.description());
        e.setDurationMin(r.durationMin());
        e.setDurationMax(r.durationMax());
        e.setDifficulty(r.difficulty());
        e.setIntensity(r.intensity());
        e.setEquipment(r.equipment());
        e.setLocation(r.location());
        e.setMuscleGroup(r.muscleGroup());
        e.setGoal(r.goal());
        e.setEnergyRequirement(r.energyRequirement());
        e.setRecoverySuitable(r.recoverySuitable());
        e.setInstructions(r.instructions());
        e.setMet(r.met() == null ? null : r.met().doubleValue());
        String steps = TextLists.join(r.steps());
        if (steps != null && steps.length() > 4000) {
            throw new BadRequestException("Tổng độ dài các bước không được quá 4000 ký tự");
        }
        e.setSteps(steps);
        String media = TextLists.join(r.media());
        if (media != null && media.length() > 1000) {
            throw new BadRequestException("Tổng độ dài các đường dẫn ảnh không được quá 1000 ký tự");
        }
        e.setMediaUrls(media);
        e.setTips(r.tips());
        if (r.active() != null) {
            e.setActive(r.active());
        }
        Set<String> tags = new HashSet<>();
        if (r.tags() != null) {
            r.tags().forEach(t -> tags.add(t.trim().toLowerCase()));
        }
        e.getTags().clear();
        e.getTags().addAll(tags);
    }

    private Specification<Exercise> filter(String q, String tag, String location, String intensity, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                ps.add(cb.like(cb.lower(root.get("name")), like, '\\'));
            }
            if (tag != null && !tag.isBlank()) {
                query.distinct(true);
                Join<Exercise, String> tags = root.join("tags");
                ps.add(cb.equal(tags, tag.trim().toLowerCase()));
            }
            if (location != null && !location.isBlank()) {
                ps.add(cb.equal(root.get("location"), location.trim().toUpperCase()));
            }
            if (intensity != null && !intensity.isBlank()) {
                ps.add(cb.equal(root.get("intensity"), intensity.trim().toUpperCase()));
            }
            if (active != null) {
                ps.add(cb.equal(root.get("active"), active));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
