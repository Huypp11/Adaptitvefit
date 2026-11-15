package com.adaptivefit.service;

import com.adaptivefit.engine.UserState;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.UserProfile;
import com.adaptivefit.repository.DailyCheckinRepository;
import com.adaptivefit.repository.ExerciseRepository;
import com.adaptivefit.repository.FeedbackRepository;
import com.adaptivefit.repository.RecommendationHistoryRepository;
import com.adaptivefit.repository.UserProfileRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private CurrentUserService currentUser;
    @Mock private DailyCheckinRepository checkinRepository;
    @Mock private UserProfileRepository profileRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private InterventionService interventionService;
    @Mock private UserStateService userStateService;
    @Mock private RiskService riskService;
    @Mock private RecommendationHistoryRepository recommendationRepository;
    @Mock private WorkoutSessionRepository workoutRepository;
    @Mock private FeedbackRepository feedbackRepository;

    @Test
    void riskBandsUseThirtyAndSixtyPercentBoundaries() {
        assertEquals("LOW", RecommendationService.riskBand(0.30));
        assertEquals("MEDIUM", RecommendationService.riskBand(0.3001));
        assertEquals("MEDIUM", RecommendationService.riskBand(0.60));
        assertEquals("HIGH", RecommendationService.riskBand(0.6001));
    }

    @Test
    void missingCheckinReturnsAnEphemeralProfileBasedNormalRecommendation() {
        User user = new User();
        user.setId(8L);
        UserProfile profile = new UserProfile();
        profile.setAvailableMinutes(30);
        profile.setFitnessLevel("BEGINNER");
        profile.setGoal("STAY_ACTIVE");
        profile.setLocation("HOME");
        profile.setMaxExerciseIntensity("MEDIUM");
        when(currentUser.get()).thenReturn(user);
        when(profileRepository.findByUserId(8L)).thenReturn(Optional.of(profile));
        when(checkinRepository.findByUserIdAndCheckinDateForUpdate(eq(8L), any())).thenReturn(Optional.empty());
        when(userStateService.profileFallback(user, 30))
                .thenReturn(new UserState(7.0, 3, 3, 3, 3, 30, false, 1, 2, "LOW", "history_rule"));
        when(exerciseRepository.findByActiveTrue()).thenReturn(java.util.List.of());

        RecommendationService service = new RecommendationService(currentUser, checkinRepository, profileRepository,
                exerciseRepository, interventionService, userStateService, riskService, recommendationRepository,
                workoutRepository, feedbackRepository, new ObjectMapper(), 0.25, 0.20, 0.20, 0.15, 0.10, 0.10);

        var response = service.today();

        assertEquals("PROFILE_FALLBACK", response.mode());
        assertEquals("NORMAL", response.strategy().code());
        assertNull(response.recommendationId());
        assertNull(response.state());
        assertNull(response.risk());
        verify(userStateService).profileFallback(user, 30);
        verifyNoInteractions(riskService);
        verify(recommendationRepository, never()).saveAndFlush(any());
    }
}
