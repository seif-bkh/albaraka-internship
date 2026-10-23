package com.albaraka.backend.services;

import com.albaraka.backend.model.AgentAdminAudit;
import com.albaraka.backend.repository.AgentAdminAuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Writes and reads the assistant administration journal.
 *
 * <p>Every state-changing action on the assistant goes through here, whether it
 * succeeded or not — the point of the journal is to answer "who changed what,
 * when and why" after an incident, not to be a happy-path log.
 */
@Service
public class AgentAdminAuditService {

    private final AgentAdminAuditRepository repository;

    public AgentAdminAuditService(AgentAdminAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String action, String target, String detail,
                       String reason, String actor, String outcome) {
        repository.save(AgentAdminAudit.of(action, target, detail, reason, actor, outcome));
    }

    @Transactional(readOnly = true)
    public List<AgentAdminAudit> recent() {
        return repository.findTop100ByOrderByCreatedAtDescIdDesc();
    }

    /**
     * The journal window the console shows, newest first.
     *
     * <p>Filtering by action is done in SQL rather than in the page: "who rebuilt an
     * index" must be answerable even when the last hundred rows are something else.
     * The window is capped so this stays a read, not a report.
     */
    public List<AgentAdminAudit> recent(int limit, String action) {
        int size = Math.max(1, Math.min(limit, MAX_WINDOW));
        Pageable window = PageRequest.of(0, size);
        String wanted = action == null ? "" : action.trim();
        if (wanted.isEmpty() || ALL.equalsIgnoreCase(wanted)) {
            return repository.findAllByOrderByCreatedAtDescIdDesc(window).getContent();
        }
        return repository.findAllByActionOrderByCreatedAtDescIdDesc(wanted, window).getContent();
    }

    /**
     * Action code → how many entries, newest activity first — the filter's own data.
     *
     * <p>Counted over the same window the journal can show, and reported as such: this
     * is "what is in the journal I can read", not a total nobody could act on.
     */
    public List<Map<String, Object>> summary() {
        List<AgentAdminAudit> window = repository
                .findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, MAX_WINDOW)).getContent();
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (AgentAdminAudit entry : window) {
            String action = entry.getAction() == null ? "?" : entry.getAction();
            counts.merge(action, 1L, Long::sum);
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("action", e.getKey());
                    row.put("count", e.getValue());
                    out.add(row);
                });
        return out;
    }

    /** Everything except this, so the journal can be read without the console's own noise. */
    public static final String ALL = "ALL";

    /** Upper bound on a single journal read. */
    public static final int MAX_WINDOW = 500;
}
