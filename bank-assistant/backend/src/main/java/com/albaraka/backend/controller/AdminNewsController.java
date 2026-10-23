package com.albaraka.backend.controller;

import com.albaraka.backend.dto.NewsItemRequest;
import com.albaraka.backend.dto.NewsItemResponse;
import com.albaraka.backend.services.NewsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Back-office management of news items (P2). Same role model as the other
 * admin surfaces: anyone with a read role can list, documents admins write.
 */
@RestController
@RequestMapping("/api/admin/news")
public class AdminNewsController {

    private static final String READ =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN_DOCUMENTS','ROLE_ADMIN_SUPERVISION','ROLE_VIEWER')";
    private static final String WRITE =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN_DOCUMENTS')";

    private final NewsService service;

    public AdminNewsController(NewsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<NewsItemResponse> list() {
        return service.listAll().stream().map(NewsItemResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public NewsItemResponse create(@Valid @RequestBody NewsItemRequest req) {
        return NewsItemResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NewsItemResponse update(@PathVariable Long id, @Valid @RequestBody NewsItemRequest req) {
        return NewsItemResponse.from(service.update(id, req));
    }

    @PostMapping("/{id}/toggle-active")
    @PreAuthorize(WRITE)
    public NewsItemResponse toggleActive(@PathVariable Long id) {
        return NewsItemResponse.from(service.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
