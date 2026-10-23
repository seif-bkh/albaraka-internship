package com.albaraka.backend.repository;

import com.albaraka.backend.model.AdminRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {
    @EntityGraph(attributePaths = "permissions")
    Optional<AdminRole> findByCode(String code);

    /** Serialize staff assignment/deactivation for a role. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AdminRole r where r.code = :code")
    Optional<AdminRole> findByCodeForUpdate(@Param("code") String code);

    /** Serialize definition and activation changes for a role. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AdminRole r where r.id = :id")
    Optional<AdminRole> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "permissions")
    @Query("select distinct r from AdminRole r where r.id = :id")
    Optional<AdminRole> findByIdWithPermissions(@org.springframework.data.repository.query.Param("id") Long id);

    @EntityGraph(attributePaths = "permissions")
    @Query("select distinct r from AdminRole r where r.staffRole = true order by r.name")
    List<AdminRole> findAllStaffWithPermissions();

    boolean existsByCode(String code);
}
