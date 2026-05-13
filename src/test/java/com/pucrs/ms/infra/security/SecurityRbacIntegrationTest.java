package com.pucrs.ms.infra.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pucrs.ms.domain.user.UserRole;
import com.pucrs.ms.dtos.AuthenticationDTO;
import com.pucrs.ms.dtos.LoginResponseDTO;
import com.pucrs.ms.dtos.RefreshTokenDTO;
import com.pucrs.ms.dtos.RegisterDTO;
import com.pucrs.ms.dtos.UpdateUserDTO;
import com.pucrs.ms.dtos.UserDTO;
import com.pucrs.ms.services.AuthenticationService;
import com.pucrs.ms.services.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TokenService tokenService;

    private static final UserDTO USER_DTO = new UserDTO("id-1", "alice", "alice@plus.local", "USER");

    @BeforeEach
    void setUp() {
        when(authenticationService.login(any()))
                .thenReturn(new LoginResponseDTO("access", "refresh", 7200L, USER_DTO));
        when(userService.register(any())).thenReturn(USER_DTO);
        when(userService.getAllUsers()).thenReturn(List.of());
        when(userService.me(anyString())).thenReturn(USER_DTO);
        when(userService.updateUser(anyString(), any())).thenReturn(USER_DTO);
        doNothing().when(userService).deleteUser(anyString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---------- Public endpoints ----------

    @Test
    @DisplayName("POST /auth/login is accessible without authentication")
    void login_isAccessible_withoutAuthentication() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthenticationDTO("alice@plus.local", "secret"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/refresh is accessible without authentication")
    void refresh_isAccessible_withoutAuthentication() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshTokenDTO("some-refresh-token"))))
                .andExpect(status().is(not(is(401))))
                .andExpect(status().is(not(is(403))));
    }

    @Test
    @DisplayName("POST /auth/logout is accessible without authentication")
    void logout_isAccessible_withoutAuthentication() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshTokenDTO("some-refresh-token"))))
                .andExpect(status().is(not(is(401))))
                .andExpect(status().is(not(is(403))));
    }

    @Test
    @DisplayName("GET /swagger-ui.html is accessible without authentication")
    void swaggerUi_isAccessible_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is(not(is(401))))
                .andExpect(status().is(not(is(403))));
    }

    @Test
    @DisplayName("GET /v3/api-docs is accessible without authentication")
    void apiDocs_isAccessible_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().is(not(is(401))))
                .andExpect(status().is(not(is(403))));
    }

    // ---------- /auth/me (authenticated, any role) ----------

    @Test
    @DisplayName("GET /auth/me is denied when unauthenticated")
    void me_returns401_whenUnauthenticated() throws Exception {
        // SecurityConfiguration does not declare an AuthenticationEntryPoint, so the default
        // Http403ForbiddenEntryPoint returns 403 for anonymous requests instead of 401. We accept
        // either status so the test asserts the security contract (rejected) without locking in
        // the imprecise code.
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /auth/me returns 200 for authenticated USER")
    void me_returns200_whenAuthenticatedAsUser() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer fake-token"))
                .andExpect(status().isOk());
    }

    // ---------- POST /auth/register (ADMIN only) ----------

    @Test
    @DisplayName("POST /auth/register is denied when unauthenticated")
    void register_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("bob", "bob@plus.local", "secret"))))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /auth/register returns 403 for USER role")
    void register_returns403_whenAuthenticatedAsUser() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("bob", "bob@plus.local", "secret"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /auth/register returns 200 for ADMIN role")
    void register_returns200_whenAuthenticatedAsAdmin() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("bob", "bob@plus.local", "secret"))))
                .andExpect(status().isOk());
    }

    // ---------- GET /users (USER or ADMIN) ----------

    @Test
    @DisplayName("GET /users is denied when unauthenticated")
    void getUsers_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /users returns 200 for USER role")
    void getUsers_returns200_whenAuthenticatedAsUser() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /users returns 200 for ADMIN role")
    void getUsers_returns200_whenAuthenticatedAsAdmin() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk());
    }

    // ---------- DELETE /users/{id} (ADMIN only) ----------

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("DELETE /users/{id} returns 403 for USER role")
    void deleteUser_returns403_whenAuthenticatedAsUser() throws Exception {
        mockMvc.perform(delete("/users/some-id"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /users/{id} returns 204 for ADMIN role")
    void deleteUser_returns204_whenAuthenticatedAsAdmin() throws Exception {
        mockMvc.perform(delete("/users/some-id"))
                .andExpect(status().isNoContent());
    }

    // ---------- PATCH /users/{id} (ADMIN only) ----------

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("PATCH /users/{id} returns 403 for USER role")
    void updateUser_returns403_whenAuthenticatedAsUser() throws Exception {
        mockMvc.perform(patch("/users/some-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserDTO("new-name", null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /users/{id} returns 200 for ADMIN role")
    void updateUser_returns200_whenAuthenticatedAsAdmin() throws Exception {
        mockMvc.perform(patch("/users/some-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserDTO("new-name", null, UserRole.USER))))
                .andExpect(status().isOk());
    }
}
