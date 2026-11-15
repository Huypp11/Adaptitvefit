package com.adaptivefit.service;

import com.adaptivefit.entity.User;
import com.adaptivefit.entity.WorkoutSession;
import com.adaptivefit.entity.WorkoutStatus;
import com.adaptivefit.repository.DailyCheckinRepository;
import com.adaptivefit.repository.FeedbackRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStateServiceTest {

    @Mock private DailyCheckinRepository checkinRepository;
    @Mock private WorkoutSessionRepository sessionRepository;
    @Mock private FeedbackRepository feedbackRepository;

    @Test
    void fallbackUsesRecentCompletedDurationAndExcludesInvalidWorkouts() {
        User user = new User();
        user.setId(8L);
        user.setCreatedAt(LocalDateTime.now().minusDays(20));
        WorkoutSession recent = new WorkoutSession();
        recent.setStartedAt(LocalDateTime.now().minusDays(2).minusMinutes(10));
        recent.setFinishedAt(LocalDateTime.now().minusDays(2));
        when(checkinRepository.findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(
                eq(8L), any(), any())).thenReturn(List.of());
        when(sessionRepository.findByUserIdAndStatusAndFinishedAtGreaterThanEqual(
                eq(8L), eq(WorkoutStatus.COMPLETED), any())).thenReturn(List.of(recent));

        UserStateService service = new UserStateService(checkinRepository, sessionRepository, feedbackRepository);
        var fallback = service.profileFallback(user, 30);

        assertEquals(10, fallback.freeTimeMin());
        assertEquals(1, fallback.workoutFrequency7d());
        verify(sessionRepository).findByUserIdAndStatusAndFinishedAtGreaterThanEqual(
                eq(8L), eq(WorkoutStatus.COMPLETED), any());
    }
}
