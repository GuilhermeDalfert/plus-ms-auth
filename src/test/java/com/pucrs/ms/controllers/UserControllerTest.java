package com.pucrs.ms.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pucrs.ms.domain.user.UserRole;
import com.pucrs.ms.dtos.UpdateUserDTO;
import com.pucrs.ms.dtos.UserDTO;
import com.pucrs.ms.infra.security.TokenService;
import com.pucrs.ms.services.AuthenticationService;
import com.pucrs.ms.services.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class UserControllerTest {

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

    // ---------- GET /users ----------

    @Test
    void getAllUsers_returns200_andUserList() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(
                new UserDTO("id-1", "a", "a@x", "USER"),
                new UserDTO("id-2", "b", "b@x", "ADMIN")));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("ADMIN"));
    }

    @Test
    void getAllUsers_returns200_andEmptyArray_whenNoUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    // ---------- DELETE /users/{id} ----------

    @Test
    void deleteUser_returns204_whenSuccess() throws Exception {
        doNothing().when(userService).deleteUser("id-1");

        mockMvc.perform(delete("/users/id-1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).deleteUser("id-1");
    }

    @Test
    void deleteUser_returns404_whenUserNotFound() throws Exception {
        doThrow(new RuntimeException("User not found"))
                .when(userService).deleteUser(anyString());

        mockMvc.perform(delete("/users/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("User not found")));
    }

    // ---------- PATCH /users/{id} ----------

    @Test
    void updateUser_returns200_andUpdatedUserDTO_whenSuccess() throws Exception {
        when(userService.updateUser(eq("id-1"), any()))
                .thenReturn(new UserDTO("id-1", "alice2", "alice@x", "USER"));

        mockMvc.perform(patch("/users/id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserDTO("alice2", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice2"))
                .andExpect(jsonPath("$.id").value("id-1"));
    }

    @Test
    void updateUser_returns400_whenServiceThrows() throws Exception {
        when(userService.updateUser(eq("id-1"), any()))
                .thenThrow(new RuntimeException("Email already in use"));

        mockMvc.perform(patch("/users/id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserDTO(null, "taken@x", null))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Email already in use")));
    }

    @Test
    void updateUser_returns400_whenChangingOwnRole() throws Exception {
        when(userService.updateUser(eq("id-1"), any()))
                .thenThrow(new RuntimeException("Você não pode alterar seu próprio cargo"));

        mockMvc.perform(patch("/users/id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserDTO(null, null, UserRole.ADMIN))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("alterar seu próprio cargo")));
    }
}
