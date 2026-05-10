package com.pucrs.ms.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.pucrs.ms.domain.refreshtoken.RefreshToken;
import com.pucrs.ms.domain.user.User;
import com.pucrs.ms.repositories.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    public String generateToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("ms")
                    .withSubject(user.getEmail())
                    .withClaim("role", user.getRole().name())
                    .withExpiresAt(generateExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Error while generating access token", exception);
        }
    }

    public String validateToken(String token) {
        if (token == null || token.isBlank())
            return "";
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("ms")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            return "";
        }
    }

    public String generateRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUser(user);
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setExpiresAt(
                Instant.now().plus(7, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    public RefreshToken getRefreshTokenEntity(String token) {
        return refreshTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));
    }

    public boolean validateRefreshToken(String token) {
        if (token == null || token.isBlank())
            return false;
        var refreshToken = refreshTokenRepository.findByToken(token);
        if (refreshToken.isEmpty())
            return false;
        var entity = refreshToken.get();
        if (entity.isRevoked())
            return false;
        if (entity.isExpired())
            return false;
        return true;
    }

    public void revokeRefreshToken(String token) {
        if (token == null || token.isBlank())
            return;
        var refreshToken = refreshTokenRepository.findByToken(token);
        if (refreshToken.isEmpty())
            return;
        var entity = refreshToken.get();
        entity.setRevokedAt(Instant.now());
        refreshTokenRepository.save(entity);
    }

    private Instant generateExpirationDate() {
        return LocalDateTime.now().plusMinutes(15).toInstant(ZoneOffset.of("-03:00"));
    }
}