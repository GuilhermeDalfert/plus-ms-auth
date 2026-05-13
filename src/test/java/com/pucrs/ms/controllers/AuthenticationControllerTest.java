package com.pucrs.ms.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pucrs.ms.dtos.AuthenticationDTO;
import com.pucrs.ms.dtos.LoginResponseDTO;
import com.pucrs.ms.dtos.RefreshTokenDTO;
import com.pucrs.ms.dtos.RefreshTokenResponseDTO;
import com.pucrs.ms.dtos.RegisterDTO;
import com.pucrs.ms.dtos.UserDTO;
import com.pucrs.ms.infra.security.TokenService;
import com.pucrs.ms.services.AuthenticationService;
import com.pucrs.ms.services.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TokenService tokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---------- POST /auth/login ----------

    @Test
    @WithAnonymousUser
    void login_returns200_andLoginResponseJson_whenServiceSucceeds() throws Exception {
        UserDTO user = new UserDTO("id-1", "alice", "alice@x", "USER");
        when(authenticationService.login(any()))
                .thenReturn(new LoginResponseDTO("access-jwt", "refresh-uuid", 7200L, user));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthenticationDTO("alice@x", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-uuid"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.user.id").value("id-1"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    @WithAnonymousUser
    void login_returns400_withErrorMessage_whenServiceThrows() throws Exception {
        when(authenticationService.login(any()))
                .thenThrow(new RuntimeException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthenticationDTO("alice@x", "wrong"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid email or password")));
    }

    // ---------- POST /auth/register ----------

    @Test
    void register_returns200_andUserDTO_whenServiceSucceeds() throws Exception {
        when(userService.register(any()))
                .thenReturn(new UserDTO("id-1", "alice", "alice@x", "USER"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("alice", "alice@x", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.id").value("id-1"));
    }

    @Test
    void register_returns400_whenEmailAlreadyExists() throws Exception {
        when(userService.register(any()))
                .thenThrow(new RuntimeException("User already exists"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("alice", "alice@x", "pw"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("User already exists")));
    }

    // ---------- POST /auth/refresh ----------

    @Test
    @WithAnonymousUser
    void refresh_returns200_andNewTokens_whenValid() throws Exception {
        when(authenticationService.refresh(any()))
                .thenReturn(new RefreshTokenResponseDTO("new-access", "new-refresh", 7200L));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshTokenDTO("old"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.expiresIn").value(7200));
    }

    @Test
    @WithAnonymousUser
    void refresh_returns400_whenInvalidToken() throws Exception {
        when(authenticationService.refresh(any()))
                .thenThrow(new RuntimeException("Invalid refresh token"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshTokenDTO("bad"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid refresh token")));
    }

    // ---------- POST /auth/logout ----------

    @Test
    @WithAnonymousUser
    void logout_returns204_andEmptyBody_whenServiceSucceeds() throws Exception {
        doNothing().when(authenticationService).logout(any());

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshTokenDTO("x"))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        ArgumentCaptor<RefreshTokenDTO> captor = ArgumentCaptor.forClass(RefreshTokenDTO.class);
        verify(authenticationService).logout(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().refreshToken()).isEqualTo("x");
    }

    // ---------- GET /auth/me ----------

    @Test
    void me_returns200_andUserDTO_whenBearerTokenValid() throws Exception {
        when(tokenService.validateToken(anyString())).thenReturn("");
        when(userService.me("jwt-token"))
                .thenReturn(new UserDTO("id-1", "alice", "alice@x", "USER"));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.email").value("alice@x"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(userService).me(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue()).isEqualTo("jwt-token");
    }

    @Test
    void me_returns400_whenAuthorizationHeaderMissing() throws Exception {
        when(tokenService.validateToken(anyString())).thenReturn("");

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Missing Authorization Bearer token")));
    }

    @Test
    void me_returns400_whenAuthorizationHeaderHasNoBearerPrefix() throws Exception {
        when(tokenService.validateToken(anyString())).thenReturn("");
        // pre-stub to avoid a NullPointerException for any default void/object behavior
        doThrow(new RuntimeException("should not be called"))
                .when(userService).me("Basic xyz");

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Basic xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Missing Authorization Bearer token")));
    }
}
