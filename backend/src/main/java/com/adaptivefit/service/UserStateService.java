package com.adaptivefit.service;

import com.adaptivefit.engine.HistoryRisk;
import com.adaptivefit.engine.UserState;
import com.adaptivefit.entity.DailyCheckin;
import com.adaptivefit.entity.Feedback;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.WorkoutSession;
import com.adaptivefit.entity.WorkoutStatus;
import com.adaptivefit.observability.PerformanceTiming;
import com.adaptivefit.repository.DailyCheckinRepository;
import com.adaptivefit.repository.FeedbackRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dung trang thai nguoi dung va 10 feature cho model tu du lieu that. Mot ngay "co tap" la ngay check-in ghi da tap
 * HOAC co buoi tap hoan thanh. missed_days = so ngay lien tiep khong tap tinh den hom nay (gom hom nay).
 * Thoi luong tap: lay tu check-in; ngay chi co buoi tap hoan thanh thi dung thoi gian thuc te (bat dau -> ket thuc).
 * Do kho: uu tien feedback buoi tap hoan thanh; dung check-in neu ngay do khong co feedback buoi tap. Khong co du lieu thi 0.
 */
@Service
@RequiredArgsConstructor
public class UserStateService {

    private static final int LOOKBACK_DAYS = 60;

    /** state: dung cho Intervention Engine; features: dung de goi model (ten khop schema cua ai-service). */
    public record Snapshot(UserState state, Map<String, Double> features) {}

    private final DailyCheckinRepository checkinRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;

    public UserState build(User user, DailyCheckin today) {
        return snapshot(user, today).state();
    }

    public Snapshot snapshot(User user, DailyCheckin today) {
        return PerformanceTiming.measure("user_state.snapshot",
                () -> buildSnapshot(user, today));
    }

    public UserState profileFallback(User user, int availableMinutes) {
        LocalDate date = LocalDate.now();
        LocalDate earliest = user.getCreatedAt().toLocalDate();
        LocalDate lookbackStart = date.minusDays(LOOKBACK_DAYS - 1L);
        LocalDate windowStart = earliest.isAfter(lookbackStart) ? earliest : lookbackStart;
        List<DailyCheckin> checkins = checkinRepository.findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(
                user.getId(), windowStart, date);
        List<WorkoutSession> sessions = sessionRepository.findByUserIdAndStatusAndFinishedAtGreaterThanEqual(
                user.getId(), WorkoutStatus.COMPLETED, windowStart.atStartOfDay());

        Set<LocalDate> exercised = new HashSet<>();
        checkins.stream().filter(c -> Boolean.TRUE.equals(c.getExercisedToday()))
                .map(DailyCheckin::getCheckinDate).forEach(exercised::add);
        sessions.stream().map(s -> s.getFinishedAt().toLocalDate()).forEach(exercised::add);

        int missed = 0;
        LocalDate day = date;
        while (!exercised.contains(day) && !day.isBefore(windowStart)) {
            missed++;
            day = day.minusDays(1);
        }
        int frequency = (int) exercised.stream()
                .filter(d -> !d.isBefore(date.minusDays(6)) && !d.isAfter(date)).count();
        int observedDays = (int) Math.min(7, ChronoUnit.DAYS.between(earliest, date) + 1);
        Map<LocalDate, Integer> recentDurationByDay = new HashMap<>();
        Set<LocalDate> checkinDurationDays = new HashSet<>();
        for (DailyCheckin checkin : checkins) {
            if (Boolean.TRUE.equals(checkin.getExercisedToday()) && checkin.getDurationMin() != null
                    && !checkin.getCheckinDate().isBefore(date.minusDays(6))) {
                recentDurationByDay.put(checkin.getCheckinDate(), checkin.getDurationMin());
                checkinDurationDays.add(checkin.getCheckinDate());
            }
        }
        for (WorkoutSession session : sessions) {
            LocalDate workoutDate = session.getFinishedAt().toLocalDate();
            if (workoutDate.isBefore(date.minusDays(6)) || checkinDurationDays.contains(workoutDate)) continue;
            int minutes = (int) Math.max(1, Math.min(300,
                    Duration.between(session.getStartedAt(), session.getFinishedAt()).toMinutes()));
            recentDurationByDay.merge(workoutDate, minutes, (total, added) -> Math.min(300, total + added));
        }
        int historyBudget = recentDurationByDay.isEmpty() ? availableMinutes
                : Math.max(5, (int) Math.round(recentDurationByDay.values().stream()
                .mapToInt(Integer::intValue).average().orElse(availableMinutes)));
        int freeTime = Math.min(availableMinutes, historyBudget);
        return new UserState(7.0, 3, 3, 3, 3, freeTime, exercised.contains(date),
                missed, frequency, HistoryRisk.level(missed, frequency, observedDays),
                UserState.RISK_SOURCE_HISTORY);
    }

