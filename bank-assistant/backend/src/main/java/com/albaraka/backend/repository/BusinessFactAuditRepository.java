package com.albaraka.backend.repository;

import com.albaraka.backend.model.BusinessFactAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessFactAuditRepository extends JpaRepository<BusinessFactAudit, Long> {
    List<BusinessFactAudit> findByFactIdOrderByChangedAtDesc(Long factId);
}
