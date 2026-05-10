package com.pucrs.ms.dtos;

public record LoginResponseDTO(String accessToken, String refreshToken, Long expiresIn, UserDTO user) {
}