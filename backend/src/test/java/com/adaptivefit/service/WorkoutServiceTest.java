package com.adaptivefit.service;

import com.adaptivefit.entity.Exercise;
import com.adaptivefit.entity.Feedback;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.WorkoutSession;
import com.adaptivefit.entity.WorkoutSessionExercise;
import com.adaptivefit.entity.WorkoutStatus;
import com.adaptivefit.repository.ExerciseRepository;
import com.adaptivefit.repository.FeedbackRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock private WorkoutSessionRepository sessionRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private CurrentUserService currentUser;
    @Mock private RecommendationService recommendationService;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private RecommendationEventService recommendationEventService;

    private WorkoutService service;
    private WorkoutSession session;

    @BeforeEach
    void setUp() {
        service = new WorkoutService(sessionRepository, exerciseRepository, currentUser,
                recommendationService, feedbackRepository, recommendationEventService);
        User user = new User();
        user.setId(2L);
        when(currentUser.get()).thenReturn(user);

        session = new WorkoutSession();
        session.setId(7L);
        session.setUser(user);
        session.setStatus(WorkoutStatus.STARTED);
        session.setStartedAt(LocalDateTime.now().minusMinutes(5));
        WorkoutSessionExercise planned = new WorkoutSessionExercise();
        planned.setExercise(new Exercise());
        planned.setOrderNo(1);
        planned.setPlannedMin(10);
        planned.setSession(session);
        session.getExercises().add(planned);

        when(sessionRepository.findByIdAndUserId(7L, 2L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(feedbackRepository.findBySession_Id(7L)).thenReturn(Optional.empty());
    }

    @Test
    void invalidatesWorkoutShorterThanThreeMinutes() {
        session.setStartedAt(LocalDateTime.now().minusSeconds(179));

        assertEquals("INVALID_WORKOUT", service.complete(7L).status());
    }

    @Test
    void invalidatesWorkoutBelowThirtyPercentOfPlannedDuration() {
        session.setStartedAt(LocalDateTime.now().minusMinutes(5));
        session.getExercises().get(0).setPlannedMin(20);

        assertEquals("INVALID_WORKOUT", service.complete(7L).status());
    }

    @Test
    void completesWorkoutAtBothMinimumThresholds() {
        session.setStartedAt(LocalDateTime.now().minusSeconds(180));
        session.getExercises().get(0).setPlannedMin(10);

        assertEquals("COMPLETED", service.complete(7L).status());
    }

    @Test
    void storesOptionalAbandonReasonWithTheWorkoutFeedback() {
        when(feedbackRepository.existsBySessionId(7L)).thenReturn(false);
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Feedback feedback = new Feedback();
        feedback.setNotCompletedReason("TOO_TIRED");
        when(feedbackRepository.findBySession_Id(7L)).thenReturn(Optional.of(feedback));

        var response = service.abandon(7L, "TOO_TIRED");

        assertEquals("ABANDONED", response.status());
        assertEquals("TOO_TIRED", response.notCompletedReason());
        verify(feedbackRepository).save(any(Feedback.class));
    }
}
