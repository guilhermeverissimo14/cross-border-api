package com.guilherme.crossborderapi.dto.user;

import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.enums.UserType;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        UserType userType,
        String document,
        LocalDateTime createdAt
) {

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getUserType(),
                user.getDocument(),
                user.getCreatedAt()
        );
    }
}
