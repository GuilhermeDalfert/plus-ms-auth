package com.pucrs.ms.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.pucrs.ms.domain.user.User;
import com.pucrs.ms.domain.user.UserRole;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void findByEmail_returnsUser_whenEmailExists() {
        em.persist(new User("alice", "alice@plus.local", "encoded", UserRole.USER));
        em.flush();

        User found = userRepository.findByEmail("alice@plus.local");

        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("alice");
        assertThat(found.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void findByEmail_returnsNull_whenEmailNotFound() {
        assertThat(userRepository.findByEmail("missing@x.com")).isNull();
    }

    @Test
    void findByUsername_returnsUser_whenUsernameExists() {
        em.persist(new User("alice", "alice@plus.local", "encoded", UserRole.USER));
        em.flush();

        User found = userRepository.findByUsername("alice");

        assertThat(found).isNotNull();
        assertThat(found.getEmail()).isEqualTo("alice@plus.local");
    }

    @Test
    void findByUsername_returnsNull_whenNotFound() {
        assertThat(userRepository.findByUsername("nope")).isNull();
    }

    @Test
    void findByEmail_isCaseSensitive() {
        em.persist(new User("alice", "alice@plus.local", "encoded", UserRole.USER));
        em.flush();

        // Documents current behavior: default JPA / H2 (PostgreSQL mode) uses case-sensitive
        // equality on `=` comparisons, so the lookup must match the persisted case exactly.
        assertThat(userRepository.findByEmail("ALICE@PLUS.LOCAL")).isNull();
        assertThat(userRepository.findByEmail("alice@plus.local")).isNotNull();
    }

    @Test
    void save_persistsUser_andGeneratesId() {
        User user = new User("bob", "bob@x", "pw", UserRole.ADMIN);

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        // confirms it's a valid UUID
        UUID parsed = UUID.fromString(saved.getId());
        assertThat(parsed).isNotNull();
    }
}
