package com.albaraka.backend.controller;

import com.albaraka.backend.dto.AdvisorRequestRequest;
import com.albaraka.backend.dto.AdvisorRequestResponse;
import com.albaraka.backend.security.UserDetailsImpl;
import com.albaraka.backend.services.AdvisorRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Portal advisor flows (P0): contact details (governed facts) + appointment /
 * callback requests persisted as EN_ATTENTE — the advisor confirms by phone.
 */
@RestController
@RequestMapping("/api/client/me/advisor")
@PreAuthorize("hasAuthority('ROLE_UTILISATEUR')")
public class ClientAdvisorController {

    private final AdvisorRequestService service;

    public ClientAdvisorController(AdvisorRequestService service) {
        this.service = service;
    }

    /** Active contact facts (phone/e-mail) to display; empty until configured. */
    @GetMapping("/contacts")
    public List<String> contacts() {
        return service.contacts();
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AdvisorRequestResponse create(@AuthenticationPrincipal UserDetailsImpl principal,
                                         @Valid @RequestBody AdvisorRequestRequest req) {
        return AdvisorRequestResponse.from(service.create(principal.getId(), req));
    }
}
