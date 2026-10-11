package com.adaptivefit.service;

import com.adaptivefit.dto.PageResponse;
import com.adaptivefit.dto.WorkoutResponse;
import com.adaptivefit.dto.WorkoutStartRequest;
import com.adaptivefit.dto.WorkoutSummary;
import com.adaptivefit.entity.*;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.ConflictException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.ExerciseRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutSessionRepository sessionRepository;
    private final ExerciseRepository exerciseRepository;
    private final CurrentUserService currentUser;
    private final RecommendationService recommendationService;

    @Transactional
    public WorkoutResponse start(WorkoutStartRequest req) {
        User user = currentUser.get();
        if (sessionRepository.existsByUserIdAndStatus(user.getId(), WorkoutStatus.STARTED)) {
            throw new ConflictException("Bạn đang có một buổi tập chưa kết thúc. Hãy hoàn thành hoặc huỷ nó trước");
        }

        List<Long> ids = req.exerciseIds().stream().distinct().toList();
        List<Integer> minutes = req.plannedMinutes();
        if (minutes != null && (ids.size() != req.exerciseIds().size() || minutes.size() != ids.size())) {
            throw new BadRequestException("plannedMinutes phải cùng số lượng và thứ tự với exerciseIds (không trùng bài)");
        }
        Map<Long, Exercise> byId = exerciseRepository.findByIdInAndActiveTrue(ids).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
        if (byId.size() != ids.size()) {
            throw new NotFoundException("Một hoặc nhiều bài tập không tồn tại hoặc đã bị tắt");
        }

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setStatus(WorkoutStatus.STARTED);
        session.setStartedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(req.followRecommendation())) {
            session.setInterventionCode(recommendationService.strategyCodeFor(user));
        }

        int order = 1;
        for (int i = 0; i < ids.size(); i++) {
            Exercise e = byId.get(ids.get(i));
            int planned = minutes == null ? e.getDurationMin() : minutes.get(i);
            if (planned < e.getDurationMin() || planned > e.getDurationMax()) {
                throw new BadRequestException("Thời lượng bài \"" + e.getName() + "\" phải từ "
                        + e.getDurationMin() + " đến " + e.getDurationMax() + " phút");
            }
            WorkoutSessionExercise item = new WorkoutSessionExercise();
            item.setSession(session);
            item.setExercise(e);
            item.setOrderNo(order++);
            item.setPlannedMin(planned);
            session.getExercises().add(item);
        }
        return WorkoutResponse.from(sessionRepository.save(session));
    }

    @Transactional
    public WorkoutResponse complete(Long id) {
        return finish(id, WorkoutStatus.COMPLETED);
    }

    @Transactional
    public WorkoutResponse abandon(Long id) {
        return finish(id, WorkoutStatus.ABANDONED);
    }

    @Transactional(readOnly = true)
    public WorkoutResponse get(Long id) {
        return WorkoutResponse.from(findOwned(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkoutSummary> history(int page, int size) {
        User user = currentUser.get();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("startedAt").descending());
        Page<WorkoutSession> result = sessionRepository.findByUserId(user.getId(), pageable);
        return PageResponse.of(result.map(WorkoutSummary::from));
    }

    private WorkoutResponse finish(Long id, WorkoutStatus target) {
        WorkoutSession s = findOwned(id);
        if (s.getStatus() != WorkoutStatus.STARTED) {
            throw new ConflictException("Buổi tập này đã kết thúc");
        }
        s.setStatus(target);
        s.setFinishedAt(LocalDateTime.now());
        return WorkoutResponse.from(sessionRepository.save(s));
    }

    /** Tra 404 ca khi buoi tap khong ton tai lan khi thuoc ve nguoi khac (khong lo su ton tai). */
    private WorkoutSession findOwned(Long id) {
        User user = currentUser.get();
        return sessionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy buổi tập"));
    }
}
