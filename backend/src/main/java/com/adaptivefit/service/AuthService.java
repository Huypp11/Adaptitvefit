package com.adaptivefit.service;

import com.adaptivefit.dto.AuthResponse;
import com.adaptivefit.dto.LoginRequest;
import com.adaptivefit.dto.RegisterRequest;
import com.adaptivefit.entity.Role;
import com.adaptivefit.entity.User;
import com.adaptivefit.exception.ConflictException;
import com.adaptivefit.exception.UnauthorizedException;
import com.adaptivefit.repository.UserRepository;
import com.adaptivefit.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email đã được đăng ký");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER); // luon USER, khong cho client chon role
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Email hoặc mật khẩu không đúng"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), user.getEmail(), user.getRole().name());
    }
}
