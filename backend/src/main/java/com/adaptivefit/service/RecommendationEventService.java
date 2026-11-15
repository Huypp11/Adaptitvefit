package com.adaptivefit.service;

import com.adaptivefit.dto.RecommendationAnalyticsResponse;
import com.adaptivefit.dto.RecommendationDataConsentRequest;
import com.adaptivefit.dto.RecommendationDataConsentResponse;
import com.adaptivefit.dto.RecommendationEventRequest;
import com.adaptivefit.entity.*;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.RecommendationDataConsentRepository;
import com.adaptivefit.repository.RecommendationEventRepository;
import com.adaptivefit.repository.RecommendationHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecommendationEventService {

    public static final String POLICY_VERSION = "recommendation-events-v1";
    private static final int WINDOW_DAYS = 90;
    private static final Duration OUTCOME_WINDOW = Duration.ofHours(24);

    private final CurrentUserService currentUser;
    private final RecommendationDataConsentRepository consentRepository;
    private final RecommendationEventRepository eventRepository;
    private final RecommendationHistoryRepository recommendationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RecommendationDataConsentResponse consent() {
        User user = currentUser.get();
        RecommendationDataConsent latest = consentRepository
                .findFirstByUser_IdOrderByRecordedAtDescIdDesc(user.getId()).orElse(null);
        return new RecommendationDataConsentResponse(latest != null && latest.isGranted(),
                POLICY_VERSION, latest == null ? null : latest.getRecordedAt(),
                eventRepository.countByUser_Id(user.getId()));
    }

    @Transactional
    public RecommendationDataConsentResponse updateConsent(RecommendationDataConsentRequest request) {
        if (!POLICY_VERSION.equals(request.policyVersion())) {
            throw new BadRequestException("Phiên bản nội dung đồng ý đã thay đổi; hãy tải lại trang và xem lại thông tin");
        }
        User user = currentUser.get();
        RecommendationDataConsent current = consentRepository
                .findFirstByUser_IdOrderByRecordedAtDescIdDesc(user.getId()).orElse(null);
        boolean granted = request.granted();
        if (current == null || current.isGranted() != granted || !POLICY_VERSION.equals(current.getPolicyVersion())) {
            if (!granted) eventRepository.deleteByUser_Id(user.getId());
            RecommendationDataConsent record = new RecommendationDataConsent();
            record.setUser(user);
            record.setPolicyVersion(POLICY_VERSION);
            record.setGranted(granted);
            record.setRecordedAt(LocalDateTime.now());
            consentRepository.save(record);
        }
        return consent();
    }

    @Transactional
    public boolean recordClientEvent(RecommendationEventRequest request) {
        if (request.eventType() != RecommendationEventRequest.EventType.DISPLAYED
                && request.eventType() != RecommendationEventRequest.EventType.SKIPPED) {
            throw new BadRequestException("Chỉ có thể gửi sự kiện hiển thị hoặc chủ động bỏ qua");
        }
        User user = currentUser.get();
        if (!hasConsent(user.getId())) return false;
        RecommendationHistory recommendation = ownedRecommendation(user, request.recommendationId(),
                request.recommendationMode());
        if (request.recommendationMode().equals("PROFILE_FALLBACK") && request.recommendationId() != null) {
            throw new BadRequestException("Gợi ý dự phòng không được liên kết với recommendation đã lưu");
        }
        if (recommendation != null && !request.trackingId().equals("recommendation:" + recommendation.getId())) {
            throw new BadRequestException("trackingId không khớp với recommendation");
        }
        if (recommendation == null) {
            try {
                UUID.fromString(request.trackingId());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("trackingId của gợi ý dự phòng không hợp lệ");
            }
        }
        append(user, recommendation, null, request.trackingId(), request.eventType().name(),
                request.recommendationMode(), request.idempotencyKey(), null);
        return true;
    }

    @Transactional
    public void recordWorkoutStarted(User user, WorkoutSession session, String trackingId,
                                     boolean selectionChanged) {
        if (trackingId == null || trackingId.isBlank() || !hasConsent(user.getId())) return;
        String mode = session.getRecommendation() == null ? "PROFILE_FALLBACK" : "CHECKIN";
        append(user, session.getRecommendation(), session, trackingId, "ACCEPTED", mode,
                "accepted:session:" + session.getId(), null);
        if (selectionChanged) {
            append(user, session.getRecommendation(), session, trackingId, "SELECTION_CHANGED", mode,
                    "selection-changed:session:" + session.getId(),
                    session.getExercises().stream().map(item -> item.getExercise().getId()).toList());
        }
        append(user, session.getRecommendation(), session, trackingId, "WORKOUT_STARTED", mode,
                "workout-started:session:" + session.getId(), null);
    }

    @Transactional
    public void recordWorkoutFinished(WorkoutSession session) {
        User user = session.getUser();
        RecommendationEvent started = eventRepository.findByUser_IdAndWorkoutSession_IdAndEventType(
                user.getId(), session.getId(), "WORKOUT_STARTED").orElse(null);
        if (started == null || !hasConsent(user.getId())) return;
        String type = switch (session.getStatus()) {
            case COMPLETED -> "WORKOUT_COMPLETED";
            case ABANDONED -> "WORKOUT_ABANDONED";
            case INVALID_WORKOUT -> "INVALID_WORKOUT";
            case STARTED -> null;
        };
        if (type != null) {
            append(user, started.getRecommendation(), session, started.getTrackingId(), type,
                    started.getRecommendationMode(), type.toLowerCase(Locale.ROOT) + ":session:" + session.getId(), null);
        }
    }

    @Transactional
    public void recordFeedbackSubmitted(User user, WorkoutSession session, Long recommendationId) {
        if (!hasConsent(user.getId())) return;
        RecommendationEvent started = session == null ? null
                : eventRepository.findByUser_IdAndWorkoutSession_IdAndEventType(
                        user.getId(), session.getId(), "WORKOUT_STARTED").orElse(null);
        RecommendationHistory recommendation = recommendationId == null ? null
                : ownedRecommendation(user, recommendationId, "CHECKIN");
        String trackingId = recommendation != null ? "recommendation:" + recommendation.getId()
                : started == null ? null : started.getTrackingId();
        if (trackingId == null) return;
        String mode = recommendation != null ? "CHECKIN" : started.getRecommendationMode();
        append(user, recommendation != null ? recommendation : started.getRecommendation(), session,
                trackingId, "FEEDBACK_SUBMITTED", mode,
                "feedback-submitted:" + trackingId, null);
    }

    @Transactional(readOnly = true)
    public RecommendationAnalyticsResponse analytics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(WINDOW_DAYS);
        List<RecommendationEvent> events = eventRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(since);
        Map<String, List<RecommendationEvent>> journeys = new LinkedHashMap<>();
        Set<String> startedSessions = new HashSet<>();
        for (RecommendationEvent event : events) {
            journeys.computeIfAbsent(journeyKey(event), ignored -> new ArrayList<>()).add(event);
            if ("WORKOUT_STARTED".equals(event.getEventType())) {
                startedSessions.add(sessionKey(event));
            }
        }

        long displayedCheckin = 0, displayedFallback = 0, mature = 0, validCompleted = 0;
        long matureCheckin = 0, completedCheckin = 0, matureFallback = 0, completedFallback = 0;
        long skipped = 0, started = 0, abandoned = 0, invalid = 0, noFeedback = 0, immature = 0;
        long lifecycleWithoutStart = events.stream()
                .filter(e -> Set.of("WORKOUT_COMPLETED", "WORKOUT_ABANDONED", "INVALID_WORKOUT")
                        .contains(e.getEventType()))
                .filter(e -> e.getWorkoutSession() != null && !startedSessions.contains(sessionKey(e)))
                .count();
        long journeysWithoutCompletion = 0;
        Set<String> countedImpressions = new HashSet<>();
        for (List<RecommendationEvent> journey : journeys.values()) {
            List<RecommendationEvent> displays = journey.stream()
                    .filter(e -> "DISPLAYED".equals(e.getEventType())).toList();
            if (displays.isEmpty()) continue;
            RecommendationEvent impression = displays.get(0);
            String impressionKey = journeyKey(impression);
            if (!countedImpressions.add(impressionKey)) continue;
            if ("CHECKIN".equals(impression.getRecommendationMode())) displayedCheckin++;
            else if ("PROFILE_FALLBACK".equals(impression.getRecommendationMode())) displayedFallback++;
            LocalDateTime cutoff = impression.getOccurredAt().plus(OUTCOME_WINDOW);
            if (cutoff.isAfter(now)) {
                immature++;
                continue;
            }
            mature++;
            boolean checkinJourney = "CHECKIN".equals(impression.getRecommendationMode());
            if (checkinJourney) matureCheckin++;
            else if ("PROFILE_FALLBACK".equals(impression.getRecommendationMode())) matureFallback++;
            boolean didStart = hasEventBetween(journey, "WORKOUT_STARTED", impression.getOccurredAt(), cutoff);
            boolean didComplete = hasEventBetween(journey, "WORKOUT_COMPLETED", impression.getOccurredAt(), cutoff);
            boolean didAbandon = hasEventBetween(journey, "WORKOUT_ABANDONED", impression.getOccurredAt(), cutoff);
            boolean didInvalidate = hasEventBetween(journey, "INVALID_WORKOUT", impression.getOccurredAt(), cutoff);
            boolean didFeedback = hasEventBetween(journey, "FEEDBACK_SUBMITTED", impression.getOccurredAt(), cutoff);
            if (didStart) started++;
            if (didComplete) {
                validCompleted++;
                if (checkinJourney) completedCheckin++;
                else if ("PROFILE_FALLBACK".equals(impression.getRecommendationMode())) completedFallback++;
            }
            if (didAbandon) abandoned++;
            if (didInvalidate) invalid++;
            if (!didFeedback) noFeedback++;
            if (didStart && !didComplete && !didAbandon && !didInvalidate) {
                journeysWithoutCompletion++;
            }
            if (hasEventBetween(journey, "SKIPPED", impression.getOccurredAt(), cutoff)) skipped++;
        }
        long usersWithConsent = consentRepository.countUsersWithCurrentConsent();
        return new RecommendationAnalyticsResponse(WINDOW_DAYS, usersWithConsent, displayedCheckin,
                displayedFallback, mature, validCompleted,
                mature == 0 ? null : (double) validCompleted / mature,
                matureCheckin, completedCheckin,
                matureCheckin == 0 ? null : (double) completedCheckin / matureCheckin,
                matureFallback, completedFallback,
                matureFallback == 0 ? null : (double) completedFallback / matureFallback,
                skipped, started,
                abandoned, invalid, noFeedback, immature,
                lifecycleWithoutStart, journeysWithoutCompletion,
                "Outcome chính: recommendation đã hiển thị và có workout COMPLETED trong 24 giờ. "
                        + "Chỉ gồm consent hiện còn hiệu lực; cửa sổ tối đa 90 ngày. "
                        + "Không có feedback không đồng nghĩa với thất bại. "
                        + "Gợi ý CHECKIN và PROFILE_FALLBACK được tách riêng. "
                        + "Yêu cầu trùng được INSERT IGNORE chặn theo idempotency key.");
    }

    private boolean hasEventBetween(List<RecommendationEvent> events, String type,
                                    LocalDateTime start, LocalDateTime end) {
        return events.stream().anyMatch(e -> type.equals(e.getEventType())
                && !e.getOccurredAt().isBefore(start) && !e.getOccurredAt().isAfter(end));
    }

    private String journeyKey(RecommendationEvent event) {
        return event.getUser().getId() + ":" + event.getTrackingId();
    }

    private String sessionKey(RecommendationEvent event) {
        return event.getWorkoutSession() == null ? "" : event.getWorkoutSession().getId().toString();
    }

    private boolean hasConsent(Long userId) {
        return consentRepository.findFirstByUser_IdOrderByRecordedAtDescIdDesc(userId)
                .map(RecommendationDataConsent::isGranted).orElse(false);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredEvents() {
        eventRepository.deleteByOccurredAtBefore(LocalDateTime.now().minusDays(WINDOW_DAYS));
    }

    private RecommendationHistory ownedRecommendation(User user, Long recommendationId, String mode) {
        if (recommendationId == null) {
            if (!mode.equals("PROFILE_FALLBACK")) {
                throw new BadRequestException("Gợi ý check-in cần recommendationId");
            }
            return null;
        }
        if (!mode.equals("CHECKIN")) throw new BadRequestException("Loại gợi ý không khớp recommendationId");
        return recommendationRepository.findByIdAndUserId(recommendationId, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy gợi ý"));
    }

    private void append(User user, RecommendationHistory recommendation, WorkoutSession session, String trackingId,
                       String type, String mode, String idempotencyKey, Object payload) {
        String json = null;
        if (payload != null) {
            try {
                json = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Không tuần tự hóa được payload sự kiện", e);
            }
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO recommendation_events
                  (user_id, recommendation_id, workout_session_id, tracking_id, event_type,
                   recommendation_mode, idempotency_key, payload_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, user.getId(), recommendation == null ? null : recommendation.getId(),
                session == null ? null : session.getId(), trackingId, type, mode,
                idempotencyKey, json, LocalDateTime.now());
    }
}
