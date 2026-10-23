package com.albaraka.backend;

import com.albaraka.backend.repository.AgentAdminAuditRepository;
import com.albaraka.backend.repository.AgenceRepository;
import com.albaraka.backend.repository.BeneficiaireRepository;
import com.albaraka.backend.repository.FinancementRepository;
import com.albaraka.backend.repository.AgentEndpointRepository;
import com.albaraka.backend.repository.BusinessFactRepository;
import com.albaraka.backend.repository.CarteRepository;
import com.albaraka.backend.repository.CatalogExemplarRepository;
import com.albaraka.backend.repository.CatalogReleaseRepository;
import com.albaraka.backend.repository.ClientRepository;
import com.albaraka.backend.repository.CompteRepository;
import com.albaraka.backend.repository.MessageRepository;
import com.albaraka.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that every mapped table really exists in the test schema.
 *
 * <p>Hibernate's schema export only WARNs when a CREATE TABLE fails (H2 used to
 * reject the {@code text[]} column definition on {@code messages}), so
 * {@code contextLoads} stayed green while a table was silently missing. Touching
 * each repository turns that into a hard failure. The client-portal queries
 * double as join-path smoke tests (client → comptes → transactions) while
 * tolerating the seed runner's rows (filtered by an id that never exists).
 */
@SpringBootTest
class SchemaSmokeTests {

    @Autowired MessageRepository messages;
    @Autowired BusinessFactRepository facts;
    @Autowired CatalogExemplarRepository exemplars;
    @Autowired CatalogReleaseRepository releases;
    @Autowired ClientRepository clients;
    @Autowired CompteRepository comptes;
    @Autowired CarteRepository cartes;
    @Autowired TransactionRepository transactions;
    @Autowired AgentEndpointRepository agentEndpoints;
    @Autowired AgentAdminAuditRepository agentAudit;
    @Autowired AgenceRepository agences;
    @Autowired FinancementRepository financements;
    @Autowired BeneficiaireRepository beneficiaires;

    @Test
    void allMappedTablesAreQueryable() {
        assertEquals(0, messages.count());
        assertEquals(0, facts.count());
        assertEquals(0, exemplars.count());
        assertEquals(0, releases.count());
    }

    @Test
    void customerBankingTablesAreQueryable() {
        // V15's new tables: the branch catalogue, the financing contracts and the saved
        // beneficiaries. Counting is enough — a missing table fails here, not in prod.
        assertEquals(0, agences.count());
        assertEquals(0, financements.count());
        assertEquals(0, beneficiaires.count());
        assertEquals(List.of(), financements.findByClientIdOrderByDateDebutDesc(-1L));
        assertEquals(List.of(), beneficiaires.findByClientIdOrderByNomAsc(-1L));
    }

    @Test
    void agentAdminTablesAreQueryable() {
        assertTrue(agentEndpoints.findFirstByOrderByUpdatedAtDescIdDesc().isEmpty(),
                "no seed row: an untouched deployment uses RAGLAB_URL");
        assertEquals(List.of(), agentAudit.findTop100ByOrderByCreatedAtDescIdDesc());
    }

    @Test
    void clientPortalTablesAndJoinPathsAreQueryable() {
        assertTrue(clients.findByEmail("nobody@schema-smoke.invalid").isEmpty());
        assertEquals(List.of(), comptes.findByClientIdOrderByIdAsc(-1L));
        assertEquals(List.of(), cartes.findByClientIdOrderByIdAsc(-1L));
        assertEquals(List.of(),
                transactions.findByCompteClientIdOrderByIdDesc(-1L, PageRequest.of(0, 1)));
    }
}
