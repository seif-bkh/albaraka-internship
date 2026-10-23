package com.albaraka.backend.repository;

import com.albaraka.backend.model.AdminAccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAccessAuditRepository extends JpaRepository<AdminAccessAudit, Long> {
    List<AdminAccessAudit> findTop50ByOrderByCreatedAtDesc();
}