    private Snapshot buildSnapshot(User user, DailyCheckin today) {
        PerformanceTiming.setUserId(user.getId());
        LocalDate date = today.getCheckinDate();
        LocalDate earliest = user.getCreatedAt().toLocalDate();
        LocalDate lookbackStart = date.minusDays(LOOKBACK_DAYS - 1L);
        LocalDate windowStart = earliest.isAfter(lookbackStart) ? earliest : lookbackStart;

        List<DailyCheckin> checkins = PerformanceTiming.measure("user_state.checkin_history_query",
                () -> checkinRepository.findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(
                        user.getId(), windowStart, date));
        List<WorkoutSession> sessions = PerformanceTiming.measure("user_state.workout_history_query",
                () -> sessionRepository.findByUserIdAndStatusAndFinishedAtGreaterThanEqual(
                        user.getId(), WorkoutStatus.COMPLETED, windowStart.atStartOfDay()));
        List<Feedback> feedbacks = PerformanceTiming.measure("user_state.feedback_history_query",
                () -> feedbackRepository.findWorkoutFeedbackSince(
                        user.getId(), WorkoutStatus.COMPLETED, windowStart.atStartOfDay()));

        Set<LocalDate> exercised = new HashSet<>();
        for (DailyCheckin c : checkins) {
            if (Boolean.TRUE.equals(c.getExercisedToday())) exercised.add(c.getCheckinDate());
        }
        for (WorkoutSession s : sessions) exercised.add(s.getFinishedAt().toLocalDate());
        if (Boolean.TRUE.equals(today.getExercisedToday())) exercised.add(date);

        int missed = 0;
        LocalDate d = date;
        while (!exercised.contains(d) && !d.isBefore(windowStart)) {
            missed++;
            d = d.minusDays(1);
        }
        int freq7 = 0;
        for (int i = 0; i < 7; i++) {
            if (exercised.contains(date.minusDays(i))) freq7++;
        }
        int observedDays = (int) Math.min(7, ChronoUnit.DAYS.between(earliest, date) + 1);

        // Thoi luong va do kho trung binh trong 7 ngay gan nhat
        LocalDate weekStart = date.minusDays(6);
        Map<LocalDate, Integer> durationByDay = new HashMap<>();
        Map<LocalDate, List<Integer>> feedbackDifficultyByDay = new HashMap<>();
        for (Feedback feedback : feedbacks) {
            LocalDate day = feedback.getSession().getFinishedAt().toLocalDate();
            Integer difficulty = feedback.getDifficulty();
            if (!day.isBefore(weekStart) && difficulty != null) {
                feedbackDifficultyByDay.computeIfAbsent(day, ignored -> new java.util.ArrayList<>()).add(difficulty);
            }
        }
        Map<LocalDate, List<Integer>> difficultyByDay = new HashMap<>(feedbackDifficultyByDay);
        for (DailyCheckin c : checkins) {
            if (!Boolean.TRUE.equals(c.getExercisedToday()) || c.getCheckinDate().isBefore(weekStart)) continue;
            if (c.getDurationMin() != null) durationByDay.put(c.getCheckinDate(), c.getDurationMin());
            if (c.getDifficulty() != null && !feedbackDifficultyByDay.containsKey(c.getCheckinDate())) {
                difficultyByDay.computeIfAbsent(c.getCheckinDate(), ignored -> new java.util.ArrayList<>())
                        .add(c.getDifficulty());
            }
        }
        for (WorkoutSession s : sessions) {
            LocalDate day = s.getFinishedAt().toLocalDate();
            if (day.isBefore(weekStart) || durationByDay.containsKey(day)) continue;
            long minutes = Math.max(1, Math.min(300, Duration.between(s.getStartedAt(), s.getFinishedAt()).toMinutes()));
            durationByDay.merge(day, (int) minutes, Integer::sum);
        }
        double avgDuration = durationByDay.isEmpty() ? 0.0
                : durationByDay.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double avgDifficulty = difficultyByDay.values().stream()
                .mapToDouble(scores -> scores.stream().mapToInt(Integer::intValue).average().orElse(0.0))
                .average().orElse(0.0);

        UserState state = new UserState(today.getSleepHours(), today.getStress(), today.getEnergy(), today.getMood(),
                today.getMotivation(), today.getFreeTimeMin(), exercised.contains(date),
                missed, freq7, HistoryRisk.level(missed, freq7, observedDays), UserState.RISK_SOURCE_HISTORY);

        Map<String, Double> features = new LinkedHashMap<>();
        features.put("sleep_hours", today.getSleepHours());
        features.put("stress_level", (double) today.getStress());
        features.put("energy_level", (double) today.getEnergy());
        features.put("mood", (double) today.getMood());
        features.put("motivation", (double) today.getMotivation());
        features.put("free_time_min", (double) today.getFreeTimeMin());
        features.put("missed_days", (double) Math.min(missed, 60));
        features.put("workout_frequency_7d", (double) freq7);
        features.put("avg_duration_7d", Math.min(300.0, avgDuration));
        features.put("avg_difficulty_7d", Math.min(5.0, avgDifficulty));
        return new Snapshot(state, features);
    }
}
