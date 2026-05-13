package com.pucrs.ms.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.pucrs.ms.domain.refreshtoken.RefreshToken;
import com.pucrs.ms.domain.user.User;
import com.pucrs.ms.domain.user.UserRole;
import com.pucrs.ms.dtos.AuthenticationDTO;
import com.pucrs.ms.dtos.LoginResponseDTO;
import com.pucrs.ms.dtos.RefreshTokenDTO;
import com.pucrs.ms.dtos.RefreshTokenResponseDTO;
import com.pucrs.ms.infra.security.TokenService;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User buildUser(UserRole role) {
        return new User("uuid-1", "alice", "alice@plus.local", "encoded-pass", role);
    }

    // ---------- login ----------

    @Test
    void login_returnsLoginResponse_whenCredentialsValid() {
        // arrange
        User user = buildUser(UserRole.USER);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenService.generateToken(user)).thenReturn("access-jwt");
        when(tokenService.generateRefreshToken(user)).thenReturn("refresh-uuid");

        // act
        LoginResponseDTO response = authenticationService.login(
                new AuthenticationDTO("alice@plus.local", "pass"));

        // assert
        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-uuid");
        assertThat(response.expiresIn()).isEqualTo(7200L);
        assertThat(response.user().id()).isEqualTo(user.getId());
        assertThat(response.user().email()).isEqualTo(user.getEmail());
        assertThat(response.user().role()).isEqualTo(user.getRole().name());

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("alice@plus.local");
        assertThat(captor.getValue().getCredentials()).isEqualTo("pass");
    }

    @Test
    void login_throwsRuntimeException_whenAuthenticationFails() {
        // arrange
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad creds"));

        // act + assert
        assertThatThrownBy(() -> authenticationService.login(
                new AuthenticationDTO("alice@plus.local", "wrong")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(tokenService);
    }

    @Test
    void login_throwsRuntimeException_whenTokenServiceFails() {
        // arrange — auth OK, but generateToken explodes; the broad catch maps it to the same message
        User user = buildUser(UserRole.USER);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenService.generateToken(user)).thenThrow(new RuntimeException("jwt boom"));

        // act + assert
        assertThatThrownBy(() -> authenticationService.login(
                new AuthenticationDTO("alice@plus.local", "pass")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_passesEmailAndPasswordToAuthenticationManager() {
        // arrange
        User user = buildUser(UserRole.ADMIN);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenService.generateToken(user)).thenReturn("a");
        when(tokenService.generateRefreshToken(user)).thenReturn("r");

        // act
        authenticationService.login(new AuthenticationDTO("alice@plus.local", "pass"));

        // assert
        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("alice@plus.local");
        assertThat(captor.getValue().getCredentials()).isEqualTo("pass");
    }

    // ---------- logout ----------

    @Test
    void logout_revokesRefreshToken() {
        // act
        authenticationService.logout(new RefreshTokenDTO("refresh-uuid"));

        // assert
        verify(tokenService).revokeRefreshToken("refresh-uuid");
    }

    @Test
    void logout_throwsRuntimeException_whenDtoIsNull() {
        // act + assert
        assertThatThrownBy(() -> authenticationService.logout(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Refresh token is required");

        verify(tokenService, never()).revokeRefreshToken(anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void logout_throwsRuntimeException_whenRefreshTokenIsBlankOrNull(String token) {
        // act + assert
        assertThatThrownBy(() -> authenticationService.logout(new RefreshTokenDTO(token)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Refresh token is required");

        verify(tokenService, never()).revokeRefreshToken(anyString());
    }

    // ---------- refresh ----------

    @Test
    void refresh_returnsNewTokensAndRotatesRefreshToken() {
        // arrange
        User user = buildUser(UserRole.USER);
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        when(tokenService.validateRefreshToken("old-refresh")).thenReturn(true);
        when(tokenService.getRefreshTokenEntity("old-refresh")).thenReturn(entity);
        when(tokenService.generateRefreshToken(user)).thenReturn("new-refresh");
        when(tokenService.generateToken(user)).thenReturn("new-access");

        // act
        RefreshTokenResponseDTO response = authenticationService.refresh(
                new RefreshTokenDTO("old-refresh"));

        // assert
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.expiresIn()).isEqualTo(7200L);

        InOrder inOrder = inOrder(tokenService);
        inOrder.verify(tokenService).revokeRefreshToken("old-refresh");
        inOrder.verify(tokenService).generateRefreshToken(user);
    }

    @Test
    void refresh_throwsRuntimeException_whenRefreshTokenInvalid() {
        // arrange
        when(tokenService.validateRefreshToken("bad")).thenReturn(false);

        // act + assert
        assertThatThrownBy(() -> authenticationService.refresh(new RefreshTokenDTO("bad")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid refresh token");

        verify(tokenService, never()).getRefreshTokenEntity(anyString());
        verify(tokenService, never()).revokeRefreshToken(anyString());
        verify(tokenService, never()).generateToken(any());
        verify(tokenService, never()).generateRefreshToken(any());
    }

    @Test
    void refresh_throwsRuntimeException_whenEntityNotFound() {
        // arrange
        when(tokenService.validateRefreshToken("orphan")).thenReturn(true);
        when(tokenService.getRefreshTokenEntity("orphan"))
                .thenThrow(new RuntimeException("Refresh token not found"));

        // act + assert
        assertThatThrownBy(() -> authenticationService.refresh(new RefreshTokenDTO("orphan")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Refresh token not found");

        verify(tokenService, never()).revokeRefreshToken(anyString());
        verify(tokenService, never()).generateToken(any());
        verify(tokenService, never()).generateRefreshToken(any());
    }
}
