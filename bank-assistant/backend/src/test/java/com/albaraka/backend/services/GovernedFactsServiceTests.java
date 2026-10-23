package com.albaraka.backend.services;

import com.albaraka.backend.dto.BusinessFactRequest;
import com.albaraka.backend.model.BusinessFact;
import com.albaraka.backend.model.BusinessFactAudit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Business rules of the governed-facts store (3.7) against the real JPA layer
 * on H2: identity is (domain,key), every mutation is audited, edits and
 * deactivations require a reason, and the agent's enum vocabulary is enforced.
 */
@SpringBootTest
@Transactional
class GovernedFactsServiceTests {

    @Autowired GovernedFactsService service;

    private static BusinessFactRequest req(String key, String value, String reason) {
        return new BusinessFactRequest("financement", key, value, "rate", "Sayarat", "fr",
                "grille 2026", null, reason);
    }

    @Test
    void createThenUpdateWritesAnAuditTrailAndKeepsIdentity() {
        BusinessFact created = service.create(req("car_rate", "TMM + 3", null), "alice");
        assertThat(created.getId()).isNotNull();
        assertThat(created.isActive()).isTrue();

        BusinessFact updated = service.update(created.getId(), req("car_rate", "TMM + 2.5", "nouvelle grille"), "bob");
        assertThat(updated.getValue()).isEqualTo("TMM + 2.5");
        assertThat(updated.getUpdatedBy()).isEqualTo("bob");

        List<BusinessFactAudit> trail = service.history(created.getId());
        assertThat(trail).hasSize(2);
        assertThat(trail.get(0).getAction()).isEqualTo(BusinessFactAudit.Action.UPDATE.name());
        assertThat(trail.get(0).getOldValue()).isEqualTo("TMM + 3");
        assertThat(trail.get(1).getAction()).isEqualTo(BusinessFactAudit.Action.CREATE.name());
    }

    @Test
    void duplicateIdentityAndIdentityChangesAreRejected() {
        BusinessFact created = service.create(req("fee_dossier", "50 TND", null), "alice");
        assertThatThrownBy(() -> service.create(req("fee_dossier", "60 TND", null), "alice"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("existe déjà");
        assertThatThrownBy(() -> service.update(created.getId(), req("other_key", "60 TND", "r"), "alice"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pas modifiables");
    }

    @Test
    void reasonIsMandatoryForUpdateAndDeactivation() {
        BusinessFact created = service.create(req("min_income", "800 TND", null), "alice");
        assertThatThrownBy(() -> service.update(created.getId(), req("min_income", "900 TND", " "), "alice"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> service.setActive(created.getId(), false, null, "alice"))
                .isInstanceOf(IllegalArgumentException.class);

        BusinessFact off = service.setActive(created.getId(), false, "produit retiré", "alice");
        assertThat(off.isActive()).isFalse();
        // Idempotent: deactivating twice adds no second audit row.
        service.setActive(created.getId(), false, "encore", "alice");
        assertThat(service.history(created.getId())).hasSize(2);
        assertThat(service.list("FINANCEMENT")).extracting(BusinessFact::getKey).contains("min_income");
    }

    @Test
    void vocabularyOutsideTheAgentEnumsIsRejected() {
        assertThatThrownBy(() -> service.create(new BusinessFactRequest("assurance", "k", "v", "rate",
                null, "fr", null, null, null), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("domain invalide");
        assertThatThrownBy(() -> service.create(new BusinessFactRequest("comptes", "k", "v", "colour",
                null, "fr", null, null, null), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("factType invalide");
        assertThatThrownBy(() -> service.create(new BusinessFactRequest("comptes", "k", "v", "fee",
                null, "de", null, null, null), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("language invalide");
        assertThatThrownBy(() -> service.get(999_999L)).isInstanceOf(NoSuchElementException.class);
    }
}
