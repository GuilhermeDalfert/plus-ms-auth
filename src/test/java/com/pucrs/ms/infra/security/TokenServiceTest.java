package com.pucrs.ms.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.pucrs.ms.domain.refreshtoken.RefreshToken;
import com.pucrs.ms.domain.user.User;
import com.pucrs.ms.domain.user.UserRole;
import com.pucrs.ms.repositories.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String SECRET = "test-secret-1234";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
    }

    private User buildUser(String email, UserRole role) {
        return new User("user-id-1", "username", email, "encoded-password", role);
    }

    // ---------- generateToken ----------

    @Test
    void generateToken_returnsValidJwt_withCorrectClaims() {
        // arrange
        User user = buildUser("alice@plus.local", UserRole.ADMIN);
        Instant before = Instant.now();

        // act
        String token = tokenService.generateToken(user);

        // assert
        assertThat(token).isNotBlank();
        DecodedJWT decoded = JWT.require(Algorithm.HMAC256(SECRET))
                .withIssuer("ms")
                .build()
                .verify(token);
        assertThat(decoded.getIssuer()).isEqualTo("ms");
        assertThat(decoded.getSubject()).isEqualTo(user.getEmail());
        assertThat(decoded.getClaim("role").asString()).isEqualTo(user.getRole().name());
        assertThat(decoded.getExpiresAtAsInstant()).isAfter(before);
    }

    // ---------- validateToken ----------

    @Test
    void validateToken_returnsEmail_whenTokenIsValid() {
        // arrange
        User user = buildUser("bob@plus.local", UserRole.USER);
        String token = tokenService.generateToken(user);

        // act
        String subject = tokenService.validateToken(token);

        // assert
        assertThat(subject).isEqualTo(user.getEmail());
    }

    @Test
    void validateToken_returnsEmptyString_whenTokenIsExpired() {
        // arrange — build an expired JWT manually with the same secret
        String expiredToken = JWT.create()
                .withIssuer("ms")
                .withSubject("expired@plus.local")
                .withClaim("role", UserRole.USER.name())
                .withExpiresAt(Instant.now().minusSeconds(60))
                .sign(Algorithm.HMAC256(SECRET));

        // act
        String subject = tokenService.validateToken(expiredToken);

        // assert
        assertThat(subject).isEmpty();
    }

    @Test
    void validateToken_returnsEmptyString_whenTokenIsMalformed() {
        // act
        String subject = tokenService.validateToken("not.a.jwt");

        // assert
        assertThat(subject).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void validateToken_returnsEmptyString_whenTokenIsNullOrBlank(String token) {
        // act
        String subject = tokenService.validateToken(token);

        // assert
        assertThat(subject).isEmpty();
    }

    // ---------- generateRefreshToken ----------

    @Test
    void generateRefreshToken_persistsTokenWithSevenDayExpiry() {
        // arrange
        User user = buildUser("carol@plus.local", UserRole.USER);
        Instant expectedExpiry = Instant.now().plus(7, ChronoUnit.DAYS);

        // act
        String returned = tokenService.generateRefreshToken(user);

        // assert
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        // token is a valid UUID
        UUID parsed = UUID.fromString(returned);
        assertThat(parsed).isNotNull();

        // user matches
        assertThat(saved.getUser()).isSameAs(user);

        // expiresAt is ~7 days in the future (5s tolerance)
        Instant savedExpiry = (Instant) ReflectionTestUtils.getField(saved, "expiresAt");
        assertThat(savedExpiry).isBetween(
                expectedExpiry.minusSeconds(5),
                expectedExpiry.plusSeconds(5));

        // returned token equals the persisted token
        String savedToken = (String) ReflectionTestUtils.getField(saved, "token");
        assertThat(returned).isEqualTo(savedToken);
    }

    // ---------- validateRefreshToken ----------

    @Test
    void validateRefreshToken_returnsTrue_whenValid() {
        // arrange
        String rawToken = UUID.randomUUID().toString();
        RefreshToken entity = new RefreshToken();
        entity.setToken(rawToken);
        entity.setUser(buildUser("dan@plus.local", UserRole.USER));
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(entity));

        // act
        boolean valid = tokenService.validateRefreshToken(rawToken);

        // assert
        assertThat(valid).isTrue();
    }

    @Test
    void validateRefreshToken_returnsFalse_whenRevoked() {
        // arrange
        String rawToken = UUID.randomUUID().toString();
        RefreshToken entity = new RefreshToken();
        entity.setToken(rawToken);
        entity.setUser(buildUser("eve@plus.local", UserRole.USER));
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        entity.setRevokedAt(Instant.now());
        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(entity));

        // act
        boolean valid = tokenService.validateRefreshToken(rawToken);

        // assert
        assertThat(valid).isFalse();
    }

    @Test
    void validateRefreshToken_returnsFalse_whenExpired() {
        // arrange
        String rawToken = UUID.randomUUID().toString();
        RefreshToken entity = new RefreshToken();
        entity.setToken(rawToken);
        entity.setUser(buildUser("frank@plus.local", UserRole.USER));
        entity.setCreatedAt(Instant.now().minus(8, ChronoUnit.DAYS));
        entity.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(entity));

        // act
        boolean valid = tokenService.validateRefreshToken(rawToken);

        // assert
        assertThat(valid).isFalse();
    }

    @Test
    void validateRefreshToken_returnsFalse_whenNotFound() {
        // arrange
        String rawToken = UUID.randomUUID().toString();
        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.empty());

        // act
        boolean valid = tokenService.validateRefreshToken(rawToken);

        // assert
        assertThat(valid).isFalse();
    }

    // ---------- revokeRefreshToken ----------

    @Test
    void revokeRefreshToken_setsRevokedAt_andSaves() {
        // arrange
        String rawToken = UUID.randomUUID().toString();
        RefreshToken entity = new RefreshToken();
        entity.setToken(rawToken);
        entity.setUser(buildUser("grace@plus.local", UserRole.USER));
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(entity));

        // act
        tokenService.revokeRefreshToken(rawToken);

        // assert
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        Instant revokedAt = (Instant) ReflectionTestUtils.getField(saved, "revokedAt");
        assertThat(revokedAt).isNotNull();
        assertThat(saved.isRevoked()).isTrue();
    }

    @Test
    void revokeRefreshToken_doesNothing_whenTokenNotFound() {
        // arrange
        String rawToken = UUID.randomUUID().toString();
        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.empty());

        // act
        tokenService.revokeRefreshToken(rawToken);

        // assert
        verify(refreshTokenRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void revokeRefreshToken_doesNothing_whenTokenIsBlank(String token) {
        // act
        tokenService.revokeRefreshToken(token);

        // assert
        verify(refreshTokenRepository, never()).findByToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }
}
