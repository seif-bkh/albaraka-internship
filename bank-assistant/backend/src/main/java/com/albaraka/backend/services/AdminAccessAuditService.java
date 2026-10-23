package com.albaraka.backend.services;

import com.albaraka.backend.dto.AdminAccessDtos.AuditDto;
import com.albaraka.backend.model.AdminAccessAudit;
import com.albaraka.backend.repository.AdminAccessAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAccessAuditService {
    private final AdminAccessAuditRepository repository;

    public AdminAccessAuditService(AdminAccessAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String action, String actor, String roleCode, Long userId, String details) {
        String safeActor = actor == null || actor.isBlank() ? "unknown" : actor;
        String safeDetails = details == null ? "" : details;
        repository.save(AdminAccessAudit.of(action, safeActor, roleCode, userId, safeDetails));
    }

    @Transactional(readOnly = true)
    public List<AuditDto> latest() {
        return repository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(entry -> new AuditDto(entry.getId(), entry.getAction(), entry.getActor(),
                        entry.getTargetRoleCode(), entry.getTargetUserId(), entry.getDetails(),
                        entry.getCreatedAt()))
                .toList();
    }
}
