package com.adaptivefit.service;

import com.adaptivefit.dto.CheckinRequest;
import com.adaptivefit.dto.CheckinResponse;
import com.adaptivefit.entity.DailyCheckin;
import com.adaptivefit.entity.User;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.ConflictException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.DailyCheckinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private final DailyCheckinRepository repository;
    private final CurrentUserService currentUser;

    @Transactional
    public CheckinResponse create(CheckinRequest req) {
        User user = currentUser.get();
        LocalDate today = LocalDate.now();
        if (repository.existsByUserIdAndCheckinDate(user.getId(), today)) {
            throw new ConflictException("Bạn đã check-in hôm nay");
        }
        boolean exercised = req.exercisedToday();
        if (exercised && req.durationMin() == null) {
            throw new BadRequestException("Cần nhập thời lượng tập khi bạn đã tập hôm nay");
        }

        DailyCheckin c = new DailyCheckin();
        c.setUser(user);
        c.setCheckinDate(today);
        c.setSleepHours(req.sleepHours().doubleValue());
        c.setStress(req.stress());
        c.setEnergy(req.energy());
        c.setMood(req.mood());
        c.setFreeTimeMin(req.freeTimeMin());
        c.setMotivation(req.motivation());
        c.setExercisedToday(exercised);
        // Chi luu cac truong phu hop voi trang thai de du lieu sach cho viec gan nhan sau nay
        c.setDurationMin(exercised ? req.durationMin() : null);
        c.setDifficulty(exercised ? req.difficulty() : null);
        c.setMissedReason(exercised ? null : req.missedReason());
        // TODO Phase 7: goi AI predict + Intervention + Recommendation tai day
        return CheckinResponse.from(repository.save(c));
    }

    @Transactional(readOnly = true)
    public CheckinResponse today() {
        User user = currentUser.get();
        return repository.findByUserIdAndCheckinDate(user.getId(), LocalDate.now())
                .map(CheckinResponse::from)
                .orElseThrow(() -> new NotFoundException("Hôm nay bạn chưa check-in"));
    }

    @Transactional(readOnly = true)
    public List<CheckinResponse> list(LocalDate from, LocalDate to) {
        User user = currentUser.get();
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw new BadRequestException("from phải nhỏ hơn hoặc bằng to");
        }
        return repository.findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(user.getId(), start, end)
                .stream().map(CheckinResponse::from).toList();
    }
}
