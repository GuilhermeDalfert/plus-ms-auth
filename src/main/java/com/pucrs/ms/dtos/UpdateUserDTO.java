package com.pucrs.ms.dtos;

import com.pucrs.ms.domain.user.UserRole;

public record UpdateUserDTO(String username, String email, UserRole role) {
}
