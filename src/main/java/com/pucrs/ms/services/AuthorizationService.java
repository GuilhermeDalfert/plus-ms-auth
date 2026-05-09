package com.pucrs.ms.services;

import com.pucrs.ms.domain.user.User;
import com.pucrs.ms.domain.user.UserRole;
import com.pucrs.ms.dtos.AuthenticationDTO;
import com.pucrs.ms.dtos.RegisterDTO;
import com.pucrs.ms.infra.security.TokenService;
import com.pucrs.ms.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private TokenService tokenService;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        UserDetails user = userRepository.findByEmail(identifier);
        if (user == null) {
            user = userRepository.findByUsername(identifier);
        }
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + identifier);
        }
        return user;
    }

    public String login(AuthenticationDTO data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.username(), data.password());
        var auth = this.authenticationManager.authenticate(usernamePassword);

        return tokenService.generateToken((User) auth.getPrincipal());
    }
    @Transactional
    public void register(RegisterDTO data) {
        if(this.userRepository.findByEmail(data.email()) != null) throw new RuntimeException("User already exists");

        String encryptedPassword = new BCryptPasswordEncoder().encode(data.password());
        User newUser = new User(data.username(), data.email(), encryptedPassword, UserRole.USER);

        this.userRepository.save(newUser);
    }
}
