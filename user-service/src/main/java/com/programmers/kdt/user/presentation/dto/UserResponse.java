package com.programmers.kdt.user.presentation.dto;

import com.programmers.kdt.user.domain.entity.Role;
import com.programmers.kdt.user.domain.entity.User;
import com.programmers.kdt.user.domain.entity.UserStatus;

public record UserResponse(
        Long userId,
        String email,
        String username,
        UserStatus status,
        Role userType,
        boolean emailVerified
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getEmail(), user.getUsername(), user.getStatus(), user.getUserType(), user.isEmailVerified());
    }
}
