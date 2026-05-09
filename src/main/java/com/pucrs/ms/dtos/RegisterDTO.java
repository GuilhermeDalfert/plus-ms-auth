package com.pucrs.ms.dtos;

import com.pucrs.ms.domain.user.UserRole;

public record RegisterDTO(String username, String email, String password, UserRole role) {
}
