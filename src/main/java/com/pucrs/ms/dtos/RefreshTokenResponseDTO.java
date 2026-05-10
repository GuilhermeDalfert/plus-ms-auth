package com.pucrs.ms.dtos;

public record RefreshTokenResponseDTO(String accessToken, String refreshToken, long expiresIn) {
}
