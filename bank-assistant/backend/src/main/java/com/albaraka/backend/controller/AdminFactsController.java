package com.albaraka.backend.controller;

import com.albaraka.backend.dto.BusinessFactAuditResponse;
import com.albaraka.backend.dto.BusinessFactRequest;
import com.albaraka.backend.dto.BusinessFactResponse;
import com.albaraka.backend.dto.StatusChangeRequest;
import com.albaraka.backend.services.GovernedFactsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin back-office API for governed business facts (Phase 3.7 / Slice A).
 *
 * <p>Read access: every staff role. Write access: SUPER_ADMIN and
 * ADMIN_DOCUMENTS (decision 2026-09-03 — the team that curates the corpus also
 * curates the figures). {@code DELETE} deactivates; nothing is ever erased.
 */
@RestController
@RequestMapping("/api/admin/facts")
public class AdminFactsController {

    private static final String READ = "hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN_DOCUMENTS','ROLE_ADMIN_SUPERVISION','ROLE_VIEWER')";
    private static final String WRITE = "hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN_DOCUMENTS')";

    private final GovernedFactsService service;

    public AdminFactsController(GovernedFactsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<BusinessFactResponse> list(@RequestParam(required = false) String domain) {
        return service.list(domain).stream().map(BusinessFactResponse::from).toList();
    }

    /** Allowed enum values, so the admin UI never has to hardcode them. */
    @GetMapping("/vocabulary")
    @PreAuthorize(READ)
    public Map<String, Set<String>> vocabulary() {
        return Map.of("domains", GovernedFactsService.DOMAINS,
                      "factTypes", GovernedFactsService.FACT_TYPES);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public BusinessFactResponse get(@PathVariable Long id) {
        return BusinessFactResponse.from(service.get(id));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize(READ)
    public List<BusinessFactAuditResponse> history(@PathVariable Long id) {
        return service.history(id).stream().map(BusinessFactAuditResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessFactResponse create(@Valid @RequestBody BusinessFactRequest req, Authentication auth) {
        return BusinessFactResponse.from(service.create(req, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public BusinessFactResponse update(@PathVariable Long id, @Valid @RequestBody BusinessFactRequest req,
                                       Authentication auth) {
        return BusinessFactResponse.from(service.update(id, req, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE)
    public BusinessFactResponse deactivate(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req,
                                           Authentication auth) {
        return BusinessFactResponse.from(service.setActive(id, false, req.reason(), auth.getName()));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(WRITE)
    public BusinessFactResponse reactivate(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req,
                                           Authentication auth) {
        return BusinessFactResponse.from(service.setActive(id, true, req.reason(), auth.getName()));
    }
}
