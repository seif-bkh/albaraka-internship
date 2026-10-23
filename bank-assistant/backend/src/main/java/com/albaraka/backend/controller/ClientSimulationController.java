package com.albaraka.backend.controller;

import com.albaraka.backend.dto.FinancingSimulationRequest;
import com.albaraka.backend.dto.FinancingSimulationResponse;
import com.albaraka.backend.services.FinancingSimulationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Portal financing simulator (P0). The rate is governed data: only products
 * with an active rate fact are simulatable (AGENTS.md §10).
 */
@RestController
@RequestMapping("/api/client/me/simulation")
@PreAuthorize("hasAuthority('ROLE_UTILISATEUR')")
public class ClientSimulationController {

    private final FinancingSimulationService simulation;

    public ClientSimulationController(FinancingSimulationService simulation) {
        this.simulation = simulation;
    }

    /** Product display names that currently have a governed rate. */
    @GetMapping("/products")
    public List<String> products() {
        return simulation.simulationProducts();
    }

    @PostMapping("/financement")
    public FinancingSimulationResponse simulate(@Valid @RequestBody FinancingSimulationRequest req) {
        return simulation.simulate(req);
    }
}
