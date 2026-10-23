package com.albaraka.backend.repository;

import com.albaraka.backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    List<RefreshToken> findByFamily(String family);
    List<RefreshToken> findBySubjectAndRevokedAtIsNull(String subject);
}
