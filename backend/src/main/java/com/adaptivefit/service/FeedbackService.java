package com.adaptivefit.service;

import com.adaptivefit.dto.FeedbackRequest;
import com.adaptivefit.dto.FeedbackResponse;
import com.adaptivefit.entity.Feedback;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.WorkoutSession;
import com.adaptivefit.entity.WorkoutStatus;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.ConflictException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.FeedbackRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final CurrentUserService currentUser;

    @Transactional
    public FeedbackResponse create(FeedbackRequest req) {
        User user = currentUser.get();
        WorkoutSession session = sessionRepository.findByIdAndUserId(req.sessionId(), user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy buổi tập"));

        if (session.getStatus() == WorkoutStatus.STARTED) {
            throw new ConflictException("Hãy kết thúc buổi tập trước khi gửi feedback");
        }
        if (feedbackRepository.existsBySessionId(session.getId())) {
            throw new ConflictException("Buổi tập này đã có feedback");
        }

        boolean sessionCompleted = session.getStatus() == WorkoutStatus.COMPLETED;
        if (req.completed() != sessionCompleted) {
            throw new BadRequestException("Trường completed không khớp với trạng thái buổi tập");
        }
        if (sessionCompleted) {
            if (req.difficulty() == null || req.fatigue() == null || req.satisfaction() == null) {
                throw new BadRequestException("Cần nhập difficulty, fatigue và satisfaction khi đã hoàn thành");
            }
        } else if (req.notCompletedReason() == null) {
            throw new BadRequestException("Cần nhập notCompletedReason khi không hoàn thành");
        }

        Feedback f = new Feedback();
        f.setSession(session);
        f.setCompleted(req.completed());
        f.setDifficulty(req.difficulty());
        f.setFatigue(req.fatigue());
        f.setSatisfaction(req.satisfaction());
        f.setLiked(req.liked());
        f.setNotCompletedReason(sessionCompleted ? null : req.notCompletedReason());
        return FeedbackResponse.from(feedbackRepository.save(f));
    }
}
