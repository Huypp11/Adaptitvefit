package com.adaptivefit.service;

import com.adaptivefit.entity.User;
import com.adaptivefit.exception.UnauthorizedException;
import com.adaptivefit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Lay user dang dang nhap. Moi query du lieu USER deu phai di qua day de khong xem duoc du lieu nguoi khac. */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Chưa đăng nhập");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new UnauthorizedException("Phiên đăng nhập không hợp lệ"));
    }
}
