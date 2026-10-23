package com.albaraka.backend.repository;

import com.albaraka.backend.model.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface AdminPermissionRepository extends JpaRepository<AdminPermission, Long> {
    List<AdminPermission> findAllByOrderBySortOrderAsc();
    List<AdminPermission> findByCodeInOrderBySortOrderAsc(Set<String> codes);
}
