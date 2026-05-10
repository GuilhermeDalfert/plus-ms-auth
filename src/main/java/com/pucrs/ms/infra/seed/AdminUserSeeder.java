package com.pucrs.ms.infra.seed;

import com.pucrs.ms.domain.user.User;
import com.pucrs.ms.domain.user.UserRole;
import com.pucrs.ms.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin.enabled:false}")
    private boolean enabled;

    @Value("${app.seed.admin.username:admin}")
    private String username;

    @Value("${app.seed.admin.email:admin@gmail.com}")
    private String email;

    @Value("${app.seed.admin.password:admin123}")
    private String password;

    public AdminUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        if (userRepository.findByEmail(email) != null) {
            return;
        }

        User admin = new User(username, email, passwordEncoder.encode(password), UserRole.ADMIN);
        userRepository.save(admin);
    }
}
