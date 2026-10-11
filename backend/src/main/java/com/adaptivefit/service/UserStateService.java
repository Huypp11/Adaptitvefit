package com.adaptivefit.service;

import com.adaptivefit.engine.HistoryRisk;
import com.adaptivefit.engine.UserState;
import com.adaptivefit.entity.DailyCheckin;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.WorkoutSession;
import com.adaptivefit.entity.WorkoutStatus;
import com.adaptivefit.repository.DailyCheckinRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

/**
 * Dung UserState tu du lieu that. Mot ngay "co tap" la ngay check-in ghi da tap HOAC co buoi tap hoan thanh.
 * missed_days = so ngay lien tiep khong tap tinh den hom nay (gom hom nay), cung dinh nghia voi feature cua model.
 */
@Service
@RequiredArgsConstructor
public class UserStateService {

    private static final int LOOKBACK_DAYS = 60;

    private final DailyCheckinRepository checkinRepository;
    private final WorkoutSessionRepository sessionRepository;

    public UserState build(User user, DailyCheckin today) {
        LocalDate date = today.getCheckinDate();
        LocalDate earliest = user.getCreatedAt().toLocalDate();
        LocalDate windowStart = earliest.isAfter(date.minusDays(LOOKBACK_DAYS - 1)) ? earliest : date.minusDays(LOOKBACK_DAYS - 1);

        Set<LocalDate> exercised = new HashSet<>();
        for (DailyCheckin c : checkinRepository.findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(user.getId(), windowStart, date)) {
            if (Boolean.TRUE.equals(c.getExercisedToday())) exercised.add(c.getCheckinDate());
        }
        for (WorkoutSession s : sessionRepository.findByUserIdAndStatusAndFinishedAtGreaterThanEqual(
                user.getId(), WorkoutStatus.COMPLETED, windowStart.atStartOfDay())) {
            exercised.add(s.getFinishedAt().toLocalDate());
        }
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

        return new UserState(today.getSleepHours(), today.getStress(), today.getEnergy(), today.getMood(),
                today.getMotivation(), today.getFreeTimeMin(), Boolean.TRUE.equals(today.getExercisedToday()),
                missed, freq7, HistoryRisk.level(missed, freq7, observedDays), UserState.RISK_SOURCE_HISTORY);
    }
}
