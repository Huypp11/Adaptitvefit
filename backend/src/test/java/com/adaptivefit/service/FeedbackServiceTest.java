package com.adaptivefit.service;

import com.adaptivefit.dto.RecommendationFeedbackRequest;
import com.adaptivefit.dto.FeedbackRequest;
import com.adaptivefit.entity.Feedback;
import com.adaptivefit.entity.RecommendationFeedbackStatus;
import com.adaptivefit.entity.RecommendationHistory;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.WorkoutSession;
import com.adaptivefit.entity.WorkoutStatus;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.ConflictException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.FeedbackRepository;
import com.adaptivefit.repository.RecommendationHistoryRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private WorkoutSessionRepository sessionRepository;
    @Mock private RecommendationHistoryRepository recommendationRepository;
    @Mock private CurrentUserService currentUser;
    @Mock private RecommendationEventService recommendationEventService;
    @InjectMocks private FeedbackService feedbackService;

    private User user;
    private RecommendationHistory recommendation;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        recommendation = new RecommendationHistory();
        recommendation.setId(42L);
    }

    @Test
    void createsOptionalFeedbackForOwnedRecommendation() {
        when(currentUser.get()).thenReturn(user);
        when(recommendationRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(recommendation));
        when(feedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RecommendationFeedbackRequest request = new RecommendationFeedbackRequest(
                RecommendationFeedbackStatus.NOT_DONE, 2, null, null, "  Không đủ thời gian  ");

        var response = feedbackService.createForRecommendation(42L, request);

        assertEquals(RecommendationFeedbackStatus.NOT_DONE, response.status());
        assertEquals("Không đủ thời gian", response.comment());
        assertNull(response.reason());
        verify(feedbackRepository).save(any(Feedback.class));
    }

    @Test
    void rejectsFeedbackForAnotherUsersRecommendation() {
        when(currentUser.get()).thenReturn(user);
        when(recommendationRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> feedbackService.createForRecommendation(
                42L, new RecommendationFeedbackRequest(RecommendationFeedbackStatus.PARTIAL, null, null, null, null)));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void rejectsFeedbackForInvalidWorkout() {
        when(currentUser.get()).thenReturn(user);
        WorkoutSession session = new WorkoutSession();
        session.setId(11L);
        session.setStatus(WorkoutStatus.INVALID_WORKOUT);
        when(sessionRepository.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(session));

        assertThrows(BadRequestException.class,
                () -> feedbackService.create(new FeedbackRequest(11L, false, null, null, null, null, "TOO_TIRED")));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateCreateAndRequiresExplicitUpdate() {
        when(currentUser.get()).thenReturn(user);
        when(recommendationRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(recommendation));
        when(feedbackRepository.existsByRecommendation_Id(42L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> feedbackService.createForRecommendation(
                42L, new RecommendationFeedbackRequest(RecommendationFeedbackStatus.COMPLETED, null, null, null, null)));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void updatesExistingFeedbackAndClearsReasonForCompletedStatus() {
        when(currentUser.get()).thenReturn(user);
        when(recommendationRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(recommendation));
        Feedback existing = new Feedback();
        existing.setRecommendation(recommendation);
        existing.setRecommendationStatus(RecommendationFeedbackStatus.NOT_DONE);
        existing.setRecommendationReason("NO_TIME");
        when(feedbackRepository.findByRecommendation_Id(42L)).thenReturn(Optional.of(existing));

        var response = feedbackService.updateForRecommendation(42L,
                new RecommendationFeedbackRequest(RecommendationFeedbackStatus.COMPLETED, 5, 3, "OTHER", null));

        assertEquals(RecommendationFeedbackStatus.COMPLETED, response.status());
        assertEquals(5, response.suitabilityRating());
        assertNull(response.reason());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void reportsStatisticsUsingSavedRecommendationsAndValidLinkedWorkoutsAsDenominators() {
        when(recommendationRepository.count()).thenReturn(4L);
        when(sessionRepository.countByRecommendationIsNotNull()).thenReturn(2L);
        when(sessionRepository.countByRecommendationIsNotNullAndStatus(WorkoutStatus.COMPLETED)).thenReturn(1L);
        when(sessionRepository.countByRecommendationIsNotNullAndStatusNot(WorkoutStatus.INVALID_WORKOUT)).thenReturn(2L);
        when(feedbackRepository.countByRecommendationStatus(any())).thenReturn(0L);
        when(feedbackRepository.countByRecommendationStatus(RecommendationFeedbackStatus.COMPLETED)).thenReturn(1L);
        when(feedbackRepository.countRecommendationReasons()).thenReturn(
                java.util.List.<Object[]>of(new Object[]{"NO_TIME", 2L}));
        when(feedbackRepository.averageRecommendationSuitability()).thenReturn(4.0);

        var stats = feedbackService.statistics();

        assertEquals(4L, stats.recommendationsSaved());
        assertEquals(2L, stats.recommendationsStarted());
        assertEquals(1L, stats.workoutSessionsCompleted());
        assertEquals(0.5, stats.startRate());
        assertEquals(0.5, stats.workoutCompletionRate());
        assertEquals(1L, stats.feedbackByStatus().get("COMPLETED"));
        assertEquals(2L, stats.reasonsNotDone().get("NO_TIME"));
        assertEquals(4.0, stats.averageSuitabilityRating());
    }

    @Test
    void workoutFeedbackReusesRecommendationFeedbackInsteadOfCreatingADuplicate() {
        when(currentUser.get()).thenReturn(user);
        WorkoutSession session = new WorkoutSession();
        session.setId(9L);
        session.setStatus(WorkoutStatus.COMPLETED);
        session.setRecommendation(recommendation);
        when(sessionRepository.findByIdAndUserId(9L, 7L)).thenReturn(Optional.of(session));
        when(feedbackRepository.existsBySessionId(9L)).thenReturn(false);
        Feedback existing = new Feedback();
        existing.setRecommendation(recommendation);
        existing.setRecommendationStatus(RecommendationFeedbackStatus.PARTIAL);
        when(feedbackRepository.findByRecommendation_Id(42L)).thenReturn(Optional.of(existing));
        when(feedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        feedbackService.create(new FeedbackRequest(9L, true, 2, 2, 4, true, null));

        assertSame(session, existing.getSession());
        assertEquals(RecommendationFeedbackStatus.PARTIAL, existing.getRecommendationStatus());
        assertEquals(4, existing.getSatisfaction());
        verify(feedbackRepository).save(existing);
    }
}
