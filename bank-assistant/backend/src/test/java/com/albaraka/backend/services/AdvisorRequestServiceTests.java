package com.albaraka.backend.services;

import com.albaraka.backend.dto.AdvisorRequestRequest;
import com.albaraka.backend.dto.BusinessFactRequest;
import com.albaraka.backend.model.AdvisorRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Advisor requests (P0): per-kind field rules, booking horizon (tomorrow →
 * +60 days), and contacts served from governed facts only.
 */
@SpringBootTest
@Transactional
class AdvisorRequestServiceTests {

    @Autowired AdvisorRequestService service;
    @Autowired GovernedFactsService facts;

    @Test
    void appointmentPersistsTheRequestAsPending() {
        AdvisorRequest r = service.create(1L, new AdvisorRequestRequest(
                "APPOINTMENT", "Financement", "AGENCE", "Agence de Tunis",
                LocalDate.now().plusDays(3), "10:00", null, "Rapprocher un dossier"));

        assertThat(r.getId()).isNotNull();
        assertThat(r.getKind()).isEqualTo(AdvisorRequest.Kind.APPOINTMENT);
        assertThat(r.getStatus()).isEqualTo(AdvisorRequest.Status.EN_ATTENTE);
        assertThat(r.getMeetingMethod()).isEqualTo("AGENCE");
        assertThat(r.getPreferredBranch()).isEqualTo("Agence de Tunis");
        assertThat(r.getMessage()).isEqualTo("Rapprocher un dossier");
    }

    @Test
    void appointmentEnforcesMotifMethodDateAndTime() {
        LocalDate d = LocalDate.now().plusDays(3);
        assertThatThrownBy(() -> service.create(1L,
                new AdvisorRequestRequest("APPOINTMENT", null, "AGENCE", null, d, "10:00", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(1L,
                new AdvisorRequestRequest("APPOINTMENT", "Financement", null, null, d, "10:00", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(1L,
                new AdvisorRequestRequest("APPOINTMENT", "Financement", "AGENCE", null, null, "10:00", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(1L,
                new AdvisorRequestRequest("APPOINTMENT", "Financement", "AGENCE", null, d, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appointmentRespectsTheBookingHorizon() {
        assertThatThrownBy(() -> service.create(1L, new AdvisorRequestRequest(
                "APPOINTMENT", "Financement", "TELEPHONE", null, LocalDate.now(), "10:00", null, null)))
                .isInstanceOf(IllegalArgumentException.class); // today: 24 h notice
        assertThatThrownBy(() -> service.create(1L, new AdvisorRequestRequest(
                "APPOINTMENT", "Financement", "TELEPHONE", null, LocalDate.now().plusDays(90), "10:00", null, null)))
                .isInstanceOf(IllegalArgumentException.class); // beyond 60 days
    }

    @Test
    void callbackRequiresTelephone() {
        assertThatThrownBy(() -> service.create(1L,
                new AdvisorRequestRequest("CALLBACK", null, null, null, null, "15:00", "", "Merci de me rappeler")))
                .isInstanceOf(IllegalArgumentException.class);

        AdvisorRequest r = service.create(1L, new AdvisorRequestRequest(
                "CALLBACK", null, null, null, null, "15:00", "+216 20 123 456", "Merci de me rappeler"));
        assertThat(r.getKind()).isEqualTo(AdvisorRequest.Kind.CALLBACK);
        assertThat(r.getTelephone()).isEqualTo("+216 20 123 456");
        assertThat(r.getPreferredTime()).isEqualTo("15:00");
    }

    @Test
    void contactsComeFromGovernedFactsOnly() {
        assertThat(service.contacts()).isEmpty();

        facts.create(new BusinessFactRequest("service", "bank_phone", "71 18 65 00", "contact",
                "Contact", "fr", "site officiel", null, "test"), "tester");
        facts.create(new BusinessFactRequest("service", "bank_email", "inbox@albarakabank.com.tn", "contact",
                "Contact", "fr", "site officiel", null, "test"), "tester");

        // Deterministic order by fact key: bank_email < bank_phone.
        assertThat(service.contacts()).containsExactly("inbox@albarakabank.com.tn", "71 18 65 00");
    }
}
