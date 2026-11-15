package com.adaptivefit.service;

import com.adaptivefit.dto.PageResponse;
import com.adaptivefit.dto.WorkoutResponse;
import com.adaptivefit.dto.WorkoutStartRequest;
import com.adaptivefit.dto.WorkoutSummary;
import com.adaptivefit.entity.Feedback;
import com.adaptivefit.entity.*;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.ConflictException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.ExerciseRepository;
import com.adaptivefit.repository.FeedbackRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
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
    private final FeedbackRepository feedbackRepository;
    private final RecommendationEventService recommendationEventService;

    @Transactional
    public WorkoutResponse start(WorkoutStartRequest req) {
        User user = currentUser.get();
        List<Long> ids = req.exerciseIds().stream().distinct().toList();
        List<Integer> minutes = req.plannedMinutes();
        if (minutes != null && (ids.size() != req.exerciseIds().size() || minutes.size() != ids.size())) {
            throw new BadRequestException("plannedMinutes phải cùng số lượng và thứ tự với exerciseIds (không trùng bài)");
        }

        if (req.recommendationId() != null) {
            WorkoutSession existing = sessionRepository
                    .findByRecommendation_IdAndUser_Id(req.recommendationId(), user.getId()).orElse(null);
            if (existing != null) {
                if (existing.getStatus() == WorkoutStatus.STARTED && sameSelection(existing, ids, minutes)) {
                    return toResponse(existing);
                }
                throw new ConflictException("Gợi ý này đã được dùng để bắt đầu một buổi tập");
            }
        }
        if (sessionRepository.existsByUserIdAndStatus(user.getId(), WorkoutStatus.STARTED)) {
            throw new ConflictException("Bạn đang có một buổi tập chưa kết thúc. Hãy hoàn thành hoặc huỷ nó trước");
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
        RecommendationService.StrategyContext strategyContext = null;
        if (req.recommendationId() != null || Boolean.TRUE.equals(req.followRecommendation())) {
            strategyContext = req.recommendationId() == null
                    ? recommendationService.strategyContextFor(user)
                    : recommendationService.strategyContextFor(user, req.recommendationId(), ids, minutes);
            if (strategyContext != null) {
                session.setInterventionCode(strategyContext.code());
                session.setPredictionId(strategyContext.predictionId());
                session.setRecommendation(strategyContext.recommendation());
            }
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
        WorkoutSession saved = sessionRepository.save(session);
        String recommendationTrackingId = saved.getRecommendation() == null ? null
                : "recommendation:" + saved.getRecommendation().getId();
        if (req.trackingId() != null && !req.trackingId().equals(recommendationTrackingId)) {
            if (recommendationTrackingId != null || !isUuid(req.trackingId())) {
                throw new BadRequestException("trackingId không khớp với gợi ý đang được sử dụng");
            }
        }
        String trackingId = recommendationTrackingId == null ? req.trackingId() : recommendationTrackingId;
        recommendationEventService.recordWorkoutStarted(user, saved, trackingId,
                strategyContext != null && strategyContext.selectionChanged());
        return toResponse(saved);
    }

    private boolean isUuid(String value) {
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean sameSelection(WorkoutSession existing, List<Long> ids, List<Integer> minutes) {
        List<Long> existingIds = existing.getExercises().stream().map(item -> item.getExercise().getId()).toList();
        List<Integer> existingMinutes = existing.getExercises().stream().map(WorkoutSessionExercise::getPlannedMin).toList();
        List<Integer> requestedMinutes = minutes == null
                ? existing.getExercises().stream().map(item -> item.getExercise().getDurationMin()).toList()
                : minutes;
        return ids.equals(existingIds) && requestedMinutes.equals(existingMinutes);
    }

    @Transactional
    public WorkoutResponse complete(Long id) {
        return finish(id, null, null);
    }

    @Transactional
    public WorkoutResponse abandon(Long id, String reason) {
        return finish(id, WorkoutStatus.ABANDONED, reason);
    }

    @Transactional(readOnly = true)
    public WorkoutResponse get(Long id) {
        return toResponse(findOwned(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkoutSummary> history(int page, int size) {
        User user = currentUser.get();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("startedAt").descending());
        Page<WorkoutSession> result = sessionRepository.findByUserId(user.getId(), pageable);
        return PageResponse.of(result.map(WorkoutSummary::from));
    }

    private WorkoutResponse finish(Long id, WorkoutStatus target, String reason) {
        WorkoutSession s = findOwned(id);
        if (s.getStatus() != WorkoutStatus.STARTED) {
            throw new ConflictException("Buổi tập này đã kết thúc");
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        if (target == null) {
            long actualSeconds = Duration.between(s.getStartedAt(), finishedAt).getSeconds();
            long plannedSeconds = s.getExercises().stream()
                    .mapToLong(item -> item.getPlannedMin() * 60L).sum();
            target = actualSeconds < 180 || actualSeconds * 100 < plannedSeconds * 30
                    ? WorkoutStatus.INVALID_WORKOUT : WorkoutStatus.COMPLETED;
        }
        s.setStatus(target);
        s.setFinishedAt(finishedAt);
        WorkoutSession saved = sessionRepository.save(s);
        recommendationEventService.recordWorkoutFinished(saved);
        if (target == WorkoutStatus.ABANDONED && reason != null && !feedbackRepository.existsBySessionId(s.getId())) {
            Feedback feedback = new Feedback();
            feedback.setSession(saved);
            feedback.setCompleted(false);
            feedback.setNotCompletedReason(reason);
            feedbackRepository.save(feedback);
        }
        return toResponse(saved);
    }

    private WorkoutResponse toResponse(WorkoutSession session) {
        String reason = session.getId() == null ? null : feedbackRepository.findBySession_Id(session.getId())
                .map(Feedback::getNotCompletedReason).orElse(null);
        return WorkoutResponse.from(session, reason);
    }

    /** Tra 404 ca khi buoi tap khong ton tai lan khi thuoc ve nguoi khac (khong lo su ton tai). */
    private WorkoutSession findOwned(Long id) {
        User user = currentUser.get();
        return sessionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy buổi tập"));
    }
}
