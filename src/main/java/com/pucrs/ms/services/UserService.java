package com.pucrs.ms.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pucrs.ms.domain.user.User;
import com.pucrs.ms.domain.user.UserRole;
import com.pucrs.ms.dtos.RegisterDTO;
import com.pucrs.ms.dtos.UpdateUserDTO;
import com.pucrs.ms.dtos.UserDTO;
import com.pucrs.ms.infra.security.TokenService;
import com.pucrs.ms.repositories.UserRepository;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(identifier);
        if (user == null) {
            user = userRepository.findByUsername(identifier);
        }
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + identifier);
        }
        return user;
    }

    @Transactional
    public UserDTO register(RegisterDTO data) {
        if (userRepository.findByEmail(data.email()) != null) {
            throw new RuntimeException("User already exists");
        }

        String encryptedPassword = passwordEncoder.encode(data.password());
        User newUser = new User(data.username(), data.email(), encryptedPassword, UserRole.USER);
        userRepository.save(newUser);

        return toDTO(newUser);
    }

    @Transactional
    public void deleteUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        userRepository.delete(user);
    }

    @Transactional
    public UserDTO updateUser(String id, UpdateUserDTO data) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (data.username() != null) user.setUsername(data.username());
        if (data.email() != null) {
            User existing = userRepository.findByEmail(data.email());
            if (existing != null && !existing.getId().equals(id)) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(data.email());
        }
        if (data.role() != null) user.setRole(data.role());

        return toDTO(userRepository.save(user));
    }

    public UserDTO me(String accessToken) {
        var email = tokenService.validateToken(accessToken);
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Invalid access token");
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found: " + email);
        }

        return toDTO(user);
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream().map(this::toDTO).toList();
    }

    private UserDTO toDTO(User user) {
        return new UserDTO(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }



}
