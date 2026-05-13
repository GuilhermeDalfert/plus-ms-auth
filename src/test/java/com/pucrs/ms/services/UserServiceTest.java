package com.pucrs.ms.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pucrs.ms.domain.user.User;
import com.pucrs.ms.domain.user.UserRole;
import com.pucrs.ms.dtos.RegisterDTO;
import com.pucrs.ms.dtos.UpdateUserDTO;
import com.pucrs.ms.dtos.UserDTO;
import com.pucrs.ms.infra.security.TokenService;
import com.pucrs.ms.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User buildUser(UserRole role) {
        return new User("uuid-1", "alice", "alice@plus.local", "encoded-pass", role);
    }

    private User buildUser(String id, String username, String email, UserRole role) {
        return new User(id, username, email, "encoded-pass", role);
    }

    @BeforeEach
    void clearSecurityContextBefore() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContextAfter() {
        SecurityContextHolder.clearContext();
    }

    // ---------- loadUserByUsername ----------

    @Test
    void loadUserByUsername_returnsUser_whenFoundByEmail() {
        // arrange
        User user = buildUser(UserRole.USER);
        when(userRepository.findByEmail("alice@plus.local")).thenReturn(user);

        // act
        UserDetails result = userService.loadUserByUsername("alice@plus.local");

        // assert
        assertThat(result).isSameAs(user);
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void loadUserByUsername_returnsUser_whenFoundByUsernameAsFallback() {
        // arrange
        User user = buildUser(UserRole.USER);
        when(userRepository.findByEmail("alice")).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(user);

        // act
        UserDetails result = userService.loadUserByUsername("alice");

        // assert
        assertThat(result).isSameAs(user);
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFoundException_whenNotFound() {
        // arrange
        when(userRepository.findByEmail("alice")).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(null);

        // act + assert
        assertThatThrownBy(() -> userService.loadUserByUsername("alice"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("alice");
    }

    // ---------- register ----------

    @Test
    void register_savesNewUser_withEncodedPasswordAndUserRole() {
        // arrange
        when(userRepository.findByEmail("alice@plus.local")).thenReturn(null);
        when(passwordEncoder.encode("plain")).thenReturn("encoded");

        // act
        UserDTO dto = userService.register(
                new RegisterDTO("alice", "alice@plus.local", "plain"));

        // assert
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@plus.local");
        assertThat(saved.getPassword()).isEqualTo("encoded");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);

        assertThat(dto.role()).isEqualTo("USER");
        assertThat(dto.email()).isEqualTo("alice@plus.local");
    }

    @Test
    void register_throwsRuntimeException_whenEmailAlreadyExists() {
        // arrange
        when(userRepository.findByEmail("alice@plus.local")).thenReturn(buildUser(UserRole.USER));

        // act + assert
        assertThatThrownBy(() -> userService.register(
                new RegisterDTO("alice", "alice@plus.local", "plain")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User already exists");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void register_alwaysAssignsUserRole_neverAdmin() {
        // arrange
        when(userRepository.findByEmail("eve@plus.local")).thenReturn(null);
        when(passwordEncoder.encode("plain")).thenReturn("encoded");

        // act — even though the caller might pretend to want ADMIN, the DTO has no role field
        userService.register(new RegisterDTO("eve", "eve@plus.local", "plain"));

        // assert
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.USER);
    }

    // ---------- deleteUser ----------

    @Test
    void deleteUser_deletesExistingUser() {
        // arrange
        User user = buildUser(UserRole.USER);
        when(userRepository.findById("uuid-1")).thenReturn(Optional.of(user));

        // act
        userService.deleteUser("uuid-1");

        // assert
        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_throwsRuntimeException_whenUserNotFound() {
        // arrange
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> userService.deleteUser("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");

        verify(userRepository, never()).delete(any());
    }

    // ---------- updateUser ----------

    @Test
    void updateUser_partialUpdate_ignoresNullFields() {
        // arrange
        User user = buildUser("uuid-1", "alice", "alice@x", UserRole.USER);
        when(userRepository.findById("uuid-1")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("new@x")).thenReturn(null);
        when(userRepository.save(user)).thenReturn(user);

        // act
        UserDTO dto = userService.updateUser("uuid-1",
                new UpdateUserDTO(null, "new@x", null));

        // assert
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("new@x");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(dto.email()).isEqualTo("new@x");
    }

    @Test
    void updateUser_throwsRuntimeException_whenUserNotFound() {
        // arrange
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> userService.updateUser("missing",
                new UpdateUserDTO("x", null, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }

    @Test
    void updateUser_throwsRuntimeException_whenEmailAlreadyInUseByOtherUser() {
        // arrange
        User target = buildUser("uuid-1", "alice", "alice@x", UserRole.USER);
        User other = buildUser("uuid-2", "bob", "taken@x", UserRole.USER);
        when(userRepository.findById("uuid-1")).thenReturn(Optional.of(target));
        when(userRepository.findByEmail("taken@x")).thenReturn(other);

        // act + assert
        assertThatThrownBy(() -> userService.updateUser("uuid-1",
                new UpdateUserDTO(null, "taken@x", null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email already in use");
    }

    @Test
    void updateUser_allowsEmailUpdate_whenSameUserKeepsOwnEmail() {
        // arrange
        User user = buildUser("uuid-1", "alice", "alice@x", UserRole.USER);
        when(userRepository.findById("uuid-1")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("alice@x")).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        // act
        UserDTO dto = userService.updateUser("uuid-1",
                new UpdateUserDTO(null, "alice@x", null));

        // assert
        assertThat(dto.email()).isEqualTo("alice@x");
        verify(userRepository).save(user);
    }

    @Test
    void updateUser_throwsRuntimeException_whenChangingOwnRole() {
        // arrange — logged-in principal IS the target
        User user = buildUser("uuid-1", "alice", "alice@x", UserRole.USER);
        when(userRepository.findById("uuid-1")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        // act + assert
        assertThatThrownBy(() -> userService.updateUser("uuid-1",
                new UpdateUserDTO(null, null, UserRole.ADMIN)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Você não pode alterar seu próprio cargo");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_allowsRoleChange_whenAdminEditsAnotherUser() {
        // arrange — logged-in admin edits a different user
        User admin = buildUser("admin-id", "root", "root@x", UserRole.ADMIN);
        User target = buildUser("uuid-1", "alice", "alice@x", UserRole.USER);
        when(userRepository.findById("uuid-1")).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));

        // act
        UserDTO dto = userService.updateUser("uuid-1",
                new UpdateUserDTO(null, null, UserRole.ADMIN));

        // assert
        assertThat(target.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(dto.role()).isEqualTo("ADMIN");
        verify(userRepository).save(target);
    }

    // ---------- me ----------

    @Test
    void me_returnsUserDTO_whenTokenValid() {
        // arrange
        User user = buildUser(UserRole.USER);
        when(tokenService.validateToken("jwt")).thenReturn("alice@plus.local");
        when(userRepository.findByEmail("alice@plus.local")).thenReturn(user);

        // act
        UserDTO dto = userService.me("jwt");

        // assert
        assertThat(dto.email()).isEqualTo("alice@plus.local");
        assertThat(dto.id()).isEqualTo("uuid-1");
        assertThat(dto.role()).isEqualTo("USER");
    }

    @Test
    void me_throwsRuntimeException_whenTokenInvalid() {
        // arrange — TokenService.validateToken returns "" when the JWT is bad
        when(tokenService.validateToken("bad-jwt")).thenReturn("");

        // act + assert
        assertThatThrownBy(() -> userService.me("bad-jwt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid access token");

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void me_throwsRuntimeException_whenUserNotFoundForValidToken() {
        // arrange
        when(tokenService.validateToken("jwt")).thenReturn("alice@plus.local");
        when(userRepository.findByEmail("alice@plus.local")).thenReturn(null);

        // act + assert
        assertThatThrownBy(() -> userService.me("jwt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("alice@plus.local");
    }

    // ---------- getAllUsers ----------

    @Test
    void getAllUsers_returnsMappedDTOs() {
        // arrange
        User u1 = buildUser("uuid-1", "alice", "alice@x", UserRole.USER);
        User u2 = buildUser("uuid-2", "root", "root@x", UserRole.ADMIN);
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        // act
        List<UserDTO> result = userService.getAllUsers();

        // assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).role()).isEqualTo("USER");
        assertThat(result.get(0).email()).isEqualTo("alice@x");
        assertThat(result.get(1).role()).isEqualTo("ADMIN");
        assertThat(result.get(1).email()).isEqualTo("root@x");
    }
}
