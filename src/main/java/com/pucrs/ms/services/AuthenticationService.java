package com.pucrs.ms.services;

import com.pucrs.ms.domain.user.User;
import com.pucrs.ms.dtos.AuthenticationDTO;
import com.pucrs.ms.dtos.LoginResponseDTO;
import com.pucrs.ms.dtos.RefreshTokenDTO;
import com.pucrs.ms.dtos.RefreshTokenResponseDTO;
import com.pucrs.ms.dtos.UserDTO;
import com.pucrs.ms.infra.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    public LoginResponseDTO login(AuthenticationDTO data) {
        try {
            var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.password());
            var auth = this.authenticationManager.authenticate(usernamePassword);
            User user = (User) auth.getPrincipal();
            String accessToken = tokenService.generateToken(user);
            String refreshToken = tokenService.generateRefreshToken(user);

            return new LoginResponseDTO(
                    accessToken,
                    refreshToken,
                    7200L,
                    new UserDTO(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name()));
        } catch (Exception e) {
            throw new RuntimeException("Invalid email or password");
        }
    }

    public void logout(RefreshTokenDTO data) {
        if (data == null || data.refreshToken() == null || data.refreshToken().isBlank()) {
            throw new RuntimeException("Refresh token is required");
        }
        tokenService.revokeRefreshToken(data.refreshToken());
    }

    public RefreshTokenResponseDTO refresh(RefreshTokenDTO data) {
        boolean valid = tokenService.validateRefreshToken(data.refreshToken());
        if (!valid) {
            throw new RuntimeException("Invalid refresh token");
        }

        var refreshTokenEntity = tokenService.getRefreshTokenEntity(data.refreshToken());
        User user = refreshTokenEntity.getUser();

        tokenService.revokeRefreshToken(data.refreshToken());
        String newRefreshToken = tokenService.generateRefreshToken(user);
        String newAccessToken = tokenService.generateToken(user);

        return new RefreshTokenResponseDTO(newAccessToken, newRefreshToken, 7200L);
    }
}
