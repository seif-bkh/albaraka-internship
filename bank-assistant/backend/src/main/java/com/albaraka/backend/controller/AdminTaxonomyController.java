package com.albaraka.backend.controller;

import com.albaraka.backend.dto.CatalogExemplarRequest;
import com.albaraka.backend.dto.CatalogExemplarResponse;
import com.albaraka.backend.dto.CatalogReleaseRequest;
import com.albaraka.backend.dto.CatalogReleaseResponse;
import com.albaraka.backend.model.CatalogRelease;
import com.albaraka.backend.model.CatalogReleaseExemplar;
import com.albaraka.backend.services.TaxonomyService;
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
 * Admin API for the classifier taxonomy (Phase 3.7 / Slice B): exemplar CRUD on
 * the working copy, plus sealing/publishing frozen releases. Read: all staff.
 * Write + seal: SUPER_ADMIN, ADMIN_DOCUMENTS.
 */
@RestController
@RequestMapping("/api/admin/taxonomy")
public class AdminTaxonomyController {

    private static final String READ = "hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN_DOCUMENTS','ROLE_ADMIN_SUPERVISION','ROLE_VIEWER')";
    private static final String WRITE = "hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN_DOCUMENTS')";

    private final TaxonomyService service;

    public AdminTaxonomyController(TaxonomyService service) {
        this.service = service;
    }

    @GetMapping("/vocabulary")
    @PreAuthorize(READ)
    public Map<String, Set<String>> vocabulary() {
        return Map.of("domains", TaxonomyService.DOMAINS, "intents", TaxonomyService.INTENTS);
    }

    // -------------------------------------------------------------- exemplars

    @GetMapping("/exemplars")
    @PreAuthorize(READ)
    public List<CatalogExemplarResponse> list(@RequestParam(required = false) String domain) {
        return service.list(domain).stream().map(CatalogExemplarResponse::from).toList();
    }

    @GetMapping("/exemplars/{id}")
    @PreAuthorize(READ)
    public CatalogExemplarResponse get(@PathVariable Long id) {
        return CatalogExemplarResponse.from(service.get(id));
    }

    @PostMapping("/exemplars")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogExemplarResponse create(@Valid @RequestBody CatalogExemplarRequest req, Authentication auth) {
        return CatalogExemplarResponse.from(service.create(req, auth.getName()));
    }

    @PutMapping("/exemplars/{id}")
    @PreAuthorize(WRITE)
    public CatalogExemplarResponse update(@PathVariable Long id, @Valid @RequestBody CatalogExemplarRequest req,
                                          Authentication auth) {
        return CatalogExemplarResponse.from(service.update(id, req, auth.getName()));
    }

    @DeleteMapping("/exemplars/{id}")
    @PreAuthorize(WRITE)
    public CatalogExemplarResponse deactivate(@PathVariable Long id, Authentication auth) {
        return CatalogExemplarResponse.from(service.setActive(id, false, auth.getName()));
    }

    @PostMapping("/exemplars/{id}/reactivate")
    @PreAuthorize(WRITE)
    public CatalogExemplarResponse reactivate(@PathVariable Long id, Authentication auth) {
        return CatalogExemplarResponse.from(service.setActive(id, true, auth.getName()));
    }

    // --------------------------------------------------------------- releases

    @GetMapping("/releases")
    @PreAuthorize(READ)
    public List<CatalogReleaseResponse> releases() {
        String published = service.publishedVersion();
        return service.releases().stream()
                .map(r -> CatalogReleaseResponse.from(r, r.getVersion().equals(published)))
                .toList();
    }

    @GetMapping("/releases/{id}/exemplars")
    @PreAuthorize(READ)
    public List<CatalogReleaseExemplar> releaseContent(@PathVariable Long id) {
        return service.releaseContent(id);
    }

    @PostMapping("/releases")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogReleaseResponse seal(@Valid @RequestBody(required = false) CatalogReleaseRequest req,
                                       Authentication auth) {
        CatalogRelease r = service.seal(req == null ? null : req.note(), auth.getName());
        return CatalogReleaseResponse.from(r, r.getVersion().equals(service.publishedVersion()));
    }
}
