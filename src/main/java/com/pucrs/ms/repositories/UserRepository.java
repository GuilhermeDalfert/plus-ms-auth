package com.pucrs.ms.repositories;

import com.pucrs.ms.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserRepository extends JpaRepository<User, String> {
    UserDetails findByEmail(String email);
    UserDetails findByUsername(String username);
}
