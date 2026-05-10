package com.pucrs.ms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pucrs.ms.domain.refreshtoken.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);
}