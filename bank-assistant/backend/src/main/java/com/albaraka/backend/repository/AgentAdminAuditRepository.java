package com.albaraka.backend.repository;

import com.albaraka.backend.model.AgentAdminAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Append-only assistant administration journal. */
public interface AgentAdminAuditRepository extends JpaRepository<AgentAdminAudit, Long> {

    List<AgentAdminAudit> findTop100ByOrderByCreatedAtDescIdDesc();

    /** The most recent time an action was journalled — e.g. who last declared a rebuild. */
    Optional<AgentAdminAudit> findFirstByActionOrderByCreatedAtDescIdDesc(String action);

    /** The journal window, newest first — optionally one kind of action. */
    Page<AgentAdminAudit> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<AgentAdminAudit> findAllByActionOrderByCreatedAtDescIdDesc(String action, Pageable pageable);
}
