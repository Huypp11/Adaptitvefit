package com.adaptivefit.service;

import com.adaptivefit.dto.ProfileRequest;
import com.adaptivefit.dto.ProfileResponse;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.UserProfile;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileRepository profileRepository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        User user = currentUser.get();
        return profileRepository.findByUserId(user.getId())
                .map(ProfileResponse::from)
                .orElseThrow(() -> new NotFoundException("Chưa có hồ sơ sức khỏe"));
    }

    @Transactional
    public ProfileResponse upsert(ProfileRequest req) {
        User user = currentUser.get();
        UserProfile p = profileRepository.findByUserId(user.getId()).orElseGet(() -> {
            UserProfile created = new UserProfile();
            created.setUser(user);
            return created;
        });
        p.setAge(req.age());
        p.setHeightCm(req.heightCm());
        p.setWeightKg(req.weightKg());
        p.setFitnessLevel(req.fitnessLevel());
        p.setGoal(req.goal());
        p.setAvailableMinutes(req.availableMinutes());
        p.setPreferredWorkout(req.preferredWorkout());
        p.setLocation(req.location());
        p.setEquipment(req.equipment());
        return ProfileResponse.from(profileRepository.save(p));
    }
}
