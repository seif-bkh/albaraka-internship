package com.albaraka.backend.repository;

import com.albaraka.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /** Serialize concurrent role/status mutations on the same staff identity. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE u.role IS NOT NULL AND u.role <> 'UTILISATEUR'")
    java.util.List<User> findInternalUsers();

    long countByRole(String role);
    long countByRoleAndActifTrue(String role);
    boolean existsByRoleAndActifTrue(String role);
}
